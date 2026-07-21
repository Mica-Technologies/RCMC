package com.micatechnologies.minecraft.rcmc.physics.transit;

/**
 * Rate-limits an acceleration command so it never changes faster than a maximum jerk.
 *
 * <p>This is the difference between a metro stop and a coaster brake run. Coaster hardware slams
 * force on and off — that is part of the ride. A passenger service must ramp force in and out, or
 * every station stop throws standing riders off their feet; comfort standards for metros bound
 * jerk at roughly 1–2 m/s³. The elements in {@code physics.element} deliberately have no such
 * limit; every command a {@link TrainDriver} issues passes through one of these.</p>
 *
 * <p>Mutable and tick-driven: {@link #advance} is called exactly once per game tick, and the
 * output is a function only of the previous output and the new command — no wall clock, matching
 * the determinism rule the whole physics package lives under. The one subtlety is that limiting
 * happens on the <em>command</em>, not on the physics: the train's actual acceleration also
 * includes gravity and drag, which can change step-wise at a grade break. Smoothing what the
 * motors and brakes do is the part comfort standards care about, and the part a controller can
 * actually control.</p>
 */
public final class JerkLimiter {

    private final double maxJerk;
    private final double tickSeconds;
    private double output;

    /**
     * @param maxJerk     largest allowed change in output per second, blocks/s³ — must be positive
     * @param tickSeconds length of the tick each {@link #advance} call represents — must be
     *                    positive
     */
    public JerkLimiter(double maxJerk, double tickSeconds) {
        if (maxJerk <= 0.0D) {
            throw new IllegalArgumentException("maxJerk must be positive, got " + maxJerk);
        }
        if (tickSeconds <= 0.0D) {
            throw new IllegalArgumentException("tickSeconds must be positive, got " + tickSeconds);
        }
        this.maxJerk = maxJerk;
        this.tickSeconds = tickSeconds;
    }

    /**
     * Moves the output toward {@code commanded} by at most {@code maxJerk · tickSeconds}, and
     * returns the new output. Call once per tick.
     */
    public double advance(double commanded) {
        double maxStep = maxJerk * tickSeconds;
        double delta = commanded - output;
        if (delta > maxStep) {
            delta = maxStep;
        } else if (delta < -maxStep) {
            delta = -maxStep;
        }
        output += delta;
        return output;
    }

    /**
     * Snaps the output to {@code value} without rate limiting. For discontinuities that are
     * legitimate rather than uncomfortable — arming a controller on a train already at rest, or
     * an emergency brake application, where stopping distance outranks comfort by definition.
     */
    public void reset(double value) {
        this.output = value;
    }

    /** The last value {@link #advance} returned (or {@link #reset} set). */
    public double output() {
        return output;
    }

    public double maxJerk() {
        return maxJerk;
    }
}
