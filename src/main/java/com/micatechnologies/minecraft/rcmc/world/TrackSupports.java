package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Where the automatically-generated support columns under a section are, shared by rendering and
 * collision.
 *
 * <p>Support positions used to be computed inside the renderer, which meant they existed only on
 * the client — fine while they were purely decorative, and impossible the moment they needed to be
 * solid. A dedicated server has no renderer, so it would have had nothing to collide against.
 * Both sides now derive them here from the same inputs and get the same answer.</p>
 *
 * <p><b>Cached per section, invalidated by identity.</b> {@code TrackSection} is immutable and every
 * edit produces a new instance, so an identity check is a sound staleness test. The cache matters
 * more here than it did for rendering: collision is queried on every entity move, not once a
 * frame, and re-probing terrain height at every sample point on every query would be ruinous.</p>
 */
public final class TrackSupports {

    /**
     * Arc-length spacing between columns, in blocks. Roughly matches the bent spacing on real steel
     * coasters — close enough to look structural, far enough apart not to become a wall.
     */
    public static final double SPACING = 9.0D;

    /** Half-width of a column. Must match {@code TrackMeshBuilder}'s, or you collide with air. */
    public static final double HALF_WIDTH = 0.16D;

    /**
     * Below this height above ground a column is skipped: track running along the floor does not
     * need holding up, and stub columns everywhere would be both visual noise and a trip hazard.
     */
    public static final double MIN_HEIGHT = 2.0D;

    /** Offset from the track centreline to the underside of the spine, where a column tops out. */
    private static final double SPINE_UNDERSIDE = -0.35D;

    /**
     * How far outboard of the track a column stands when the track is tilted or inverted, in blocks.
     *
     * <p>Enough to clear the track's own half-width (0.70) plus the column's (0.16) with a little
     * margin. A column meeting banked track head-on has to stand beside it, not under it, or it ends
     * up inside the very track it is holding up.</p>
     */
    private static final double OUTBOARD = 1.2D;

    /**
     * How far the track's own "up" must lean from vertical before a column is moved outboard.
     *
     * <p>Below this the offset would be a fraction of a block and the arm invisible, so level and
     * gently banked track keeps standing on a plain column directly beneath it — which is both what
     * it should look like and what shipped before this.</p>
     */
    private static final double TILT_THRESHOLD = 0.05D;



    /** One column: a vertical post at a fixed x/z between two heights. */
    public static final class Column {

        public final double x;
        public final double z;
        public final double bottomY;
        public final double topY;

        /**
         * Where the support meets the track's underside.
         *
         * <p>Equal to {@code (x, z)} for a plain column beneath level track. Where the track is
         * banked or inverted the column stands outboard and this is the point its arm reaches in
         * to — so the pair describes an L: up the outside, then in to the track.</p>
         */
        public final double attachX;
        public final double attachZ;

        Column(double x, double z, double bottomY, double topY, double attachX, double attachZ) {
            this.x = x;
            this.z = z;
            this.bottomY = bottomY;
            this.topY = topY;
            this.attachX = attachX;
            this.attachZ = attachZ;
            this.bounds = new AxisAlignedBB(x - HALF_WIDTH, bottomY, z - HALF_WIDTH,
                x + HALF_WIDTH, topY, z + HALF_WIDTH);
            this.arm = hasArm() ? new AxisAlignedBB(
                Math.min(x, attachX) - HALF_WIDTH, topY - HALF_WIDTH,
                Math.min(z, attachZ) - HALF_WIDTH,
                Math.max(x, attachX) + HALF_WIDTH, topY + HALF_WIDTH,
                Math.max(z, attachZ) + HALF_WIDTH) : null;
        }

        /**
         * Built once with the column rather than per call. Collision asks for these for every
         * entity moving near track, every tick, and an allocation per column per query adds up to
         * millions a tick on a long line.
         */
        private final AxisAlignedBB bounds;
        private final AxisAlignedBB arm;

        /** True when this column stands off to the side and needs an arm to reach the track. */
        public boolean hasArm() {
            double dx = attachX - x;
            double dz = attachZ - z;
            return dx * dx + dz * dz > 1.0e-6D;
        }

        public AxisAlignedBB toBounds() {
            return bounds;
        }

        /** The horizontal arm in to the track, or {@code null} when there is none. */
        public AxisAlignedBB armBounds() {
            return arm;
        }
    }

    /** Grid cell edge for {@link #columnsNear}, in blocks: one chunk. */
    private static final int GRID = 16;

