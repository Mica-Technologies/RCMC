package com.micatechnologies.minecraft.rcmc.client.render.track;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits one section's {@link TrackMesh} into fixed-size spatial cells, each with its own bounds,
 * so the renderer culls and draws track by the piece of it in view rather than by the section.
 *
 * <p><b>Why.</b> A section's bounding box is the box around all of it. For a coaster that is a
 * few dozen blocks and per-section culling is fine; for a city metro loop it is the whole map, so
 * the box is always in range and always in the frustum, and every quad of a 23 km line was drawn
 * every frame for every player — 84% of the frame on the server that reported it. Culling by cell
 * makes the cost follow the track actually near the camera, whatever the section's length.</p>
 *
 * <p><b>By space, not by arc length.</b> A quad goes to the cell its centroid falls in, on a
 * fixed world grid. That treats supports, catenary masts and the track itself alike without
 * knowing which is which, and a cell's bounds are taken from its quads' real corners, so a quad
 * poking over a cell boundary is still culled correctly.</p>
 *
 * <p><b>Compact on purpose.</b> Each cell keeps flat float arrays, positions relative to its own
 * origin, rather than {@link MeshQuad} objects: a long line has hundreds of thousands of quads,
 * and four {@link Vec3} objects per quad is several times the memory of the floats they hold.
 * Relative positions also keep every value small, which is what float32 needs to stay exact.</p>
 *
 * <p>Pure: no Minecraft types, like the builder it partitions, so it is unit-testable.</p>
 */
public final class TrackMeshCells {

    /** Cell edge in blocks. Two chunk widths: fine enough that culling is tight, coarse enough
     *  that a busy park is a few hundred cells rather than thousands of tiny draw calls. */
    public static final double CELL_SIZE = 32.0D;

    /** Floats per quad in {@link Cell#positions}: four corners of x, y, z. */
    public static final int POSITION_FLOATS = 12;

    /** Floats per quad in {@link Cell#colours}: one red, green, blue for the whole face. */
    public static final int COLOUR_FLOATS = 3;

    private TrackMeshCells() {
        throw new AssertionError("No instances.");
    }

    /** One cell's share of a mesh. */
    public static final class Cell {

        /** World position every entry of {@link #positions} is relative to. */
        public final double originX;
        public final double originY;
        public final double originZ;

        /** {@link #POSITION_FLOATS} per quad, corner order a, b, c, d as the builder emitted it. */
        public final float[] positions;

        /** {@link #COLOUR_FLOATS} per quad. */
        public final float[] colours;

        public final double minX;
        public final double minY;
        public final double minZ;
        public final double maxX;
        public final double maxY;
        public final double maxZ;

        Cell(double originX, double originY, double originZ, float[] positions, float[] colours,
             double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.positions = positions;
            this.colours = colours;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public int quadCount() {
            return colours.length / COLOUR_FLOATS;
        }

        /** World-space centroid of quad {@code quad}, for sampling the light it sits in. */
        public double centroidX(int quad) {
            return originX + corners(quad, 0) / 4.0D;
        }

        public double centroidY(int quad) {
            return originY + corners(quad, 1) / 4.0D;
        }

        public double centroidZ(int quad) {
            return originZ + corners(quad, 2) / 4.0D;
        }

        private double corners(int quad, int axis) {
            int base = quad * POSITION_FLOATS + axis;
            return (double) positions[base] + positions[base + 3] + positions[base + 6] + positions[base + 9];
        }
    }

    /** Partitions {@code mesh} into {@link #CELL_SIZE} cells. Empty mesh, empty list. */
    public static List<Cell> partition(TrackMesh mesh) {
        return partition(mesh.quads, CELL_SIZE);
    }

    /** As above with an explicit cell size, for tests. */
    static List<Cell> partition(List<MeshQuad> quads, double cellSize) {
        // Insertion order kept so a section's cells come out in the order the builder swept them,
        // which keeps the result deterministic for tests and for anyone reading a dump.
        Map<Long, List<MeshQuad>> byCell = new java.util.LinkedHashMap<>();
        Map<Long, int[]> cellIndex = new HashMap<>();
        for (MeshQuad quad : quads) {
            int cx = cellOf((quad.a.x + quad.b.x + quad.c.x + quad.d.x) / 4.0D, cellSize);
            int cy = cellOf((quad.a.y + quad.b.y + quad.c.y + quad.d.y) / 4.0D, cellSize);
            int cz = cellOf((quad.a.z + quad.b.z + quad.c.z + quad.d.z) / 4.0D, cellSize);
            long key = key(cx, cy, cz);
            List<MeshQuad> bucket = byCell.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                byCell.put(key, bucket);
                cellIndex.put(key, new int[] { cx, cy, cz });
            }
            bucket.add(quad);
        }

        List<Cell> cells = new ArrayList<>(byCell.size());
        for (Map.Entry<Long, List<MeshQuad>> entry : byCell.entrySet()) {
            int[] index = cellIndex.get(entry.getKey());
            cells.add(toCell(entry.getValue(), index[0] * cellSize, index[1] * cellSize,
                index[2] * cellSize));
        }
        return cells;
    }

    private static Cell toCell(List<MeshQuad> quads, double originX, double originY, double originZ) {
        float[] positions = new float[quads.size() * POSITION_FLOATS];
        float[] colours = new float[quads.size() * COLOUR_FLOATS];
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        int p = 0;
        int c = 0;
        for (MeshQuad quad : quads) {
            for (Vec3 v : new Vec3[] { quad.a, quad.b, quad.c, quad.d }) {
                positions[p++] = (float) (v.x - originX);
                positions[p++] = (float) (v.y - originY);
                positions[p++] = (float) (v.z - originZ);
                minX = Math.min(minX, v.x);
                minY = Math.min(minY, v.y);
                minZ = Math.min(minZ, v.z);
                maxX = Math.max(maxX, v.x);
                maxY = Math.max(maxY, v.y);
                maxZ = Math.max(maxZ, v.z);
            }
            colours[c++] = quad.red;
            colours[c++] = quad.green;
            colours[c++] = quad.blue;
        }
        return new Cell(originX, originY, originZ, positions, colours, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static int cellOf(double coordinate, double cellSize) {
        return (int) Math.floor(coordinate / cellSize);
    }

    /** 21 bits per axis: ±1,048,576 cells of 32 blocks is ±33 million blocks, past the world border. */
    private static long key(int cx, int cy, int cz) {
        return ((long) (cx & 0x1FFFFF) << 42) | ((long) (cy & 0x1FFFFF) << 21) | (cz & 0x1FFFFF);
    }
}
