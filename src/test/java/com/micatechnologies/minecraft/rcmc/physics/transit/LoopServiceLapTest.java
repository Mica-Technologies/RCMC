package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A loop service must berth at every station on the line, once per lap, in the order it reaches
 * them.
 *
 * <p>{@link LoopServiceDirectionTest} pins the direction {@code enterService} <em>chooses</em>, but
 * it never runs the service: it asserts three getters and stops. This drives a full lap through
 * {@link LineService#tick} and the real {@link TransitStopController} and watches where the train
 * actually opens its doors, which is the thing a rider on the platform is complaining about.</p>
 *
 * <p>Reported again from the underground demo after the direction fix landed: a train serves one
 * station and then runs past the next several at line speed.</p>
 */
class LoopServiceLapTest {

    private static final double TICK = 1.0D / 20.0D;
    private static final int SUB_STEPS = 4;
    private static final String[] NAMES =
        {"Union", "Central", "Harbor", "Seaside", "Midway", "Parkside"};

    /** A closed ring, laid out as a square — the demo's underground loop in miniature. */
    private static TrackNetwork ring() {
        List<TrackNode> nodes = new ArrayList<>();
        double side = 195.0D;
        for (double[] p : new double[][] {{0, 0}, {side, 0}, {side, side}, {0, side}}) {
            nodes.add(new TrackNode(new Vec3(p[0], 64.0D, p[1])));
        }
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, nodes, true, null));
        return network;
    }

    /** Six stations spread evenly round the ring, listed in ascending distance order. */
    private static TransitSystem lineOn(TrackNetwork network) {
        double length = network.section(1).totalLength();
        TransitSystem transit = new TransitSystem();
        List<TransitStation> stops = new ArrayList<>();
        for (int i = 0; i < NAMES.length; i++) {
            TransitStation station =
                new TransitStation(NAMES[i], new TrackRef(1, length * (i + 0.5D) / NAMES.length));
            transit.addStation(station);
            stops.add(station);
        }
        transit.addLine(new TransitLine("Subway", stops, true));
        return transit;
    }

    private static Train trainAt(double distance) {
        return trainAt(distance, 0.0D);
    }

    private static Train trainAt(double distance, double velocity) {
        return new Train(TrainSpec.metroTrain(3),
            new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, distance), velocity);
    }

    /**
     * Runs the service and returns the stations it berthed at, in order. A berth is recorded on
     * the transition into {@code DOORS_OPENING}, which is the tick the controller commits to the
     * stop it has arrived at.
     */
    private static List<String> lap(TransitSystem transit, TrackNetwork network, Train train,
                                    LineService service, int ticks) {
        TransitStopController controller = service.controller();
        List<String> served = new ArrayList<>();
        TransitStopController.Phase previous = controller.phase();
        for (int t = 0; t < ticks && served.size() < NAMES.length; t++) {
            String target = service.line().station(service.currentStopIndex()).name();
            double acceleration = service.tick(train, network, TrainDriver.NO_STOP);
            TransitStopController.Phase phase = controller.phase();
            if (phase == TransitStopController.Phase.DOORS_OPENING
                && previous != TransitStopController.Phase.DOORS_OPENING) {
                served.add(target);
            }
            previous = phase;
            train.setHeld(service.isHolding(train));
            train.tick(network, acceleration, SUB_STEPS, TICK);
        }
        return served;
    }

    /**
     * A generous ceiling on one lap, not an estimate of it. Cruising the ring accounts for well
     * under half the time: each stop also costs a full brake from line speed, a dwell, and a full
     * acceleration back up to it, and at 1.2 blocks/s² those bracket a 15 blocks/s stop with about
     * 25 seconds of ramp on their own. {@link #lap} returns as soon as every station has been
     * berthed at, so overshooting here costs nothing but the headroom to catch a train that is
     * genuinely lapping past a station instead of stopping at it.
     */
    private static int ticksForOneLap(TrackNetwork network) {
        return (int) (network.section(1).totalLength() / 15.0D * 20.0D)
            + NAMES.length * (TransitDrives.METRO_DWELL_TICKS + 700) + 400;
    }

    @Test
    @DisplayName("a loop service running with the station order berths at all six, in order")
    void servesEveryStationRunningForward() {
        TrackNetwork network = ring();
        TransitSystem transit = lineOn(network);
        double length = network.section(1).totalLength();

        // Just short of Union, so the nearest stop is ahead: facing +1, direction +1.
        Train train = trainAt(length * 0.5D / NAMES.length - 8.0D);
        LineService service =
            transit.enterService(1, train, network, "Subway", TransitDrives.metro(15.0D, TICK));

        assertEquals(1, service.serviceDirection(), "pointed with the station order");
        assertEquals(
            Arrays.asList("Union", "Central", "Harbor", "Seaside", "Midway", "Parkside"),
            lap(transit, network, train, service, ticksForOneLap(network)),
            "every station on the loop, once, in the order the train reaches them");
    }

    @Test
    @DisplayName("a loop service running against the station order berths at all six, in order")
    void servesEveryStationRunningBackward() {
        TrackNetwork network = ring();
        TransitSystem transit = lineOn(network);
        double length = network.section(1).totalLength();

        // Just past Union, so the nearest stop is behind: enterService faces the train backwards
        // to reach it and counts down the line. This is what a train reloading at a platform can
        // land in, and it is the case the live demo is running in.
        Train train = trainAt(length * 0.5D / NAMES.length + 3.0D);
        LineService service =
            transit.enterService(1, train, network, "Subway", TransitDrives.metro(15.0D, TICK));

        assertEquals(-1, service.serviceDirection(), "pointed against the station order");
        assertEquals(
            Arrays.asList("Union", "Parkside", "Midway", "Seaside", "Harbor", "Central"),
            lap(transit, network, train, service, ticksForOneLap(network)),
            "every station on the loop, once, in the order the train reaches them");
    }

    /**
     * A train entering service <em>already running</em> cannot be turned round, and the schedule
     * has to agree with the direction it is really going.
     *
     * <p>This is the case the live underground demo was stuck in and the one the earlier direction
     * fix left open. {@code enterService} chose the first stop and the service direction from
     * whichever way reached the nearest station, ignoring the train's velocity entirely — then
     * {@link LineService#tick} resynced facing from the velocity sign on its very first tick and
     * threw that choice away. The service direction, derived from the discarded facing, stayed
     * behind: counting indices up while the train drove down. On a ring that serves exactly one
     * station per lap and drives past the rest, forever.</p>
     *
     * <p>Trains are spawned at rest, so this only bites a service entered on a moving train —
     * which is precisely what resuming from a save does to a metro that was saved in motion.</p>
     */
    @Test
    @DisplayName("a service entered on a moving train schedules the stops it is actually heading for")
    void servesEveryStationWhenEnteredAtSpeed() {
        TrackNetwork network = ring();
        TransitSystem transit = lineOn(network);
        double length = network.section(1).totalLength();

        // Five blocks short of Union, so the nearest station either way is Union just ahead in +s
        // — but the train is running the other way at line speed and cannot be reversed. The
        // stations it is actually about to reach are Parkside, Midway, Seaside, ... in descending
        // order, and that is the schedule the service must adopt.
        Train train = trainAt(length * 0.5D / NAMES.length - 5.0D, -15.0D);
        LineService service =
            transit.enterService(1, train, network, "Subway", TransitDrives.metro(15.0D, TICK));

        assertEquals(-1.0D, service.facing(), 1e-9D,
            "the facing has to be the one the train already has");
        assertEquals(-1, service.serviceDirection(),
            "running down the ring serves the stations in descending order");
        assertEquals(
            Arrays.asList("Parkside", "Midway", "Seaside", "Harbor", "Central", "Union"),
            lap(transit, network, train, service, ticksForOneLap(network)),
            "every station on the loop, once, in the order the train reaches them");
    }
}
