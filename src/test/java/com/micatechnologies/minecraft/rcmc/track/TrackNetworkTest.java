package com.micatechnologies.minecraft.rcmc.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrackNetworkTest {

    private static TrackNode node(double x, double y, double z) {
        return new TrackNode(new Vec3(x, y, z));
    }

    /** A straight open section along +X from {@code x0} to {@code x0 + length}. */
    private static TrackSection straight(int id, double x0, double length) {
        return new TrackSection(id, Arrays.asList(
            node(x0, 64, 0),
            node(x0 + length / 2.0D, 64, 0),
            node(x0 + length, 64, 0)), false, null);
    }

    @Test
    @DisplayName("advancing within one section is a plain addition")
    void advanceWithinSection() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(straight(1, 0, 100));

        TrackNetwork.Traversal t = n.advance(new TrackRef(1, 10.0D), 25.0D);
        assertEquals(1, t.ref.sectionId());
        assertEquals(35.0D, t.ref.distance(), 1e-6);
        assertFalse(t.reversed);
        assertFalse(t.hitDeadEnd);
    }

    @Test
    @DisplayName("crossing an END-to-START join continues in the same direction")
    void crossEndToStartJoin() {
        TrackNetwork n = new TrackNetwork();
        TrackSection a = straight(1, 0, 100);
        TrackSection b = straight(2, 100, 100);
        n.addSection(a);
        n.addSection(b);
        n.connect(new TrackNetwork.SectionEnd(1, TrackNetwork.End.END),
                  new TrackNetwork.SectionEnd(2, TrackNetwork.End.START));

        TrackNetwork.Traversal t = n.advance(new TrackRef(1, a.totalLength() - 10.0D), 30.0D);
        assertEquals(2, t.ref.sectionId());
        assertEquals(20.0D, t.ref.distance(), 1e-6);
        assertFalse(t.reversed, "an END->START join should not reverse direction");
    }

    @Test
    @DisplayName("crossing an END-to-END join reverses the direction of travel")
    void crossEndToEndJoinReverses() {
        // Two sections built toward each other. The second's distance axis runs opposite to our
        // travel, so a train's velocity sign must flip — callers that ignore `reversed` will see
        // the train turn around at the join.
        TrackNetwork n = new TrackNetwork();
        TrackSection a = straight(1, 0, 100);
        TrackSection b = straight(2, 200, 100);
        n.addSection(a);
        n.addSection(b);
        n.connect(new TrackNetwork.SectionEnd(1, TrackNetwork.End.END),
                  new TrackNetwork.SectionEnd(2, TrackNetwork.End.END));

        TrackNetwork.Traversal t = n.advance(new TrackRef(1, a.totalLength() - 10.0D), 30.0D);
        assertEquals(2, t.ref.sectionId());
        assertEquals(b.totalLength() - 20.0D, t.ref.distance(), 1e-6);
        assertTrue(t.reversed, "an END->END join must report a direction reversal");
    }

    @Test
    @DisplayName("running off unconnected track stops at the boundary and reports a dead end")
    void deadEndIsReported() {
        // A train reaching the end of unconnected track is a real operational failure. It must be
        // visible to the ride-control layer, not silently absorbed.
        TrackNetwork n = new TrackNetwork();
        TrackSection a = straight(1, 0, 100);
        n.addSection(a);

        TrackNetwork.Traversal t = n.advance(new TrackRef(1, a.totalLength() - 5.0D), 50.0D);
        assertTrue(t.hitDeadEnd);
        assertEquals(a.totalLength(), t.ref.distance(), 1e-6);

        TrackNetwork.Traversal back = n.advance(new TrackRef(1, 5.0D), -50.0D);
        assertTrue(back.hitDeadEnd);
        assertEquals(0.0D, back.ref.distance(), 1e-6);
    }

    @Test
    @DisplayName("a closed circuit laps forever without ever leaving its section")
    void closedCircuitLaps() {
        List<TrackNode> ring = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double ang = 2.0D * Math.PI * i / 8;
            ring.add(node(Math.cos(ang) * 40.0D, 64, Math.sin(ang) * 40.0D));
        }
        TrackNetwork n = new TrackNetwork();
        TrackSection circuit = new TrackSection(1, ring, true, null);
        n.addSection(circuit);

        TrackNetwork.Traversal t = n.advance(new TrackRef(1, 0.0D), circuit.totalLength() * 3.5D);
        assertEquals(1, t.ref.sectionId());
        assertFalse(t.hitDeadEnd);
        assertEquals(circuit.totalLength() * 0.5D, t.ref.distance(), 1e-3);
    }

    @Test
    @DisplayName("advancing across several short sections in one step works")
    void multipleJoinsInOneAdvance() {
        TrackNetwork n = new TrackNetwork();
        for (int i = 1; i <= 4; i++) {
            n.addSection(straight(i, (i - 1) * 20.0D, 20.0D));
        }
        for (int i = 1; i <= 3; i++) {
            n.connect(new TrackNetwork.SectionEnd(i, TrackNetwork.End.END),
                      new TrackNetwork.SectionEnd(i + 1, TrackNetwork.End.START));
        }

        // 45 blocks from the start of section 1 lands in section 3.
        TrackNetwork.Traversal t = n.advance(new TrackRef(1, 0.0D), 45.0D);
        assertEquals(3, t.ref.sectionId());
        assertEquals(5.0D, t.ref.distance(), 1e-6);
    }

    @Test
    @DisplayName("joining an end replaces any existing join, since an end leads one place")
    void connectingReplacesExistingJoin() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(straight(1, 0, 50));
        n.addSection(straight(2, 50, 50));
        n.addSection(straight(3, 100, 50));

        TrackNetwork.SectionEnd endOfOne = new TrackNetwork.SectionEnd(1, TrackNetwork.End.END);
        n.connect(endOfOne, new TrackNetwork.SectionEnd(2, TrackNetwork.End.START));
        n.connect(endOfOne, new TrackNetwork.SectionEnd(3, TrackNetwork.End.START));

        assertEquals(3, n.joinedTo(endOfOne).sectionId);
        // The displaced end must be cleaned up on its side too, or it would dangle.
        assertNull(n.joinedTo(new TrackNetwork.SectionEnd(2, TrackNetwork.End.START)));
    }

    @Test
    @DisplayName("removing a section removes every join touching it")
    void removingSectionClearsJoins() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(straight(1, 0, 50));
        n.addSection(straight(2, 50, 50));
        n.connect(new TrackNetwork.SectionEnd(1, TrackNetwork.End.END),
                  new TrackNetwork.SectionEnd(2, TrackNetwork.End.START));

        n.removeSection(2);
        assertNull(n.joinedTo(new TrackNetwork.SectionEnd(1, TrackNetwork.End.END)));
        assertTrue(n.joins().isEmpty());
    }

    @Test
    @DisplayName("allocated section ids never collide with existing ones")
    void idAllocation() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(straight(5, 0, 50));
        int allocated = n.allocateSectionId();
        assertTrue(allocated > 5, "allocated id " + allocated + " collides with existing section 5");
        assertNull(n.section(allocated));
    }

    @Test
    @DisplayName("rejects nonsensical joins")
    void rejectsBadJoins() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(straight(1, 0, 50));

        assertThrows(IllegalArgumentException.class,
            () -> n.connect(new TrackNetwork.SectionEnd(1, TrackNetwork.End.END),
                            new TrackNetwork.SectionEnd(1, TrackNetwork.End.END)));
        assertThrows(IllegalArgumentException.class,
            () -> n.connect(new TrackNetwork.SectionEnd(1, TrackNetwork.End.END),
                            new TrackNetwork.SectionEnd(99, TrackNetwork.End.START)));
        assertThrows(IllegalArgumentException.class,
            () -> n.advance(new TrackRef(99, 0.0D), 1.0D));
    }
}
