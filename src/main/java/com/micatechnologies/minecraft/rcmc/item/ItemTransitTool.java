package com.micatechnologies.minecraft.rcmc.item;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.RcmcTab;
import com.micatechnologies.minecraft.rcmc.builder.TransitBuildSession;
import com.micatechnologies.minecraft.rcmc.net.PacketTrackSync;
import com.micatechnologies.minecraft.rcmc.net.PacketTransitSync;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackPicker;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.TrackStyleIds;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Builds metro systems by pointing at track: stations, lines, switches and electrification.
 *
 * <p>Controls:</p>
 * <ul>
 *   <li><b>G</b> — cycle mode: station → platform → line → switch → track style.</li>
 *   <li><b>Right-click track</b> — do this mode's thing at the point aimed at.</li>
 *   <li><b>C</b> — commit what is being assembled (create the line, throw in the switch).</li>
 *   <li><b>V</b> — in line mode, cycle loop / shuttle / turnback.</li>
 *   <li><b>Sneak + right-click track</b> — the mode's destructive counterpart (remove a stop).</li>
 *   <li><b>Sneak + right-click air</b> — abandon what is being assembled.</li>
 * </ul>
 *
 * <p><b>Names come from the item's own name.</b> Rename the tool in an anvil to "Central" and the
 * next station placed is called Central; rename it to "Orange" and the next line created is the
 * Orange line. 1.12.2 has no text-entry affordance for an item, and the alternative — placing an
 * unnamed thing and then typing a chat command to rename it — would leave the command as the real
 * authoring path, which is the exact problem this tool exists to remove. Unnamed falls back to
 * "Station N" / "Line N", so the tool is usable the moment it is picked up.</p>
 *
 * <p>Everything here is server-side and goes through operations that already existed and were
 * already tested — {@code addStation}, {@code addLine}, {@code addSwitch}, {@code withStyle}. The
 * phase is about reachability, not new transit behaviour: several of these had no caller outside a
 * save codec, which is why a metro could be simulated but not built.</p>
 */
public class ItemTransitTool extends Item {

    public static final String NAME = "transit_tool";

    /** How far down the look ray to search for track — matches the editor wand, deliberately. */
    private static final double LOOK_RANGE = 64.0D;

    /** How far off the look ray track may sit and still count as aimed at. */
    private static final double AIM_RADIUS = 1.5D;

    /** Fallback radius when a block was clicked rather than track itself. */
    private static final double PICK_RADIUS = 4.0D;

    /**
     * How close a click must land to a section end for it to count as picking that end, in blocks.
     *
     * <p>Switch ends are points, not spans, so unlike station placement there is no sensible
     * "nearest" — a click a hundred blocks down the section is not a vague request for its end, it
     * is a miss, and telling the player so beats silently switching the wrong thing.</p>
     */
    private static final double END_PICK_RADIUS = 6.0D;

    /** The styles the tool cycles: the transit looks, then back to the plain coaster default. */
    private static final String[] STYLE_CYCLE = {
        TrackStyleIds.TRANSIT, TrackStyleIds.TRANSIT_CATENARY,
        TrackStyleIds.TRANSIT_PORTAL, TrackStyleIds.TRANSIT_TUNNEL, null
    };

    public ItemTransitTool() {
        setRegistryName(RcmcConstants.MOD_NAMESPACE, NAME);
        setTranslationKey(RcmcConstants.MOD_NAMESPACE + "." + NAME);
        setCreativeTab(RcmcTab.RCMC_TAB);
        setMaxStackSize(1);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }
        RcmcWorldState state = RcmcWorldState.of(world);
        TrackPicker.Hit hit = pickAlongLook(player, state);
        if (hit == null) {
            Vec3 query = new Vec3(pos.getX() + hitX, pos.getY() + hitY, pos.getZ() + hitZ);
            hit = TrackPicker.pick(state.network(), query, PICK_RADIUS);
        }
        if (hit == null) {
            say(player, TextFormatting.GRAY, "No track under the cursor, and none within "
                + (int) PICK_RADIUS + " blocks of where you clicked.");
            return EnumActionResult.SUCCESS;
        }
        apply(player, world, state, hit);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (world.isRemote) {
            return new ActionResult<>(EnumActionResult.SUCCESS, held);
        }
        RcmcWorldState state = RcmcWorldState.of(world);

