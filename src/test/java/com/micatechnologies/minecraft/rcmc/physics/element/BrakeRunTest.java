package com.micatechnologies.minecraft.rcmc.physics.element;

import static com.micatechnologies.minecraft.rcmc.physics.element.ElementTestSupport.TICK;
import static com.micatechnologies.minecraft.rcmc.physics.element.ElementTestSupport.flatNetwork;
import static com.micatechnologies.minecraft.rcmc.physics.element.ElementTestSupport.frictionless;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BrakeRunTest {

    @Test
    @DisplayName("a TRIM brake slows a train to its target speed but never stops it")
    void trimSlowsButNeverStops() {
        TrackNetwork flat = flatNetwork(500.0D);
        BrakeRun trim = new BrakeRun(1, 0.0D, 200.0D, 10.0D, 8.0D, BrakeRun.Mode.TRIM, TICK);
        Train train = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 0.0D), 30.0D);

        for (int tick = 0; tick < 200 && train.isRunning(); tick++) {
            train.tick(flat, trim.accelerationFor(train), 4, TICK);
        }
        assertEquals(10.0D, train.velocity(), 0.1D, "expected the train to settle at the trim target");
        assertTrue(train.velocity() > 0.0D, "a trim brake must never bring the train to a stop");

        // Keep ticking on frictionless, un-braked-below-target track: it should hold, not decay
        // to zero, since a=0 once at/below the floor.
        for (int tick = 0; tick < 200 && train.isRunning(); tick++) {
            train.tick(flat, trim.accelerationFor(train), 4, TICK);
        }
        assertTrue(train.velocity() > 5.0D, "trim brake let the train decay well below its floor");
    }

    @Test
    @DisplayName("a BLOCK brake can bring a train to a complete stop")
    void blockCanBringTrainToAStop() {
        TrackNetwork flat = flatNetwork(500.0D);
        BrakeRun block = new BrakeRun(1, 0.0D, 200.0D, 0.0D, 8.0D, BrakeRun.Mode.BLOCK, TICK);
        Train train = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 0.0D), 20.0D);

        for (int tick = 0; tick < 200; tick++) {
            train.tick(flat, block.accelerationFor(train), 4, TICK);
        }
        assertEquals(0.0D, train.velocity(), 1e-6D, "expected the train to come to a complete stop");
    }

    @Test
    @DisplayName("braking never overshoots a stop into reverse, even in the final tick")
    void neverOvershootsIntoReverse() {
        // A plain constant deceleration (not run through the deadbeat servo) could push a slow
        // train past zero and into reverse within a single tick; this is exactly what the servo
        // plus explicit sign guard in BrakeRun.accelerationFor exists to prevent.
        TrackNetwork flat = flatNetwork(500.0D);
        BrakeRun block = new BrakeRun(1, 0.0D, 200.0D, 0.0D, 8.0D, BrakeRun.Mode.BLOCK, TICK);
        Train train = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 0.0D), 0.1D);

        double minVelocitySeen = 0.0D;
        for (int tick = 0; tick < 50; tick++) {
            train.tick(flat, block.accelerationFor(train), 4, TICK);
            minVelocitySeen = Math.min(minVelocitySeen, train.velocity());
        }
        assertTrue(minVelocitySeen >= -1e-6D,
            "brake pushed the train into reverse; min velocity seen was " + minVelocitySeen);
    }

    @Test
    @DisplayName("a TRIM brake cannot be constructed with a zero or negative target speed")
    void trimRejectsNonPositiveTarget() {
        assertThrows(IllegalArgumentException.class,
            () -> new BrakeRun(1, 0.0D, 200.0D, 0.0D, 8.0D, BrakeRun.Mode.TRIM, TICK));
    }

    @Test
    @DisplayName("braking always opposes the train's current direction of travel")
    void opposesCurrentDirectionRegardlessOfSign() {
        BrakeRun block = new BrakeRun(1, 0.0D, 200.0D, 0.0D, 8.0D, BrakeRun.Mode.BLOCK, TICK);
        Train forward = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 50.0D), 10.0D);
        Train backward = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 50.0D), -10.0D);
        assertTrue(block.accelerationFor(forward) < 0.0D, "should decelerate a forward-moving train");
        assertTrue(block.accelerationFor(backward) > 0.0D, "should decelerate a backward-moving train");
    }

    @Test
    @DisplayName("a train outside the brake run's span is unaffected")
    void trainOutsideSpanIsUnaffected() {
        BrakeRun block = new BrakeRun(1, 50.0D, 100.0D, 0.0D, 8.0D, BrakeRun.Mode.BLOCK, TICK);
        assertFalse(block.contains(new TrackRef(1, 10.0D)));
        assertTrue(block.contains(new TrackRef(1, 75.0D)));
    }

    @Test
    @DisplayName("brake acceleration is a deterministic function of train state")
    void isDeterministic() {
        BrakeRun block = new BrakeRun(1, 0.0D, 200.0D, 0.0D, 8.0D, BrakeRun.Mode.BLOCK, TICK);
        Train a = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 20.0D), 12.0D);
        Train b = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 20.0D), 12.0D);
        assertEquals(block.accelerationFor(a), block.accelerationFor(b), 0.0D);
    }
}
