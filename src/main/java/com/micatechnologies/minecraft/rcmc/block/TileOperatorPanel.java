package com.micatechnologies.minecraft.rcmc.block;

import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * Which ride an operator panel controls: the coaster whose station is nearest when it is placed.
 *
 * <p>Linking by proximity, like the station speaker does, rather than asking the player to type an
 * id: an operator's booth stands at its ride's station, so "the nearest station" is the right answer
 * whenever the panel is where a panel belongs.</p>
 */
public class TileOperatorPanel extends TileEntity {

    /** Not linked to any ride. */
    public static final int UNLINKED = -1;

    /** How far a panel looks for a station, in blocks. */
    public static final double LINK_RANGE = 24.0D;

    private static final String KEY_SECTION = "Section";

    private int linkedSection = UNLINKED;

    public int linkedSection() {
        return linkedSection;
    }

    public boolean isLinked() {
        return linkedSection != UNLINKED;
    }

    /**
     * Links to the coaster station nearest this panel, within {@link #LINK_RANGE}. Server side.
     *
     * @return whether it is linked now
     */
    public boolean linkToNearestRide() {
        if (world == null || world.isRemote) {
            return isLinked();
        }
        RcmcWorldState state = RcmcWorldState.of(world);
        if (state == null) {
            return false;
        }
        double best = LINK_RANGE * LINK_RANGE;
        int bestSection = UNLINKED;
        for (RideElement element : state.elements().elements()) {
            if (!(element instanceof StationPlatform)
                || state.network().section(element.sectionId()) == null) {
                continue;
            }
            StationPlatform station = (StationPlatform) element;
            Vec3 at;
            try {
                at = state.network().frameAt(
                    new TrackRef(station.sectionId(), station.stopDistance())).position;
            }
            catch (RuntimeException e) {
                continue;
            }
            double dx = at.x - (pos.getX() + 0.5D);
            double dy = at.y - (pos.getY() + 0.5D);
            double dz = at.z - (pos.getZ() + 0.5D);
            double d = dx * dx + dy * dy + dz * dz;
            if (d < best) {
                best = d;
                bestSection = station.sectionId();
            }
        }
        if (bestSection != linkedSection) {
            linkedSection = bestSection;
            markDirty();
        }
        return isLinked();
    }

    /**
     * Whether the ride this panel was linked to still exists; relinks if it does not, since a panel
     * pointing at a deleted coaster (or at a reused section id) must not control whatever took its
     * place.
     */
    public boolean ensureLinked() {
        if (world == null || world.isRemote) {
            return isLinked();
        }
        RcmcWorldState state = RcmcWorldState.of(world);
        if (isLinked() && state != null && state.network().section(linkedSection) != null) {
            for (RideElement element : state.elements().elements()) {
                if (element instanceof StationPlatform && element.sectionId() == linkedSection) {
                    return true;
                }
            }
        }
        return linkToNearestRide();
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        linkedSection = compound.hasKey(KEY_SECTION) ? compound.getInteger(KEY_SECTION) : UNLINKED;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger(KEY_SECTION, linkedSection);
        return compound;
    }
}
