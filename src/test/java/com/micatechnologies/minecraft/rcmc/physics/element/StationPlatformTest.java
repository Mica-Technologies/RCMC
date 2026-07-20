package com.micatechnologies.minecraft.rcmc.physics.element;

import static com.micatechnologies.minecraft.rcmc.physics.element.ElementTestSupport.TICK;
import static com.micatechnologies.minecraft.rcmc.physics.element.ElementTestSupport.flatNetwork;
import static com.micatechnologies.minecraft.rcmc.physics.element.ElementTestSupport.frictionless;
import static com.micatechnologies.minecraft.rcmc.physics.element.ElementTestSupport.tickUnder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StationPlatformTest {

    private static final int DWELL_TICKS = 40;

    private static StationPlatform station() {
        return new StationPlatform(1, 0.0D, 200.0D, 100.0D, 5.0D, DWELL_TICKS, 8.0D, 5.0D, TICK);
    }

    @Test
    @DisplayName("a station brings an arriving train to rest at the stop point")
    void bringsAnArrivingTrainToRestAtTheStopPoint() {
        TrackNetwork flat = flatNetwork(200.0D);
        StationPlatform platform = station();
        Train train = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 0.0D), 20.0D);

        int guard = 0;
        while (platform.phase() == StationPlatform.Phase.ARRIVING && guard++ < 2000) {
            tickUnder(train, flat, platform);
        }
        assertEquals(StationPlatform.Phase.DWELLING, platform.phase(),
            "expected the train to have come to rest and begun dwelling");
        assertEquals(100.0D, train.reference().distance(), 1.0D, "expected the train to stop near the stop point");
        assertEquals(0.0D, train.velocity(), 0.1D, "expected the train to be at rest when dwell begins");
        assertTrue(platform.isHolding(), "the platform should report holding while dwelling");
    }

    @Test
    @DisplayName("a station holds a stopped train for exactly dwellTicks calls, then dispatches it")
    void holdsForExactlyDwellTicksThenDispatches() {
        TrackNetwork flat = flatNetwork(200.0D);
        StationPlatform platform = station();
        // Start already at rest at the stop point, isolating the dwell/dispatch behaviour from
        // the arrival braking tested separately above.
        Train train = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 100.0D), 0.0D);

        // Holding a stopped train on level track applies exactly zero force, which is physically
        // correct. That it is indistinguishable from a stalled train is handled by isHolding(),
        // not by applying a token push.
        double firstAccel = platform.accelerationFor(train);
        assertEquals(0.0D, firstAccel, 1e-6D);
        assertEquals(StationPlatform.Phase.DWELLING, platform.phase());

        int dwellCalls = 0;
        while (platform.phase() == StationPlatform.Phase.DWELLING) {
            double a = tickUnder(train, flat, platform);
            dwellCalls++;
            if (platform.phase() == StationPlatform.Phase.DWELLING) {
                assertEquals(0.0D, a, 1e-6D, "should not meaningfully accelerate while still dwelling");
            }
        }
        // dwellTicks calls holding at zero, plus exactly one more call that transitions to
        // DISPATCHING and returns the dispatch push in the same call.
        assertEquals(DWELL_TICKS + 1, dwellCalls, "expected exactly dwellTicks holding calls before dispatch");
        assertEquals(StationPlatform.Phase.DISPATCHING, platform.phase());
        assertFalse(platform.isHolding(), "the platform should no longer report holding once dispatching");
    }

    @Test
    @DisplayName("a station dispatches a train up to dispatch speed, then goes idle")
    void dispatchesUpToDispatchSpeedThenGoesIdle() {
        TrackNetwork flat = flatNetwork(200.0D);
        StationPlatform platform = station();
        Train train = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 100.0D), 0.0D);

        int guard = 0;
        while (platform.phase() != StationPlatform.Phase.DEPARTED && guard++ < 500) {
            tickUnder(train, flat, platform);
        }
        assertEquals(StationPlatform.Phase.DEPARTED, platform.phase(),
            "station never reached DEPARTED within the guard limit");
        assertTrue(train.velocity() >= 5.0D - 1e-6D, "expected the train to have reached dispatch speed");
        assertEquals(0.0D, platform.accelerationFor(train), 1e-9D, "should push no further once departed");
    }

    @Test
    @DisplayName("reset() re-arms the platform for another arrival")
    void resetReArmsForAnotherArrival() {
        TrackNetwork flat = flatNetwork(200.0D);
        StationPlatform platform = station();
        Train train = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 100.0D), 0.0D);

        int guard = 0;
        while (platform.phase() != StationPlatform.Phase.DEPARTED && guard++ < 500) {
            tickUnder(train, flat, platform);
        }
        assertEquals(StationPlatform.Phase.DEPARTED, platform.phase());

        platform.reset();
        assertEquals(StationPlatform.Phase.ARRIVING, platform.phase());
        assertTrue(platform.isHolding(), "a freshly reset platform should be holding again");
    }

    @Test
    @DisplayName("a train outside the platform's span is unaffected")
    void trainOutsideSpanIsUnaffected() {
        StationPlatform platform = new StationPlatform(1, 50.0D, 100.0D, 75.0D, 5.0D, 10, 8.0D, 5.0D, TICK);
        assertFalse(platform.contains(new TrackRef(1, 10.0D)));
        assertTrue(platform.contains(new TrackRef(1, 80.0D)));
    }

    @Test
    @DisplayName("the station state machine is a deterministic function of tick history")
    void isDeterministic() {
        TrackNetwork flat = flatNetwork(200.0D);
        StationPlatform platformA = station();
        StationPlatform platformB = station();
        Train trainA = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 0.0D), 20.0D);
        Train trainB = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 0.0D), 20.0D);

        for (int tick = 0; tick < 300; tick++) {
            double aA = platformA.accelerationFor(trainA);
            double aB = platformB.accelerationFor(trainB);
            assertEquals(aA, aB, 0.0D, "diverged at tick " + tick);
            trainA.tick(flat, aA, 4, TICK);
            trainB.tick(flat, aB, 4, TICK);
        }
        assertEquals(platformA.phase(), platformB.phase());
    }
}
