package com.micatechnologies.minecraft.rcmc.physics.transit;

/**
 * The speed-control law of a powered transit train: cruise at line speed, and brake on a
 * constant-deceleration curve into a stop point. The automatic train operation ("ATO") layer,
 * reduced to the one function it fundamentally is.
 *
 * <p><b>Where this sits in the architecture.</b> Coaster propulsion is track-side hardware, so it
 * lives in {@code physics.element} as spans of track that act on whatever train is on them. A
 * metro's motors travel <em>with</em> the train, so this is a per-train object instead: the
 * ride-control layer calls {@link #acceleration} each tick and feeds the result to
 * {@code Train.tick} through the same {@code TrainManager.ExternalAcceleration} hook the coaster
 * elements use. The integrator is untouched either way — traction and braking are just one more
 * external along-track acceleration, exactly as designed.</p>
 *
 * <p><b>Control law.</b> Target speed is the lesser of line speed and the classic
 * constant-deceleration stopping profile {@code sqrt(2 · brake · remaining)} — the same curve
 * {@code StationPlatform} brakes coasters with, for the same reason: it tapers to zero exactly as
 * the remaining distance does, so no separate "have we arrived" heuristic is needed. The gap to
 * the target closes with the deadbeat law {@code (target − v) / tick} (see {@code VelocityServo}
 * in {@code physics.element} for why deadbeat beats a tuned proportional gain), clamped
 * asymmetrically — propulsion by whatever the {@link TractionProfile} has at the current speed,
 * braking by the service-brake rating — and finally rate-limited by a {@link JerkLimiter},
 * because passengers stand up in these.</p>
 *
 * <p>One behaviour falls out of that law which coaster brakes explicitly forbid: if the train is
 * <em>slower</em> than the stopping curve — stopped short of the platform, say — the driver powers
 * it forward along the curve. A brake run may only ever remove energy; a train with motors creeps
 * the rest of the way in. That single sign difference is why stopping short is a non-event for a
 * metro and a documented limitation for {@code StationPlatform}. The catch-up is capped at
 * {@code creepSpeed} while inside the braking zone: chasing the curve at full power would arrive
 * on it at maximum closing rate, and the jerk limit then delays the swing from full power to full
 * brake long enough to sail metres past the platform. Real ATO creeps at walking pace for exactly
 * this reason.</p>
 *
 * <p><b>Rollback protection</b> is the one place the jerk limit is deliberately bypassed. On a
 * grade start, the comfort-rated motor ramp is briefly weaker than gravity and the train begins
 * to roll the wrong way; the moment that is detected (wrong-direction motion at near-rest), the
 * clamped command is applied at once. This mirrors real traction packages, where the holding
 * brake releases into already-built tractive effort — and like the emergency brake, it outranks
 * comfort by definition. It engages only below a small speed so that a deliberate direction
 * change at speed still ramps like any other command.</p>
 *
 * <p><b>Directions.</b> Travel direction is the sign of {@code targetLineSpeed}, and
 * {@code remainingToStop} is measured along that direction (positive = the stop is ahead).
 * Reverse running — needed for terminus reversal — is just a negative line speed.</p>
 *
 * <p>Mutable only through its jerk limiter; tick-driven and deterministic like everything else in
 * the physics packages. Holds no reference to the train or the track — the caller supplies the
 * two numbers the law actually needs, which is what keeps this testable in isolation.</p>
 */
public final class TrainDriver {

    /** Passed as {@code remainingToStop} when there is no stop ahead: cruise at line speed. */
    public static final double NO_STOP = Double.POSITIVE_INFINITY;

    /**
     * Below this speed, wrong-direction motion is rollback (jerk limit bypassed, see the class
     * javadoc); above it, it is a commanded direction change and ramps normally.
     */
    private static final double ROLLBACK_SPEED = 0.5D;

    /**
     * The stopping curve is planned against this fraction of the service brake, though the full
     * rate remains available once braking engages. Same reasoning as {@code BlockSystem}'s
     * identical factor: commands are evaluated once per tick and, here, additionally ramped by
     * the jerk limiter, so the actually-applied brake lags the curve's demand — near the curve's
     * tail, where {@code dv/ds} diverges, that lag compounds into real overshoot. Planning
     * against 70% keeps 30% of the brake in reserve to absorb it.
     */
    private static final double BRAKE_PLANNING_FACTOR = 0.7D;

    /**
     * When catching the stopping curve from below, the target is this fraction of the curve
     * rather than the curve itself. Charging the curve at full traction arrives on it at maximum
     * closing rate exactly where the jerk-limited swing to full brake is slowest to come — the
     * measured result was metres of overshoot. Riding 70% of the curve needs only half the
     * planned deceleration, leaving the rest as margin for that swing.
     */
    private static final double CATCH_UP_FRACTION = 0.7D;

    private final TractionProfile traction;
    private final double serviceBrakeDeceleration;
    private final double creepSpeed;
    private final double tickSeconds;
    private final JerkLimiter jerk;

