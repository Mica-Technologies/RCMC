package com.micatechnologies.minecraft.rcmc.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.ClearanceSweep.Occupancy;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The clearance sweep: does it find what a passing car would hit, and only that.
 *
 * <p>Every case is a single block placed against a straight run of track, asked about from the
 * car's point of view: beside it, over it, under it, and with the track banked or upside down.</p>
 */
class ClearanceSweepTest {

    /** Rail level; the track runs along X, centred in the block column at z = 0. */
    private static final double RAIL_Y = 64.0D;
    private static final double RAIL_Z = 0.5D;

    private static TrackSection straight(double bankDegrees, String style) {
        List<TrackNode> nodes = new ArrayList<>();
        for (int x = 0; x <= 20; x += 5) {
            nodes.add(new TrackNode(new Vec3(x, RAIL_Y, RAIL_Z), bankDegrees, null));
        }
        return new TrackSection(1, nodes, false, style);
    }

    /** A world with solid full blocks at {@code blocks} and nothing else. */
    private static ClearanceSweep.Obstacles solid(int[]... blocks) {
        Set<String> at = new HashSet<>();
        for (int[] b : blocks) {
            at.add(b[0] + "," + b[1] + "," + b[2]);
        }
        return (bx, by, bz, x, y, z) -> at.contains(bx + "," + by + "," + bz) ? Occupancy.BLOCKED : Occupancy.CLEAR;
    }

    private static boolean hits(TrackSection section, int x, int y, int z) {
        return !ClearanceSweep.sweep(section, solid(new int[] {x, y, z})).hits.isEmpty();
    }

    @Test
    @DisplayName("the ground the track is laid on is not in the way")
    void groundIsClear() {
        assertFalse(hits(straight(0, null), 10, 63, 0));
    }

    @Test
    @DisplayName("a block beside a coaster car, within a rider's reach, is found; one two blocks out is not")
    void besideCoaster() {
        TrackSection track = straight(0, null);
        assertTrue(hits(track, 10, 64, 1), "the block next to the rail column");
        assertTrue(hits(track, 10, 65, -1), "the other side, at shoulder height");
        assertFalse(hits(track, 10, 64, 2));
        assertFalse(hits(track, 10, 64, -2));
    }

    @Test
    @DisplayName("a block over a rider's head is found; one above that is not")
    void overheadCoaster() {
        TrackSection track = straight(0, null);
        assertTrue(hits(track, 10, 66, 0));
        assertFalse(hits(track, 10, 67, 0));
    }

    @Test
    @DisplayName("metro track sweeps a metro car: a wall 1.5 blocks out clips a metro, not a coaster")
    void metroIsWider() {
        // Block z = 2 spans 2..3; the rail is at 0.5, so its near face is 1.5 out.
        assertTrue(hits(straight(0, TrackStyleIds.TRANSIT), 10, 66, 2));
        assertFalse(hits(straight(0, null), 10, 66, 2));
        // A metro roof is just under six blocks up: a ceiling with its underside at six is clear.
        assertFalse(hits(straight(0, TrackStyleIds.TRANSIT), 10, 70, 0));
        assertTrue(hits(straight(0, TrackStyleIds.TRANSIT), 10, 69, 0));
    }

    @Test
    @DisplayName("on track banked 90 degrees the envelope lies on its side: overhead clears, one side does not")
    void bankedOnItsSide() {
        TrackSection track = straight(90, null);
        assertFalse(hits(track, 10, 66, 0), "two blocks up is now beside the rider's feet, clear");
        boolean left = hits(track, 10, 64, 2);
        boolean right = hits(track, 10, 64, -2);
        assertTrue(left ^ right, "riders' heads point out one side only (left " + left + ", right " + right + ")");
    }

    @Test
    @DisplayName("upside down, riders hang under the rail: the block below is in the way, the one above is not")
    void inverted() {
        TrackSection track = straight(180, null);
        assertTrue(hits(track, 10, 62, 0));
        assertFalse(hits(track, 10, 65, 0));
    }

    @Test
    @DisplayName("each intruding block is listed once, in order along the track")
    void listedOnceInOrder() {
        TrackSection track = straight(0, null);
        ClearanceSweep.Result result = ClearanceSweep.sweep(track,
            solid(new int[] {15, 65, 0}, new int[] {5, 65, 0}, new int[] {5, 64, 0}));
        assertEquals(3, result.hits.size());
        for (int i = 1; i < result.hits.size(); i++) {
            assertTrue(result.hits.get(i).distance >= result.hits.get(i - 1).distance);
        }
        assertEquals(15, result.hits.get(2).x);
    }

    @Test
    @DisplayName("a block the world cannot see is counted as unknown, never reported as clear or in the way")
    void unknownIsCounted() {
        ClearanceSweep.Result result = ClearanceSweep.sweep(straight(0, null),
            (bx, by, bz, x, y, z) -> bx == 10 ? Occupancy.UNKNOWN : Occupancy.CLEAR);
        assertTrue(result.hits.isEmpty());
        assertTrue(result.unknownBlocks > 0);
    }

    @Test
    @DisplayName("a part block is asked at each point: clear at one point, blocked at another, is found")
    void partBlockAskedPerPoint() {
        // A bottom slab beside the rail: only its lower half is solid.
        ClearanceSweep.Obstacles slab = (bx, by, bz, x, y, z) ->
            bx == 10 && by == 64 && bz == 1 && y - by < 0.5D ? Occupancy.BLOCKED : Occupancy.CLEAR;
        assertEquals(1, ClearanceSweep.sweep(straight(0, null), slab).hits.size());
    }
}
