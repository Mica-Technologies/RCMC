package com.micatechnologies.minecraft.rcmc.physics.transit;

import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.TICK;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.flatNetwork;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.metroDriver;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.realistic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransitSystemTest {

    private static final int TRAIN = 7;

    private static TransitSystem systemWithShuttleLine() {
        TransitSystem transit = new TransitSystem();
        transit.addStation(new TransitStation("Alpha", new TrackRef(1, 200.0D)));
        transit.addStation(new TransitStation("Beta", new TrackRef(1, 700.0D)));
        transit.addLine(new TransitLine("Test Line",
            Arrays.asList(transit.station("Alpha"), transit.station("Beta")), false));
        return transit;
    }

    private static TransitStopController quickController() {
        return new TransitStopController(metroDriver(), 15.0D, 0.75D, 5, 10, 5);
    }

    /** One world tick, exactly as RcmcWorldState composes it. */
    private static void tickWorld(TransitSystem transit, TrainManager trains, TrackNetwork network,
                                  TrainManager.ExternalAcceleration fallback) {
        transit.beginTick(trains, network);
        trains.tick(network, transit.composedWith(fallback), 4, TICK);
    }

    @Test
    @DisplayName("entering service recovers a valleyed train and runs it to the nearest station")
    void entersServiceAndServesNearestStation() {
        TrackNetwork network = flatNetwork(1000.0D);
        TrainManager trains = new TrainManager();
        Train train = new Train(TrainSpec.metroTrain(2), realistic(), new TrackRef(1, 100.0D), 0.0D);
        trains.add(TRAIN, train);
        TransitSystem transit = systemWithShuttleLine();

        // A parked train with nothing claiming it valleys — the normal pre-service state.
        for (int i = 0; i < 5; i++) {
            tickWorld(transit, trains, network, null);
        }
        assertFalse(train.isRunning(), "precondition: an unclaimed parked train valleys");

        LineService service = transit.enterService(TRAIN, train, network, "Test Line", quickController());
        assertTrue(train.isRunning(), "taking a train into service must clear the stall");
        assertEquals(0, service.currentStopIndex(), "Alpha is the nearest station from 100");
        assertEquals(1.0D, service.facing(), 1e-9D);

        int guard = 0;
        while (service.controller().stopsServed() == 0 && guard++ < 8000) {
            tickWorld(transit, trains, network, null);
        }
        assertTrue(guard < 8000, "expected the first station served");
        assertEquals(200.0D, train.reference().distance(), 1.0D, "expected a berth at Alpha");
    }

    @Test
    @DisplayName("the nearest station may be behind the train — service starts facing backwards")
    void nearestStationMayBeBehind() {
        TrackNetwork network = flatNetwork(1000.0D);
        TransitSystem transit = systemWithShuttleLine();
        Train train = new Train(TrainSpec.metroTrain(1), realistic(), new TrackRef(1, 250.0D), 0.0D);

        LineService service = transit.enterService(TRAIN, train, network, "Test Line", quickController());
        assertEquals(0, service.currentStopIndex(), "Alpha at 200 is 50 behind; Beta is 450 ahead");
        assertEquals(-1.0D, service.facing(), 1e-9D, "expected the service to start facing backwards");
    }

    @Test
    @DisplayName("trains not in service fall through to the wrapped control")
    void nonServiceTrainsFallThrough() {
        TrackNetwork network = flatNetwork(1000.0D);
        TrainManager trains = new TrainManager();
        Train coaster = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 50.0D), 5.0D);
        trains.add(99, coaster);
        TransitSystem transit = systemWithShuttleLine();

        double[] asked = {0.0D};
        TrainManager.ExternalAcceleration fallback = (trainId, train) -> {
            asked[0]++;
            return 0.5D;
        };
        tickWorld(transit, trains, network, fallback);
        assertEquals(1.0D, asked[0], 0.0D, "the wrapped control must be consulted for non-service trains");
        assertTrue(coaster.velocity() > 5.0D, "the fallback's push must actually reach the train");
    }

    @Test
    @DisplayName("boarding is gated by the doors for trains in service, and open for everything else")
    void boardingGatedByDoors() {
        TrackNetwork network = flatNetwork(1000.0D);
        TrainManager trains = new TrainManager();
        // At rest exactly on Alpha, so the service berths and cycles its doors immediately.
        Train train = new Train(TrainSpec.metroTrain(1), realistic(), new TrackRef(1, 200.0D), 0.0D);
        trains.add(TRAIN, train);
        TransitSystem transit = systemWithShuttleLine();

        assertTrue(transit.mayBoard(TRAIN), "a train not in service boards freely");

        LineService service = transit.enterService(TRAIN, train, network, "Test Line", quickController());
        assertFalse(transit.mayBoard(TRAIN), "doors are shut until berthed and opened");

        int guard = 0;
        while (service.controller().phase() != TransitStopController.Phase.BOARDING && guard++ < 200) {
            tickWorld(transit, trains, network, null);
        }
        assertTrue(guard < 200, "expected the berthed train to reach boarding");
        assertTrue(transit.mayBoard(TRAIN), "doors open means boarding allowed");
    }

    @Test
    @DisplayName("removing a line withdraws its trains from service")
    void removingALineWithdrawsItsServices() {
        TrackNetwork network = flatNetwork(1000.0D);
        TransitSystem transit = systemWithShuttleLine();
        Train train = new Train(TrainSpec.metroTrain(1), realistic(), new TrackRef(1, 100.0D), 0.0D);
        transit.enterService(TRAIN, train, network, "Test Line", quickController());
        assertNotNull(transit.serviceFor(TRAIN));

        transit.removeLine("test line");
        assertNull(transit.serviceFor(TRAIN), "a removed line must not leave orphan services");
    }
}
