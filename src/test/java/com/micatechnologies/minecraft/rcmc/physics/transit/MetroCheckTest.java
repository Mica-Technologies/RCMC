package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.rating.RideWarning;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The metro building checks find what they should, where they should, and nothing on good track. */
class MetroCheckTest {

    private static TransitSystem lineOn(double... stops) {
        TransitSystem transit = new TransitSystem();
        List<TransitStation> stations = new ArrayList<>();
        for (int i = 0; i < stops.length; i++) {
            TransitStation station = new TransitStation("S" + i, new TrackRef(1, stops[i]));
            transit.addStation(station);
            stations.add(station);
        }
        transit.addLine(new TransitLine("L", stations, false, "IN", "OUT"));
        return transit;
    }

    private static TrackNetwork network(List<TrackNode> nodes) {
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, nodes, false, null));
        return network;
    }

    private static List<TrackNode> nodes(double[][] points) {
        List<TrackNode> nodes = new ArrayList<>();
        for (double[] p : points) {
            nodes.add(new TrackNode(new Vec3(p[0], p[1], p[2])));
        }
        return nodes;
    }

    private static long count(List<RideWarning> warnings, RideWarning.Kind kind) {
        return warnings.stream().filter(w -> w.kind == kind).count();
    }

    @Test
    @DisplayName("level, straight track with level, straight platforms has nothing to say")
    void cleanTrack() {
        TrackNetwork network = network(nodes(new double[][] {{0, 64, 0}, {100, 64, 0}, {200, 64, 0}, {300, 64, 0}}));
        List<RideWarning> warnings = MetroCheck.check(network, lineOn(50.0D, 250.0D));
        assertTrue(warnings.isEmpty(), "found " + warnings);
    }

    @Test
    @DisplayName("a hairpin is flagged as a tight curve, at the hairpin")
    void tightCurve() {
        List<double[]> points = new ArrayList<>(Arrays.asList(new double[] {0, 64, 0}, new double[] {100, 64, 0}));
        for (int k = 1; k < 8; k++) {
            double a = -Math.PI / 2.0D + Math.PI * k / 8.0D;
            points.add(new double[] {100 + 10 * Math.cos(a), 64, 10 + 10 * Math.sin(a)});
        }
        points.add(new double[] {100, 64, 20});
        points.add(new double[] {0, 64, 20});
        TrackNetwork network = network(nodes(points.toArray(new double[0][])));
        List<RideWarning> warnings = MetroCheck.check(network, lineOn(40.0D, 210.0D));
        assertEquals(1, count(warnings, RideWarning.Kind.TIGHT_CURVE), "one hairpin: " + warnings);
        RideWarning curve = warnings.stream().filter(w -> w.kind == RideWarning.Kind.TIGHT_CURVE).findFirst().get();
        double mid = (curve.from + curve.to) / 2.0D;
        Vec3 at = network.section(1).frameAtDistance(mid).position;
        assertTrue(at.x > 100.0D, "the warning is at the hairpin, not at x=" + at.x);
        assertEquals(Math.sqrt(CurveSpeed.LATERAL * 10.0D), curve.peak, 1.0D, "the limit round a 10-block radius");
    }

    /** A straight climb at a constant {@code grade}, collinear nodes so the spline holds it exactly. */
    private static TrackNetwork incline(double grade) {
        List<double[]> points = new ArrayList<>();
        for (int x = 0; x <= 400; x += 50) {
            points.add(new double[] {x, 64 + grade * x, 0});
        }
        return network(nodes(points.toArray(new double[0][])));
    }

    @Test
    @DisplayName("a steep climb is flagged, and one a train can barely start on is flagged as danger")
    void steepGrades() {
        List<RideWarning> eight = MetroCheck.check(incline(0.08D), lineOn(20.0D, 380.0D));
        assertEquals(1, count(eight, RideWarning.Kind.STEEP_GRADE), "8%: " + eight);
        assertEquals(RideWarning.Severity.CAUTION, eight.stream()
            .filter(w -> w.kind == RideWarning.Kind.STEEP_GRADE).findFirst().get().severity);
        List<RideWarning> sixteen = MetroCheck.check(incline(0.16D), lineOn(20.0D, 380.0D));
        assertEquals(RideWarning.Severity.DANGER, sixteen.stream()
            .filter(w -> w.kind == RideWarning.Kind.STEEP_GRADE).findFirst().get().severity);
        assertTrue(MetroCheck.check(incline(0.04D), lineOn(20.0D, 380.0D)).stream()
            .noneMatch(w -> w.kind == RideWarning.Kind.STEEP_GRADE), "4% is ordinary");
    }

    @Test
    @DisplayName("a platform on a slope or a bend is flagged; the track either side of it is not a platform")
    void platforms() {
        TrackNetwork network = network(nodes(new double[][] {
            {0, 64, 0}, {100, 64, 0}, {200, 67, 0}, {300, 67, 0}, {400, 67, 0}}));
        // One stop on the 3% slope, one on the level.
        List<RideWarning> warnings = MetroCheck.check(network, lineOn(150.0D, 350.0D));
        assertEquals(1, count(warnings, RideWarning.Kind.PLATFORM_GRADE), "only the sloping one: " + warnings);
        assertEquals(0, count(warnings, RideWarning.Kind.STEEP_GRADE), "3% is not steep for running track");
    }
}
