package com.micatechnologies.minecraft.rcmc.physics.element;

/**
 * Shared control law for elements that hold a train at a target speed rather than applying a
 * constant force — {@link ChainLift}, {@link DriveTyres}, and the approach phase of
 * {@link StationPlatform}.
 *
 * <p><b>Why speed, not force.</b> A constant force just adds to whatever the train is already
 * doing. On a lift hill, gravity's opposing component shrinks as the train nears the crest, so a
 * constant-force "lift" would let the train keep accelerating past chain speed — and past the
 * crest, with gravity now assisting instead of opposing, it would let the train run away down the
 * far side while the model still called it "on the chain". A real chain dog cannot do that: it is
 * a mechanical speed constraint engaging a moving chain, not a motor pushing at a fixed torque.
 * Driving velocity toward a fixed target and holding it there reproduces that constraint —
 * velocity converges to the target and then stays there regardless of grade, up to the servo's
 * torque limit — which is exactly the physical behaviour a constant force cannot give.</p>
 *
 * <p><b>Deadbeat by construction.</b> {@link #accelerationToHold} returns the acceleration that
 * would close the <em>entire</em> velocity error within one game tick, ignoring every other force
 * — {@code (target − current) / tickSeconds}. Other forces (gravity, drag) act during that same
 * tick too, so the train will not land exactly on the target after a single call, but whatever
 * error remains is corrected again on the very next call, because the owning element is
 * re-evaluated every tick. That converges cleanly at any tick rate without retuning, unlike a
 * fixed proportional gain, which is only stable for the tick length it was tuned against.</p>
 *
 * <p>Clamped to {@code maxAcceleration}: real hardware has a torque limit. On a grade steeper than
 * that limit can climb, the result is the train falling short of the target speed rather than the
 * servo pretending otherwise — the honest physical outcome, not a bug.</p>
 */
final class VelocityServo {

    private VelocityServo() {
    }

    /**
     * @param currentVelocity signed current speed, blocks/s
     * @param targetVelocity  signed target speed, blocks/s
     * @param maxAcceleration torque limit, blocks/s² — must be positive; clamps the result's
     *                        magnitude, not its sign
     * @param tickSeconds     length of the tick this will be applied over, in seconds — must be
     *                        positive
     * @return along-track acceleration, blocks/s²
     */
    static double accelerationToHold(double currentVelocity, double targetVelocity,
                                      double maxAcceleration, double tickSeconds) {
        double desired = (targetVelocity - currentVelocity) / tickSeconds;
        if (desired > maxAcceleration) {
            return maxAcceleration;
        }
        if (desired < -maxAcceleration) {
            return -maxAcceleration;
        }
        return desired;
    }
}