        // Track is rendered geometry with nothing solid behind it, so aiming at it is a vanilla
        // MISS and lands here rather than in onItemUse — the bug that once made the track wand
        // appear completely dead.
        TrackPicker.Hit hit = pickAlongLook(player, state);
        if (hit != null) {
            apply(player, world, state, hit);
            return new ActionResult<>(EnumActionResult.SUCCESS, held);
        }
        if (player.isSneaking()) {
            TransitBuildSession session = TransitBuildSession.of(player.getUniqueID());
            session.clearPending();
            say(player, TextFormatting.GRAY, "Cleared. Mode: " + session.mode().label());
        }
        else {
            say(player, TextFormatting.GRAY, "No track under the cursor.");
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, held);
    }

    private static void apply(EntityPlayer player, World world, RcmcWorldState state,
                              TrackPicker.Hit hit) {
        TransitBuildSession session = TransitBuildSession.of(player.getUniqueID());
        switch (session.mode()) {
            case STATION:
                station(player, world, state, hit);
                return;
            case PLATFORM:
                platform(player, world, state, hit);
                return;
            case LINE:
                pickStop(player, state, session, hit);
                return;
            case SWITCH:
                pickSwitchEnd(player, state, session, hit);
                return;
            case STYLE:
            default:
                cycleStyle(player, world, state, hit);
        }
    }

    // --- Station mode. -------------------------------------------------------------------------

    private static void station(EntityPlayer player, World world, RcmcWorldState state,
                                TrackPicker.Hit hit) {
        TransitSystem transit = state.transit();
        if (player.isSneaking()) {
            TransitStation nearest = nearestStation(transit, hit.ref);
            if (nearest == null) {
                say(player, TextFormatting.GRAY, "No station near there to remove.");
                return;
            }
            transit.removeStation(nearest.name());
            syncTransit(world, state);
            say(player, TextFormatting.YELLOW, "Removed station " + nearest.name()
                + ". Lines already created keep their own copy of it.");
            return;
        }

        String name = chosenName(player, "Station", transit.stations().size() + 1);
        boolean moved = transit.station(name) != null;
        transit.addStation(new TransitStation(name, hit.ref));
        syncTransit(world, state);
        say(player, TextFormatting.GREEN, (moved ? "Moved station " : "Station ") + name
            + " — section " + hit.ref.sectionId() + " @ "
            + String.format("%.1f", hit.ref.distance()));
        if (!moved) {
            say(player, TextFormatting.DARK_GRAY,
                "  Rename this tool in an anvil to name the next station.");
        }
    }

    // --- Platform mode. ------------------------------------------------------------------------

    /**
     * Adds a berth to the station this track belongs to, or removes the one clicked.
     *
     * <p>The berth's whole purpose is to be on a <em>different</em> track from the station's first
     * one, so "which station is this?" cannot be answered along the rails the way station mode
     * answers it: the other side of an island can be hundreds of blocks away by track even though
     * it is six blocks away across the platform. It is answered in world space instead, which is
     * the same thing a builder means by "this station" and the same rule the platform signs use to
     * link themselves.</p>
     */
    private static void platform(EntityPlayer player, World world, RcmcWorldState state,
                                 TrackPicker.Hit hit) {
        TransitSystem transit = state.transit();
        TransitStation station = nearestStationInWorld(state, hit.ref);
        if (station == null) {
            say(player, TextFormatting.GRAY,
                "No station near there. Switch to station mode with G and place one first.");
            return;
        }
        if (player.isSneaking()) {
            int index = nearestPlatformIndex(state, station, hit.ref);
            if (station.platformCount() == 1) {
                say(player, TextFormatting.GRAY, station.name()
                    + " has only one platform — remove the station itself in station mode.");
                return;
            }
            java.util.List<com.micatechnologies.minecraft.rcmc.physics.transit.TransitPlatform>
                kept = new ArrayList<>(station.platforms());
            kept.remove(index);
            transit.addStation(new TransitStation(station.name(), kept));
            syncTransit(world, state);
            say(player, TextFormatting.YELLOW,
                "Removed a platform from " + station.name() + " — " + kept.size() + " left.");
            return;
        }
        if (station.platformAt(hit.ref) != null) {
            say(player, TextFormatting.GRAY,
                station.name() + " already has a platform at exactly that point.");
            return;
        }
        String label = chosenName(player, "", station.platformCount() + 1).trim();
        com.micatechnologies.minecraft.rcmc.physics.transit.TransitPlatform added =
            new com.micatechnologies.minecraft.rcmc.physics.transit.TransitPlatform(hit.ref,
                com.micatechnologies.minecraft.rcmc.physics.transit.DoorSide.BOTH, label);
        com.micatechnologies.minecraft.rcmc.physics.transit.DoorSide detected =
            com.micatechnologies.minecraft.rcmc.world.PlatformSide.detect(
                world, state.network(), added);
        if (detected != null) {
            added = added.withDoorSide(detected);
        }
        transit.addStation(station.withPlatform(added));
        syncTransit(world, state);
        say(player, TextFormatting.GREEN, "Added platform " + (label.isEmpty() ? "" : "'" + label
            + "' ") + "to " + station.name() + " — section " + hit.ref.sectionId() + " @ "
            + String.format("%.1f", hit.ref.distance())
            + (detected == null ? "" : ", doors "
                + detected.name().toLowerCase(java.util.Locale.ROOT)));
        if (detected == null) {
            say(player, TextFormatting.DARK_GRAY,
                "  No decking found beside it — set the side with /rcmc station doors.");
        }
    }

    /** World-space distance from a track point to the nearest of a station's berths, squared. */
    private static double worldDistanceSq(RcmcWorldState state, TransitStation station,
                                          TrackRef ref) {
        TrackNetwork network = state.network();
        if (!network.hasSection(ref.sectionId())) {
            return Double.POSITIVE_INFINITY;
        }
        Vec3 at = network.frameAt(ref).position;
        double best = Double.POSITIVE_INFINITY;
        for (com.micatechnologies.minecraft.rcmc.physics.transit.TransitPlatform platform
            : station.platforms()) {
            if (!network.hasSection(platform.stopPoint().sectionId())) {
                continue;
            }
            Vec3 p = network.frameAt(platform.stopPoint()).position;
            double dx = p.x - at.x;
            double dy = p.y - at.y;
            double dz = p.z - at.z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < best) {
                best = d;
            }
        }
        return best;
    }

    /** The station physically nearest a clicked track point, or {@code null} if none is close. */
    /** The station a platform click near {@code ref} adds to. Public for the tool's preview. */
    public static TransitStation nearestStationInWorld(RcmcWorldState state, TrackRef ref) {
        TransitStation best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (TransitStation station : state.transit().stations()) {
            double d = worldDistanceSq(state, station, ref);
            if (d < bestDistance) {
                bestDistance = d;
                best = station;
            }
        }
        // Generous next to station mode's 16: the far side of a wide island, plus the length of a
        // platform, is a long way from the stop point it belongs to and still obviously the same
        // station to anyone standing on it.
        return bestDistance <= 48.0D * 48.0D ? best : null;
    }

    /** Which of a station's berths is physically nearest a clicked point. */
    private static int nearestPlatformIndex(RcmcWorldState state, TransitStation station,
                                            TrackRef ref) {
        TrackNetwork network = state.network();
        Vec3 at = network.frameAt(ref).position;
        int best = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < station.platformCount(); i++) {
            TrackRef stop = station.platform(i).stopPoint();
            if (!network.hasSection(stop.sectionId())) {
                continue;
            }
            Vec3 p = network.frameAt(stop).position;
            double dx = p.x - at.x;
            double dy = p.y - at.y;
            double dz = p.z - at.z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    /** The authored station nearest a clicked point, on the same section, or {@code null}. */
    /** The station a station or line click at {@code ref} means. Public for the tool's preview. */
    public static TransitStation nearestStation(TransitSystem transit, TrackRef ref) {
        TransitStation best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (TransitStation station : transit.stations()) {
            if (station.stopPoint().sectionId() != ref.sectionId()) {
                continue;
            }
            double d = Math.abs(station.stopPoint().distance() - ref.distance());
            if (d < bestDistance) {
                bestDistance = d;
                best = station;
            }
        }
        // Same reasoning as END_PICK_RADIUS: beyond this a click is a miss, not a vague gesture.
        return bestDistance <= 16.0D ? best : null;
    }

    // --- Line mode. ----------------------------------------------------------------------------

    private static void pickStop(EntityPlayer player, RcmcWorldState state,
                                 TransitBuildSession session, TrackPicker.Hit hit) {
        TransitStation station = nearestStation(state.transit(), hit.ref);
        if (station == null) {
            say(player, TextFormatting.GRAY,
                "No station near there. Switch to station mode with G and place one first.");
            return;
        }
        if (!session.addStop(station.name())) {
            say(player, TextFormatting.GRAY, station.name() + " is already the last stop picked.");
            return;
        }
        pushTool(player, session);
        say(player, TextFormatting.AQUA, "Stop " + session.lineStops().size() + ": " + station.name());
        say(player, TextFormatting.DARK_GRAY, "  " + session.pendingSummary()
            + "   C to create, V for loop/shuttle/turnback.");
    }

    /** C in line mode: turns the picked stops into a real line. */
    private static void commitLine(EntityPlayer player, World world, RcmcWorldState state,
                                   TransitBuildSession session) {
        if (!session.canCommitLine()) {
            say(player, TextFormatting.GRAY,
                "Pick at least two different stations first — a line needs somewhere to go.");
            return;
        }
        TransitSystem transit = state.transit();
        List<TransitStation> stops = new ArrayList<>();
        for (String name : session.lineStops()) {
            TransitStation station = transit.station(name);
            if (station == null) {
                say(player, TextFormatting.RED, "Station " + name
                    + " was removed while you were building. Start the line again.");
                session.clearPending();
                pushTool(player, session);
                return;
            }
            stops.add(station);
        }
        String name = chosenName(player, "Line", transit.lines().size() + 1);
        transit.addLine(TransitLine.of(name, stops, session.kind()));
        session.clearPending();
        pushTool(player, session);
        syncTransit(world, state);
        say(player, TextFormatting.GREEN, "Line " + name + " created — " + stops.size()
            + " stops, " + session.kind().label() + ".");
        say(player, TextFormatting.DARK_GRAY, "  Run it: /rcmc train <section> 3 0 metro,"
            + " then /rcmc line start " + name + " <trainId>");
    }

    // --- Switch mode. --------------------------------------------------------------------------

    private static void pickSwitchEnd(EntityPlayer player, RcmcWorldState state,
                                      TransitBuildSession session, TrackPicker.Hit hit) {
        TrackNetwork.SectionEnd end = nearestEnd(state.network(), hit.ref);
        if (end == null) {
            say(player, TextFormatting.GRAY, "No section end within " + (int) END_PICK_RADIUS
                + " blocks of there — a switch is made at the ends of sections.");
            return;
        }
        boolean first = session.switchThroat() == null;
        if (!session.addSwitchEnd(end)) {
            say(player, TextFormatting.GRAY, end + " is already picked.");
            return;
        }
        say(player, TextFormatting.AQUA, (first ? "Throat: " : "Branch: ") + end);
        say(player, TextFormatting.DARK_GRAY, "  " + session.pendingSummary()
            + (session.canCommitSwitch() ? "   C to create." : "   pick another branch."));
    }

    /**
     * The end of {@code ref}'s own section nearest the click, or {@code null} if the click was not
     * near either end. Restricted to the clicked section on purpose: the ends that meet at a
     * junction are within a fraction of a block of each other, so "nearest end in the network"
     * would be a coin toss between them.
     */
    private static TrackNetwork.SectionEnd nearestEnd(TrackNetwork network, TrackRef ref) {
        TrackSection section = network.section(ref.sectionId());
        if (section == null || section.isClosed()) {
            return null;
        }
        double toStart = ref.distance();
        double toEnd = section.totalLength() - ref.distance();
        if (Math.min(toStart, toEnd) > END_PICK_RADIUS) {
            return null;
        }
        return new TrackNetwork.SectionEnd(section.id(),
            toStart <= toEnd ? TrackNetwork.End.START : TrackNetwork.End.END);
    }

    /** C in switch mode: creates the switch, with the network's own geometry checks reported. */
    private static void commitSwitch(EntityPlayer player, World world, RcmcWorldState state,
                                     TransitBuildSession session) {
        if (!session.canCommitSwitch()) {
            say(player, TextFormatting.GRAY,
                "A switch needs a throat and at least two branches — click the ends first.");
            return;
        }
        try {
            state.network().addSwitch(session.switchThroat(), session.switchBranches());
        }
        catch (IllegalArgumentException e) {
            // The network's own rules: ends must be free, sections open, endpoints within the join
            // gap. Reporting verbatim beats paraphrasing — it names the offending end.
            say(player, TextFormatting.RED, e.getMessage());
            return;
        }
        TrackNetwork.SectionEnd throat = session.switchThroat();
        int branches = session.switchBranches().size();
        session.clearPending();
        state.markTrackDirty(world);
        syncTrack(world, state);
        say(player, TextFormatting.GREEN, "Switch created at " + throat + " with " + branches
            + " branches.");
        say(player, TextFormatting.DARK_GRAY,
            "  Throw it with /rcmc switch throw " + throat.sectionId + " "
                + throat.end.name().toLowerCase(java.util.Locale.ROOT));
    }

    // --- Style mode. ---------------------------------------------------------------------------

    private static void cycleStyle(EntityPlayer player, World world, RcmcWorldState state,
                                   TrackPicker.Hit hit) {
        TrackSection section = state.network().section(hit.ref.sectionId());
        if (section == null) {
            return;
        }
        String current = section.styleId();
        int index = 0;
        for (int i = 0; i < STYLE_CYCLE.length; i++) {
            if (STYLE_CYCLE[i] == null ? current == null : STYLE_CYCLE[i].equals(current)) {
                index = i + 1;
                break;
            }
        }
        String next = STYLE_CYCLE[index % STYLE_CYCLE.length];
        // replaceSection keeps joins and switches, and a fresh instance invalidates the client
        // mesh cache, which keys on section identity.
        state.network().replaceSection(section.withStyle(next));
        state.markTrackDirty(world);
        syncTrack(world, state);
        say(player, TextFormatting.GREEN, "Section " + section.id() + " style → "
            + (next == null ? "coaster" : next));
    }

    // --- Keybind entry points. -----------------------------------------------------------------

    /** G: move to the next mode. */
    public static void cycleMode(EntityPlayer player) {
        TransitBuildSession session = TransitBuildSession.of(player.getUniqueID());
        TransitBuildSession.Mode mode = session.cycleMode();
        pushTool(player, session);
        say(player, TextFormatting.AQUA, "Transit tool: " + mode.label());
        say(player, TextFormatting.DARK_GRAY, "  " + mode.help());
    }

    /** C: commit whatever the current mode is assembling. */
    public static void commit(EntityPlayer player, World world) {
        RcmcWorldState state = RcmcWorldState.of(world);
        TransitBuildSession session = TransitBuildSession.of(player.getUniqueID());
        switch (session.mode()) {
            case LINE:
                commitLine(player, world, state, session);
                return;
            case SWITCH:
                commitSwitch(player, world, state, session);
                return;
            case STATION:
            case PLATFORM:
            case STYLE:
            default:
                say(player, TextFormatting.GRAY, session.mode().label()
                    + " mode applies immediately — nothing to commit.");
        }
    }

    /** V: loop, shuttle or turnback, for the line being assembled. */
    public static void toggleLineKind(EntityPlayer player) {
        TransitBuildSession session = TransitBuildSession.of(player.getUniqueID());
        if (session.mode() != TransitBuildSession.Mode.LINE) {
            say(player, TextFormatting.GRAY, "Loop/shuttle only applies in line mode.");
            return;
        }
        say(player, TextFormatting.AQUA, "Line kind: " + session.cycleKind().label());
    }

    // --- Shared. -------------------------------------------------------------------------------

    /**
     * The name for the next thing created: the tool's anvil name if it has one, else
     * {@code <kind> <n>}. Trimmed and length-capped, because it becomes a registry key that gets
     * rendered on a sign and shown on a board.
     */
    private static String chosenName(EntityPlayer player, String kind, int ordinal) {
        ItemStack held = player.getHeldItemMainhand();
        if (held.getItem() instanceof ItemTransitTool && held.hasDisplayName()) {
            String name = held.getDisplayName().trim();
            if (!name.isEmpty()) {
                return name.length() > 24 ? name.substring(0, 24) : name;
            }
        }
        return kind + " " + ordinal;
    }

    private static TrackPicker.Hit pickAlongLook(EntityPlayer player, RcmcWorldState state) {
        net.minecraft.util.math.Vec3d eyes = player.getPositionEyes(1.0F);
        net.minecraft.util.math.Vec3d look = player.getLookVec();
        return TrackPicker.pickAlongRay(state.network(),
            new Vec3(eyes.x, eyes.y, eyes.z), new Vec3(look.x, look.y, look.z),
            LOOK_RANGE, AIM_RADIUS);
    }

    /** Tells the player's client which mode the tool is in and the stops picked, for its preview. */
    static void pushTool(EntityPlayer player, TransitBuildSession session) {
        if (player instanceof net.minecraft.entity.player.EntityPlayerMP) {
            RcmcNetwork.sendTo(new com.micatechnologies.minecraft.rcmc.net.PacketTransitToolSync(
                session.mode().ordinal(), session.lineStops()),
                (net.minecraft.entity.player.EntityPlayerMP) player);
        }
    }

    private static void syncTransit(World world, RcmcWorldState state) {
        state.markTrackDirty(world);
        RcmcNetwork.sendToAllIn(new PacketTransitSync(state.transit()),
            world.provider.getDimension());
    }

    private static void syncTrack(World world, RcmcWorldState state) {
        RcmcNetwork.sendToAllIn(new PacketTrackSync(state.network()),
            world.provider.getDimension());
    }

    private static void say(EntityPlayer player, TextFormatting colour, String message) {
        player.sendMessage(new TextComponentString(colour + message));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + "G: mode — station, platform, line, switch, track style");
        tooltip.add(TextFormatting.GRAY + "Right-click track: apply the current mode");
        tooltip.add(TextFormatting.GRAY + "C: create the line / switch being assembled");
        tooltip.add(TextFormatting.GRAY + "V: loop / shuttle / turnback (line mode)");
        tooltip.add(TextFormatting.GRAY + "Sneak + right-click track: remove a stop");
        tooltip.add(TextFormatting.GRAY + "Sneak + right-click air: start over");
        tooltip.add(TextFormatting.DARK_GRAY + "Anvil-rename this tool to name what you place");
    }
}
