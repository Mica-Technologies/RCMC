package com.micatechnologies.minecraft.rcmc.physics.element;

import com.micatechnologies.minecraft.rcmc.physics.Train;

/**
 * A chain (or cable) lift hill: carries a train up a grade at a constant speed while engaged.
 *
 * <p>Modelled as a speed servo, not a constant force — see {@link VelocityServo} for the general
 * argument. For a chain lift specifically: a real chain dog is a mechanical constraint on a
 * continuously moving chain. It cannot let the train exceed chain speed, and its anti-rollback
 * dogs will not let the train fall back below it either, regardless of how the grade is currently
 * helping or hindering. A constant-force model gets both halves of that wrong — it would let the
 * train accelerate freely once gravity's opposing component drops away near the crest, and would
 * carry that extra speed <em>down</em> the far side of the hill while nominally still "on the
 * chain", which is not how a real lift behaves. Driving velocity toward {@code chainSpeed} and
 * holding it there reproduces the constraint directly: the train climbs no faster and no slower
 * than the chain, on any grade the motor can manage.</p>
 *
 * <p>{@code chainSpeed} is signed: positive pulls toward increasing distance along the section,
 * negative the other way. {@code maxAcceleration} stands in for the chain motor's torque limit —
 * on a grade steeper than the motor can manage the train falls short of chain speed rather than
 * the servo pretending otherwise; that is a real failure mode of underpowered lifts, not a bug.</p>
 */
public final class ChainLift extends RideElementSpan {

    private final double chainSpeed;
    private final double maxAcceleration;
    private final double tickSeconds;

    /**
     * @param sectionId       track section this lift sits on
     * @param startDistance   distance along the section where the chain engages, in blocks
     * @param endDistance     distance along the section where the chain releases, in blocks
     * @param chainSpeed      target speed, blocks/s, signed in the direction of increasing distance
     * @param maxAcceleration torque limit of the chain motor, blocks/s² — must be positive
     * @param tickSeconds     length of the game tick this lift is evaluated at; the servo is a
     *                        deadbeat controller over one tick — see {@link VelocityServo}
     */
    public ChainLift(int sectionId, double startDistance, double endDistance,
                      double chainSpeed, double maxAcceleration, double tickSeconds) {
        super(sectionId, startDistance, endDistance);
        if (maxAcceleration <= 0.0D) {
            throw new IllegalArgumentException("maxAcceleration must be positive, got " + maxAcceleration);
        }
        if (tickSeconds <= 0.0D) {
            throw new IllegalArgumentException("tickSeconds must be positive, got " + tickSeconds);
        }
        this.chainSpeed = chainSpeed;
        this.maxAcceleration = maxAcceleration;
        this.tickSeconds = tickSeconds;
    }

    @Override
    public double accelerationFor(Train train) {
        return VelocityServo.accelerationToHold(train.velocity(), chainSpeed, maxAcceleration, tickSeconds);
    }

    public double chainSpeed() {
        return chainSpeed;
    }

    public double maxAcceleration() {
        return maxAcceleration;
    }
}
