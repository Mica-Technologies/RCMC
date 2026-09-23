package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Trains in service do not run into, or through, other trains — on a line with no signals at all.
 *
 * <p>Found in game: two Subway services on the one loop, running opposite ways, passed straight
 * through each other at Midway, and both through a train parked on the loop.</p>
 */
class TrainSightTest {

    private static final double TICK = 1.0D / 20.0D;

    private final TrackNetwork network = ring();
    private final double length = network.section(1).totalLength();
    private final TransitSystem transit = new TransitSystem();
    private final TrainManager trains = new TrainManager();

    TrainSightTest() {
        TransitStation a = new TransitStation("Union", new TrackRef(1, length * 0.10D));
        TransitStation b = new TransitStation("Central", new TrackRef(1, length * 0.40D));
        TransitStation c = new TransitStation("Harbor", new TrackRef(1, length * 0.70D));
        transit.addStation(a);
        transit.addStation(b);
        transit.addStation(c);
        transit.addLine(new TransitLine("Subway", Arrays.asList(a, b, c), true, false, "INBOUND", "OUTBOUND"));
    }

    private static TrackNetwork ring() {
        List<TrackNode> nodes = new ArrayList<>();
        double side = 120.0D;
        for (double[] p : new double[][] {{0, 0}, {side, 0}, {side, side}, {0, side}}) {
            nodes.add(new TrackNode(new Vec3(p[0], 64.0D, p[1])));
        }
        TrackNetwork ring = new TrackNetwork();
        ring.addSection(new TrackSection(1, nodes, true, null));
        return ring;
    }

