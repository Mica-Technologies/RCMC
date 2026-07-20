package com.micatechnologies.minecraft.rcmc.physics.element;

import static com.micatechnologies.minecraft.rcmc.physics.element.ElementTestSupport.TICK;
import static com.micatechnologies.minecraft.rcmc.physics.element.ElementTestSupport.gentleRampNetwork;
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

class DriveTyresTest {

    @Test
    @DisplayName("drive tyres hold a fixed creep speed against a gentle grade")
    void holdsCreepSpeedAgainstAGentleGrade() {
        TrackNetwork ramp = gentleRampNetwork();
        DriveTyres tyres = new DriveTyres(1, 0.0D, 1000.0D, 1.5D, 4.0D, TICK);
        Train train = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), 0.0D);

        double maxSpeedAfterSettling = 0.0D;
        for (int tick = 0; tick < 200 && train.isRunning(); tick++) {
            train.tick(ramp, tyres.accelerationFor(train), 4, TICK);
            if (tick > 40) {
                maxSpeedAfterSettling = Math.max(maxSpeedAfterSettling, train.speed());
            }
        }
        assertEquals(1.5D, maxSpeedAfterSettling, 0.2D,
            "expected the drive to hold close to its creep speed, saw peak " + maxSpeedAfterSettling);
    }

    @Test
    @DisplayName("a train outside the drive tyres' span is unaffected")
    void trainOutsideSpanIsUnaffected() {
        DriveTyres tyres = new DriveTyres(1, 0.0D, 20.0D, 1.5D, 4.0D, TICK);
        assertTrue(tyres.contains(new TrackRef(1, 5.0D)));
        assertFalse(tyres.contains(new TrackRef(1, 25.0D)));
    }

    @Test
    @DisplayName("drive tyre acceleration is a deterministic function of train state")
    void isDeterministic() {
        DriveTyres tyres = new DriveTyres(1, 0.0D, 20.0D, 1.5D, 4.0D, TICK);
        Train a = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 5.0D), 0.5D);
        Train b = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 5.0D), 0.5D);
        assertEquals(tyres.accelerationFor(a), tyres.accelerationFor(b), 0.0D);
    }
}
