package com.micatechnologies.minecraft.rcmc.block.signal;

import com.micatechnologies.minecraft.rcmc.physics.transit.LineService;
import com.micatechnologies.minecraft.rcmc.physics.transit.LineSignals;
import com.micatechnologies.minecraft.rcmc.physics.transit.SignalAspect;
import com.micatechnologies.minecraft.rcmc.physics.transit.SignalHeads;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitPlatform;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitStopController;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.TrackWalk;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

/**
 * A signal head's state: what it looks like, how it is mounted, what controls it, and what it is
 * showing.
 *
 * <p><b>Control.</b> A head is in one of three modes. {@link Control#MANUAL}: it shows what it was
 * last set to, by right-click, its screen or {@code /rcmc signal set}. {@link Control#REDSTONE}: the
 * strongest redstone signal reaching it picks the aspect; see {@link SignalAspect#fromRedstone}.
 * {@link Control#LINKED}: it repeats one signal of a line's block signalling, bound by position
 * (section, distance, direction of travel). Separately from any of these, a route indicator can be
 * bound to a switch, and shows the route the switch is set to.</p>
 *
 * <p><b>Who computes what.</b> The server works out everything a head shows — aspect, whether its
 * lamps are lit, which route, the subsidiary lamp — every few ticks, and stores it only when it
 * changes, which is also the only time a client hears about it, through this tile's ordinary update
 * packet. Clients only draw. Occupancy and services are server-side state the transit sync does not
 * carry, and it keeps every client agreeing.</p>
 *
 * <p><b>Style and mount live here</b>, not in the block's metadata: eighteen styles and eight mounts
 * do not fit in four bits. Heads placed before that keep theirs in metadata, read once by
 * {@link #onLoad}.</p>
 */
public class TileSignalHead extends TileEntity implements ITickable {

    /** What drives a head's aspect. Saved by ordinal; new modes go on the end. */
    public enum Control {
        MANUAL, REDSTONE, LINKED;

        private static final Control[] VALUES = values();

        public static Control fromOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : MANUAL;
        }

