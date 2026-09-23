package com.micatechnologies.minecraft.rcmc.physics.element;

import com.micatechnologies.minecraft.rcmc.physics.Train;

/**
 * The storage side of a {@link TransferTrack}: where a train taken out of service stands.
 *
 * <p>Holds whatever is on it at rest — brakes on, indefinitely — and says so, so a stored train is
 * never mistaken for a stalled one. It pushes nothing; trains arrive here only by being slid across
 * from the circuit.</p>
 */
public final class StorageBerth extends RideElementSpan {

    /** How firmly a stored train is held still, blocks/s². */
    static final double HOLD_DECELERATION = 4.0D;

    private final double tickSeconds;

    public StorageBerth(int sectionId, double startDistance, double endDistance, double tickSeconds) {
        super(sectionId, startDistance, endDistance);
        if (tickSeconds <= 0.0D) {
            throw new IllegalArgumentException("tickSeconds must be positive, got " + tickSeconds);
        }
        this.tickSeconds = tickSeconds;
    }

    @Override
    public double accelerationFor(Train train) {
        return VelocityServo.accelerationToHold(train.velocity(), 0.0D, HOLD_DECELERATION, tickSeconds);
    }

    @Override
    public boolean isHolding() {
        return true;
    }
}
