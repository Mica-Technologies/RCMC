package com.micatechnologies.minecraft.rcmc.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrackWalkTest {

    private static final double HORIZON = 10_000.0D;

    private static TrackNode node(double x, double y, double z) {
        return new TrackNode(new Vec3(x, y, z));
    }

    @Test
    @DisplayName("distance within one section is a plain subtraction, in either direction")
    void withinOneSection() {
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(100, 64, 0), node(200, 64, 0)), false, null));

        assertEquals(150.0D, TrackWalk.distanceTo(network,
            new TrackRef(1, 20.0D), 1.0D, new TrackRef(1, 170.0D), HORIZON), 1e-6D);
        assertEquals(150.0D, TrackWalk.distanceTo(network,
            new TrackRef(1, 170.0D), -1.0D, new TrackRef(1, 20.0D), HORIZON), 1e-6D);
    }

    @Test
    @DisplayName("a target behind the walk direction on open track is unreachable")
    void behindOnOpenTrackIsUnreachable() {
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(100, 64, 0), node(200, 64, 0)), false, null));

        assertTrue(Double.isInfinite(TrackWalk.distanceTo(network,
            new TrackRef(1, 170.0D), 1.0D, new TrackRef(1, 20.0D), HORIZON)));
    }

    @Test
    @DisplayName("the walk crosses joins and accumulates across sections")
    void crossesJoins() {
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(50, 64, 0), node(100, 64, 0)), false, null));
        network.addSection(new TrackSection(2, Arrays.asList(
            node(100, 64, 0), node(150, 64, 0), node(200, 64, 0)), false, null));
        network.connect(new TrackNetwork.SectionEnd(1, TrackNetwork.End.END),
            new TrackNetwork.SectionEnd(2, TrackNetwork.End.START));

        double sectionOneLength = network.section(1).totalLength();
        double expected = (sectionOneLength - 10.0D) + 30.0D;
        assertEquals(expected, TrackWalk.distanceTo(network,
            new TrackRef(1, 10.0D), 1.0D, new TrackRef(2, 30.0D), HORIZON), 1e-6D);
    }

    @Test
    @DisplayName("on a closed circuit a target behind is most of a lap ahead")
    void closedCircuitWraps() {
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(100, 64, 0), node(100, 64, 100), node(0, 64, 100)), true, null));
        double length = network.section(1).totalLength();

        double there = TrackWalk.distanceTo(network,
            new TrackRef(1, length * 0.75D), 1.0D, new TrackRef(1, length * 0.25D), HORIZON);
        assertEquals(length * 0.5D, there, 1e-6D);
    }

    @Test
    @DisplayName("the walk follows a switch's current selection, and a relined switch changes the answer")
    void honoursSwitchSelection() {
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(50, 64, 0), node(100, 64, 0)), false, null));
        network.addSection(new TrackSection(2, Arrays.asList(
            node(100, 64, 0), node(150, 64, 5), node(200, 64, 20)), false, null));
        network.addSection(new TrackSection(3, Arrays.asList(
            node(100, 64, 0), node(150, 64, -5), node(200, 64, -20)), false, null));
        TrackNetwork.SectionEnd throat = new TrackNetwork.SectionEnd(1, TrackNetwork.End.END);
        network.addSwitch(throat, Arrays.asList(
            new TrackNetwork.SectionEnd(2, TrackNetwork.End.START),
            new TrackNetwork.SectionEnd(3, TrackNetwork.End.START)));

        TrackRef from = new TrackRef(1, 50.0D);
        TrackRef onBranchTwo = new TrackRef(2, 40.0D);
        assertTrue(Double.isFinite(TrackWalk.distanceTo(network, from, 1.0D, onBranchTwo, HORIZON)),
            "lined branch should be reachable");

        network.setSwitchSelection(throat, 1);
        assertTrue(Double.isInfinite(TrackWalk.distanceTo(network, from, 1.0D, onBranchTwo, HORIZON)),
            "a branch the switch is no longer lined to is unreachable");
    }

    @Test
    @DisplayName("the horizon bounds the search honestly")
    void horizonBoundsTheSearch() {
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(100, 64, 0), node(200, 64, 0)), false, null));

        assertTrue(Double.isInfinite(TrackWalk.distanceTo(network,
            new TrackRef(1, 0.0D), 1.0D, new TrackRef(1, 190.0D), 50.0D)));
    }
}