        public Control next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }
    }

    /** How far a head may be from a boundary and still link to it on placement. */
    public static final double LINK_RANGE = 16.0D;

    /** How far a route indicator may be from a switch's throat and still link to it. */
    public static final double SWITCH_LINK_RANGE = 48.0D;

    /** How far behind a starter a berthed train may stand and still be the one it sends. */
    private static final double STARTER_REACH = 48.0D;

    /** Ticks left of a train's dwell when a starter's departure indicator lights. */
    private static final int DEPARTURE_WARNING_TICKS = 60;

    /** How often the server re-reads what a head shows, in ticks. */
    private static final int UPDATE_INTERVAL = 5;

    /** Longest leg or mast a head reaches down to the ground with, in blocks. */
    public static final int MAX_REACH = 24;

    public static final int MIN_SPAN = 2;
    public static final int MAX_SPAN = 15;

    /** Most route texts a theatre holds: one per branch of its switch. */
    public static final int MAX_ROUTES = 8;

    // --- Look and mounting. --------------------------------------------------------------------

    private SignalHeadStyle style = SignalHeadStyle.LED;
    private SignalHeadMount mount = SignalHeadMount.POST;
    /** Whether style and mount have been set, so a legacy head's metadata is read only once. */
    private boolean styled;
    private float facingDegrees;
    /** Horizontal index ({@code EnumFacing.getHorizontalIndex}) of the wall a bracket is fixed to. */
    private int wallSide = -1;
    /** {@code 1} for the right-hand side (as a driver facing the head sees it), {@code -1} left. */
    private int hand = 1;
    /** Blocks down to the first solid block, for masts and gantry legs; worked out by the server. */
    private int reach;
    /** Gantry width, or cantilever arm length, in blocks. */
    private int span = 6;

    // --- Control. ------------------------------------------------------------------------------

    private Control control = Control.MANUAL;
    private SignalAspect manualAspect = SignalAspect.RED;
    /** Manual: the subsidiary lamp (lunar white, call-on, departure, feather) is lit. */
    private boolean manualSubsidiary;
    private boolean approachLit;

    private String lineName = "";
    private int sectionId;
    private double distance;
    private int direction = 1;

    /** Switch binding for a route indicator: throat section and end, or section -1 for none. */
    private int switchSection = -1;
    private boolean switchAtEnd;
    /** The branch a feather lights for. */
    private int routeBranch = 1;
    /** What a theatre shows for each branch of its switch. */
    private final List<String> routeTexts = new ArrayList<>();

    // --- What it shows: computed by the server, synced. ----------------------------------------

    private SignalAspect aspect = SignalAspect.RED;
    private boolean lampsLit = true;
    private boolean subsidiaryLit;
    /** Text a theatre shows now; empty for dark. A feather is lit when this is non-empty. */
    private String routeShown = "";

    private int countdown;

    /** Client only: the semaphore arm's drawn angle, eased toward the aspect's. */
    public float armDegrees = Float.NaN;
    /** Client only: when {@link #armDegrees} was last eased, from {@code System.nanoTime}. */
    public long armNanos;

    @Override
    public void onLoad() {
        if (!styled && world != null) {
            // Placed before style and mount moved out of the metadata: read them from it once.
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof BlockSignalHead) {
                int meta = state.getValue(BlockSignalHead.LEGACY);
                style = SignalHeadStyle.fromOrdinal(meta & 3);
                mount = SignalHeadMount.fromOrdinal((meta >> 2) & 3);
                if (style == SignalHeadStyle.DWARF && mount == SignalHeadMount.POST) {
                    // A dwarf "on a post" was always drawn standing on the ground.
                    mount = SignalHeadMount.GROUND;
                }
            }
            styled = true;
            if (lineName.isEmpty() && control == Control.MANUAL) {
                manualAspect = aspect;
            }
            else if (!lineName.isEmpty()) {
                control = Control.LINKED;
            }
            markDirty();
        }
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        if (++countdown < UPDATE_INTERVAL) {
            return;
        }
        countdown = 0;
        refresh();
    }

    /** Works out what the head shows now and syncs it if anything changed. Server side. */
    public void refresh() {
        if (world == null || world.isRemote) {
            return;
        }
        RcmcWorldState state = RcmcWorldState.of(world);
        SignalAspect nowAspect = aspect;
        boolean nowLit = true;
        boolean nowSubsidiary = false;
        int aspects = style.aspects();
        switch (control) {
            case LINKED: {
                nowAspect = SignalAspect.RED;
                LineSignals signals = state == null ? null : state.transit().signalsFor(lineName);
                TrackSection section = state == null ? null : state.network().section(sectionId);
                if (signals != null && section != null) {
                    int index = SignalHeads.protectedBlock(signals.blocks(), sectionId, distance, direction);
                    // A distant repeats the stop signal ahead: it reads the three-aspect answer and is
                    // "on" for anything short of clear.
                    SignalAspect full = SignalHeads.aspect(signals.blocks(), signals::isBlockOccupied,
                        index, direction, section.totalLength(), section.isClosed(),
                        style.isDistant() ? 3 : aspects);
                    nowAspect = style.isDistant()
                        ? (full == SignalAspect.GREEN ? SignalAspect.GREEN : SignalAspect.YELLOW)
                        : full;
                    if (approachLit) {
                        nowLit = SignalHeads.approached(signals.blocks(), signals::isBlockOccupied,
                            index, direction, section.totalLength(), section.isClosed());
                    }
                }
                if (style == SignalHeadStyle.STARTER) {
                    nowSubsidiary = nowAspect != SignalAspect.RED && trainReadyToDepart(state);
                }
                break;
            }
            case REDSTONE: {
                int power = world.getRedstonePowerFromNeighbors(pos);
                nowAspect = style.isDistant()
                    ? (power > 0 ? SignalAspect.GREEN : SignalAspect.YELLOW)
                    : SignalAspect.fromRedstone(power, aspects);
                nowSubsidiary = style.hasSubsidiary() && power > 0;
                break;
            }
            default:
                nowAspect = manualAspect;
                nowSubsidiary = manualSubsidiary;
                break;
        }
        String nowRoute = routeIndication(state, nowAspect, nowSubsidiary);
        if (nowAspect != aspect || nowLit != lampsLit || nowSubsidiary != subsidiaryLit
            || !nowRoute.equals(routeShown)) {
            aspect = nowAspect;
            lampsLit = nowLit;
            subsidiaryLit = nowSubsidiary;
            routeShown = nowRoute;
            pushUpdate();
        }
    }

    /**
     * What a route indicator shows: for a theatre, the text for the branch its switch is set to;
     * for a feather, "on" while its switch is set to its branch. Dark while the signal it goes
     * with is at stop, when it is linked to one. Empty for anything else, or dark.
     */
    private String routeIndication(RcmcWorldState state, SignalAspect shown, boolean subsidiary) {
        if (!style.isRouteIndicator()) {
            return "";
        }
        if (control == Control.LINKED && shown == SignalAspect.RED) {
            return "";
        }
        int selected;
        if (switchSection >= 0 && state != null) {
            TrackNetwork.TrackSwitch sw = state.network().switchAt(new TrackNetwork.SectionEnd(
                switchSection, switchAtEnd ? TrackNetwork.End.END : TrackNetwork.End.START));
            if (sw == null) {
                return "";
            }
            selected = sw.selectedIndex();
        }
        else if (subsidiary || (control == Control.REDSTONE && shown != SignalAspect.RED)) {
            selected = routeBranch;
        }
        else {
            return "";
        }
        if (style == SignalHeadStyle.ROUTE_FEATHER) {
            return selected == routeBranch ? "on" : "";
        }
        return selected >= 0 && selected < routeTexts.size() ? routeTexts.get(selected) : "";
    }

    /**
     * Whether a train of this head's line is berthed just behind it with its dwell nearly done: what
     * lights a platform starter's departure indicator.
     */
    private boolean trainReadyToDepart(RcmcWorldState state) {
        if (state == null) {
            return false;
        }
        TransitSystem transit = state.transit();
        TrackRef head = new TrackRef(sectionId, distance);
        for (Map.Entry<Integer, LineService> entry : transit.services().entrySet()) {
            LineService service = entry.getValue();
            if (!service.line().name().equalsIgnoreCase(lineName)) {
                continue;
            }
            TransitStopController controller = service.controller();
            boolean leaving = controller.phase() == TransitStopController.Phase.DOORS_CLOSING
                || (controller.phase() == TransitStopController.Phase.BOARDING
                    && controller.phaseTicksRemaining() > 0
                    && controller.phaseTicksRemaining() <= DEPARTURE_WARNING_TICKS);
            TransitPlatform berth = service.currentBerth();
            if (!leaving || berth == null || !state.network().hasSection(berth.stopPoint().sectionId())) {
                continue;
            }
            double ahead = TrackWalk.distanceTo(state.network(), berth.stopPoint(), direction, head,
                STARTER_REACH);
            if (!Double.isInfinite(ahead)) {
                return true;
            }
        }
        return false;
    }

    // --- Linking. ------------------------------------------------------------------------------

    /**
     * Binds to the nearest boundary of any signalled line within {@link #LINK_RANGE}. Of the two
     * signals back to back at that boundary, it takes the one whose trains would see this head's
     * face, then turns to face them square. Returns whether it linked.
     */
    public boolean linkToNearestBoundary() {
        RcmcWorldState state = RcmcWorldState.of(world);
        if (state == null) {
            return false;
        }
        TrackNetwork network = state.network();
        TransitSystem transit = state.transit();
        double yaw = Math.toRadians(facingDegrees);
        // Direction a viewer looks to see this head's face.
        double viewX = -Math.sin(yaw);
        double viewZ = Math.cos(yaw);

        String bestLine = null;
        SignalHeads.Boundary best = null;
        Vec3 bestTravel = null;
        double bestDistSq = LINK_RANGE * LINK_RANGE;
        double bestDot = -2.0D;
        for (TransitLine line : transit.lines()) {
            LineSignals signals = transit.signalsFor(line.name());
            if (signals == null) {
                continue;
            }
            for (SignalHeads.Boundary boundary : SignalHeads.boundaries(signals.blocks())) {
                if (!network.hasSection(boundary.sectionId)) {
                    continue;
                }
                TrackFrame frame = network.frameAt(new TrackRef(boundary.sectionId,
                    boundary.distance));
                double dx = frame.position.x - (pos.getX() + 0.5D);
                double dy = frame.position.y - (pos.getY() + 0.5D);
                double dz = frame.position.z - (pos.getZ() + 0.5D);
                double distSq = dx * dx + dy * dy + dz * dz;
                Vec3 travel = boundary.direction > 0 ? frame.forward : frame.forward.scale(-1.0D);
                double dot = travel.x * viewX + travel.z * viewZ;
                // Nearest wins; at the same point (two signals back to back) the one facing us.
                boolean samePoint = Math.abs(distSq - bestDistSq) < 0.25D;
                if (distSq < bestDistSq - 0.25D || (samePoint && dot > bestDot)) {
                    bestDistSq = Math.min(distSq, bestDistSq);
                    bestDot = dot;
                    best = boundary;
                    bestLine = line.name();
                    bestTravel = travel;
                }
            }
        }
        if (best == null) {
            return false;
        }
        bindTo(bestLine, best.sectionId, best.distance, best.direction);
        setFacingDegrees(yawOf(bestTravel));
        return true;
    }

    /** Binds to one signal of a line and switches to linked mode. Server-side. */
    public void bindTo(String line, int section, double atDistance, int travelDirection) {
        this.lineName = line;
        this.sectionId = section;
        this.distance = atDistance;
        this.direction = travelDirection >= 0 ? 1 : -1;
        this.control = Control.LINKED;
        this.countdown = 0;
        refresh();
        pushUpdate();
    }

    /** Back to manual mode, keeping the aspect it was showing. */
    public void unlink() {
        this.lineName = "";
        this.manualAspect = aspect;
        this.control = Control.MANUAL;
        refresh();
        pushUpdate();
    }

    /**
     * Binds a route indicator to the switch whose throat is nearest, within
     * {@link #SWITCH_LINK_RANGE}. Returns whether it found one.
     */
    public boolean linkToNearestSwitch() {
        RcmcWorldState state = RcmcWorldState.of(world);
        if (state == null) {
            return false;
        }
        TrackNetwork network = state.network();
        TrackNetwork.TrackSwitch best = null;
        double bestDistSq = SWITCH_LINK_RANGE * SWITCH_LINK_RANGE;
        for (TrackNetwork.TrackSwitch sw : network.switches()) {
            TrackSection section = network.section(sw.throat().sectionId);
            if (section == null) {
                continue;
            }
            Vec3 at = section.endpointAt(sw.throat().end);
            double dx = at.x - (pos.getX() + 0.5D);
            double dy = at.y - (pos.getY() + 0.5D);
            double dz = at.z - (pos.getZ() + 0.5D);
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = sw;
            }
        }
        if (best == null) {
            return false;
        }
        switchSection = best.throat().sectionId;
        switchAtEnd = best.throat().end == TrackNetwork.End.END;
        while (routeTexts.size() < Math.min(MAX_ROUTES, best.branches().size())) {
            routeTexts.add(routeTexts.isEmpty() ? "" : String.valueOf(routeTexts.size()));
        }
        refresh();
        pushUpdate();
        return true;
    }

    /** The switch this route indicator follows, or {@code null}. */
    public TrackNetwork.SectionEnd linkedSwitch() {
        return switchSection < 0 ? null : new TrackNetwork.SectionEnd(switchSection,
            switchAtEnd ? TrackNetwork.End.END : TrackNetwork.End.START);
    }

    public void unlinkSwitch() {
        switchSection = -1;
        refresh();
        pushUpdate();
    }

    // --- Edits from the head's screen and commands. Server side. --------------------------------

    /** Decorative mode's right-click: the next aspect this style can show. */
    public void cycleAspect() {
        setManualAspect((control == Control.MANUAL ? manualAspect : aspect).next(style.aspects()));
    }

    /** Sets a manual aspect, switching to manual control. */
    public void setManualAspect(SignalAspect wanted) {
        manualAspect = wanted.within(style.aspects());
        // The binding is kept, so switching back to linked needs no relinking.
        control = Control.MANUAL;
        refresh();
        pushUpdate();
    }

    public void setControl(Control wanted) {
        if (wanted == Control.LINKED && lineName.isEmpty() && !linkToNearestBoundary()) {
            return;
        }
        if (wanted == Control.MANUAL) {
            manualAspect = aspect;
        }
        control = wanted;
        refresh();
        pushUpdate();
    }

    public void setManualSubsidiary(boolean lit) {
        manualSubsidiary = lit;
        refresh();
        pushUpdate();
    }

    public void setApproachLit(boolean value) {
        approachLit = value;
        refresh();
        pushUpdate();
    }

    public void setStyle(SignalHeadStyle wanted) {
        style = wanted;
        styled = true;
        manualAspect = manualAspect.within(style.aspects());
        refresh();
        pushUpdate();
    }

    public void setMount(SignalHeadMount wanted) {
        mount = wanted;
        styled = true;
        if (mount == SignalHeadMount.WALL && wallSide < 0) {
            wallSide = BlockSignalHead.findWall(world, pos);
        }
        measureReach();
        pushUpdate();
    }

    public void setHand(int side) {
        hand = side >= 0 ? 1 : -1;
        pushUpdate();
    }

    public void setSpan(int blocks) {
        span = Math.max(MIN_SPAN, Math.min(MAX_SPAN, blocks));
        pushUpdate();
    }

    public void setRouteBranch(int branch) {
        routeBranch = Math.max(0, Math.min(MAX_ROUTES - 1, branch));
        refresh();
        pushUpdate();
    }

    /** Sets what a theatre shows for {@code branch}: a few characters, a number or a letter. */
    public void setRouteText(int branch, String text) {
        if (branch < 0 || branch >= MAX_ROUTES) {
            return;
        }
        while (routeTexts.size() <= branch) {
            routeTexts.add("");
        }
        String trimmed = text == null ? "" : text.trim();
        routeTexts.set(branch, trimmed.length() > 3 ? trimmed.substring(0, 3) : trimmed);
        refresh();
        pushUpdate();
    }

    /**
     * Turns the head to face trains running along the nearest track to it, square; the facing a
     * linked head already takes. Returns whether there was track in reach.
     */
    public boolean faceNearestTrack() {
        RcmcWorldState state = RcmcWorldState.of(world);
        if (state == null) {
            return false;
        }
        Vec3 here = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        TrackFrame best = null;
        double bestDistSq = LINK_RANGE * LINK_RANGE;
        for (TrackSection section : state.network().sections()) {
            double length = section.totalLength();
            for (double s = 0.0D; s <= length; s += 1.0D) {
                Vec3 at = section.positionAtDistance(s);
                double distSq = at.subtract(here).lengthSquared();
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = section.frameAtDistance(s);
                }
            }
        }
        if (best == null) {
            return false;
        }
        // Of the two ways along the track, the one whose trains see this face: the nearer yaw.
        float forward = yawOf(best.forward);
        float back = yawOf(best.forward.scale(-1.0D));
        setFacingDegrees(angleBetween(forward, facingDegrees) <= angleBetween(back, facingDegrees)
            ? forward : back);
        return true;
    }

    private static float angleBetween(float a, float b) {
        float d = Math.abs(((a - b) % 360.0F + 540.0F) % 360.0F - 180.0F);
        return d;
    }

    /** Works out {@link #reach} for a mount that stands on the ground. Server side. */
    public void measureReach() {
        if (world == null || world.isRemote) {
            return;
        }
        int found = 0;
        if (mount.reachesGround()) {
            BlockPos below = pos.down();
            while (found < MAX_REACH && !world.isSideSolid(below, EnumFacing.UP, false)
                && world.isBlockLoaded(below)) {
                found++;
                below = below.down();
            }
        }
        if (found != reach) {
            reach = found;
            pushUpdate();
        }
    }

    // --- Accessors. ----------------------------------------------------------------------------

    /** The yaw, in degrees, of someone looking along {@code direction}: the facing that greets them. */
    public static float yawOf(Vec3 direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }

    public boolean isLinked() {
        return control == Control.LINKED && !lineName.isEmpty();
    }

    public Control control() {
        return control;
    }

    public SignalHeadStyle style() {
        return style;
    }

    public SignalHeadMount mount() {
        return mount;
    }

    public int hand() {
        return hand;
    }

    public int reach() {
        return reach;
    }

    public int span() {
        return span;
    }

    public String lineName() {
        return lineName;
    }

    public int sectionId() {
        return sectionId;
    }

    public double distance() {
        return distance;
    }

    public int direction() {
        return direction;
    }

    public SignalAspect aspect() {
        return aspect;
    }

    public SignalAspect manualAspect() {
        return manualAspect;
    }

    public boolean lampsLit() {
        return lampsLit;
    }

    public boolean subsidiaryLit() {
        return subsidiaryLit;
    }

    public boolean manualSubsidiary() {
        return manualSubsidiary;
    }

    public boolean approachLit() {
        return approachLit;
    }

    public String routeShown() {
        return routeShown;
    }

    public int routeBranch() {
        return routeBranch;
    }

    public List<String> routeTexts() {
        return routeTexts;
    }

    public float facingDegrees() {
        return facingDegrees;
    }

    public void setFacingDegrees(float degrees) {
        this.facingDegrees = ((degrees % 360.0F) + 360.0F) % 360.0F;
        pushUpdate();
    }

    public int wallSide() {
        return wallSide;
    }

    public void setWallSide(int horizontalIndex) {
        this.wallSide = horizontalIndex;
        pushUpdate();
    }

    /**
     * Sets up a freshly placed head; used by placement, which knows the style from the item and
     * the mount from the face clicked, before anything is synced.
     */
    public void initialise(SignalHeadStyle newStyle, SignalHeadMount newMount) {
        style = newStyle;
        mount = newMount;
        styled = true;
        manualAspect = SignalAspect.RED;
        aspect = SignalAspect.RED;
        measureReach();
        markDirty();
    }

    /**
     * A stand-in for drawing an item: a head of {@code itemStyle} on its default mount, showing
     * its most permissive aspect.
     */
    public static TileSignalHead forItem(SignalHeadStyle itemStyle) {
        TileSignalHead head = new TileSignalHead();
        head.style = itemStyle;
        head.mount = itemStyle.defaultMount() == SignalHeadMount.MAST
            ? SignalHeadMount.POST : itemStyle.defaultMount();
        head.styled = true;
        head.facingDegrees = 0.0F;
        head.aspect = SignalAspect.GREEN.within(itemStyle.aspects());
        head.subsidiaryLit = itemStyle.hasSubsidiary();
        head.routeShown = itemStyle == SignalHeadStyle.THEATRE ? "2" : "on";
        return head;
    }

    private void pushUpdate() {
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState blockState = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, blockState, blockState, 3);
        }
    }

    // A gantry or mast reaches well beyond its block, so the render box does too.
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        int across = Math.max(2, span + 1);
        return new AxisAlignedBB(pos.add(-across, -reach - 1, -across), pos.add(across + 1, 4, across + 1));
    }

    /** Readable from well down a tunnel, but no further: the distance cull. */
    @Override
    public double getMaxRenderDistanceSquared() {
        return 128.0D * 128.0D;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("Style")) {
            style = SignalHeadStyle.fromOrdinal(compound.getByte("Style"));
            mount = SignalHeadMount.fromOrdinal(compound.getByte("Mount"));
            styled = true;
        }
        facingDegrees = compound.getFloat("Facing");
        wallSide = compound.hasKey("WallSide") ? compound.getInteger("WallSide") : -1;
        hand = compound.getByte("Hand") < 0 ? -1 : 1;
        reach = compound.getInteger("Reach");
        span = compound.hasKey("Span") ? compound.getInteger("Span") : 6;
        aspect = SignalAspect.fromOrdinal(compound.getByte("Aspect"));
        lampsLit = !compound.hasKey("Lit") || compound.getBoolean("Lit");
        subsidiaryLit = compound.getBoolean("SubsidiaryLit");
        routeShown = compound.getString("RouteShown");
        lineName = compound.getString("Line");
        sectionId = compound.getInteger("Section");
        distance = compound.getDouble("Distance");
        direction = compound.getByte("Direction") < 0 ? -1 : 1;
        if (compound.hasKey("Control")) {
            control = Control.fromOrdinal(compound.getByte("Control"));
            manualAspect = SignalAspect.fromOrdinal(compound.getByte("ManualAspect"));
        }
        else {
            // Before modes: a head with a line was linked, any other showed its stored aspect.
            control = lineName.isEmpty() ? Control.MANUAL : Control.LINKED;
            manualAspect = aspect;
        }
        manualSubsidiary = compound.getBoolean("ManualSubsidiary");
        approachLit = compound.getBoolean("ApproachLit");
        switchSection = compound.hasKey("SwitchSection") ? compound.getInteger("SwitchSection") : -1;
        switchAtEnd = compound.getBoolean("SwitchAtEnd");
        routeBranch = compound.hasKey("RouteBranch") ? compound.getInteger("RouteBranch") : 1;
        routeTexts.clear();
        NBTTagList texts = compound.getTagList("RouteTexts", Constants.NBT.TAG_STRING);
        for (int i = 0; i < texts.tagCount() && i < MAX_ROUTES; i++) {
            routeTexts.add(texts.getStringTagAt(i));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (styled) {
            compound.setByte("Style", (byte) style.ordinal());
            compound.setByte("Mount", (byte) mount.ordinal());
        }
        compound.setFloat("Facing", facingDegrees);
        compound.setInteger("WallSide", wallSide);
        compound.setByte("Hand", (byte) hand);
        compound.setInteger("Reach", reach);
        compound.setInteger("Span", span);
        compound.setByte("Aspect", (byte) aspect.ordinal());
        compound.setBoolean("Lit", lampsLit);
        compound.setBoolean("SubsidiaryLit", subsidiaryLit);
        compound.setString("RouteShown", routeShown);
        compound.setString("Line", lineName);
        compound.setInteger("Section", sectionId);
        compound.setDouble("Distance", distance);
        compound.setByte("Direction", (byte) direction);
        compound.setByte("Control", (byte) control.ordinal());
        compound.setByte("ManualAspect", (byte) manualAspect.ordinal());
        compound.setBoolean("ManualSubsidiary", manualSubsidiary);
        compound.setBoolean("ApproachLit", approachLit);
        compound.setInteger("SwitchSection", switchSection);
        compound.setBoolean("SwitchAtEnd", switchAtEnd);
        compound.setInteger("RouteBranch", routeBranch);
        NBTTagList texts = new NBTTagList();
        for (String text : routeTexts) {
            texts.appendTag(new NBTTagString(text));
        }
        compound.setTag("RouteTexts", texts);
        return compound;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(super.getUpdateTag());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }
}