    private Train put(int id, double at, double velocity) {
        Train train = new Train(TrainSpec.metroTrain(3), new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, at), velocity);
        trains.add(id, train);
        return train;
    }

    private void serve(int id, Train train) {
        transit.enterService(id, train, network, "Subway", TransitDrives.metro(15.0D, TICK));
    }

    /** Whether the two trains' bodies share any rail. Both trail their reference toward −s. */
    private boolean overlap(Train one, Train two) {
        double span = one.spec().totalLength();
        double gap = Math.floorMod((long) Math.round((two.reference().distance() - one.reference().distance()) * 1000),
            (long) Math.round(length * 1000)) / 1000.0D;
        return gap < span || gap > length - span;
    }

    private void run(int ticks, Train one, Train two) {
        for (int t = 0; t < ticks; t++) {
            transit.beginTick(trains, network);
            trains.tick(network, transit.composedWith(null), 4, TICK);
            assertFalse(overlap(one, two), "tick " + t + ": the trains share rail, at s="
                + one.reference().distance() + " and s=" + two.reference().distance());
        }
    }

    @Test
    @DisplayName("two services running at each other on one track stop short, rather than pass through")
    void headOn() {
        Train out = put(1, length * 0.20D, 12.0D);
        Train in = put(2, length * 0.60D, -12.0D);
        serve(1, out);
        serve(2, in);
        run(20 * 120, out, in);
    }

    @Test
    @DisplayName("a service stops behind a train parked on its line, and does not run through it")
    void parkedTrain() {
        Train parked = put(1, length * 0.55D, 0.0D);
        Train service = put(2, length * 0.15D, 12.0D);
        serve(2, service);
        run(20 * 120, service, parked);
        assertTrue(Math.abs(service.velocity()) < 0.1D, "it waits, standing, behind the parked train");
    }

    @Test
    @DisplayName("a faster follower on the same line keeps its distance")
    void follower() {
        // Started with room to stop: a train closing at 9 blocks/s needs ~95 blocks to.
        Train lead = put(1, length * 0.45D, 6.0D);
        Train follow = put(2, length * 0.05D, 15.0D);
        serve(1, lead);
        serve(2, follow);
        run(20 * 300, follow, lead);
    }

    @Test
    @DisplayName("head-on is told apart from following, even half a lap apart on a ring")
    void headOnIsNotFollowing() {
        Train a = put(1, length * 0.20D, 0.0D);
        Train b = put(2, length * 0.70D, 0.0D);
        assertTrue(TrainSight.headOn(network, a, 1.0D, b, -1.0D), "facing each other");
        assertTrue(TrainSight.headOn(network, b, -1.0D, a, 1.0D), "either way round");
        // Half a lap apart the two trains are exactly as far from each other both ways round, which
        // is what fooled a distance-only test: evenly spaced trains on a loop are the normal case.
        assertFalse(TrainSight.headOn(network, a, 1.0D, b, 1.0D), "following, half a lap apart");
        assertFalse(TrainSight.headOn(network, a, -1.0D, b, -1.0D), "following the other way round");
    }

    @Test
    @DisplayName("the system names the pair running at each other, and the train a service is stopping for")
    void reportsWhy() {
        Train out = put(1, length * 0.20D, 12.0D);
        Train in = put(2, length * 0.60D, -12.0D);
        serve(1, out);
        serve(2, in);
        assertTrue(transit.headOnWith(1, trains, network) == 2, "head-on found at once");
        run(20 * 60, out, in);
        assertTrue(transit.stoppingFor(1) == 2 && transit.stoppingFor(2) == 1,
            "each is stopping for the other: " + transit.stoppingFor(1) + ", " + transit.stoppingFor(2));
    }

    @Test
    @DisplayName("a train started on a line is not pointed head-on at the service already running it")
    void startsWithTheFlow() {
        Train running = put(1, length * 0.50D, 12.0D);
        serve(1, running);
        // At rest just past Union (s = 0.10 L): the nearest station is Union, behind it, and "nearest
        // station either way" would point it back there — straight at train 1, coming round.
        Train fresh = put(2, length * 0.12D, 0.0D);
        transit.beginTick(trains, network);
        transit.enterService(2, fresh, network, "Subway", TransitDrives.metro(15.0D, TICK));
        assertTrue(transit.headOnWith(2, trains, network) < 0, "it runs the way the line already runs");
    }

    @Test
    @DisplayName("a train taken out of service stops where it is, rather than coasting on")
    void withdrawnTrainStops() {
        Train train = put(1, length * 0.15D, 12.0D);
        serve(1, train);
        transit.beginTick(trains, network);
        trains.tick(network, transit.composedWith(null), 4, TICK);
        double from = train.reference().distance();
        transit.exitService(1);
        for (int t = 0; t < 20 * 30; t++) {
            transit.beginTick(trains, network);
            trains.tick(network, transit.composedWith(null), 4, TICK);
        }
        double travelled = Math.floorMod(Math.round((train.reference().distance() - from) * 1000),
            Math.round(length * 1000)) / 1000.0D;
        assertTrue(Math.abs(train.velocity()) < 0.1D, "standing, at " + train.velocity());
        // From 12 blocks/s on a 1.2 blocks/s² service brake: 60 blocks, give or take.
        assertTrue(travelled < 80.0D, "stopped within a braking distance, not " + travelled + " blocks on");
    }

    @Test
    @DisplayName("a train drifting the wrong way is turned round to run with the line, not head-on")
    void driftingTrainIsTurned() {
        Train running = put(1, length * 0.50D, 12.0D);
        serve(1, running);
        // Found in game: withdrawn without brakes, it was still creeping backwards when started
        // again, and the creep alone decided which way it would run.
        Train drifting = put(2, length * 0.12D, -0.3D);
        transit.beginTick(trains, network);
        transit.enterService(2, drifting, network, "Subway", TransitDrives.metro(15.0D, TICK));
        assertTrue(transit.headOnWith(2, trains, network) < 0, "turned to run the way the line runs");
        double fastest = 0.0D;
        double backwards = 0.0D;
        for (int t = 0; t < 20 * 60; t++) {
            transit.beginTick(trains, network);
            trains.tick(network, transit.composedWith(null), 4, TICK);
            fastest = Math.max(fastest, drifting.velocity());
            backwards = Math.min(backwards, drifting.velocity());
        }
        assertTrue(fastest > 5.0D && backwards > -0.5D,
            "and it runs that way: from " + backwards + " to " + fastest + " blocks/s");
    }
}
