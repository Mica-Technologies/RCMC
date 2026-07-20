package com.micatechnologies.minecraft.rcmc.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrackSectionTest {

    private static TrackNode node(double x, double y, double z) {
        return new TrackNode(new Vec3(x, y, z));
    }

    private static TrackNode node(double x, double y, double z, double bank) {
        return new TrackNode(new Vec3(x, y, z), bank, null);
    }

    /** A closed circuit: a horizontal ring of radius 40, which has a genuine roll residual. */
    private static TrackSection ring(int nodeCount, double radius) {
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            double a = 2.0D * Math.PI * i / nodeCount;
            nodes.add(node(Math.cos(a) * radius, 64.0D, Math.sin(a) * radius));
        }
        return new TrackSection(1, nodes, true, null);
    }

    @Test
    @DisplayName("an open section starts and ends at its first and last node")
    void openSectionSpansItsNodes() {
        TrackSection s = new TrackSection(1,
            Arrays.asList(node(0, 64, 0), node(30, 64, 0), node(60, 70, 0)), false, null);

        assertEquals(0.0D, s.nodeDistance(0), 1e-9);
        assertEquals(s.totalLength(), s.nodeDistance(2), 1e-6);

        Vec3 start = s.positionAtDistance(0.0D);
        assertEquals(0.0D, start.x, 1e-6);
        Vec3 end = s.positionAtDistance(s.totalLength());
        assertEquals(60.0D, end.x, 1e-6);
        assertEquals(70.0D, end.y, 1e-6);
    }

    @Test
    @DisplayName("a closed circuit wraps past its end instead of clamping")
    void closedCircuitWraps() {
        TrackSection s = ring(8, 40.0D);
        double length = s.totalLength();

        // One full lap plus 5 blocks lands 5 blocks in.
        assertEquals(5.0D, s.clampDistance(length + 5.0D), 1e-6);
        // Going backwards past the start wraps to near the end.
        assertEquals(length - 5.0D, s.clampDistance(-5.0D), 1e-6);

        Vec3 atZero = s.positionAtDistance(0.0D);
        Vec3 atLap = s.positionAtDistance(length);
        assertEquals(atZero.x, atLap.x, 1e-6);
        assertEquals(atZero.z, atLap.z, 1e-6);
    }

    @Test
    @DisplayName("an open section clamps at a dead end rather than wrapping")
    void openSectionClamps() {
        TrackSection s = new TrackSection(1,
            Arrays.asList(node(0, 64, 0), node(30, 64, 0), node(60, 64, 0)), false, null);
        assertEquals(s.totalLength(), s.clampDistance(s.totalLength() + 500.0D), 1e-9);
        assertEquals(0.0D, s.clampDistance(-500.0D), 1e-9);
    }

    @Test
    @DisplayName("closed-circuit roll residual is cancelled — no seam at the start/finish line")
    void closedCircuitHasNoRollSeam() {
        // The headline correctness property of this class. Parallel transport is not periodic:
        // untreated, the frame arriving back at s=0 is rotated about the tangent by the curve's
        // total torsion, which renders as a visible snap where cars cross the line.
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double a = 2.0D * Math.PI * i / 12;
            // Deliberately non-planar so transport genuinely accumulates roll.
            nodes.add(node(Math.cos(a) * 50.0D, 64.0D + Math.sin(a * 3.0D) * 12.0D, Math.sin(a) * 50.0D));
        }
        TrackSection s = new TrackSection(1, nodes, true, null);

        TrackFrame atStart = s.frameAtDistance(0.0D);
        TrackFrame atEnd = s.frameAtDistance(s.totalLength());

        assertTrue(atStart.up.dot(atEnd.up) > 0.9999D,
            "roll seam at the start/finish line: up went from " + atStart.up + " to " + atEnd.up
                + " (residual was " + Math.toDegrees(s.rollResidual()) + " deg)");
    }

    @Test
    @DisplayName("a flat ring actually has a residual worth correcting")
    void residualIsNonTrivialForSomeCircuits() {
        // Guards the test above from being vacuous. If a shape had zero residual anyway, the
        // correction would be untested. A helical circuit accumulates real torsion.
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double a = 2.0D * Math.PI * i / 12;
            nodes.add(node(Math.cos(a) * 50.0D, 64.0D + Math.sin(a * 3.0D) * 12.0D, Math.sin(a) * 50.0D));
        }
        TrackSection s = new TrackSection(1, nodes, true, null);
        assertNotEquals(0.0D, s.rollResidual(), "expected a non-zero residual on a twisting circuit");
    }

    @Test
    @DisplayName("bank interpolates smoothly between nodes and hits authored values at them")
    void bankInterpolation() {
        TrackSection s = new TrackSection(1, Arrays.asList(
            node(0, 64, 0, 0.0D),
            node(40, 64, 0, 45.0D),
            node(80, 64, 0, 0.0D)), false, null);

        assertEquals(0.0D, Math.toDegrees(s.bankRadiansAt(s.nodeDistance(0))), 1e-6);
        assertEquals(45.0D, Math.toDegrees(s.bankRadiansAt(s.nodeDistance(1))), 1e-6);
        assertEquals(0.0D, Math.toDegrees(s.bankRadiansAt(s.nodeDistance(2))), 1e-6);

        // Smoothstep is symmetric, so the midpoint of a span is exactly halfway in value.
        double mid = (s.nodeDistance(0) + s.nodeDistance(1)) / 2.0D;
        assertEquals(22.5D, Math.toDegrees(s.bankRadiansAt(mid)), 0.5D);

        // And it must be monotonic and continuous across the whole span.
        double previous = -1.0D;
        for (int i = 0; i <= 100; i++) {
            double at = s.nodeDistance(0) + (s.nodeDistance(1) - s.nodeDistance(0)) * i / 100.0D;
            double bank = Math.toDegrees(s.bankRadiansAt(at));
            assertTrue(bank >= previous - 1e-9, "bank went backwards mid-span at s=" + at);
            previous = bank;
        }
    }

    @Test
    @DisplayName("bank rate is continuous across a node — no jolt")
    void bankRateIsContinuousAcrossNodes() {
        // Linear interpolation would step the roll RATE at each node. The bank angle would still
        // look continuous on paper while the rider felt a jolt, so assert the derivative.
        TrackSection s = new TrackSection(1, Arrays.asList(
            node(0, 64, 0, 0.0D),
            node(40, 64, 0, 30.0D),
            node(80, 64, 0, 60.0D)), false, null);

        double nodeAt = s.nodeDistance(1);
        double h = 0.5D;
        double rateBefore = (s.bankRadiansAt(nodeAt) - s.bankRadiansAt(nodeAt - h)) / h;
        double rateAfter = (s.bankRadiansAt(nodeAt + h) - s.bankRadiansAt(nodeAt)) / h;

        // Smoothstep drives the rate to zero at both ends of every span, so both sides approach 0.
        assertEquals(0.0D, rateBefore, 0.02D, "bank rate nonzero approaching the node");
        assertEquals(0.0D, rateAfter, 0.02D, "bank rate nonzero leaving the node");
    }

    @Test
    @DisplayName("banking rolls the frame without disturbing the direction of travel")
    void bankingPreservesForward() {
        TrackSection banked = new TrackSection(1, Arrays.asList(
            node(0, 64, 0, 0.0D), node(40, 64, 0, 60.0D), node(80, 64, 0, 0.0D)), false, null);
        TrackSection level = new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(40, 64, 0), node(80, 64, 0)), false, null);

        double at = banked.nodeDistance(1);
        TrackFrame b = banked.frameAtDistance(at);
        TrackFrame l = level.frameAtDistance(at);

        assertTrue(b.forward.dot(l.forward) > 0.99999D, "banking changed the direction of travel");
        assertEquals(60.0D, Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, b.up.dot(l.up))))), 0.5D);
    }

    @Test
    @DisplayName("reversing a section flips bank sign, since right becomes left")
    void reversingFlipsBank() {
        TrackSection s = new TrackSection(1, Arrays.asList(
            node(0, 64, 0, 10.0D), node(40, 64, 0, 30.0D), node(80, 64, 0, 0.0D)), false, null);
        TrackSection r = s.reversed();

        assertEquals(0.0D, r.nodes().get(0).bankDegrees(), 1e-9);
        assertEquals(-30.0D, r.nodes().get(1).bankDegrees(), 1e-9);
        assertEquals(-10.0D, r.nodes().get(2).bankDegrees(), 1e-9);
        assertEquals(s.totalLength(), r.totalLength(), 1e-6);
    }

    @Test
    @DisplayName("editing produces a new section with rebuilt geometry")
    void editingRebuildsGeometry() {
        TrackSection s = new TrackSection(1,
            Arrays.asList(node(0, 64, 0), node(40, 64, 0), node(80, 64, 0)), false, null);
        double before = s.totalLength();

        TrackSection edited = s.withNode(2, node(200, 64, 0));
        assertTrue(edited.totalLength() > before + 100.0D, "geometry was not rebuilt after edit");
        assertEquals(before, s.totalLength(), 1e-9, "original section was mutated");
    }

    @Test
    @DisplayName("per-node style overrides take effect from that node onward")
    void styleOverrides() {
        TrackSection s = new TrackSection(1, Arrays.asList(
            new TrackNode(new Vec3(0, 64, 0), 0, null),
            new TrackNode(new Vec3(40, 64, 0), 0, "wooden"),
            new TrackNode(new Vec3(80, 64, 0), 0, null)), false, "steel");

        assertEquals("steel", s.styleAtDistance(0.0D));
        assertEquals("wooden", s.styleAtDistance(s.nodeDistance(1)));
        assertEquals("wooden", s.styleAtDistance(s.totalLength()));
    }

    @Test
    @DisplayName("rejects too few nodes, with a different minimum for circuits")
    void rejectsDegenerateSections() {
        assertThrows(IllegalArgumentException.class,
            () -> new TrackSection(1, Arrays.asList(node(0, 64, 0)), false, null));
        assertThrows(IllegalArgumentException.class,
            () -> new TrackSection(1, Arrays.asList(node(0, 64, 0), node(10, 64, 0)), true, null));
    }
}
