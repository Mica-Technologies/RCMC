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
        }

        /** True when this column stands off to the side and needs an arm to reach the track. */
        public boolean hasArm() {
            double dx = attachX - x;
            double dz = attachZ - z;
            return dx * dx + dz * dz > 1.0e-6D;
        }

        public AxisAlignedBB toBounds() {
            return new AxisAlignedBB(x - HALF_WIDTH, bottomY, z - HALF_WIDTH,
                x + HALF_WIDTH, topY, z + HALF_WIDTH);
        }

        /** The horizontal arm in to the track, or {@code null} when there is none. */
        public AxisAlignedBB armBounds() {
            if (!hasArm()) {
                return null;
            }
            return new AxisAlignedBB(
                Math.min(x, attachX) - HALF_WIDTH, topY - HALF_WIDTH,
                Math.min(z, attachZ) - HALF_WIDTH,
                Math.max(x, attachX) + HALF_WIDTH, topY + HALF_WIDTH,
                Math.max(z, attachZ) + HALF_WIDTH);
        }
    }

    private static final class Cached {
        final TrackSection section;
        final List<Column> columns;

        Cached(TrackSection section, List<Column> columns) {
            this.section = section;
            this.columns = columns;
        }
    }

    private static final Map<World, Map<Integer, Cached>> CACHE = new WeakHashMap<>();

    private TrackSupports() {
        throw new AssertionError("No instances.");
    }

    /** Columns under {@code section}, computing and caching them on first use. */
    public static List<Column> columnsFor(TrackSection section, World world) {
        Map<Integer, Cached> perWorld = CACHE.computeIfAbsent(world, w -> new HashMap<>());
        Cached cached = perWorld.get(section.id());
        if (cached != null && cached.section == section) {
            return cached.columns;
        }
        List<Column> columns = compute(section, world);
        perWorld.put(section.id(), new Cached(section, columns));
        return columns;
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
        return compute(section, world);
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
    private static List<Column> compute(TrackSection section, World world) {
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
                continue;
            }
            double groundY = world.getHeight(probe.getX(), probe.getZ());
            double topY = attach.y;
            if (topY - groundY < MIN_HEIGHT) {
                continue;
            }
            if (com.micatechnologies.minecraft.rcmc.track.TrackClearance.columnWouldClash(
                section, x, z, groundY, topY, s,
                com.micatechnologies.minecraft.rcmc.track.TrackClearance.COLUMN_CLEARANCE,
                com.micatechnologies.minecraft.rcmc.track.TrackClearance.ATTACH_EXCLUSION)) {
                continue;
            }
            columns.add(new Column(x, z, groundY, topY, attach.x, attach.z));
        }
        return columns;
    }

}
