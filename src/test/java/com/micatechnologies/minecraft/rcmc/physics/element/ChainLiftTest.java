package com.micatechnologies.minecraft.rcmc.physics.element;

import static com.micatechnologies.minecraft.rcmc.physics.element.ElementTestSupport.TICK;
import static com.micatechnologies.minecraft.rcmc.physics.element.ElementTestSupport.crestNetwork;
import static com.micatechnologies.minecraft.rcmc.physics.element.ElementTestSupport.realistic;
import static com.micatechnologies.minecraft.rcmc.physics.element.ElementTestSupport.steepRampNetwork;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChainLiftTest {

    private static ChainLift lift(double chainSpeed, double maxAcceleration) {
        return new ChainLift(1, 0.0D, 1000.0D, chainSpeed, maxAcceleration, TICK);
    }

    @Test
    @DisplayName("a chain lift carries a train up a grade it could not climb unpowered")
    void carriesATrainUpAGradeItCouldNotClimbAlone() {
        TrackNetwork ramp = steepRampNetwork();

        Train unpowered = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), 0.5D);
        for (int tick = 0; tick < 200 && unpowered.isRunning(); tick++) {
            unpowered.tick(ramp, 0.0D, 4, TICK);
        }
        assertTrue(unpowered.reference().distance() < 15.0D,
            "expected the unpowered train to stall near the bottom, got distance="
                + unpowered.reference().distance());

        ChainLift chain = lift(5.0D, 12.0D);
        Train powered = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), 0.5D);
        for (int tick = 0; tick < 400 && powered.isRunning(); tick++) {
            powered.tick(ramp, chain.accelerationFor(powered), 4, TICK);
        }
        assertTrue(ramp.frameAt(powered.reference()).position.y > 95.0D,
            "lift failed to climb; train at y=" + ramp.frameAt(powered.reference()).position.y);
    }

    @Test
    @DisplayName("a chain lift holds chain speed rather than continuing to accelerate once it reaches it")
    void holdsChainSpeedOnceReached() {
        TrackNetwork ramp = steepRampNetwork();
        ChainLift chain = lift(5.0D, 12.0D);
        Train train = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), 0.5D);

        double maxSpeedAfterSettling = 0.0D;
        for (int tick = 0; tick < 400 && train.isRunning(); tick++) {
            train.tick(ramp, chain.accelerationFor(train), 4, TICK);
            if (tick > 60) {
                maxSpeedAfterSettling = Math.max(maxSpeedAfterSettling, train.speed());
            }
        }
        assertEquals(5.0D, maxSpeedAfterSettling, 0.5D,
            "expected the lift to hold close to chain speed once converged, saw peak " + maxSpeedAfterSettling);
    }

    @Test
    @DisplayName("a chain lift holds speed over a crest, unlike a constant force which runs away down the far side")
    void holdsSpeedOverACrestUnlikeConstantForce() {
        TrackNetwork hill = crestNetwork();
        ChainLift chain = new ChainLift(1, 0.0D, 1000.0D, 6.0D, 20.0D, TICK);

        // A naive constant-force lift would be authored to comfortably climb the steepest part of
        // the grade — enough headroom over the ~5 blocks/s² gravity component on the initial climb
        // to make reasonable time, the same ballpark of push the chain servo itself applies while
        // actively climbing below chain speed. Apply that fixed value open-loop for the whole run,
        // exactly what a constant-force model of a lift would do.
        double constantForce = 8.0D;

        Train onChain = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), 3.0D);
        Train onConstantForce = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), 3.0D);

        double maxSpeedOnChain = 0.0D;
        double maxSpeedOnConstantForce = 0.0D;
        for (int tick = 0; tick < 500 && onChain.isRunning() && onConstantForce.isRunning(); tick++) {
            onChain.tick(hill, chain.accelerationFor(onChain), 4, TICK);
            onConstantForce.tick(hill, constantForce, 4, TICK);
            maxSpeedOnChain = Math.max(maxSpeedOnChain, onChain.speed());
            maxSpeedOnConstantForce = Math.max(maxSpeedOnConstantForce, onConstantForce.speed());
        }

        assertTrue(maxSpeedOnChain < 9.0D,
            "chain-held train should stay close to chain speed, peaked at " + maxSpeedOnChain);
        assertTrue(maxSpeedOnConstantForce > maxSpeedOnChain + 3.0D,
            "constant-force train should run away down the far side of the crest; chain peak="
                + maxSpeedOnChain + ", constant-force peak=" + maxSpeedOnConstantForce);
    }

    @Test
    @DisplayName("a train outside the lift's span is unaffected")
    void trainOutsideSpanIsUnaffected() {
        ChainLift chain = new ChainLift(1, 50.0D, 100.0D, 5.0D, 12.0D, TICK);
        assertFalse(chain.contains(new TrackRef(1, 10.0D)));
        assertFalse(chain.contains(new TrackRef(2, 60.0D)), "wrong section must never match");
        assertTrue(chain.contains(new TrackRef(1, 75.0D)));
    }

    @Test
    @DisplayName("chain lift acceleration is a deterministic function of train state")
    void isDeterministic() {
        ChainLift chain = lift(5.0D, 12.0D);
        Train a = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 10.0D), 2.0D);
        Train b = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 10.0D), 2.0D);
        assertEquals(chain.accelerationFor(a), chain.accelerationFor(b), 0.0D);
    }
}
