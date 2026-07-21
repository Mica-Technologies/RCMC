package com.micatechnologies.minecraft.rcmc.physics.transit;

import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.LINE_SPEED;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.flatNetwork;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.metroDriver;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.realistic;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.tickWithController;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransitStopControllerTest {

    private static final int DOOR_OPEN_TICKS = 30;
    private static final int DWELL_TICKS = 100;
    private static final int DOOR_CLOSE_TICKS = 30;
    private static final double BERTH_TOLERANCE = 0.5D;

    private static TransitStopController controller() {
        return new TransitStopController(metroDriver(), LINE_SPEED, BERTH_TOLERANCE,
            DOOR_OPEN_TICKS, DWELL_TICKS, DOOR_CLOSE_TICKS);
    }

    @Test
    @DisplayName("a train runs to the station, berths at the stop point, and cycles its doors")
    void runsToTheStationAndBerths() {
        TrackNetwork flat = flatNetwork(1000.0D);
        TransitStopController controller = controller();
        Train train = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), 0.0D);
        double stop = 400.0D;

        int guard = 0;
        while (controller.phase() == TransitStopController.Phase.APPROACHING && guard++ < 8000) {
            tickWithController(train, flat, controller, stop);
        }
        assertTrue(guard < 8000, "expected the train to reach the station and berth");
        assertEquals(stop, train.reference().distance(), 1.0D,
            "expected the train berthed within a block of the stop point");
        assertEquals(0.0D, train.velocity(), 0.06D, "expected the train at rest when the doors start opening");
        assertTrue(train.isRunning(), "a berthed train is held, never valleyed");
    }

    @Test
    @DisplayName("the door cycle runs open, boarding, close for exactly the configured tick counts")
    void doorCycleTimingIsExact() {
        TrackNetwork flat = flatNetwork(1000.0D);
        TransitStopController controller = controller();
        // Start already at rest on the stop point, isolating the door cycle from the approach.
        Train train = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 400.0D), 0.0D);

        int opening = 0;
        int boarding = 0;
        int closing = 0;
        int guard = 0;
        while (controller.stopsServed() == 0 && guard++ < 1000) {
            tickWithController(train, flat, controller, 400.0D);
            switch (controller.phase()) {
                case DOORS_OPENING:
                    opening++;
                    break;
                case BOARDING:
                    boarding++;
                    break;
                case DOORS_CLOSING:
                    closing++;
                    break;
                default:
                    break;
            }
        }
        assertEquals(DOOR_OPEN_TICKS, opening, "door opening duration");
        assertEquals(DWELL_TICKS, boarding, "boarding duration");
        assertEquals(DOOR_CLOSE_TICKS, closing, "door closing duration");
        assertEquals(1, controller.stopsServed());
    }

    @Test
    @DisplayName("doors are only ever open while the train is at rest")
    void doorsNeverOpenWhileMoving() {
        TrackNetwork flat = flatNetwork(1000.0D);
        TransitStopController controller = controller();
        Train train = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), 0.0D);

        int guard = 0;
        while (controller.stopsServed() == 0 && guard++ < 8000) {
            tickWithController(train, flat, controller, 400.0D);
            if (controller.doorsOpen()) {
                assertTrue(train.speed() <= 0.06D,
                    "doors open at " + train.speed() + " blocks/s");
            }
        }
        assertEquals(1, controller.stopsServed(), "expected a complete stop cycle");
    }

    @Test
    @DisplayName("after the doors close, the train departs for the next station and berths there too")
    void departsForTheNextStation() {
        TrackNetwork flat = flatNetwork(1000.0D);
        TransitStopController controller = controller();
        Train train = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), 0.0D);

        double[] stops = {300.0D, 700.0D};
        double topSpeedBetweenStops = 0.0D;
        int guard = 0;
        while (controller.stopsServed() < 2 && guard++ < 16000) {
            // The route-following contract: once a stop is served, supply the next one.
            double currentStop = stops[Math.min(controller.stopsServed(), stops.length - 1)];
            tickWithController(train, flat, controller, currentStop);
            if (controller.stopsServed() == 1) {
                topSpeedBetweenStops = Math.max(topSpeedBetweenStops, train.speed());
            }
        }
        assertEquals(2, controller.stopsServed(), "expected both stations served");
        assertEquals(700.0D, train.reference().distance(), 1.0D,
            "expected the train berthed at the second station");
        assertTrue(topSpeedBetweenStops > LINE_SPEED - 1.0D,
            "expected the train to have regained line speed between stations, peaked at " + topSpeedBetweenStops);
    }

    @Test
    @DisplayName("a train stopped short of the platform creeps the rest of the way in under power")
    void creepsInWhenStoppedShort() {
        TrackNetwork flat = flatNetwork(1000.0D);
        TransitStopController controller = controller();
        // At rest 10 blocks short of the stop point — the situation StationPlatform documents as
        // stuck for an unpowered coaster. A metro has motors; it must simply finish the job.
        Train train = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 390.0D), 0.0D);

        int guard = 0;
        while (controller.phase() == TransitStopController.Phase.APPROACHING && guard++ < 4000) {
            tickWithController(train, flat, controller, 400.0D);
        }
        assertTrue(guard < 4000, "expected the train to creep in and berth rather than sit stranded");
        assertEquals(400.0D, train.reference().distance(), 1.0D,
            "expected the creep to end berthed at the stop point");
        assertTrue(train.isRunning(), "the creep must never be misread as a valleyed train");
    }

    @Test
    @DisplayName("holding intent is reported while berthed and while creeping, not while running")
    void holdingIntentTracksThePhase() {
        TransitStopController controller = controller();
        assertTrue(controller.isHolding(0.0D), "at rest on approach is a held creep, not a stall");
        assertTrue(controller.isHolding(0.04D), "a slow creep is under active control");
        assertFalse(controller.isHolding(5.0D), "a train at speed is simply running");
        assertFalse(controller.isHolding(-5.0D), "same in reverse");
    }
}
