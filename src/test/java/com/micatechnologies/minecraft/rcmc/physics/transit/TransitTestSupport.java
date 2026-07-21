package com.micatechnologies.minecraft.rcmc.physics.transit;

import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.Arrays;

/**
 * Shared builders and tick loops for the transit tests, mirroring
 * {@code physics.element.ElementTestSupport}.
 *
 * <p>The stock profile used throughout is deliberately metro-shaped: ~1.2 blocks/s² off the line,
 * base speed above line speed so cruise sits in the constant-force region with headroom for a
 * gentle grade, service brake matching a comfortable stop, jerk bounded where a coaster would
 * slam.</p>
 */
final class TransitTestSupport {

    static final double GRAVITY = 9.81D;
    static final double TICK = 1.0D / 20.0D;

    static final double MAX_ACCELERATION = 1.2D;
    static final double RATED_POWER = 24.0D;
    static final double MAX_SERVICE_SPEED = 22.0D;
    static final double LINE_SPEED = 18.0D;
    static final double SERVICE_BRAKE = 1.2D;
    static final double MAX_JERK = 2.0D;
    static final double CREEP_SPEED = 1.5D;

    private TransitTestSupport() {
    }

    static TractionProfile metroTraction() {
        return new TractionProfile(MAX_ACCELERATION, RATED_POWER, MAX_SERVICE_SPEED);
    }

    static TrainDriver metroDriver() {
        return new TrainDriver(metroTraction(), SERVICE_BRAKE, MAX_JERK, CREEP_SPEED, TICK);
    }

    static PhysicsIntegrator frictionless() {
        return new PhysicsIntegrator(GRAVITY, 0.0D, 0.0D, 1000.0D);
    }

    static PhysicsIntegrator realistic() {
        return new PhysicsIntegrator(GRAVITY, 0.01D, 0.0015D, 60.0D);
    }

    /**
     * Advances a train under a bare driver for one tick, the way the ride-control layer will:
     * evaluate the command once, report holding intent at near-rest, tick with sub-steps.
     * Travel is along +X, so remaining distance is {@code stopDistance − current distance};
     * pass {@link TrainDriver#NO_STOP} as {@code stopDistance}'s remaining via
     * {@code Double.POSITIVE_INFINITY} to cruise.
     */
    static double tickWithDriver(Train train, TrackNetwork network, TrainDriver driver,
                                 double lineSpeed, double stopDistance) {
        double remaining = stopDistance == TrainDriver.NO_STOP
            ? TrainDriver.NO_STOP
            : signedRemaining(train, stopDistance, lineSpeed);
        double acceleration = driver.acceleration(train.velocity(), lineSpeed, remaining);
        // A train under active low-speed control is held by intent, not stalled — the same
        // contract TrainManager.ExternalAcceleration.isHolding carries for coaster elements.
        train.setHeld(Math.abs(train.velocity()) <= 0.05D);
        train.tick(network, acceleration, 4, TICK);
        return acceleration;
    }

    /** Advances a train under a full stop controller for one tick. Travel direction from line speed sign. */
    static double tickWithController(Train train, TrackNetwork network,
                                     TransitStopController controller, double stopDistance) {
        double remaining = signedRemaining(train, stopDistance, controller.lineSpeed());
        double acceleration = controller.acceleration(train.velocity(), remaining);
        train.setHeld(controller.isHolding(train.velocity()));
        train.tick(network, acceleration, 4, TICK);
        return acceleration;
    }

    /** Distance to the stop measured along the direction of travel (the sign of {@code lineSpeed}). */
    static double signedRemaining(Train train, double stopDistance, double lineSpeed) {
        double ahead = stopDistance - train.reference().distance();
        return lineSpeed >= 0.0D ? ahead : -ahead;
    }

    static TrackNode node(double x, double y, double z) {
        return new TrackNode(new Vec3(x, y, z));
    }

    /** Flat track along +X, {@code length} blocks. */
    static TrackNetwork flatNetwork(double length) {
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(length / 2.0D, 64, 0), node(length, 64, 0)), false, null));
        return n;
    }

    /**
     * A gentle grade a metro's traction can hold line speed on — long enough that a train
     * starting from rest at its foot has room to finish accelerating before it runs out of ramp.
     */
    static TrackNetwork gentleRampNetwork() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(200, 72, 0), node(400, 80, 0), node(600, 88, 0)), false, null));
        return n;
    }
}
