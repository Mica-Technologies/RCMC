package com.micatechnologies.minecraft.rcmc.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for picking the track under a click.
 *
 * <p>Picking looks obviously right and is quietly off by a segment. An editor that confidently
 * edits the <em>wrong</em> span is worse than one that refuses, because the builder has to notice
 * before they can undo it.</p>
 */
class TrackPickerTest {

    private static TrackNode at(double x, double y, double z) {
        return new TrackNode(new Vec3(x, y, z));
    }

    /** Straight along +X from the origin at y=64, 100 blocks long. */
    private static TrackSection straight() {
        return new TrackSection(1, Arrays.asList(
            at(0, 64, 0), at(50, 64, 0), at(100, 64, 0)), false, null);
    }

    @Test
    @DisplayName("a point on the track picks itself")
    void picksExactPoint() {
        TrackSection section = straight();
        TrackPicker.Hit hit = TrackPicker.pickOn(section, new Vec3(30, 64, 0));
        assertNotNull(hit);
        assertEquals(30.0D, hit.ref.distance(), 0.1D);
        assertEquals(0.0D, hit.distanceFromQuery, 0.05D);
    }

    @Test
    @DisplayName("a point beside the track picks the nearest point on it")
    void picksNearestPoint() {
        TrackSection section = straight();
        TrackPicker.Hit hit = TrackPicker.pickOn(section, new Vec3(70, 67, 2));
        assertEquals(70.0D, hit.ref.distance(), 0.2D);
        assertEquals(Math.sqrt(9.0D + 4.0D), hit.distanceFromQuery, 0.1D);
    }

    @Test
    @DisplayName("picking across a network returns the closest section, not the first")
    void picksClosestSection() {
        TrackNetwork network = new TrackNetwork();
        network.addSection(straight());
        network.addSection(new TrackSection(2, Arrays.asList(
            at(0, 90, 40), at(50, 90, 40), at(100, 90, 40)), false, null));

        TrackPicker.Hit hit = TrackPicker.pick(network, new Vec3(50, 89, 41), 10.0D);
        assertNotNull(hit);
        assertEquals(2, hit.ref.sectionId(), "should pick the section actually nearby");
    }

    @Test
    @DisplayName("a query far from any track picks nothing")
    void farQueryMisses() {
        TrackNetwork network = new TrackNetwork();
        network.addSection(straight());
        assertNull(TrackPicker.pick(network, new Vec3(50, 200, 0), 5.0D),
            "an editor should refuse rather than grab the nearest track from across the map");
    }

    @Test
    @DisplayName("picking is accurate on a curve, not just a straight")
    void accurateOnCurves() {
        // Sampling-plus-refinement has to hold up where the curve actually curves; a straight line
        // would pass even with a badly broken search.
        List<TrackNode> ring = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double a = 2.0D * Math.PI * i / 12;
            ring.add(at(Math.cos(a) * 40.0D, 64.0D, Math.sin(a) * 40.0D));
        }
        TrackSection circuit = new TrackSection(1, ring, true, null);

        for (double s = 0.0D; s < circuit.totalLength(); s += 7.0D) {
            Vec3 on = circuit.positionAtDistance(s);
            TrackPicker.Hit hit = TrackPicker.pickOn(circuit, on);
            assertTrue(hit.distanceFromQuery < 0.15D,
                "picked " + hit.distanceFromQuery + " blocks away from a point on the curve");
        }
    }

    @Test
    @DisplayName("span lookup returns the pair of nodes a distance falls between")
    void spanIndex() {
        TrackSection section = straight();
        assertEquals(0, TrackPicker.spanIndexAt(section, 1.0D));
        assertEquals(0, TrackPicker.spanIndexAt(section, section.nodeDistance(1) - 1.0D));
        assertEquals(1, TrackPicker.spanIndexAt(section, section.nodeDistance(1) + 1.0D));
        // Past the end clamps rather than running off the node list.
        assertTrue(TrackPicker.spanIndexAt(section, section.totalLength() + 50.0D) < 2);
    }
}