    /**
     * @param traction                 the stock's tractive-effort curve
     * @param serviceBrakeDeceleration magnitude of the service brake, blocks/s² — must be
     *                                 positive. This is the comfort-rated brake; an emergency
     *                                 brake rate belongs to the signalling layer (M4), which may
     *                                 bypass this driver entirely.
     * @param maxJerk                  comfort bound on how fast the commanded acceleration may
     *                                 change, blocks/s³ — see {@link JerkLimiter}
     * @param creepSpeed               speed cap, blocks/s, when powering toward the stopping curve
     *                                 from below inside the braking zone — walking pace; see the
     *                                 class javadoc for why chasing the curve any faster overshoots
     *                                 the platform
     * @param tickSeconds              length of the game tick this driver is evaluated at
     */
    public TrainDriver(TractionProfile traction, double serviceBrakeDeceleration,
                       double maxJerk, double creepSpeed, double tickSeconds) {
        if (traction == null) {
            throw new IllegalArgumentException("traction profile is required");
        }
        if (serviceBrakeDeceleration <= 0.0D) {
            throw new IllegalArgumentException(
                "serviceBrakeDeceleration must be positive, got " + serviceBrakeDeceleration);
        }
        if (creepSpeed <= 0.0D) {
            throw new IllegalArgumentException("creepSpeed must be positive, got " + creepSpeed);
        }
        if (tickSeconds <= 0.0D) {
            throw new IllegalArgumentException("tickSeconds must be positive, got " + tickSeconds);
        }
        this.traction = traction;
        this.serviceBrakeDeceleration = serviceBrakeDeceleration;
        this.creepSpeed = creepSpeed;
        this.tickSeconds = tickSeconds;
        this.jerk = new JerkLimiter(maxJerk, tickSeconds);
    }

    /**
     * The along-track acceleration to command this tick, blocks/s². Call exactly once per tick —
     * the jerk limiter advances on every call.
     *
     * @param velocity        the train's current signed velocity, blocks/s
     * @param targetLineSpeed signed cruise speed; its sign is the direction of travel. Zero means
     *                        "brake to rest and stay there".
     * @param remainingToStop distance to the stop point measured along the direction of travel,
     *                        blocks — positive when the stop is ahead, negative when overshot
     *                        (treated as a stop right here), {@link #NO_STOP} when cruising
     */
    public double acceleration(double velocity, double targetLineSpeed, double remainingToStop) {
        double direction = targetLineSpeed >= 0.0D ? 1.0D : -1.0D;

        double targetSpeed = Math.abs(targetLineSpeed);
        double speed = Math.abs(velocity);
        if (remainingToStop != NO_STOP) {
            double curveSpeed = Math.sqrt(2.0D * serviceBrakeDeceleration * BRAKE_PLANNING_FACTOR
                * Math.max(0.0D, remainingToStop));
            boolean insideBrakingZone = curveSpeed < targetSpeed;
            targetSpeed = Math.min(targetSpeed, curveSpeed);
            if (insideBrakingZone && targetSpeed > speed) {
                // Catching the curve from below (stopped short, released into the platform slow,
                // accelerating out of a nearby stop): ride below it, floored at creep so the
                // final metres still finish — see CATCH_UP_FRACTION.
                targetSpeed = Math.min(targetSpeed,
                    Math.max(creepSpeed, CATCH_UP_FRACTION * curveSpeed));
            }
        }

        // Deadbeat: close the whole velocity error in one tick, then let the clamps below say how
        // much of that is physically available. See VelocityServo for why this converges cleanly
        // at any tick rate where a fixed proportional gain would not.
        double desired = (direction * targetSpeed - velocity) / tickSeconds;

        // Asymmetric clamp — the whole reason this is not just VelocityServo: propulsion is bound
        // by the traction curve at the current speed, braking by the service-brake rating, and
        // the two are different hardware with different limits.
        double propulsionLimit = traction.availableAcceleration(velocity);
        double commanded;
        if (direction > 0.0D) {
            commanded = clamp(desired, -serviceBrakeDeceleration, propulsionLimit);
        } else {
            commanded = clamp(desired, -propulsionLimit, serviceBrakeDeceleration);
        }

        // Rollback protection — see the class javadoc. Wrong-direction motion at near-rest gets
        // the full clamped command immediately; the jerk limiter is snapped rather than advanced
        // so subsequent ticks ramp from the recovery command, not from the stale one.
        if (direction * velocity < 0.0D && speed < ROLLBACK_SPEED) {
            jerk.reset(commanded);
            return commanded;
        }
        return jerk.advance(commanded);
    }

    /**
     * Snaps the jerk limiter to zero command. Call when taking control of a train already at rest
     * — a berthed train, a newly spawned one — so the first tick does not ramp away from a stale
     * command left over from the previous movement.
     */
    public void resetCommand() {
        jerk.reset(0.0D);
    }

    /** The last commanded acceleration, blocks/s² — what the jerk limiter most recently output. */
    public double lastCommand() {
        return jerk.output();
    }

    public TractionProfile traction() {
        return traction;
    }

    public double serviceBrakeDeceleration() {
        return serviceBrakeDeceleration;
    }

    public double creepSpeed() {
        return creepSpeed;
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
