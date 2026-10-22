package com.micatechnologies.minecraft.rcmc.block.signal;

import com.micatechnologies.minecraft.rcmc.physics.transit.LineSignals;
import com.micatechnologies.minecraft.rcmc.physics.transit.SignalAspect;
import com.micatechnologies.minecraft.rcmc.physics.transit.SignalHeads;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * A signal head's state: which way it faces, what it shows, and, when linked, which signal of
 * which line it repeats.
 *
 * <p><b>Linked mode.</b> The head stores a line name and a boundary of that line's block
 * signalling (section, distance, direction of travel) and nothing else. The server works out the
 * aspect from the line's live occupancy every few ticks with {@link SignalHeads} and stores it
 * only when it changes, which is also the only time a client hears about it: through this tile's
 * ordinary update packet, a handful of bytes per change. Clients are not asked to compute it,
 * because occupancy ({@link LineSignals}) is server-side state that the transit sync does not
 * carry.</p>
 *
 * <p><b>Decorative mode.</b> An unlinked head shows whatever aspect it was last set to by
 * right-clicking it with an empty hand. It never relinks on its own, so a decorative head next to
 * a signalled line stays decorative.</p>
 */
public class TileSignalHead extends TileEntity implements ITickable {

    /** How far a head may be from a boundary and still link to it on placement. */
    public static final double LINK_RANGE = 16.0D;

    /** How often a linked head re-reads occupancy, in ticks. */
    private static final int UPDATE_INTERVAL = 5;

    private float facingDegrees;
    /** Horizontal index ({@code EnumFacing.getHorizontalIndex}) of the wall a bracket is fixed to. */
    private int wallSide = -1;
    private SignalAspect aspect = SignalAspect.RED;

    private String lineName = "";
    private int sectionId;
    private double distance;
    private int direction = 1;

    private int countdown;

    @Override
    public void update() {
        if (world == null || world.isRemote || !isLinked()) {
            return;
        }
        if (++countdown < UPDATE_INTERVAL) {
            return;
        }
        countdown = 0;
        SignalAspect now = computeLinkedAspect();
        if (now != aspect) {
            aspect = now;
            pushUpdate();
        }
    }

    /** The aspect the bound signal shows right now; red whenever the binding cannot be resolved. */
    private SignalAspect computeLinkedAspect() {
        RcmcWorldState state = RcmcWorldState.of(world);
        if (state == null) {
            return SignalAspect.RED;
        }
        LineSignals signals = state.transit().signalsFor(lineName);
        TrackSection section = state.network().section(sectionId);
        if (signals == null || section == null) {
            return SignalAspect.RED;
        }
        int index = SignalHeads.protectedBlock(signals.blocks(), sectionId, distance, direction);
        return SignalHeads.aspect(signals.blocks(), signals::isBlockOccupied, index, direction,
            section.totalLength(), section.isClosed());
    }

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

    /** Binds to one signal of a line. Server-side; the aspect follows within a few ticks. */
    public void bindTo(String line, int section, double atDistance, int travelDirection) {
        this.lineName = line;
        this.sectionId = section;
        this.distance = atDistance;
        this.direction = travelDirection >= 0 ? 1 : -1;
        this.aspect = computeLinkedAspect();
        this.countdown = 0;
        pushUpdate();
    }

    /** Back to decorative mode, keeping the aspect it was showing. */
    public void unlink() {
        this.lineName = "";
        pushUpdate();
    }

    /** Decorative mode's right-click: the next aspect in the cycle. */
    public void cycleAspect() {
        aspect = aspect.next();
        pushUpdate();
    }

    /** The yaw, in degrees, of someone looking along {@code direction}: the facing that greets them. */
    public static float yawOf(Vec3 direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }

    public boolean isLinked() {
        return !lineName.isEmpty();
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

    public float facingDegrees() {
        return facingDegrees;
    }

    public void setFacingDegrees(float degrees) {
        this.facingDegrees = degrees;
        pushUpdate();
    }

    public int wallSide() {
        return wallSide;
    }

    public void setWallSide(int horizontalIndex) {
        this.wallSide = horizontalIndex;
        pushUpdate();
    }

    private void pushUpdate() {
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState blockState = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, blockState, blockState, 3);
        }
    }

    // The head draws above and beside its block (a post head reaches into the block above).
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos.add(-1, -1, -1), pos.add(2, 3, 2));
    }

    /** Readable from well down a tunnel, but no further: the distance cull. */
    @Override
    public double getMaxRenderDistanceSquared() {
        return 96.0D * 96.0D;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        facingDegrees = compound.getFloat("Facing");
        wallSide = compound.hasKey("WallSide") ? compound.getInteger("WallSide") : -1;
        aspect = SignalAspect.fromOrdinal(compound.getByte("Aspect"));
        lineName = compound.getString("Line");
        sectionId = compound.getInteger("Section");
        distance = compound.getDouble("Distance");
        direction = compound.getByte("Direction") < 0 ? -1 : 1;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setFloat("Facing", facingDegrees);
        compound.setInteger("WallSide", wallSide);
        compound.setByte("Aspect", (byte) aspect.ordinal());
        compound.setString("Line", lineName);
        compound.setInteger("Section", sectionId);
        compound.setDouble("Distance", distance);
        compound.setByte("Direction", (byte) direction);
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
