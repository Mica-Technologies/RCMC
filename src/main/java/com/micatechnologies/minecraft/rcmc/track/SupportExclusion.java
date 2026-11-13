package com.micatechnologies.minecraft.rcmc.track;

/**
 * A box of ground, in whole blocks, that a section's automatic support columns stay out of: no
 * column stands with its foot inside it.
 *
 * <p>Columns are spaced along the track with no regard for what is underneath, so track crossing a
 * walkway put columns in the walkway. A real coaster spans a path and is held up either side; this
 * is how a builder says where the path is. It is horizontal only, the whole height of the world,
 * because a column runs the whole height from the track to the ground.</p>
 *
 * <p>Part of a {@link TrackSection}, so it is saved, synced to clients and undone with the track
 * it belongs to, and the support cache — which is keyed on the section instance on both sides —
 * sees a change to it as the edit it is.</p>
 */
public final class SupportExclusion {

    public final int minX;
    public final int minZ;
    public final int maxX;
    public final int maxZ;

    /** Corners in any order; both are inside the box. */
    public SupportExclusion(int x0, int z0, int x1, int z1) {
        this.minX = Math.min(x0, x1);
        this.minZ = Math.min(z0, z1);
        this.maxX = Math.max(x0, x1);
        this.maxZ = Math.max(z0, z1);
    }

    /** Whether the block column holding the point {@code (x, z)} is inside this box. */
    public boolean contains(double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        return bx >= minX && bx <= maxX && bz >= minZ && bz <= maxZ;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SupportExclusion)) {
            return false;
        }
        SupportExclusion that = (SupportExclusion) other;
        return minX == that.minX && minZ == that.minZ && maxX == that.maxX && maxZ == that.maxZ;
    }

    @Override
    public int hashCode() {
        return ((minX * 31 + minZ) * 31 + maxX) * 31 + maxZ;
    }

    @Override
    public String toString() {
        return minX + "," + minZ + " to " + maxX + "," + maxZ;
    }
}
