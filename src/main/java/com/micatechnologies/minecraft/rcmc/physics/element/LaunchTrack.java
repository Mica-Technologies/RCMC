package com.micatechnologies.minecraft.rcmc.physics.element;

import com.micatechnologies.minecraft.rcmc.physics.Train;

/**
 * An LSM/LIM-style launch: pushes a train from whatever speed it arrives with up to a target
 * launch speed over the length of the element, then stops pushing.
 *
 * <p>Unlike {@link ChainLift}, a launch is genuinely a constant (or profiled) <em>force</em>, not
 * a speed constraint. Real launch tracks are open-loop: the linear motors apply the force they
 * are built for and whatever speed results downstream of that is the result — there is nothing
 * physically holding the train at a target the way a chain dog does. That is exactly why launches
 * can be tuned to overshoot or fall short in a way a chain lift structurally cannot: get the
 * force or the run length wrong and the train simply leaves at the wrong speed. This element still
 * needs a defined stopping condition, though — once the train reaches {@code targetSpeed} in the
 * launch direction, it stops applying force, mirroring the motors being switched off past the end
 * of the powered run rather than continuing to push forever.</p>
 */
public final class LaunchTrack extends RideElementSpan {

    /**
     * How hard the launch pushes at a given point along the element.
     *
     * <p>Kept as a strategy rather than a single number so the element can support a ramped
     * profile — real LSM launches are staged in blocks of motors, often tuned to push harder early
     * to build speed quickly and taper off approaching the target — without every caller needing
     * to know about that.</p>
     */
    public interface AccelerationProfile {

        /**
         * @param fractionAlong how far through the element the train is, in {@code [0, 1]}
         * @return acceleration <em>magnitude</em> to apply, blocks/s², always {@code >= 0}; the
         *         element applies the direction, derived from the sign of {@code targetSpeed}
         */
        double accelerationAt(double fractionAlong);
    }

    /** A fixed push for the whole length of the element — the simplest possible launch. */
    public static AccelerationProfile constant(double acceleration) {
        if (acceleration < 0.0D) {
            throw new IllegalArgumentException("acceleration must be >= 0, got " + acceleration);
        }
        return fractionAlong -> acceleration;
    }

    /**
     * Linearly interpolated push from {@code startAcceleration} to {@code endAcceleration} across
     * the element — a staged launch that ramps up (or tapers off) rather than a single fixed-force
     * motor bank.
     */
    public static AccelerationProfile ramped(double startAcceleration, double endAcceleration) {
        if (startAcceleration < 0.0D || endAcceleration < 0.0D) {
            throw new IllegalArgumentException("accelerations must be >= 0");
        }
        return fractionAlong -> startAcceleration
            + (endAcceleration - startAcceleration) * clamp01(fractionAlong);
    }

    private static double clamp01(double t) {
        return Math.max(0.0D, Math.min(1.0D, t));
    }

    private final double targetSpeed;
    private final AccelerationProfile profile;

    /**
     * @param targetSpeed signed target speed, blocks/s; its sign gives the launch direction
     * @param profile     acceleration magnitude as a function of position along the element
     */
    public LaunchTrack(int sectionId, double startDistance, double endDistance,
                        double targetSpeed, AccelerationProfile profile) {
        super(sectionId, startDistance, endDistance);
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        this.targetSpeed = targetSpeed;
        this.profile = profile;
    }

    @Override
    public double accelerationFor(Train train) {
        double direction = Math.signum(targetSpeed);
        double v = train.velocity();
        // Already at or past the target in the launch direction: the motors are off.
        if (direction == 0.0D || direction * v >= Math.abs(targetSpeed)) {
            return 0.0D;
        }
        double span = endDistance - startDistance;
        double distance = train.reference().distance();
        double fraction = span <= 0.0D ? 0.0D : clamp01((distance - startDistance) / span);
        return direction * profile.accelerationAt(fraction);
    }

    public double targetSpeed() {
        return targetSpeed;
    }
}
