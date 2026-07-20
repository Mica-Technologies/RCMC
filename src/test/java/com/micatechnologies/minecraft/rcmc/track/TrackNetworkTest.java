package com.micatechnologies.minecraft.rcmc.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    /**
     * A straight section along −X, i.e. one built <em>toward</em> the origin. Its END is the
     * lower-x endpoint, which is what makes an END-to-END join geometrically valid.
     */
    private static TrackSection straightTowardOrigin(int id, double xFar, double length) {
        return new TrackSection(id, Arrays.asList(
            node(xFar, 64, 0),
            node(xFar - length / 2.0D, 64, 0),
            node(xFar - length, 64, 0)), false, null);
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
        // Built from x=200 back toward x=100, so its END meets section 1's END head-on.
        TrackSection b = straightTowardOrigin(2, 200, 100);
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
    @DisplayName("moving backwards across a join continues backwards, it does not turn around")
    void backwardTraversalKeepsGoing() {
        // Regression: advance() used to pick the new section's travel direction from BOTH the
        // departing and arriving ends, which was correct only for forward motion. Moving
        // backwards, it re-entered the section it had just left and bounced. Trailing cars of a
        // multi-car train are addressed by negative offsets, so every long train was wrong.
        TrackNetwork n = new TrackNetwork();
        n.addSection(straight(1, 0, 50));
        n.addSection(straight(2, 50, 50));
        n.connect(new TrackNetwork.SectionEnd(1, TrackNetwork.End.END),
                  new TrackNetwork.SectionEnd(2, TrackNetwork.End.START));

        // From 5 blocks into section 2, walking 20 back must land 35 into section 1.
        TrackNetwork.Traversal t = n.advance(new TrackRef(2, 5.0D), -20.0D);
        assertEquals(1, t.ref.sectionId(), "should have crossed back onto section 1");
        assertEquals(35.0D, t.ref.distance(), 1e-6);
        assertFalse(t.reversed, "an END->START join crossed backwards is still not a reversal");
    }

    @Test
    @DisplayName("backing out of a section's start onto another section's end is not a reversal")
    void backwardTraversalOntoAnEnd() {
        // Both legs are "travelling backwards along the distance axis", so the sign of a train's
        // velocity should carry through unchanged. The reversal flag keys on the change in
        // direction, not on the fact that an END was involved.
        TrackNetwork n = new TrackNetwork();
        n.addSection(straight(1, 0, 50));
        // Runs from x=-50 up to x=0, so its END coincides with section 1's START.
        n.addSection(new TrackSection(2, Arrays.asList(
            node(-50, 64, 0), node(-25, 64, 0), node(0, 64, 0)), false, null));
        n.connect(new TrackNetwork.SectionEnd(1, TrackNetwork.End.START),
                  new TrackNetwork.SectionEnd(2, TrackNetwork.End.END));

        TrackNetwork.Traversal t = n.advance(new TrackRef(1, 5.0D), -20.0D);
        assertEquals(2, t.ref.sectionId());
        assertEquals(n.section(2).totalLength() - 15.0D, t.ref.distance(), 1e-6);
        assertFalse(t.reversed);
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
        // Both candidates start where section 1 ends, so either join is geometrically valid —
        // isolating what this test is actually about.
        n.addSection(straight(2, 50, 50));
        n.addSection(straight(3, 50, 80));

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
    @DisplayName("refuses to join endpoints that are far apart — a train would teleport")
    void refusesGappedJoins() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(straight(1, 0, 50));      // ends at x=50
        n.addSection(straight(2, 200, 50));    // starts at x=200

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> n.connect(new TrackNetwork.SectionEnd(1, TrackNetwork.End.END),
                            new TrackNetwork.SectionEnd(2, TrackNetwork.End.START)));
        assertTrue(thrown.getMessage().contains("teleport"), thrown.getMessage());
    }

    @Test
    @DisplayName("a well-formed join measures as smooth; a kinked one does not")
    void alignmentMeasuresJoinQuality() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(straight(1, 0, 50));
        n.addSection(straight(2, 50, 50));

        TrackNetwork.SectionEnd endOfOne = new TrackNetwork.SectionEnd(1, TrackNetwork.End.END);
        TrackNetwork.SectionEnd startOfTwo = new TrackNetwork.SectionEnd(2, TrackNetwork.End.START);

        JoinAlignment collinear = n.alignmentOf(endOfOne, startOfTwo);
        assertEquals(0.0D, collinear.positionGap, 1e-6);
        assertEquals(0.0D, collinear.tangentAngleDegrees, 0.5D);
        assertTrue(collinear.isSmooth());

        // A section leaving the same point at right angles: touching, but kinked.
        n.addSection(new TrackSection(3, Arrays.asList(
            node(50, 64, 0), node(50, 64, 25), node(50, 64, 50)), false, null));
        JoinAlignment kinked = n.alignmentOf(endOfOne, new TrackNetwork.SectionEnd(3, TrackNetwork.End.START));
        assertEquals(0.0D, kinked.positionGap, 1e-6);
        assertEquals(90.0D, kinked.tangentAngleDegrees, 1.0D);
        assertFalse(kinked.isSmooth());
    }

    @Test
    @DisplayName("a kinked join is allowed but reported — warn, don't forbid")
    void kinkedJoinsAreAllowedButReported() {
        // Position gaps are degenerate and refused; kinks are merely uncomfortable, and a builder
        // may well want one while iterating. The editor surfaces it rather than blocking.
        TrackNetwork n = new TrackNetwork();
        n.addSection(straight(1, 0, 50));
        n.addSection(new TrackSection(2, Arrays.asList(
            node(50, 64, 0), node(50, 64, 25), node(50, 64, 50)), false, null));

        TrackNetwork.SectionEnd endOfOne = new TrackNetwork.SectionEnd(1, TrackNetwork.End.END);
        n.connect(endOfOne, new TrackNetwork.SectionEnd(2, TrackNetwork.End.START));

        assertNotNull(n.joinedTo(endOfOne), "the join should have been made");
        assertFalse(n.alignmentOf(endOfOne).isSmooth(), "and reported as rough");
    }

    @Test
    @DisplayName("a closed circuit has no ends, so it cannot be joined")
    void closedCircuitsCannotBeJoined() {
        List<TrackNode> ring = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double ang = 2.0D * Math.PI * i / 6;
            ring.add(node(Math.cos(ang) * 30.0D, 64, Math.sin(ang) * 30.0D));
        }
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, ring, true, null));
        n.addSection(straight(2, 30, 50));

        assertThrows(IllegalArgumentException.class,
            () -> n.connect(new TrackNetwork.SectionEnd(1, TrackNetwork.End.END),
                            new TrackNetwork.SectionEnd(2, TrackNetwork.End.START)));
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
