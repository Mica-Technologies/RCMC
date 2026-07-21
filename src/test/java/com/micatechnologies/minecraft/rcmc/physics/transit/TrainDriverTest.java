package com.micatechnologies.minecraft.rcmc.physics.transit;

import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.LINE_SPEED;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.MAX_JERK;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.SERVICE_BRAKE;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.TICK;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.flatNetwork;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.gentleRampNetwork;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.metroDriver;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.metroTraction;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.realistic;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.tickWithDriver;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrainDriverTest {

    @Test
    @DisplayName("a driver accelerates a train from rest to line speed and holds it there against drag")
    void reachesAndHoldsLineSpeedOnFlatTrack() {
        TrackNetwork flat = flatNetwork(2000.0D);
        TrainDriver driver = metroDriver();
        Train train = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), 0.0D);

        for (int i = 0; i < 600; i++) {
            tickWithDriver(train, flat, driver, LINE_SPEED, TrainDriver.NO_STOP);
        }
        assertEquals(LINE_SPEED, train.velocity(), 0.5D,
            "expected the train to be cruising at line speed after 30 seconds");
    }

    @Test
    @DisplayName("a driver starts a train from rest on a grade and holds line speed up the climb")
    void holdsLineSpeedOnAGentleGrade() {
        TrackNetwork ramp = gentleRampNetwork();
        TrainDriver driver = metroDriver();
        // From rest ON the grade: during the jerk-limited motor ramp-up, gravity briefly wins and
        // the train starts to roll backwards — rollback protection is what recovers it. Started a
        // few blocks in so the recovery has track behind it to happen on.
        Train train = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 10.0D), 0.0D);

        double minDistance = 10.0D;
        int guard = 0;
        while (train.reference().distance() < 500.0D && guard++ < 4000) {
            tickWithDriver(train, ramp, driver, LINE_SPEED, TrainDriver.NO_STOP);
            minDistance = Math.min(minDistance, train.reference().distance());
        }
        assertTrue(guard < 4000, "expected the train to climb the whole grade");
        assertEquals(LINE_SPEED, train.velocity(), 0.5D,
            "expected line speed to be held on the climb, not sagging with the grade");
        assertTrue(minDistance > 9.5D,
            "rollback protection should bound the grade-start rollback to centimetres, rolled to " + minDistance);
    }

    @Test
    @DisplayName("the commanded acceleration never exceeds the traction curve or the service brake")
    void staysWithinTractionAndBrakeLimits() {
        TrackNetwork flat = flatNetwork(2000.0D);
        TrainDriver driver = metroDriver();
        Train train = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), 0.0D);
        TractionProfile traction = metroTraction();

        for (int i = 0; i < 800; i++) {
            double available = traction.availableAcceleration(train.velocity());
            double command = tickWithDriver(train, flat, driver, LINE_SPEED,
                i < 400 ? TrainDriver.NO_STOP : 1200.0D);
            assertTrue(command <= available + 1e-9D,
                "commanded " + command + " with only " + available + " available at v=" + train.velocity());
            assertTrue(command >= -SERVICE_BRAKE - 1e-9D,
                "commanded " + command + ", beyond the service brake rating");
        }
    }

    @Test
    @DisplayName("the command stream is jerk-limited even across cruise/brake transitions")
    void commandsAreJerkLimited() {
        TrackNetwork flat = flatNetwork(2000.0D);
        TrainDriver driver = metroDriver();
        Train train = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), 0.0D);

        double previous = 0.0D;
        double maxStep = MAX_JERK * TICK;
        for (int i = 0; i < 800; i++) {
            // Stop assigned mid-run, forcing the harshest transition the driver ever makes:
            // full power to full brake.
            double command = tickWithDriver(train, flat, driver, LINE_SPEED,
                i < 300 ? TrainDriver.NO_STOP : 500.0D);
            assertTrue(Math.abs(command - previous) <= maxStep + 1e-9D,
                "command moved " + Math.abs(command - previous) + " in one tick at tick " + i);
            previous = command;
        }
    }

    @Test
    @DisplayName("a driver brakes on the stopping curve and comes to rest at the stop point")
    void stopsAtTheStopPoint() {
        TrackNetwork flat = flatNetwork(2000.0D);
        TrainDriver driver = metroDriver();
        Train train = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), LINE_SPEED);
        double stop = 400.0D;

        double maxDistance = 0.0D;
        int guard = 0;
        while ((train.speed() > 0.05D || train.reference().distance() < stop - 1.0D) && guard++ < 4000) {
            tickWithDriver(train, flat, driver, LINE_SPEED, stop);
            maxDistance = Math.max(maxDistance, train.reference().distance());
        }
        assertTrue(guard < 4000, "expected the train to reach the stop point and come to rest");
        assertEquals(stop, train.reference().distance(), 1.0D,
            "expected the train at rest within a block of the stop point");
        assertTrue(maxDistance <= stop + 1.0D,
            "overshot the stop point by " + (maxDistance - stop) + " blocks");
    }

    @Test
    @DisplayName("negative line speed runs the train backwards and stops it at a stop point behind it")
    void reverseRunningIsJustANegativeLineSpeed() {
        TrackNetwork flat = flatNetwork(2000.0D);
        TrainDriver driver = metroDriver();
        Train train = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 1000.0D), 0.0D);
        double stop = 700.0D;

        int guard = 0;
        while ((train.speed() > 0.05D || train.reference().distance() > stop + 1.0D) && guard++ < 4000) {
            tickWithDriver(train, flat, driver, -LINE_SPEED, stop);
        }
        assertTrue(guard < 4000, "expected the train to reach the stop point running in reverse");
        assertEquals(stop, train.reference().distance(), 1.0D,
            "expected the train at rest within a block of the reverse stop point");
    }
}
