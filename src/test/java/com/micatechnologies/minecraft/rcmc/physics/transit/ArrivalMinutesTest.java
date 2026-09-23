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
 * Arrival boards show minutes, and the minutes come true.
 *
 * <p>Boards used to show only "N stops" — exact, but no help deciding whether to run for a train.
 * Minutes come from leg times the line's trains have actually been timed running, so the test that
 * matters is the last one: ask a board how long, then watch the train arrive.</p>
 */
class ArrivalMinutesTest {

    private static final double TICK = 1.0D / 20.0D;

    private static List<TransitStation> stations(int count) {
        List<TransitStation> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new TransitStation("S" + i, new TrackRef(1, 10.0D + 50.0D * i)));
        }
        return list;
    }

    @Test
    @DisplayName("legs are added up along the service pattern, round the terminus")
    void addsLegsRoundTheTerminus() {
        TransitLine shuttle = new TransitLine("L", stations(3), false);
        LineTimings timings = new LineTimings(3);
        // Arriving outbound (+1) at 1 and 2, then inbound (-1) at 1 and 0.
        timings.record(1, 1, 400);
        timings.record(2, 1, 600);
        timings.record(1, -1, 500);
        timings.record(0, -1, 700);
        // Standing at S0, heading out, arrived 100 ticks ago.
        double[] s = ArrivalEstimator.secondsToStations(shuttle, timings, 1, 0, false, 100, -1, TICK);
        assertEquals(0.0D, s[0], 1e-9, "it is here");
        assertEquals((400 - 100) * TICK, s[1], 1e-9);
        assertEquals((400 + 600 - 100) * TICK, s[2], 1e-9);
    }

    @Test
    @DisplayName("an untimed leg stops the estimate there, and later stations fall back to stops")
    void untimedLegEndsTheEstimate() {
        TransitLine shuttle = new TransitLine("L", stations(3), false);
        LineTimings timings = new LineTimings(3);
        timings.record(1, 1, 400);
        double[] s = ArrivalEstimator.secondsToStations(shuttle, timings, 1, 0, false, 0, -1, TICK);
        assertEquals(400 * TICK, s[1], 1e-9);
        assertTrue(s[2] < 0.0D, "S2's leg was never timed");
    }

    @Test
    @DisplayName("a train running late on its leg is estimated from where it is")
    void lateUsesFallback() {
        TransitLine shuttle = new TransitLine("L", stations(2), false);
        LineTimings timings = new LineTimings(2);
        timings.record(1, 1, 400);
        double[] s = ArrivalEstimator.secondsToStations(shuttle, timings, 1, 1, true, 500, 60, TICK);
        assertEquals(60 * TICK, s[1], 1e-9);
    }

    @Test
    @DisplayName("the label is minutes once timed, and BRD / APPR at the train's own next stop")
    void labels() {
        assertEquals("3 min", TransitSignText.arrivalLabel(2, false, "", "OUT", 150.0D));
        assertEquals("1 min", TransitSignText.arrivalLabel(1, false, "", "OUT", 20.0D));
        assertEquals("2 stops", TransitSignText.arrivalLabel(2, false, "", "OUT", -1.0D));
        assertEquals("APPR", TransitSignText.arrivalLabel(0, false, "", "OUT", 30.0D));
    }

    @Test
    @DisplayName("once a lap has been run, the board's estimate is when the train really arrives")
    void estimateComesTrue() {
        List<TrackNode> nodes = new ArrayList<>();
        for (double[] p : new double[][] {{0, 0}, {220, 0}, {220, 160}, {0, 160}}) {
            nodes.add(new TrackNode(new Vec3(p[0], 64.0D, p[1])));
        }
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, nodes, true, null));
        double length = network.section(1).totalLength();
        TransitSystem transit = new TransitSystem();
        List<TransitStation> stops = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            TransitStation station = new TransitStation("S" + i, new TrackRef(1, length * (i + 0.5D) / 4));
            transit.addStation(station);
            stops.add(station);
        }
        transit.addLine(new TransitLine("Ring", stops, true));
        TrainManager trains = new TrainManager();
        trains.add(1, new Train(TrainSpec.metroTrain(2),
            new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D), new TrackRef(1, 5.0D), 0.0D));
        LineService service = transit.enterService(1, trains.train(1), network, "Ring",
            TransitDrives.metro(12.0D, TICK));

        // Learn every leg: run until the train has arrived somewhere five times.
        TransitStopController.Phase previous = service.controller().phase();
        int arrivals = 0;
        for (int t = 0; t < 20 * 900 && arrivals < 5; t++) {
            transit.beginTick(trains, network);
            trains.tick(network, transit.composedWith(null), 4, TICK);
            TransitStopController.Phase phase = service.controller().phase();
            if (previous == TransitStopController.Phase.APPROACHING
                && phase != TransitStopController.Phase.APPROACHING) {
                arrivals++;
            }
            previous = phase;
        }
        assertEquals(5, arrivals);

        // Ask about the station two stops beyond the next one, then watch.
        int target = Math.floorMod(service.currentStopIndex() + 2 * service.serviceDirection(), 4);
        double promised = transit.serviceSnapshots().get(0).secondsTo(target);
        assertTrue(promised > 0.0D, "an estimate once the legs are timed");
        int ticks = 0;
        previous = service.controller().phase();
        while (ticks < 20 * 600) {
            transit.beginTick(trains, network);
            trains.tick(network, transit.composedWith(null), 4, TICK);
            ticks++;
            TransitStopController.Phase phase = service.controller().phase();
            if (previous == TransitStopController.Phase.APPROACHING
                && phase != TransitStopController.Phase.APPROACHING
                && service.currentStopIndex() == target) {
                break;
            }
            previous = phase;
        }
        double actual = ticks * TICK;
        assertEquals(actual, promised, Math.max(3.0D, actual * 0.05D),
            "promised " + promised + " s, arrived after " + actual + " s");
    }
}
