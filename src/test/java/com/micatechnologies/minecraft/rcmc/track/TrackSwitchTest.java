package com.micatechnologies.minecraft.rcmc.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrackSwitchTest {

    private static TrackNode node(double x, double y, double z) {
        return new TrackNode(new Vec3(x, y, z));
    }

    /**
     * A trunk (section 1) ending at x=100, with two diverging branches (sections 2 and 3)
     * starting exactly there — the throat is the trunk's END.
     */
    private static TrackNetwork forkNetwork() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(50, 64, 0), node(100, 64, 0)), false, null));
        n.addSection(new TrackSection(2, Arrays.asList(
            node(100, 64, 0), node(150, 64, 5), node(200, 64, 20)), false, null));
        n.addSection(new TrackSection(3, Arrays.asList(
            node(100, 64, 0), node(150, 64, -5), node(200, 64, -20)), false, null));
        n.addSwitch(throat(), branches());
        return n;
    }

    private static TrackNetwork.SectionEnd throat() {
        return new TrackNetwork.SectionEnd(1, TrackNetwork.End.END);
    }

    private static List<TrackNetwork.SectionEnd> branches() {
        return Arrays.asList(
            new TrackNetwork.SectionEnd(2, TrackNetwork.End.START),
            new TrackNetwork.SectionEnd(3, TrackNetwork.End.START));
    }

    @Test
    @DisplayName("traversal through the throat follows the selected branch")
    void throatFollowsSelectedBranch() {
        TrackNetwork network = forkNetwork();
        double trunkLength = network.section(1).totalLength();

        TrackNetwork.Traversal t = network.advance(new TrackRef(1, trunkLength - 1.0D), 5.0D);
        assertEquals(2, t.ref.sectionId(), "expected the initially-lined first branch");
        assertEquals(4.0D, t.ref.distance(), 1e-9D);
    }

    @Test
    @DisplayName("relining the switch sends the next traversal down the other branch")
    void reliningChangesThePath() {
        TrackNetwork network = forkNetwork();
        network.setSwitchSelection(throat(), 1);
        double trunkLength = network.section(1).totalLength();

        TrackNetwork.Traversal t = network.advance(new TrackRef(1, trunkLength - 1.0D), 5.0D);
        assertEquals(3, t.ref.sectionId());
    }

    @Test
    @DisplayName("a trailing move from the selected branch passes through to the throat")
    void trailingFromSelectedBranchPasses() {
        TrackNetwork network = forkNetwork();
        // Backwards along branch 2, toward its START and through the switch into the trunk.
        TrackNetwork.Traversal t = network.advance(new TrackRef(2, 1.0D), -5.0D);
        assertEquals(1, t.ref.sectionId(), "expected to pass through the points into the trunk");
        assertTrue(t.reversed == false, "trunk END to branch START keeps the axis sense");
        assertEquals(network.section(1).totalLength() - 4.0D, t.ref.distance(), 1e-9D);
    }

    @Test
    @DisplayName("a trailing move against the points is a dead end, not a teleport")
    void trailingAgainstThePointsDeadEnds() {
        TrackNetwork network = forkNetwork();
        // Switch is lined to branch 2; arrive from branch 3.
        TrackNetwork.Traversal t = network.advance(new TrackRef(3, 1.0D), -5.0D);
        assertTrue(t.hitDeadEnd, "expected running against the points to stop the train");
        assertEquals(3, t.ref.sectionId());
        assertEquals(0.0D, t.ref.distance(), 1e-9D);
    }

    @Test
    @DisplayName("switched ends refuse plain joins, and joined ends refuse switches")
    void switchAndJoinAreMutuallyExclusive() {
        TrackNetwork network = forkNetwork();
        assertThrows(IllegalArgumentException.class, () -> network.connect(
            throat(), new TrackNetwork.SectionEnd(2, TrackNetwork.End.START)));

        TrackNetwork plain = new TrackNetwork();
        plain.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(50, 64, 0), node(100, 64, 0)), false, null));
        plain.addSection(new TrackSection(2, Arrays.asList(
            node(100, 64, 0), node(150, 64, 5), node(200, 64, 20)), false, null));
        plain.addSection(new TrackSection(3, Arrays.asList(
            node(100, 64, 0), node(150, 64, -5), node(200, 64, -20)), false, null));
        plain.connect(new TrackNetwork.SectionEnd(1, TrackNetwork.End.END),
            new TrackNetwork.SectionEnd(2, TrackNetwork.End.START));
        assertThrows(IllegalArgumentException.class,
            () -> plain.addSwitch(new TrackNetwork.SectionEnd(1, TrackNetwork.End.END),
                Arrays.asList(new TrackNetwork.SectionEnd(2, TrackNetwork.End.START),
                    new TrackNetwork.SectionEnd(3, TrackNetwork.End.START))));
    }

    @Test
    @DisplayName("a switch whose branch endpoints do not meet the throat is rejected")
    void misalignedSwitchIsRejected() {
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(50, 64, 0), node(100, 64, 0)), false, null));
        network.addSection(new TrackSection(2, Arrays.asList(
            node(100, 64, 0), node(150, 64, 5), node(200, 64, 20)), false, null));
        network.addSection(new TrackSection(3, Arrays.asList(
            node(120, 64, 0), node(150, 64, -5), node(200, 64, -20)), false, null));
        assertThrows(IllegalArgumentException.class,
            () -> network.addSwitch(new TrackNetwork.SectionEnd(1, TrackNetwork.End.END),
                Arrays.asList(new TrackNetwork.SectionEnd(2, TrackNetwork.End.START),
                    new TrackNetwork.SectionEnd(3, TrackNetwork.End.START))));
    }

    @Test
    @DisplayName("removing a section removes any switch touching it")
    void removingASectionRemovesItsSwitch() {
        TrackNetwork network = forkNetwork();
        network.removeSection(3);
        assertNull(network.switchAt(throat()), "expected the whole switch dropped with its branch");
        // The trunk end is free again, so a plain join is legal now.
        network.connect(throat(), new TrackNetwork.SectionEnd(2, TrackNetwork.End.START));
    }
}