    /**
     * How long a section whose columns could not all be placed waits before trying again, in
     * milliseconds. Columns over chunks that were not loaded are skipped rather than guessed; this
     * fills them in once the chunk arrives. Keyed on the section alone, as the cache used to be,
     * they were never filled in: a long line only had supports, and collision, where chunks
     * happened to be loaded when it was first asked about.
     */
    private static final long RETRY_INCOMPLETE_MS = 5000L;

    private static final class Cached {
        final TrackSection section;
        final List<Column> columns;
        /** Columns by the {@link #GRID} cells their post or arm covers. */
        final Map<Long, List<Column>> grid = new HashMap<>();
        /** Some sample points fell on unloaded chunks and were skipped. */
        final boolean incomplete;
        final long computedAt = System.currentTimeMillis();
        /** Kept for the retry, so it does not re-sample the whole section to fill in a few columns. */
        final com.micatechnologies.minecraft.rcmc.track.TrackClearance.Index clearance;

        Cached(TrackSection section, List<Column> columns, Pass pass) {
            this.section = section;
            this.columns = columns;
            this.incomplete = pass.incomplete;
            this.clearance = pass.clearance;
            for (Column column : columns) {
                AxisAlignedBB reach = column.arm != null ? column.bounds.union(column.arm) : column.bounds;
                for (int cx = cellOf(reach.minX); cx <= cellOf(reach.maxX); cx++) {
                    for (int cz = cellOf(reach.minZ); cz <= cellOf(reach.maxZ); cz++) {
                        grid.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(column);
                    }
                }
            }
        }

        boolean dueForRetry() {
            return incomplete && System.currentTimeMillis() - computedAt > RETRY_INCOMPLETE_MS;
        }
    }

    /**
     * Synchronized because in singleplayer the integrated server and the client both ask, from
     * their own threads, and an unsynchronized {@code WeakHashMap} can corrupt itself into a hang.
     * Only the lookup is locked; columns are computed outside it, so two threads racing on one
     * section at worst both compute it.
     */
    private static final Map<World, Map<Integer, Cached>> CACHE =
        java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private TrackSupports() {
        throw new AssertionError("No instances.");
    }

    /** Columns under {@code section}, computing and caching them on first use. */
    public static List<Column> columnsFor(TrackSection section, World world) {
        return cachedFor(section, world).columns;
    }

