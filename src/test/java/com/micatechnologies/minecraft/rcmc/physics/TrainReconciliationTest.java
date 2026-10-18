package com.micatechnologies.minecraft.rcmc.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * How a client brings its predicted trains back in line with the server's corrections.
 *
 * <p>What a rider sees is the requirement: the train, and their view with it, must not jump when
 * a correction lands, and must not drift away from where the server has it either.</p>
 */
class TrainReconciliationTest {

    private static final double GRAVITY = 9.81D;
    private static final double TICK = 1.0D / 20.0D;
    private static final double EASE = 0.25D;

    private static TrackNode node(double x, double y, double z) {
        return new TrackNode(new Vec3(x, y, z));
    }

    private static PhysicsIntegrator frictionless() {
        return new PhysicsIntegrator(GRAVITY, 0.0D, 0.0D, 1000.0D);
    }

    private static TrackNetwork flatNetwork() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(100, 64, 0), node(200, 64, 0)), false, null));
        return n;
    }

    /** A steady climb: 60 up over 120 of run, flat at both ends. */
    private static TrackNetwork climbNetwork() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(20, 64, 0), node(80, 94, 0), node(140, 124, 0),
            node(160, 124, 0)), false, null));
        return n;
    }

    private static TrackNetwork ringNetwork(double radius) {
        List<TrackNode> ring = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double a = 2.0D * Math.PI * i / 12;
            ring.add(node(Math.cos(a) * radius, 64.0D, Math.sin(a) * radius));
        }
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, ring, true, null));
        return n;
    }

    private static Train train(double distance, double velocity) {
        return new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, distance), velocity);
    }

    @Test
    @DisplayName("a correction a block ahead moves the train there over several ticks, never in one")
    void correctionIsEasedNotJumped() {
        TrackNetwork network = flatNetwork();
        Train client = train(50.0D, 20.0D);
        double speedPerTick = 20.0D * TICK;

        assertTrue(client.correctToward(network, new TrackRef(1, 51.0D), 20.0D));
        // The correction itself moves nothing.
        assertEquals(50.0D, client.reference().distance(), 1e-9);

        double expected = 51.0D;
        double last = client.reference().distance();
        double largestExtra = 0.0D;
        for (int i = 0; i < 20; i++) {
            client.tick(network, 0.0D, 4, TICK);
            client.easeCorrection(network, EASE);
            expected += speedPerTick;
            double now = client.reference().distance();
            largestExtra = Math.max(largestExtra, now - last - speedPerTick);
            last = now;
        }
        // No tick moves the train more than a quarter block beyond its own motion...
        assertTrue(largestExtra <= 0.25D + 1e-9, "largest step beyond motion: " + largestExtra);
        // ...and it ends up where the server has it.
        assertEquals(expected, client.reference().distance(), 0.01D);
    }

    @Test
    @DisplayName("the server's velocity is taken at once")
    void velocityIsTakenAtOnce() {
        TrackNetwork network = flatNetwork();
        Train client = train(50.0D, 20.0D);
        client.correctToward(network, new TrackRef(1, 50.5D), 18.0D);
        assertEquals(18.0D, client.velocity(), 1e-12);
    }

    @Test
    @DisplayName("an error too large to be timing, or on another section, is applied outright")
    void largeOrCrossSectionErrorsSnap() {
        TrackNetwork network = flatNetwork();
        Train far = train(50.0D, 10.0D);
        assertFalse(far.correctToward(network, new TrackRef(1, 50.0D + Train.MAX_EASED_CORRECTION + 1.0D), 10.0D));
        assertEquals(50.0D + Train.MAX_EASED_CORRECTION + 1.0D, far.reference().distance(), 1e-9);
        assertEquals(0.0D, far.pendingCorrection(), 0.0D);

        network.addSection(new TrackSection(2, Arrays.asList(
            node(200, 64, 0), node(250, 64, 0), node(300, 64, 0)), false, null));
        Train other = train(199.0D, 10.0D);
        assertFalse(other.correctToward(network, new TrackRef(2, 0.5D), 10.0D));
        assertEquals(2, other.reference().sectionId());
    }

    @Test
    @DisplayName("on a circuit, a correction across the start line is a small step, not a lap")
    void circuitCorrectionTakesTheShortWay() {
        TrackNetwork network = ringNetwork(30.0D);
        double lap = network.section(1).totalLength();
        Train client = train(lap - 0.5D, 0.0D);
        assertTrue(client.correctToward(network, new TrackRef(1, 0.5D), 0.0D));
        assertEquals(1.0D, client.pendingCorrection(), 1e-9);
    }

    @Test
    @DisplayName("a lift the server reports is predicted, so the train does not sag between corrections")
    void predictedLiftHoldsSpeedUpTheClimb() {
        TrackNetwork network = climbNetwork();
        // Partway up the climb, at a steady lift speed: the server's chain is cancelling gravity.
        Train probe = train(70.0D, 4.0D);
        double liftAcceleration = -probe.averageGravityAlongTrack(network);
        assertTrue(liftAcceleration > 1.0D, "the test needs a real climb");

        Train predicted = train(70.0D, 4.0D);
        predicted.setSyncedControl(liftAcceleration, false);
        Train unpredicted = train(70.0D, 4.0D);
        PredictedControl control = new PredictedControl(network, TICK);
        for (int i = 0; i < 5; i++) {
            predicted.tick(network, control.forTrain(1, predicted), 4, TICK);
            unpredicted.tick(network, 0.0D, 4, TICK);
        }
        // Five ticks is the gap between corrections.
        assertEquals(4.0D, predicted.velocity(), 0.1D);
        assertTrue(unpredicted.velocity() < 3.0D, "without the lift the train should sag");
    }

    @Test
    @DisplayName("a reported brake stops the predicted train and never runs it backwards")
    void predictedBrakeStopsAtRest() {
        TrackNetwork network = flatNetwork();
        Train client = train(50.0D, 1.0D);
        // The server's servo, last seen bringing the train to a stop.
        client.setSyncedControl(-6.0D, false);
        PredictedControl control = new PredictedControl(network, TICK);
        double furthest = client.reference().distance();
        for (int i = 0; i < 10; i++) {
            client.tick(network, control.forTrain(1, client), 4, TICK);
            assertTrue(client.velocity() >= -1e-9, "reversed at tick " + i + ": " + client.velocity());
            assertTrue(client.reference().distance() >= furthest - 1e-9, "rolled back at tick " + i);
            furthest = client.reference().distance();
        }
    }

    @Test
    @DisplayName("a train the server is holding on a grade stays put on the client")
    void heldTrainStaysPutOnAGrade() {
        TrackNetwork network = climbNetwork();
        Train client = train(70.0D, 0.0D);
        client.setSyncedControl(0.0D, true);
        PredictedControl control = new PredictedControl(network, TICK);
        for (int i = 0; i < 5; i++) {
            client.setHeld(control.isHolding(1, client));
            client.tick(network, control.forTrain(1, client), 4, TICK);
        }
        assertEquals(70.0D, client.reference().distance(), 0.01D);
    }
}
