package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
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

/**
 * Metro trains slow for curves, early enough that they are at the curve's limit when they reach it.
 *
 * <p>Before, a service ran every curve at line speed: 15 blocks/s round a 30-block radius is 7.5
 * blocks/s² sideways, three-quarters of a g.</p>
 */
class CurveSpeedTest {

    private static final double TICK = 1.0D / 20.0D;
    private static final double RADIUS = 30.0D;
    private static final double STRAIGHT = 240.0D;

    /** Two long straights joined by tight half-circles: a stadium, closed. */
    private static TrackSection stadium() {
        List<TrackNode> nodes = new ArrayList<>();
        for (double x = 0.0D; x < STRAIGHT; x += 20.0D) {
            nodes.add(new TrackNode(new Vec3(x, 64.0D, -RADIUS)));
        }
        for (int k = 0; k <= 12; k++) {
            double a = -Math.PI / 2.0D + Math.PI * k / 12.0D;
            nodes.add(new TrackNode(new Vec3(STRAIGHT + RADIUS * Math.cos(a), 64.0D, RADIUS * Math.sin(a))));
        }
        for (double x = STRAIGHT - 20.0D; x > 0.0D; x -= 20.0D) {
            nodes.add(new TrackNode(new Vec3(x, 64.0D, RADIUS)));
        }
        for (int k = 0; k < 12; k++) {
            double a = Math.PI / 2.0D + Math.PI * k / 12.0D;
            nodes.add(new TrackNode(new Vec3(RADIUS * Math.cos(a), 64.0D, RADIUS * Math.sin(a))));
        }
        return new TrackSection(1, nodes, true, null);
    }

    @Test
    @DisplayName("a curve's limit is the speed at which it asks the allowed sideways acceleration")
    void limitOnACircle() {
        TrackSection track = stadium();
        // Mid-way round the far end, well clear of where the straight eases into it.
        double far = CurveSpeed.limitAt(track, nearestTo(track, new Vec3(STRAIGHT + RADIUS, 64.0D, 0.0D)));
        double expected = Math.sqrt(CurveSpeed.LATERAL * RADIUS);
        assertEquals(expected, far, expected * 0.15D, "on the 30-block curve");
        double straight = CurveSpeed.limitAt(track, nearestTo(track, new Vec3(STRAIGHT / 2.0D, 64.0D, -RADIUS)));
        assertTrue(straight > 20.0D, "no limit worth the name on the straight: " + straight);
    }

    @Test
    @DisplayName("a service keeps to the curve limits round the ends and runs faster on the straights")
    void serviceObeysCurves() {
        TrackNetwork network = new TrackNetwork();
        TrackSection track = stadium();
        network.addSection(track);
        double length = track.totalLength();
        TransitSystem transit = new TransitSystem();
        TransitStation a = new TransitStation("West", new TrackRef(1, nearestTo(track, new Vec3(40.0D, 64.0D, -RADIUS))));
        TransitStation b = new TransitStation("East", new TrackRef(1, nearestTo(track, new Vec3(200.0D, 64.0D, RADIUS))));
        transit.addStation(a);
        transit.addStation(b);
        transit.addLine(new TransitLine("Ring", Arrays.asList(a, b), true, false, "IN", "OUT"));
        TrainManager trains = new TrainManager();
        Train train = new Train(TrainSpec.metroTrain(3), new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, nearestTo(track, new Vec3(60.0D, 64.0D, -RADIUS))), 0.0D);
        trains.add(1, train);
        transit.enterService(1, train, network, "Ring", TransitDrives.metro(15.0D, TICK));

        double worstLateral = 0.0D;
        double fastest = 0.0D;
        double travelled = 0.0D;
        double last = train.reference().distance();
        for (int t = 0; t < 20 * 240; t++) {
            transit.beginTick(trains, network);
            trains.tick(network, transit.composedWith(null), 4, TICK);
            double v = Math.abs(train.velocity());
            fastest = Math.max(fastest, v);
            // Every car, not just the lead: the last one takes the bend at the same speed.
            for (int car = 0; car < train.spec().carCount(); car++) {
                double s = train.refOfCar(network, car).distance();
                worstLateral = Math.max(worstLateral, v * v * CurveSpeed.curvature(track, s));
            }
            double now = train.reference().distance();
            travelled += Math.floorMod(Math.round((now - last) * 1000), Math.round(length * 1000)) / 1000.0D;
            last = now;
        }
        assertTrue(travelled > 2.0D * length, "it did lap the ring: " + travelled);
        // Stations on both straights leave no leg long enough for full line speed; what matters is
        // that the straights are run well above the curves' limit, not crawled at it.
        assertTrue(fastest > 1.4D * Math.sqrt(CurveSpeed.LATERAL * RADIUS),
            "faster on the straights than round the ends: " + fastest);
        assertTrue(worstLateral <= CurveSpeed.LATERAL * 1.3D,
            "sideways acceleration reached " + worstLateral + " blocks/s², against a limit of " + CurveSpeed.LATERAL);
    }

    private static double nearestTo(TrackSection section, Vec3 point) {
        double best = 0.0D;
        double bestSq = Double.MAX_VALUE;
        for (double s = 0.0D; s < section.totalLength(); s += 0.5D) {
            Vec3 p = section.frameAtDistance(s).position;
            double dx = p.x - point.x;
            double dz = p.z - point.z;
            if (dx * dx + dz * dz < bestSq) {
                bestSq = dx * dx + dz * dz;
                best = s;
            }
        }
        return best;
    }
}
