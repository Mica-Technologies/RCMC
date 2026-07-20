package com.micatechnologies.minecraft.rcmc.physics.element;

import static com.micatechnologies.minecraft.rcmc.physics.element.ElementTestSupport.TICK;
import static com.micatechnologies.minecraft.rcmc.physics.element.ElementTestSupport.flatNetwork;
import static com.micatechnologies.minecraft.rcmc.physics.element.ElementTestSupport.realistic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LaunchTrackTest {

    @Test
    @DisplayName("a launch reaches its target speed and then stops pushing")
    void reachesTargetSpeedThenStopsPushing() {
        TrackNetwork flat = flatNetwork(400.0D);
        LaunchTrack launch = new LaunchTrack(1, 0.0D, 150.0D, 30.0D, LaunchTrack.constant(15.0D));
        Train train = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), 0.0D);

        boolean reachedTarget = false;
        for (int tick = 0; tick < 400 && train.isRunning(); tick++) {
            train.tick(flat, launch.accelerationFor(train), 4, TICK);
            if (train.velocity() >= 30.0D) {
                reachedTarget = true;
                break;
            }
        }
        assertTrue(reachedTarget, "train never reached target launch speed");
        assertEquals(0.0D, launch.accelerationFor(train), 1e-9D,
            "the launch must stop pushing once the target speed is reached");
    }

    @Test
    @DisplayName("a launch does not push a train that has already reached its target speed by other means")
    void doesNotPushBeyondTarget() {
        LaunchTrack launch = new LaunchTrack(1, 0.0D, 150.0D, 20.0D, LaunchTrack.constant(15.0D));
        Train atTarget = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 10.0D), 20.0D);
        Train pastTarget = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 10.0D), 25.0D);
        assertEquals(0.0D, launch.accelerationFor(atTarget), 1e-9D);
        assertEquals(0.0D, launch.accelerationFor(pastTarget), 1e-9D);
    }

    @Test
    @DisplayName("a constant profile applies the same push everywhere along the element")
    void constantProfileIsUniform() {
        LaunchTrack.AccelerationProfile profile = LaunchTrack.constant(7.5D);
        assertEquals(7.5D, profile.accelerationAt(0.0D), 1e-9D);
        assertEquals(7.5D, profile.accelerationAt(0.5D), 1e-9D);
        assertEquals(7.5D, profile.accelerationAt(1.0D), 1e-9D);
    }

    @Test
    @DisplayName("a ramped profile interpolates linearly from start to end")
    void rampedProfileInterpolatesLinearly() {
        LaunchTrack.AccelerationProfile profile = LaunchTrack.ramped(2.0D, 10.0D);
        assertEquals(2.0D, profile.accelerationAt(0.0D), 1e-9D);
        assertEquals(10.0D, profile.accelerationAt(1.0D), 1e-9D);
        assertEquals(6.0D, profile.accelerationAt(0.5D), 1e-9D);
    }

    @Test
    @DisplayName("a train outside the launch's span is unaffected")
    void trainOutsideSpanIsUnaffected() {
        LaunchTrack launch = new LaunchTrack(1, 50.0D, 100.0D, 20.0D, LaunchTrack.constant(10.0D));
        assertFalse(launch.contains(new TrackRef(1, 10.0D)));
        assertTrue(launch.contains(new TrackRef(1, 75.0D)));
    }

    @Test
    @DisplayName("launch acceleration is a deterministic function of train state")
    void isDeterministic() {
        LaunchTrack launch = new LaunchTrack(1, 0.0D, 150.0D, 30.0D, LaunchTrack.constant(15.0D));
        Train a = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 20.0D), 5.0D);
        Train b = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 20.0D), 5.0D);
        assertEquals(launch.accelerationFor(a), launch.accelerationFor(b), 0.0D);
    }
}
