package com.micatechnologies.minecraft.rcmc.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork.End;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork.SectionEnd;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Splitting, merging, closing and reversing sections: whatever was on the track must end up at the
 * same place in the world afterwards, on whichever section that place now belongs to.
 */
class SectionSurgeryTest {

    /** A gently curving open run, so spans differ in length and a naive mapping would show. */
    private static TrackSection run(int id, double x0, int nodes) {
        List<TrackNode> list = new ArrayList<>();
        for (int i = 0; i < nodes; i++) {
            list.add(new TrackNode(new Vec3(x0 + i * 10.0D, 64.0D + (i % 2) * 2.0D, i * i * 0.8D)));
        }
        return new TrackSection(id, list, false, null);
    }

    private static TrackSection loop(int id) {
        List<TrackNode> list = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2.0D * i / 8.0D;
            list.add(new TrackNode(new Vec3(Math.cos(a) * 30.0D, 64.0D + (i % 3), Math.sin(a) * 20.0D)));
        }
        return new TrackSection(id, list, true, null);
    }

    private static TrackSection sectionIn(SectionSurgery.Result r, int id) {
        for (TrackSection s : r.sections()) {
            if (s.id() == id) {
                return s;
            }
        }
        throw new AssertionError("no section " + id + " in the result");
    }

    private static void assertSamePlace(TrackSection before, double d, SectionSurgery.Result r, double tol) {
        TrackRef now = r.map(before.id(), d);
        Vec3 was = before.positionAtDistance(d);
        Vec3 is = sectionIn(r, now.sectionId()).positionAtDistance(now.distance());
        assertTrue(was.distanceTo(is) < tol,
            "distance " + d + " was at " + was + " and is now at " + is + " on #" + now.sectionId());
    }

    @Test
    @DisplayName("reversing a section leaves every point where it was, and turns its ends round")
    void reverseKeepsEveryPoint() {
        TrackSection open = run(1, 0.0D, 6);
        SectionSurgery.Result r = SectionSurgery.reverse(open);
        for (double d = 0.0D; d < open.totalLength(); d += 1.7D) {
            assertSamePlace(open, d, r, 0.05D);
        }
        assertTrue(r.reverses(1));
        assertEquals(new SectionEnd(1, End.END), r.endOf(new SectionEnd(1, End.START)));

        TrackSection circuit = loop(2);
        SectionSurgery.Result c = SectionSurgery.reverse(circuit);
        for (double d = 0.0D; d < circuit.totalLength(); d += 1.9D) {
            assertSamePlace(circuit, d, c, 0.05D);
        }
    }

    @Test
    @DisplayName("splitting open track keeps every point away from the cut in place, on the right half")
    void splitOpenTrack() {
        TrackSection open = run(1, 0.0D, 7);
        SectionSurgery.Result r = SectionSurgery.split(open, 3, 9);
        double cut = open.nodeDistance(3);

        assertEquals(1, r.map(1, cut - 0.5D).sectionId(), "before the node is the first half");
        assertEquals(9, r.map(1, cut + 0.5D).sectionId(), "after the node is the second half");
        assertEquals(9, r.map(1, cut).sectionId(), "the node itself starts the second half");
        assertEquals(1, r.mapEnd(1, cut).sectionId(), "but something ending at the node stays on the first");
        assertEquals(1, r.cutsOn(1).size());
        assertEquals(cut, r.cutsOn(1).get(0), 1e-9);

        // Spans next to the cut change shape a little, since their ends are estimated now; the rest
        // are the same curve.
        for (double d = 0.0D; d < open.nodeDistance(2); d += 1.3D) {
            assertSamePlace(open, d, r, 0.05D);
        }
        for (double d = open.nodeDistance(4); d < open.totalLength(); d += 1.3D) {
            assertSamePlace(open, d, r, 0.05D);
        }
        assertEquals(new SectionEnd(9, End.END), r.endOf(new SectionEnd(1, End.END)));
        SectionEnd[] join = r.joins().get(0);
        assertEquals(new SectionEnd(1, End.END), join[0]);
        assertEquals(new SectionEnd(9, End.START), join[1]);
    }

    @Test
    @DisplayName("splitting a circuit opens it at the node, and trains still run through the join")
    void splitCircuitOpensIt() {
        TrackSection circuit = loop(4);
        SectionSurgery.Result r = SectionSurgery.split(circuit, 2, 99);
        TrackSection open = sectionIn(r, 4);
        assertFalse(open.isClosed());
        assertEquals(circuit.nodes().get(2).position(), open.nodes().get(0).position());
        assertEquals(circuit.nodes().get(2).position(), open.nodes().get(open.nodes().size() - 1).position());
        assertEquals(0.0D, r.map(4, circuit.nodeDistance(2)).distance(), 1e-9);

        TrackNetwork network = new TrackNetwork();
        network.addSection(open);
        for (SectionEnd[] join : r.joins()) {
            network.connect(join[0], join[1]);
        }
        TrackNetwork.Traversal past = network.advance(new TrackRef(4, open.totalLength() - 1.0D), 3.0D);
        assertFalse(past.hitDeadEnd, "a train running off the end should carry on round, not stop dead");
        assertEquals(2.0D, past.ref.distance(), 1e-6);
    }

    @Test
    @DisplayName("merging end to start makes one section with both runs' track where it was")
    void mergeEndToStart() {
        TrackSection a = run(1, 0.0D, 4);
        TrackSection b = run(2, 30.0D, 4);
        // Put b's first node on a's last, as the builder would.
        List<TrackNode> bn = new ArrayList<>(b.nodes());
        bn.set(0, new TrackNode(a.nodes().get(3).position()));
        b = new TrackSection(2, bn, false, null);

        SectionSurgery.Result r = SectionSurgery.merge(a, End.END, b, End.START);
        TrackSection merged = sectionIn(r, 1);
        assertEquals(7, merged.nodes().size(), "the meeting nodes become one");
        assertTrue(r.removedIds().contains(2));
        assertEquals(1, r.map(2, 5.0D).sectionId());
        assertTrue(r.map(2, 5.0D).distance() > a.nodeDistance(2), "b's track comes after a's");
        assertFalse(r.reverses(1));
        assertFalse(r.reverses(2));
        assertNull(r.endOf(new SectionEnd(1, End.END)), "the ends that met are used up");
        assertEquals(new SectionEnd(1, End.END), r.endOf(new SectionEnd(2, End.END)));
        for (double d = 0.0D; d < a.nodeDistance(2); d += 1.1D) {
            assertSamePlace(a, d, r, 0.05D);
        }
    }

    @Test
    @DisplayName("merging tail to tail turns the other section round, never the one being edited")
    void mergeTailToTailTurnsTheOtherRound() {
        TrackSection a = run(1, 0.0D, 4);
        List<TrackNode> bn = new ArrayList<>();
        bn.add(new TrackNode(new Vec3(70.0D, 64.0D, 3.0D)));
        bn.add(new TrackNode(new Vec3(55.0D, 64.0D, 6.0D)));
        bn.add(new TrackNode(new Vec3(42.0D, 64.0D, 7.0D)));
        bn.add(new TrackNode(a.nodes().get(3).position()));
        TrackSection b = new TrackSection(2, bn, false, null);

        SectionSurgery.Result r = SectionSurgery.merge(a, End.END, b, End.END);
        assertFalse(r.reverses(1));
        assertTrue(r.reverses(2));
        TrackSection merged = sectionIn(r, 1);
        assertEquals(bn.get(0).position(), merged.nodes().get(merged.nodes().size() - 1).position(),
            "b's far end is now the merged section's end");
        assertEquals(new SectionEnd(1, End.END), r.endOf(new SectionEnd(2, End.START)));
        // b's start, turned round, is now at the merged section's far end.
        assertEquals(merged.totalLength(), r.mapEnd(2, 0.0D).distance(), 1e-6);
    }

    @Test
    @DisplayName("closing an open run makes a circuit whose last span leads back to the start")
    void closeMakesACircuit() {
        TrackSection circuit = loop(5);
        List<TrackNode> nodes = new ArrayList<>(circuit.nodes());
        nodes.add(new TrackNode(circuit.nodes().get(0).position().add(new Vec3(0.4D, 0.0D, 0.0D))));
        TrackSection open = new TrackSection(5, nodes, false, null);

        SectionSurgery.Result r = SectionSurgery.close(open);
        TrackSection closed = sectionIn(r, 5);
        assertTrue(closed.isClosed());
        assertEquals(8, closed.nodes().size());
        TrackRef last = r.map(5, open.nodeDistance(7) + 0.5D);
        assertTrue(last.distance() > closed.nodeDistance(7), "the last span becomes the wrap span");
        assertNull(r.endOf(new SectionEnd(5, End.START)));
    }
}
