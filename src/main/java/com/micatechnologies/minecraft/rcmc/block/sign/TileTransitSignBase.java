package com.micatechnologies.minecraft.rcmc.block.sign;

import com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/**
 * Shared state for both transit signs: which station they are linked to, and which way they
 * face.
 *
 * <p><b>Linking is automatic, and by name.</b> On placement the sign binds to the nearest
 * station's <em>name</em> — never to a snapshot of its data. Every render resolves that name
 * against the live (synced) transit registry, so a renamed line, an added stop, or a re-routed
 * service updates every sign in the network the moment the registry syncs. A sign whose station
 * no longer exists renders as unlinked rather than stale — the whole point of the design.</p>
 *
 * <p>Facing lives here rather than in a blockstate so the block needs no properties (and thus no
 * per-facing model variants) — the renderer rotates by it, exactly as the car renderer orients
 * by a frame instead of entity yaw.</p>
 */
public abstract class TileTransitSignBase extends TileEntity
    implements net.minecraft.util.ITickable {

    /** How far away a station may be and still count as "this platform's station". */
    private static final double LINK_RANGE = 48.0D;

    /** How often an unlinked sign retries its link, in ticks. */
    private static final int RELINK_INTERVAL = 40;

    private String stationName = "";
    private float facingDegrees;
    private int relinkCountdown;

    /**
     * An unlinked sign keeps trying to link itself. This is what makes a sign placed by
     * {@code /setblock} (which never fires {@code onBlockPlacedBy}), or placed before its
     * station existed, come alive on its own instead of needing a ritual right-click — the
     * signs' whole design is "resolve from the live registry", and the link should be no
     * exception. Linked signs do nothing here; the retry is server-side and cheap.
     */
    @Override
    public void update() {
        if (world == null || world.isRemote || isLinked()) {
            return;
        }
        if (++relinkCountdown >= RELINK_INTERVAL) {
            relinkCountdown = 0;
            linkToNearestStation();
        }
    }

    /**
     * Binds to the nearest station (by its stop point's world position) within range, or to
     * nothing if none. Server-side; the result reaches clients through the update packet.
     */
    public void linkToNearestStation() {
        RcmcWorldState state = RcmcWorldState.of(world);
        if (state == null) {
            return;
        }
        TrackNetwork network = state.network();
        String nearest = "";
        Vec3 nearestForward = null;
        double best = LINK_RANGE * LINK_RANGE;
        for (TransitStation station : state.transit().stations()) {
            if (!network.hasSection(station.stopPoint().sectionId())) {
                continue;
            }
            com.micatechnologies.minecraft.rcmc.track.math.TrackFrame frame =
                network.frameAt(station.stopPoint());
            Vec3 at = frame.position;
            double dx = at.x - (pos.getX() + 0.5D);
            double dy = at.y - (pos.getY() + 0.5D);
            double dz = at.z - (pos.getZ() + 0.5D);
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (distanceSq < best) {
                best = distanceSq;
                nearest = station.name();
                nearestForward = frame.forward;
            }
        }
        // Only announce a change — the periodic relink retry must not mark the world dirty
        // and respam watching clients every interval while there is still nothing to find.
        if (!nearest.equals(stationName)) {
            this.stationName = nearest;
            if (nearestForward != null) {
                // Turn the sign to look down the platform (its face perpendicular to the track), so
                // a ceiling board or post sign reads head-on to anyone standing along the platform
                // rather than edge-on across the track. Placed signs get this on link, so even a
                // /setblock (which never runs onBlockPlacedBy) still ends up oriented correctly.
                this.facingDegrees = facingAlongTrack(nearestForward);
            }
            pushUpdate();
        }
    }

    public String stationName() {
        return stationName;
    }

    public boolean isLinked() {
        return !stationName.isEmpty();
    }

    public float facingDegrees() {
        return facingDegrees;
    }

    public void setFacingDegrees(float degrees) {
        this.facingDegrees = degrees;
        pushUpdate();
    }

    /**
     * The 90°-quantised facing that turns a sign's face to look along the track it serves.
     *
     * <p>Uses the same quantised-yaw convention {@code onBlockPlacedBy} derives from a placer, but
     * from the track heading instead — so the panel's normal points down the platform. The panel is
     * double-sided, so either of the two headings along the track reads correctly; quantising to the
     * nearest quarter turn keeps a row of signs aligned.</p>
     */
    private static float facingAlongTrack(Vec3 forward) {
        double yaw = Math.toDegrees(Math.atan2(-forward.x, forward.z));
        int quarter = net.minecraft.util.math.MathHelper.floor(yaw * 4.0D / 360.0D + 0.5D) & 3;
        return quarter * 90.0F;
    }

    /** Marks dirty and notifies, so the change saves and reaches watching clients. */
    protected void pushUpdate() {
        markDirty();
        if (world != null) {
            IBlockState blockState = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, blockState, blockState, 3);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        stationName = compound.getString("Station");
        facingDegrees = compound.getFloat("Facing");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setString("Station", stationName);
        compound.setFloat("Facing", facingDegrees);
        return compound;
    }

    // Standard TE sync plumbing: full-tag updates, applied on arrival. The payload is two small
    // fields; delta encoding would be pure ceremony.

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
