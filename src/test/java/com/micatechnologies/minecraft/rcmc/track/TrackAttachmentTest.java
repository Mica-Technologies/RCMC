package com.micatechnologies.minecraft.rcmc.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Continuing an existing section from one of its free ends.
 *
 * <p>The point of these tests is the seam. A builder who continues a run expects the track to carry
 * on, not to kink where the new nodes begin — and "no kink" is a measurable claim about tangent
 * direction, so it is measured here rather than asserted as an implementation detail.</p>
 *
 * <p>Note what is <em>not</em> tested: that {@link TrackAttachment} enforces tangent continuity. It
 * does not, and should not. Continuity comes from keeping the track in one Catmull-Rom section,
 * which is C¹ by construction. These tests verify that property actually holds at the seam, which
 * is the thing worth knowing — an implementation that quietly created a second section and joined
 * it would pass every structural test and fail this one.</p>
 */
class TrackAttachmentTest {

    private static TrackNode node(double x, double y, double z) {
        return new TrackNode(new Vec3(x, y, z), 0.0D, null);
    }

    /** A gently curving open run, so the seam is not trivially straight. */
    private static TrackSection existing(int id) {
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            nodes.add(node(i * 10.0D, 64.0D + i * 0.5D, i * i * 0.4D));
        }
        return new TrackSection(id, nodes, false, null);
    }

    private static TrackNetwork networkWith(TrackSection section) {
        TrackNetwork network = new TrackNetwork();
        network.addSection(section);
        return network;
    }

    /** Largest angle between tangents either side of {@code at}, in degrees. */
    private static double kinkAt(TrackSection section, double at) {
        double step = 0.05D;
        Vec3 before = section.tangentAtDistance(Math.max(0.0D, at - step));
        Vec3 after = section.tangentAtDistance(Math.min(section.totalLength(), at + step));
        double cos = Math.max(-1.0D, Math.min(1.0D, before.normalize().dot(after.normalize())));
        return Math.toDegrees(Math.acos(cos));
    }

    @Test
    @DisplayName("a free end within the radius is found, and a joined or closed one is not")
    void findsOnlyFreeEnds() {
        TrackSection section = existing(1);
        TrackNetwork network = networkWith(section);
        Vec3 tail = section.endpointAt(TrackNetwork.End.END);

        TrackAttachment.Target target = TrackAttachment.find(network, tail.add(new Vec3(1, 0, 1)), 3.0D);
        assertNotNull(target, "the tail is free and within reach");
        assertEquals(1, target.sectionId);
        assertEquals(TrackNetwork.End.END, target.end);
        assertTrue(target.appends());

        assertNull(TrackAttachment.find(network, tail.add(new Vec3(20, 0, 0)), 3.0D),
            "nothing should be offered well outside the radius");

        // A circuit has no free end; offering to continue one would mean cutting it open.
        TrackNetwork circuitNet = networkWith(new TrackSection(2, existing(2).nodes(), true, null));
        assertNull(TrackAttachment.find(circuitNet, circuitNet.section(2).endpointAt(
            TrackNetwork.End.END), 5.0D), "a closed circuit has no free end");
    }

    @Test
    @DisplayName("continuing a run leaves no kink where the new track begins")
    void noKinkAtTheSeam() {
        TrackSection section = existing(1);
        double seam = section.totalLength();
        Vec3 tail = section.endpointAt(TrackNetwork.End.END);
        Vec3 out = section.exitDirectionAt(TrackNetwork.End.END);

        // Built the way the tool builds it: first node snapped onto the endpoint, then carrying on.
        List<TrackNode> added = new ArrayList<>();
        added.add(new TrackNode(tail, 0.0D, null));
        for (int i = 1; i <= 4; i++) {
            Vec3 p = tail.add(out.scale(i * 9.0D)).add(new Vec3(0, i * 0.6D, i * 1.5D));
            added.add(new TrackNode(p, 0.0D, null));
        }

        TrackSection extended = TrackAttachment.extend(section, TrackNetwork.End.END, added);
        assertTrue(extended.totalLength() > section.totalLength(), "the section should have grown");
        assertEquals(1, extended.id(), "extending keeps the section's identity");

        // THE assertion. A join between two sections could be off by any angle and nothing would
        // complain; inside one spline the tangent is continuous, so this must be ~0.
        double kink = kinkAt(extended, seam);
        assertTrue(kink < 1.0D,
            "the track kinks by " + String.format("%.2f", kink) + "° where the new nodes start; "
                + "continuing a section is supposed to be seamless because it stays one spline");
    }

    @Test
    @DisplayName("the snapped duplicate node is dropped, so the seam has no zero-length span")
    void dropsTheCoincidentNode() {
        TrackSection section = existing(1);
        Vec3 tail = section.endpointAt(TrackNetwork.End.END);
        List<TrackNode> added = new ArrayList<>();
        added.add(new TrackNode(tail, 0.0D, null));
        added.add(node(tail.x + 8.0D, tail.y, tail.z + 3.0D));
        added.add(node(tail.x + 16.0D, tail.y, tail.z + 8.0D));

        TrackSection extended = TrackAttachment.extend(section, TrackNetwork.End.END, added);
        assertEquals(section.nodes().size() + 2, extended.nodes().size(),
            "the node sitting on the old endpoint should not be kept as well as the endpoint");
        for (int i = 0; i + 1 < extended.nodes().size(); i++) {
            double gap = extended.nodes().get(i).position()
                .distanceTo(extended.nodes().get(i + 1).position());
            assertTrue(gap > 1.0e-6D, "coincident nodes at index " + i + " give the spline a "
                + "zero-length span to turn through");
        }
    }

    @Test
    @DisplayName("continuing from the start puts the new track before the old, still seamlessly")
    void prependKeepsOrderAndContinuity() {
        TrackSection section = existing(1);
        Vec3 head = section.endpointAt(TrackNetwork.End.START);
        Vec3 out = section.exitDirectionAt(TrackNetwork.End.START);

        List<TrackNode> added = new ArrayList<>();
        added.add(new TrackNode(head, 0.0D, null));
        for (int i = 1; i <= 3; i++) {
            added.add(new TrackNode(head.add(out.scale(i * 9.0D)), 0.0D, null));
        }

        TrackSection extended = TrackAttachment.extend(section, TrackNetwork.End.START, added);
        assertEquals(section.nodes().size() + 3, extended.nodes().size());

        // The old run must still be at the far end, in its original order.
        List<TrackNode> tailOfNew = extended.nodes()
            .subList(extended.nodes().size() - section.nodes().size(), extended.nodes().size());
        for (int i = 0; i < section.nodes().size(); i++) {
            assertEquals(section.nodes().get(i).position(), tailOfNew.get(i).position(),
                "prepending must not reorder or move the track that was already there");
        }

        double seam = extended.totalLength() - section.totalLength();
        double kink = kinkAt(extended, seam);
        assertTrue(kink < 5.0D, "kink of " + String.format("%.2f", kink) + "° at the prepend seam");
    }

    @Test
    @DisplayName("the same nodes as a SEPARATE section would kink — why extending is the design")
    void separateSectionWouldKink() {
        // The contrast case, and the justification for the whole class. Identical geometry, built
        // the obvious way instead: a second section starting at the first one's endpoint, joined
        // end-to-end. TrackNetwork.connect permits this — it checks the gap, not the angle.
        //
        // If this test ever starts passing, extending is no longer buying anything and the simpler
        // join-two-sections approach would do.
        TrackSection section = existing(1);
        Vec3 tail = section.endpointAt(TrackNetwork.End.END);

        // Deliberately setting off at an angle to the arriving track, which is what a builder does
        // when they click the next node wherever looks right.
        List<TrackNode> added = new ArrayList<>();
        added.add(new TrackNode(tail, 0.0D, null));
        added.add(node(tail.x + 9.0D, tail.y + 1.0D, tail.z - 6.0D));
        added.add(node(tail.x + 18.0D, tail.y + 2.0D, tail.z - 14.0D));
        added.add(node(tail.x + 27.0D, tail.y + 3.0D, tail.z - 24.0D));

        TrackSection separate = new TrackSection(2, added, false, null);
        Vec3 arriving = section.tangentAtDistance(section.totalLength()).normalize();
        Vec3 leaving = separate.tangentAtDistance(0.0D).normalize();
        double cos = Math.max(-1.0D, Math.min(1.0D, arriving.dot(leaving)));
        double joinKink = Math.toDegrees(Math.acos(cos));

        assertTrue(joinKink > 5.0D,
            "expected a visible kink across a section join, got " + String.format("%.2f", joinKink)
                + "° — if joins are seamless on their own, TrackAttachment is unnecessary");

        // And the same nodes, extended into one section instead, are smooth.
        TrackSection extended = TrackAttachment.extend(section, TrackNetwork.End.END, added);
        double seamKink = kinkAt(extended, section.totalLength());
        assertTrue(seamKink < joinKink * 0.5D,
            "extending should be markedly smoother than joining: " + String.format("%.2f", seamKink)
                + "° vs " + String.format("%.2f", joinKink) + "°");
    }

    @Test
    @DisplayName("a closed circuit cannot be continued")
    void refusesToExtendACircuit() {
        TrackSection circuit = new TrackSection(1, existing(1).nodes(), true, null);
        List<TrackNode> added = new ArrayList<>();
        added.add(node(200.0D, 64.0D, 0.0D));
        assertThrows(IllegalArgumentException.class,
            () -> TrackAttachment.extend(circuit, TrackNetwork.End.END, added));
    }
}