    /**
     * Columns under {@code section} whose post or arm may meet {@code query}: a superset, read from
     * the chunk-sized cells the query overlaps rather than by walking every column of the section.
     */
    public static List<Column> columnsNear(TrackSection section, World world, AxisAlignedBB query) {
        Cached cached = cachedFor(section, world);
        int minX = cellOf(query.minX);
        int maxX = cellOf(query.maxX);
        int minZ = cellOf(query.minZ);
        int maxZ = cellOf(query.maxZ);
        if (minX == maxX && minZ == maxZ) {
            List<Column> only = cached.grid.get(key(minX, minZ));
            return only == null ? java.util.Collections.<Column>emptyList() : only;
        }
        // A column spanning two cells is listed in both; the set keeps it from being added twice.
        java.util.Set<Column> near = new java.util.LinkedHashSet<>();
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                List<Column> cell = cached.grid.get(key(cx, cz));
                if (cell != null) {
                    near.addAll(cell);
                }
            }
        }
        return new ArrayList<>(near);
    }

    private static Cached cachedFor(TrackSection section, World world) {
        Map<Integer, Cached> perWorld;
        synchronized (CACHE) {
            perWorld = CACHE.computeIfAbsent(world, w -> new java.util.concurrent.ConcurrentHashMap<>());
        }
        Cached cached = perWorld.get(section.id());
        if (cached != null && cached.section == section && !cached.dueForRetry()) {
            return cached;
        }
        boolean retry = cached != null && cached.section == section;
        Pass pass = new Pass(retry ? cached.clearance : null);
        List<Column> columns = compute(section, world, pass);
        if (retry && sameColumns(cached.columns, columns)) {
            // Nothing new came into range. Keep the old list, whose identity the renderer uses to
            // decide whether to rebuild the mesh, and just restart the retry clock.
            columns = cached.columns;
        }
        Cached next = new Cached(section, columns, pass);
        perWorld.put(section.id(), next);
        return next;
    }

    private static boolean sameColumns(List<Column> a, List<Column> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            Column p = a.get(i);
            Column q = b.get(i);
            if (p.x != q.x || p.z != q.z || p.bottomY != q.bottomY || p.topY != q.topY) {
                return false;
            }
        }
        return true;
    }

    private static int cellOf(double coordinate) {
        return (int) Math.floor(coordinate / GRID);
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /** Every column on every section of {@code network}. */
    public static List<Column> allColumns(TrackNetwork network, World world) {
        List<Column> all = new ArrayList<>();
        for (TrackSection section : network.sections()) {
            all.addAll(columnsFor(section, world));
        }
        return all;
    }

    public static void invalidate(World world) {
        CACHE.remove(world);
    }

    /**
     * Columns for a section that is not part of the network, computed without caching.
     *
     * <p>For the build preview, whose provisional section is a new instance every frame the cursor
     * moves. Routing that through the cache would replace the entry each frame and never hit,
     * paying the bookkeeping for none of the benefit.</p>
     */
    public static List<Column> computeUncached(TrackSection section, World world) {
        return compute(section, world, new Pass(null));
    }

    /** One computation's by-products: whether it was complete, and the index it built, if any. */
    private static final class Pass {
        boolean incomplete;
        com.micatechnologies.minecraft.rcmc.track.TrackClearance.Index clearance;

        /** @param clearance an index of the same section from an earlier pass, or {@code null} */
        Pass(com.micatechnologies.minecraft.rcmc.track.TrackClearance.Index clearance) {
            this.clearance = clearance;
        }
    }

    /**
     * Places a column at each sample point that can take one.
     *
     * <p><b>Inversions are why this is not simply "drop a line from the track to the ground".</b> On
     * a vertical loop or a corkscrew the track passes over itself, so a column dropped straight down
     * from the upper track runs through the lower track on the way to the ground — which is exactly
     * what a builder reported seeing. Two things fix it.</p>
     *
     * <p>First, the attachment comes from the track's own frame rather than from world-down.
     * {@code frame.up} is the direction a rider's head points, so {@code -up} is the structural
     * outside of the track, and that is the face a support belongs on whatever the bank. On level
     * track it points straight down and nothing changes; on the side of a loop it points sideways,
     * so the column stands beside the track and reaches in — the L real coasters use.</p>
     *
     * <p>Second, a column that would still pass through track is abandoned rather than drawn. Near
     * the crown of a loop the outside faces straight up, so there is no sensible vertical column at
     * all; real loops are not supported from directly beneath their crown either, but by the
     * structure around them. Leaving that gap is honest. Drawing a column through the track is not.</p>
     */
    private static List<Column> compute(TrackSection section, World world, Pass pass) {
        List<Column> columns = new ArrayList<>();
        double total = section.totalLength();
        for (double s = 0.0D; s < total; s += SPACING) {
            TrackFrame frame = section.frameAtDistance(s);
            // The underside in the track's own terms, not the world's: on inverted track this is
            // above the centreline, which is precisely the point.
            Vec3 attach = frame.position.add(frame.up.scale(SPINE_UNDERSIDE));

            // Which way is structurally outboard, flattened into the horizontal plane the column
            // stands in. Vanishes for level track, leaving the original behaviour untouched.
            Vec3 outward = frame.up.scale(-1.0D);
            double lean = Math.sqrt(outward.x * outward.x + outward.z * outward.z);
            double x = attach.x;
            double z = attach.z;
            if (lean > TILT_THRESHOLD) {
                x += outward.x / lean * OUTBOARD;
                z += outward.z / lean * OUTBOARD;
            }

            BlockPos probe = new BlockPos(x, 0, z);
            if (!world.isBlockLoaded(probe)) {
                // No terrain to stand on yet. Skipping beats guessing a height and leaving a column
                // ending in mid-air — or worse, an invisible collider there — once the chunk loads.
                pass.incomplete = true;
                continue;
            }
            double groundY = world.getHeight(probe.getX(), probe.getZ());
            double topY = attach.y;
            if (topY - groundY < MIN_HEIGHT) {
                continue;
            }
            if (pass.clearance == null) {
                // Built on first need: a buried line never reaches this test, so it never pays to
                // sample itself.
                pass.clearance = com.micatechnologies.minecraft.rcmc.track.TrackClearance.Index.of(section);
            }
            if (pass.clearance.columnWouldClash(x, z, groundY, topY, s,
                com.micatechnologies.minecraft.rcmc.track.TrackClearance.COLUMN_CLEARANCE,
                com.micatechnologies.minecraft.rcmc.track.TrackClearance.ATTACH_EXCLUSION)) {
                continue;
            }
            columns.add(new Column(x, z, groundY, topY, attach.x, attach.z));
        }
        return columns;
    }

}
