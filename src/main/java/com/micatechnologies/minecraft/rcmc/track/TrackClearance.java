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
        return Index.of(section).columnWouldClash(x, z, bottomY, topY, attachAt, clearance,
            attachExclusion);
    }

    /**
     * A section's clearance samples, bucketed by horizontal cell, so one column's check costs the
     * few samples near it rather than the whole section.
     *
     * <p>Exists because the support generator asks once per column, and a column is dropped every
     * nine blocks: a walk over the whole section per column is quadratic in its length. On a
     * 23 km city line that was ~2,500 columns times ~46,000 samples — over a hundred million
     * spline lookups, on both sides, every time the section changed. Build one per section and
     * reuse it for all its columns.</p>
     */
    public static final class Index {

        /** Cell edge in blocks. Comfortably more than any clearance asked of it, so a query reads
         *  at most a 3x3 block of cells. */
        private static final double CELL = 4.0D;

        private final double total;
        private final boolean closed;
        private final double[] xs;
        private final double[] ys;
        private final double[] zs;
        private final java.util.Map<Long, int[]> cells;

        private Index(TrackSection section) {
            this.total = section.totalLength();
            this.closed = section.isClosed();
            int count = total <= 0.0D ? 0 : (int) Math.ceil(total / SAMPLE_STEP);
            this.xs = new double[count];
            this.ys = new double[count];
            this.zs = new double[count];
            java.util.Map<Long, java.util.List<Integer>> building = new java.util.HashMap<>();
            for (int i = 0; i < count; i++) {
                Vec3 at = section.positionAtDistance(i * SAMPLE_STEP);
                xs[i] = at.x;
                ys[i] = at.y;
                zs[i] = at.z;
                building.computeIfAbsent(key(cellOf(at.x), cellOf(at.z)), k -> new java.util.ArrayList<>())
                    .add(i);
            }
            this.cells = new java.util.HashMap<>(building.size() * 2);
            for (java.util.Map.Entry<Long, java.util.List<Integer>> entry : building.entrySet()) {
                java.util.List<Integer> list = entry.getValue();
                int[] indices = new int[list.size()];
                for (int i = 0; i < indices.length; i++) {
                    indices[i] = list.get(i);
                }
                cells.put(entry.getKey(), indices);
            }
        }

        /** Samples {@code section}. Costs one walk of it; build once and query many times. */
        public static Index of(TrackSection section) {
            return new Index(section);
        }

        /** As {@link TrackClearance#columnWouldClash}, against this index's section. */
        public boolean columnWouldClash(double x, double z, double bottomY, double topY,
                                        double attachAt, double clearance, double attachExclusion) {
            if (xs.length == 0) {
                return false;
            }
            double clearanceSquared = clearance * clearance;
            int minCellX = cellOf(x - clearance);
            int maxCellX = cellOf(x + clearance);
            int minCellZ = cellOf(z - clearance);
            int maxCellZ = cellOf(z + clearance);
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                    int[] indices = cells.get(key(cellX, cellZ));
                    if (indices == null) {
                        continue;
                    }
                    for (int i : indices) {
                        double along = Math.abs(i * SAMPLE_STEP - attachAt);
                        if (closed) {
                            along = Math.min(along, total - along);
                        }
                        if (along <= attachExclusion) {
                            continue;
                        }
                        // Only track at heights the column actually occupies can be in its way.
                        if (ys[i] < bottomY || ys[i] > topY) {
                            continue;
                        }
                        double dx = xs[i] - x;
                        double dz = zs[i] - z;
                        if (dx * dx + dz * dz < clearanceSquared) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private static int cellOf(double coordinate) {
            return (int) Math.floor(coordinate / CELL);
        }

        private static long key(int cellX, int cellZ) {
            return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
        }
    }
}
