package com.micatechnologies.minecraft.rcmc.physics.element;

import com.micatechnologies.minecraft.rcmc.physics.Train;

/**
 * A brake run: opposes a train's motion to slow it toward a target speed.
 *
 * <p>Two modes, matching the two kinds of brake a real coaster uses:</p>
 * <ul>
 *   <li>{@link Mode#TRIM} — a friction or magnetic fin brake that trims excess speed but is not
 *       designed to stop a train; {@code targetSpeed} must be strictly positive, and the element
 *       never brakes below it. Used mid-circuit to cap entry speed into a tight element without a
 *       full stop.</li>
 *   <li>{@link Mode#BLOCK} — can bring the train to a complete halt ({@code targetSpeed = 0}) or
 *       hold it to a low creep speed. <b>This is what makes multi-train operation safe</b>: a
 *       block brake holding one train prevents it from ever colliding with the train ahead, and
 *       that is the entire mechanism Phase 7.2's block-section signalling is built on top of — a
 *       single-train ride has no need for one, and a multi-train ride cannot safely run without at
 *       least one between each pair of trains that might otherwise meet.</li>
 * </ul>
 *
 * <p>Braking always opposes whichever direction the train is currently moving — unlike
 * {@link LaunchTrack}, a brake run doesn't need to know which way the train arrived from, only
 * which way it is going right now.</p>
 *
 * <p>Uses {@link VelocityServo} to close the gap to the floor speed, the same as {@link ChainLift}
 * and {@link DriveTyres}, rather than simply applying {@code -deceleration} outright. That matters
 * at the very end of a stop: a plain constant deceleration can overshoot past zero within a single
 * tick and leave the train (however briefly) rolling backwards, which is not how a real brake
 * behaves — a brake removes energy, it does not reverse the train. The servo's deadbeat property
 * means the applied acceleration shrinks as the train approaches the floor speed, and an explicit
 * guard below additionally forbids the servo from ever proposing a push in the assisting direction,
 * since brakes only ever remove energy.</p>
 */
public final class BrakeRun extends RideElementSpan {

    /** Which kind of brake this is — see the class javadoc. */
    public enum Mode {
        TRIM,
        BLOCK
    }

    /** Below this speed a braked train is considered at its floor rather than left to creep on it. */
    private static final double REST_EPSILON = 1e-6D;

    private final double targetSpeed;
    private final double deceleration;
    private final Mode mode;
    private final double tickSeconds;

    /**
     * @param targetSpeed  floor speed the brake slows toward, blocks/s, always {@code >= 0}. Must
     *                     be strictly positive in {@link Mode#TRIM} — a "trim" brake that could
     *                     stop the train is a block brake by definition, so that combination is
     *                     rejected rather than silently accepted.
     * @param deceleration magnitude of braking acceleration, blocks/s² — must be positive
     * @param tickSeconds  length of the game tick this brake is evaluated at — see
     *                     {@link VelocityServo}
     */
    public BrakeRun(int sectionId, double startDistance, double endDistance,
                     double targetSpeed, double deceleration, Mode mode, double tickSeconds) {
        super(sectionId, startDistance, endDistance);
        if (targetSpeed < 0.0D) {
            throw new IllegalArgumentException("targetSpeed must be >= 0, got " + targetSpeed);
        }
        if (mode == Mode.TRIM && targetSpeed <= 0.0D) {
            throw new IllegalArgumentException(
                "a TRIM brake cannot have targetSpeed <= 0 (that is a full stop) — use Mode.BLOCK");
        }
        if (deceleration <= 0.0D) {
            throw new IllegalArgumentException("deceleration must be positive, got " + deceleration);
        }
        if (tickSeconds <= 0.0D) {
            throw new IllegalArgumentException("tickSeconds must be positive, got " + tickSeconds);
        }
        this.targetSpeed = targetSpeed;
        this.deceleration = deceleration;
        this.mode = mode;
        this.tickSeconds = tickSeconds;
    }

    @Override
    public double accelerationFor(Train train) {
        double v = train.velocity();
        double speed = Math.abs(v);
        if (speed <= targetSpeed + REST_EPSILON) {
            // At or below the floor already: TRIM must not push the train back up to it, and BLOCK
            // holding a stopped train must not re-accelerate it either. Only ever remove energy.
            return 0.0D;
        }
        double sign = v > 0.0D ? 1.0D : -1.0D;
        double target = sign * targetSpeed;
        double a = VelocityServo.accelerationToHold(v, target, deceleration, tickSeconds);
        // The servo alone would be happy to push in the assisting direction once very close to the
        // floor (e.g. floating point landing a hair below target); a brake must never do that.
        return sign > 0.0D ? Math.min(a, 0.0D) : Math.max(a, 0.0D);
    }

    public double targetSpeed() {
        return targetSpeed;
    }

    public double deceleration() {
        return deceleration;
    }

    public Mode mode() {
        return mode;
    }
}
