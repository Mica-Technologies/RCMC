package com.micatechnologies.minecraft.rcmc.client.render.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.TrackStyleIds;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The third-rail transit style (issue #7): a conductor beside the running rails, on the side away
 * from platforms, broken where it changes sides and at switches, with nothing overhead.
 */
class ThirdRailTest {

    private static final double TRACK_Y = 47.0D;

    /** Along +x from 0 to 100, so distance along the section is just x. */
    private static TrackSection straight(String styleId) {
        return new TrackSection(1, Arrays.asList(
            new TrackNode(new Vec3(0, TRACK_Y, 0)),
            new TrackNode(new Vec3(50, TRACK_Y, 0)),
            new TrackNode(new Vec3(100, TRACK_Y, 0))), false, styleId);
    }

    /**
     * Distance along the section of every conductor quad, signed by which side of the track it is
     * on: positive right, negative left. A conductor quad is one whose centre sits where the rail
     * is, across the track and in height.
     */
    private static double[] conductorAt(TrackSection section, TrackMesh mesh) {
        return mesh.quads.stream()
            .filter(q -> {
                Vec3 c = centre(q);
                TrackFrame frame = section.frameAtDistance(c.x);
                Vec3 local = c.subtract(frame.position);
                double across = Math.abs(local.dot(frame.right));
                double up = local.dot(frame.up);
                return Math.abs(across - TrackMeshBuilder.THIRD_RAIL_OFFSET) < 0.09D
                    && Math.abs(up - TrackMeshBuilder.THIRD_RAIL_CENTRE_U) < 0.07D;
            })
            .mapToDouble(q -> {
                Vec3 c = centre(q);
                TrackFrame frame = section.frameAtDistance(c.x);
                return Math.signum(c.subtract(frame.position).dot(frame.right)) * Math.max(c.x, 1.0e-3D);
            })
            .toArray();
    }

    private static Vec3 centre(MeshQuad q) {
        return q.a.add(q.b).add(q.c).add(q.d).scale(0.25D);
    }

    // ---- ids ----

    @Test
    @DisplayName("the style resolves, takes a side, and has no overhead wire")
    void styleIds() {
        assertEquals(TrackStyleIds.TRANSIT_THIRD_RAIL, TrackStyleIds.resolve("transit-third-rail"));
        assertEquals(TrackStyleIds.TRANSIT_THIRD_RAIL, TrackStyleIds.resolve("Transit-Third-Rail-Right"));
        assertEquals("transit-third-rail-left", TrackStyleIds.resolve("transit-third-rail-left"));
        assertThrows(IllegalArgumentException.class, () -> TrackStyleIds.resolve("transit-third-rail-10"));
        assertTrue(TrackStyleIds.COMMAND_CHOICES.contains(TrackStyleIds.TRANSIT_THIRD_RAIL));

        assertEquals(1, TrackStyleIds.thirdRailSide("transit-third-rail"));
        assertEquals(-1, TrackStyleIds.thirdRailSide("transit-third-rail-left"));
        assertEquals(0, TrackStyleIds.thirdRailSide(TrackStyleIds.TRANSIT_TUNNEL));
        // No wire, so a pantograph has nothing to reach for and the car collects from the rail.
        assertEquals(0.0D, TrackStyleIds.contactWireHeight("transit-third-rail"));
        assertEquals(0.0D, TrackStyleIds.contactWireHeight("transit-third-rail-left"));
        assertTrue(TrackStyleIds.isTransit("transit-third-rail-left"));
        // The tool's cycle carries on past it rather than snapping back to the start.
        assertEquals(TrackStyleIds.TRANSIT_THIRD_RAIL, TrackStyleIds.next(TrackStyleIds.TRANSIT_TUNNEL));
        assertEquals(null, TrackStyleIds.next("transit-third-rail-left"));
    }

    // ---- mesh ----

    @Test
    @DisplayName("the conductor runs the length of the section on its side, with nothing overhead")
    void conductorOnItsSide() {
        TrackSection right = straight(TrackStyleIds.TRANSIT_THIRD_RAIL);
        TrackMesh mesh = TrackMeshBuilder.build(right);
        double[] at = conductorAt(right, mesh);
        assertTrue(at.length > 0, "no conductor rail in the mesh");
        assertTrue(Arrays.stream(at).allMatch(s -> s > 0), "conductor found on the left of a right-hand rail");
        assertTrue(Arrays.stream(at).max().getAsDouble() > 95.0D && Arrays.stream(at).min().getAsDouble() < 5.0D,
            "conductor should run end to end");
        assertTrue(mesh.maxY < TRACK_Y + 1.0D, "third rail builds nothing overhead");

        TrackSection left = straight("transit-third-rail-left");
        double[] leftAt = conductorAt(left, TrackMeshBuilder.build(left));
        assertTrue(leftAt.length > 0 && Arrays.stream(leftAt).allMatch(s -> s < 0),
            "a -left style must put the conductor on the left");

        TrackSection plain = straight(TrackStyleIds.TRANSIT);
        assertEquals(0, conductorAt(plain, TrackMeshBuilder.build(plain)).length,
            "plain transit has no third rail");
    }

    @Test
    @DisplayName("at a platform the conductor moves to the other side, with a break at each change")
    void conductorMovesAwayFromAPlatform() {
        TrackSection section = straight(TrackStyleIds.TRANSIT_THIRD_RAIL);
        // A berth with its platform on the right between 40 and 70: rail goes left there.
        ThirdRailPlan plan = new ThirdRailPlan(1,
            Collections.singletonList(new double[] { 40.0D, 70.0D, -1 }), Collections.<double[]>emptyList());
        double[] at = conductorAt(section, TrackMeshBuilder.build(section,
            Collections.emptyList(), Collections.<double[]>emptyList(), plan));

        for (double s : at) {
            double along = Math.abs(s);
            if (along > 42.0D && along < 68.0D) {
                assertTrue(s < 0, "conductor on the platform side at " + along);
            }
            if (along < 38.0D || along > 72.0D) {
                assertTrue(s > 0, "conductor off its normal side at " + along);
            }
            // The breaks: nothing within a block of either change.
            assertFalse(Math.abs(along - 40.0D) < 1.0D || Math.abs(along - 70.0D) < 1.0D,
                "conductor runs through the side change at " + along);
        }
    }

    // ---- plan ----

    @Test
    @DisplayName("a switch at the end leaves bare track; a berth over a circuit's seam wraps round it")
    void planGapsAndWrap() {
        ThirdRailPlan switched = new ThirdRailPlan(1, Collections.<double[]>emptyList(),
            Collections.singletonList(new double[] { 92.0D, 100.0D }));
        List<ThirdRailPlan.Run> runs = switched.runs(100.0D, false);
        assertEquals(1, runs.size());
        assertEquals(92.0D, runs.get(0).to, 1e-9, "rail should stop short of the switch");

        // A berth at distance 2 on a 100-block circuit: its reach wraps past the seam.
        ThirdRailPlan wrapped = new ThirdRailPlan(1,
            Collections.singletonList(new double[] { -8.0D, 12.0D, -1 }), Collections.<double[]>emptyList());
        boolean leftNearEnd = false;
        boolean leftNearStart = false;
        for (ThirdRailPlan.Run run : wrapped.runs(100.0D, true)) {
            if (run.side < 0 && run.to > 95.0D) {
                leftNearEnd = true;
            }
            if (run.side < 0 && run.from < 5.0D) {
                leftNearStart = true;
            }
            assertTrue(run.to > run.from, "empty run " + run);
        }
        assertTrue(leftNearEnd && leftNearStart, "the berth's stretch should cover both sides of the seam");
    }
}
