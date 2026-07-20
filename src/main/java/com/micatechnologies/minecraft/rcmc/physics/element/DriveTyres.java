package com.micatechnologies.minecraft.rcmc.physics.element;

import com.micatechnologies.minecraft.rcmc.physics.Train;

/**
 * Station drive tyres: low-speed friction wheels that move a train along the platform at a fixed
 * creep speed — positioning it for loading, or nudging it out toward a launch or lift.
 *
 * <p>Physically the same kind of constraint as {@link ChainLift} — a speed servo, not a constant
 * force, so it holds a gentle creep speed rather than accelerating a train indefinitely — just
 * sized for station speeds (on the order of a block per second) rather than a lift hill's chain
 * speed. See {@link VelocityServo} for the control law shared by both.</p>
 */
public final class DriveTyres extends RideElementSpan {

    private final double driveSpeed;
    private final double maxAcceleration;
    private final double tickSeconds;

    /**
     * @param driveSpeed      target creep speed, blocks/s, signed in the direction of increasing
     *                        distance
     * @param maxAcceleration torque limit of the tyre drive, blocks/s² — must be positive
     * @param tickSeconds     length of the game tick this drive is evaluated at — see
     *                        {@link VelocityServo}
     */
    public DriveTyres(int sectionId, double startDistance, double endDistance,
                       double driveSpeed, double maxAcceleration, double tickSeconds) {
        super(sectionId, startDistance, endDistance);
        if (maxAcceleration <= 0.0D) {
            throw new IllegalArgumentException("maxAcceleration must be positive, got " + maxAcceleration);
        }
        if (tickSeconds <= 0.0D) {
            throw new IllegalArgumentException("tickSeconds must be positive, got " + tickSeconds);
        }
        this.driveSpeed = driveSpeed;
        this.maxAcceleration = maxAcceleration;
        this.tickSeconds = tickSeconds;
    }

    @Override
    public double accelerationFor(Train train) {
        return VelocityServo.accelerationToHold(train.velocity(), driveSpeed, maxAcceleration, tickSeconds);
    }

    /** Peak acceleration the tyres can apply; needed to round-trip this element through a save. */
    public double maxAcceleration() {
        return maxAcceleration;
    }

    public double driveSpeed() {
        return driveSpeed;
    }
}
