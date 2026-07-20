package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
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

    /** One column: a vertical post at a fixed x/z between two heights. */
    public static final class Column {

        public final double x;
        public final double z;
        public final double bottomY;
        public final double topY;

        Column(double x, double z, double bottomY, double topY) {
            this.x = x;
            this.z = z;
            this.bottomY = bottomY;
            this.topY = topY;
        }

        public AxisAlignedBB toBounds() {
            return new AxisAlignedBB(x - HALF_WIDTH, bottomY, z - HALF_WIDTH,
                x + HALF_WIDTH, topY, z + HALF_WIDTH);
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

    private static List<Column> compute(TrackSection section, World world) {
        List<Column> columns = new ArrayList<>();
        double total = section.totalLength();
        for (double s = 0.0D; s < total; s += SPACING) {
            Vec3 at = section.positionAtDistance(s);
            BlockPos probe = new BlockPos(at.x, 0, at.z);
            if (!world.isBlockLoaded(probe)) {
                // No terrain to stand on yet. Skipping beats guessing a height and leaving a column
                // ending in mid-air — or worse, an invisible collider there — once the chunk loads.
                continue;
            }
            double groundY = world.getHeight(probe.getX(), probe.getZ());
            double topY = at.y + SPINE_UNDERSIDE;
            if (topY - groundY < MIN_HEIGHT) {
                continue;
            }
            columns.add(new Column(at.x, at.z, groundY, topY));
        }
        return columns;
    }
}
