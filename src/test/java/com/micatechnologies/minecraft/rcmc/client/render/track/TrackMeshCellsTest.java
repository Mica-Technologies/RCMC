package com.micatechnologies.minecraft.rcmc.client.render.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The spatial split behind per-cell culling (issue #8): a 23 km section was drawn in full every
 * frame because its one bounding box covered the map. What matters is that the track near a
 * camera is a small part of what gets drawn, and that splitting loses and moves nothing.
 */
class TrackMeshCellsTest {

    @Test
    @DisplayName("near any point of a 25 km loop, only a sliver of the mesh is within render distance")
    void cullingFollowsTheTrackInView() {
        TrackSection loop = TrackMeshBuilderTest.cityLoop(4000.0D);
        TrackMesh mesh = TrackMeshBuilder.build(loop);
        List<TrackMeshCells.Cell> cells = TrackMeshCells.partition(mesh);

        // Stand on the track, as the players who reported it were.
        Vec3 camera = loop.positionAtDistance(1234.0D).add(new Vec3(0, 2, 0));
        double range = 256.0D;
        long inRange = 0;
        for (TrackMeshCells.Cell cell : cells) {
            double dx = Math.max(cell.minX, Math.min(cell.maxX, camera.x)) - camera.x;
            double dy = Math.max(cell.minY, Math.min(cell.maxY, camera.y)) - camera.y;
            double dz = Math.max(cell.minZ, Math.min(cell.maxZ, camera.z)) - camera.z;
            if (dx * dx + dy * dy + dz * dz <= range * range) {
                inRange += cell.quadCount();
            }
        }
        // 2 x 256 blocks of track out of 25,133 is 2%; cell granularity adds a little.
        double share = inRange / (double) mesh.quads.size();
        assertTrue(share > 0.0D && share < 0.04D,
            "expected only the track near the camera in range; got " + (share * 100) + "% of "
                + mesh.quads.size() + " quads");
    }

    @Test
    @DisplayName("every quad lands in exactly one cell, at the same world position")
    void partitionKeepsEveryQuadWhereItWas() {
        TrackSection loop = TrackMeshBuilderTest.cityLoop(300.0D);
        TrackMesh mesh = TrackMeshBuilder.build(loop);
        List<TrackMeshCells.Cell> cells = TrackMeshCells.partition(mesh);

        int total = 0;
        double worst = 0.0D;
        for (TrackMeshCells.Cell cell : cells) {
            total += cell.quadCount();
            float[] p = cell.positions;
            for (int i = 0; i < p.length; i += 3) {
                double x = cell.originX + p[i];
                double y = cell.originY + p[i + 1];
                double z = cell.originZ + p[i + 2];
                // Inside the cell's own bounds, which are what culling tests.
                assertTrue(x >= cell.minX - 1e-3 && x <= cell.maxX + 1e-3
                    && y >= cell.minY - 1e-3 && y <= cell.maxY + 1e-3
                    && z >= cell.minZ - 1e-3 && z <= cell.maxZ + 1e-3, "vertex outside its cell's bounds");
            }
            // A cell is a cell, not a section: bounded by the grid plus one quad's overhang.
            worst = Math.max(worst, Math.max(cell.maxX - cell.minX, cell.maxZ - cell.minZ));
        }
        assertEquals(mesh.quads.size(), total, "quads lost or duplicated by the split");
        assertTrue(worst <= TrackMeshCells.CELL_SIZE + 8.0D, "a cell spans " + worst + " blocks");

        // Same geometry: the first quad of the mesh is somewhere among the cells, to float precision.
        MeshQuad first = mesh.quads.get(0);
        boolean found = false;
        for (TrackMeshCells.Cell cell : cells) {
            float[] p = cell.positions;
            for (int q = 0; q < cell.quadCount() && !found; q++) {
                int b = q * TrackMeshCells.POSITION_FLOATS;
                found = Math.abs(cell.originX + p[b] - first.a.x) < 1e-4
                    && Math.abs(cell.originY + p[b + 1] - first.a.y) < 1e-4
                    && Math.abs(cell.originZ + p[b + 2] - first.a.z) < 1e-4
                    && Math.abs(cell.originX + p[b + 9] - first.d.x) < 1e-4;
            }
        }
        assertTrue(found, "the mesh's first quad is not in any cell at its original position");
    }
}
