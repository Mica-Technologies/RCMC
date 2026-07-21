package com.micatechnologies.minecraft.rcmc.track;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;

/**
 * Whether a vertical support column can reach the ground without passing through track.
 *
 * <p>Lives here, in the pure package, rather than beside the support generator that uses it. The
 * generator needs a {@code World} to ask how high the ground is, and that one Minecraft dependency
 * would otherwise make this decision untestable — while the decision itself is the part worth
 * testing, being pure geometry and the reason supports stopped running through inverted track.</p>
 */
public final class TrackClearance {

    private TrackClearance() {
        throw new AssertionError("No instances.");
    }

    private static final double SAMPLE_STEP = 0.5D;

    /**
     * How close a support column may come to track it is not holding, in blocks.
     *
     * <p><b>Not derived from the width of the track.</b> The rails are 1.4 blocks wide, so a column
     * 0.86 blocks away already fails to touch them — and that is the wrong test, because the thing
     * that has to fit through is the train, not the track. A car body is 1.24 blocks wide
     * ({@code CarModel.BODY_HALF_WIDTH} either side) with riders inside it, and real parks specify a
     * clearance envelope around the vehicle rather than around the rails, for the obvious reason.
     * 0.62 for the body, 0.16 for the column and a half-block envelope gives this.</p>
     *
     * <p>It also decides a case that is otherwise finely balanced. A vertical loop's two legs pass
     * 1.14 blocks apart, so a column dropped from the crown misses both rails — it threads the gap
     * and stands through the middle of the loop, where it would take a rider's head off and looks
     * absurd besides. Measuring against the vehicle rather than the rails rejects it.</p>
     */
    public static final double COLUMN_CLEARANCE = 1.4D;

    /**
     * Track within this arc distance of a column's attachment is what it holds up, not an
     * obstruction. Comfortably more than {@link #COLUMN_CLEARANCE} so the approach either side of
     * the attachment does not read as a clash.
     */
    public static final double ATTACH_EXCLUSION = 3.5D;

    /**
     * Whether a column at {@code (x, z)} spanning {@code bottomY..topY} would pass through
     * {@code section}.
     *
     * <p>Track within {@code attachExclusion} of {@code attachAt} is ignored: that is the track the
     * column is holding up, not something it collides with. On a closed circuit the arc distance is
     * measured the short way round, or a column just past the seam would read its own attachment as
     * a clash and no circuit would ever be supported near distance zero.</p>
     *
     * @param clearance how close the column may come to other track, horizontally, in blocks
     */
    public static boolean columnWouldClash(TrackSection section, double x, double z,
                                           double bottomY, double topY, double attachAt,
                                           double clearance, double attachExclusion) {
        if (section == null) {
            return false;
        }
        double total = section.totalLength();
        if (total <= 0.0D) {
            return false;
        }
        double clearanceSquared = clearance * clearance;
        for (double s = 0.0D; s < total; s += SAMPLE_STEP) {
            double along = Math.abs(s - attachAt);
            if (section.isClosed()) {
                along = Math.min(along, total - along);
            }
            if (along <= attachExclusion) {
                continue;
            }
            Vec3 at = section.positionAtDistance(s);
            // Only track at heights the column actually occupies can be in its way.
            if (at.y < bottomY || at.y > topY) {
                continue;
            }
            double dx = at.x - x;
            double dz = at.z - z;
            if (dx * dx + dz * dz < clearanceSquared) {
                return true;
            }
        }
        return false;
    }
}
