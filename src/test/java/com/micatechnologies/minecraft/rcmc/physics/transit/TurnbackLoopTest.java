package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A metro does not stop inbound service while an outbound train runs. Inbound and outbound are
 * separate tracks joined by a turning loop at each terminus, so a train never reverses — it keeps
 * driving forward, round the loop, and comes back on the other track.
 *
 * <p>That is what {@code TransitLine.turnsBackOnLoop} models, and the whole of it comes down to one
 * thing: at a terminus the <em>service direction</em> flips while the train's physical facing does
 * not. Get that wrong and the train reverses in the platform, runs back down the track it arrived
 * on, and every station's second berth becomes unreachable.</p>
 *
 * <p>The fixture is the demo's shape reduced to its essentials: one closed circuit whose first half
 * is the outbound track and whose second half is the inbound track, with a station's two berths on
 * opposite halves.</p>
 */
class TurnbackLoopTest {

    private static final double TICK = 1.0D / 20.0D;

    /** A closed ring; the first half of its length is "outbound", the second half "inbound". */
    private static TrackNetwork ring() {
        List<TrackNode> nodes = new ArrayList<>();
        double side = 160.0D;
        for (double[] p : new double[][] {{0, 0}, {side, 0}, {side, side}, {0, side}}) {
            nodes.add(new TrackNode(new Vec3(p[0], 64.0D, p[1])));
        }
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, nodes, true, null));
        return network;
    }

    /**
     * Three stations in service order. The middle one has a berth on each half of the ring; the two
     * termini have a single berth each, on the track they are approached along — which is what a
     * turning loop terminus actually has.
     */
    private static TransitSystem lineOn(TrackNetwork network, boolean turnbackLoop) {
        double length = network.section(1).totalLength();
        TransitSystem transit = new TransitSystem();

        TransitStation start = new TransitStation("Kingsway", Arrays.asList(
            new TransitPlatform(new TrackRef(1, length * 0.95D), DoorSide.RIGHT, "1")));
        TransitStation middle = new TransitStation("Guildhall", Arrays.asList(
            new TransitPlatform(new TrackRef(1, length * 0.25D), DoorSide.RIGHT, "1"),
            new TransitPlatform(new TrackRef(1, length * 0.75D), DoorSide.LEFT, "2")));
        TransitStation end = new TransitStation("Lakeshore", Arrays.asList(
            new TransitPlatform(new TrackRef(1, length * 0.45D), DoorSide.RIGHT, "1")));

        transit.addStation(start);
        transit.addStation(middle);
        transit.addStation(end);
        transit.addLine(new TransitLine("Circle Line", Arrays.asList(start, middle, end),
            false, turnbackLoop, "INBOUND", "OUTBOUND"));
        return transit;
    }

    private static Train runningTrain(double distance) {
        return new Train(TrainSpec.metroTrain(3),
            new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, distance), 15.0D);
    }

    /**
     * The heart of it. Drive a full service cycle and collect every berth actually served; a train
     * that reversed at the terminus would serve the outbound berths again on the way back and never
     * touch the inbound ones.
     */
    @Test
    @DisplayName("a turnback line serves the other track's berth after its terminus")
    void servesBothTracks() {
        TrackNetwork network = ring();
        TransitSystem transit = lineOn(network, true);
        double length = network.section(1).totalLength();

        Train train = runningTrain(length * 0.10D);
        LineService service = transit.enterService(1, train, network, "Circle Line",
            TransitDrives.metro(15.0D, TICK));

        Set<String> servedBerths = new HashSet<>();
        double minimumVelocity = Double.MAX_VALUE;
        for (int t = 0; t < 20000; t++) {
            double acceleration = service.tick(train, network, TrainDriver.NO_STOP);
            train.setHeld(service.isHolding(train));
            train.tick(network, acceleration, 4, TICK);
            minimumVelocity = Math.min(minimumVelocity, train.velocity());
            if (service.controller().phase() == TransitStopController.Phase.BOARDING
                && service.currentBerth() != null) {
                servedBerths.add(service.line().station(service.currentStopIndex()).name()
                    + "/" + service.currentBerth().label());
            }
        }

        assertTrue(servedBerths.contains("Guildhall/1"),
            "the outbound berth, served on the way out — got " + servedBerths);
        assertTrue(servedBerths.contains("Guildhall/2"),
            "the INBOUND berth, on the other track, reachable only by driving round the turnback "
                + "rather than reversing into it — got " + servedBerths);
        assertTrue(minimumVelocity >= -0.001D,
            "the train must never run backwards on a turnback line; lowest velocity seen was "
                + minimumVelocity);
    }

    /**
     * The contrast, and the reason this is a line property rather than a global change: a stub
     * terminus is a dead end, and there the train genuinely does change ends.
     */
    @Test
    @DisplayName("a stub terminus still reverses the train in the platform")
    void stubTerminusStillReverses() {
        TrackNetwork network = ring();
        TransitSystem transit = lineOn(network, false);
        double length = network.section(1).totalLength();

        Train train = runningTrain(length * 0.10D);
        LineService service = transit.enterService(1, train, network, "Circle Line",
            TransitDrives.metro(15.0D, TICK));

        double minimumVelocity = Double.MAX_VALUE;
        for (int t = 0; t < 20000; t++) {
            double acceleration = service.tick(train, network, TrainDriver.NO_STOP);
            train.setHeld(service.isHolding(train));
            train.tick(network, acceleration, 4, TICK);
            minimumVelocity = Math.min(minimumVelocity, train.velocity());
        }
        assertTrue(minimumVelocity < -1.0D,
            "a stub terminus reverses the train, so velocity must go negative; lowest seen was "
                + minimumVelocity);
    }

    @Test
    @DisplayName("a loop line is never a turnback line — it has no terminus to turn at")
    void loopLinesAreNeverTurnbacks() {
        TransitStation a = new TransitStation("A", new TrackRef(1, 0.0D));
        TransitStation b = new TransitStation("B", new TrackRef(1, 50.0D));
        assertEquals(false,
            new TransitLine("Loop", Arrays.asList(a, b), true, true, "IN", "OUT")
                .turnsBackOnLoop(),
            "asking for a turnback on a loop is a contradiction, and is normalised away rather "
                + "than stored as a second, disagreeing source of truth");
    }
}
