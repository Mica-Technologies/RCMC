package com.micatechnologies.minecraft.rcmc.client.hud;

/**
 * Turns an instantaneous, tick-by-tick G reading into a "how loaded has the rider been lately"
 * signal, for {@link GForceEffects} to react to.
 *
 * <p><b>Why this exists at all.</b> Reacting to the raw G value would black out or red out the
 * screen for a single-tick numerical blip — a curvature sample landing exactly on a section
 * boundary (see {@link RideTelemetry}), a physics sub-step catching a sharp transition — even
 * though the rider felt nothing of the sort. Real vision loss under G-load is also not
 * instantaneous; it takes a sustained load over roughly a second for blood to actually pool away
 * from the eyes. Both point at the same fix: filter for sustained load, not peak load.</p>
 *
 * <p><b>The filter: a discretised first-order lag (exponential moving average), in time, not in
 * ticks.</b> Each {@link #update} moves the smoothed value a fraction of the way toward the new
 * sample:</p>
 *
 * <pre>
 * alpha = 1 - exp(-dt / tau)
 * value += (sample - value) * alpha
 * </pre>
 *
 * <p>This is the exact discretisation of an RC low-pass filter with time constant {@code tau}, and
 * it is deliberately driven by wall/tick time ({@code dt}) rather than a fixed per-call blend
 * factor: a constant blend factor (as {@code RiderCamera}'s camera-roll smoothing uses, which is
 * fine there because it always runs once per render frame) would make the *effective* time
 * constant depend on how often {@link #update} is called. Feeding it a real {@code dt} keeps the
 * "how many seconds of sustained load before the effect ramps up" behaviour identical regardless
 * of call rate. In practice {@link RideMonitor} calls this once per client tick at a fixed
 * {@code dt}, so the two approaches would agree here, but the time-based form is the one that
 * stays correct if that ever changes.</p>
 *
 * <p>After {@code tau} seconds of a step input, the output has covered ~63% of the gap to the new
 * value; after {@code 3 * tau} seconds, ~95%. A spike shorter than {@code tau} moves the output by
 * only a small fraction of its own size, which is exactly the "single-tick spike must not black
 * the screen" requirement.</p>
 */
public final class GForceSmoother {

    private final double timeConstantSeconds;

    private double value;
    private boolean initialized;

    /**
     * @param timeConstantSeconds {@code tau} above, in seconds. Must be positive; larger values
     *                            make the effect slower to ramp up and slower to release.
     */
    public GForceSmoother(double timeConstantSeconds) {
        if (timeConstantSeconds <= 0.0D) {
            throw new IllegalArgumentException(
                "timeConstantSeconds must be positive, got " + timeConstantSeconds);
        }
        this.timeConstantSeconds = timeConstantSeconds;
    }

    /**
     * Folds in one new sample and returns the updated smoothed value.
     *
     * @param sample    the instantaneous reading this tick
     * @param dtSeconds time elapsed since the previous {@link #update} call. Non-positive values
     *                  are treated as "no time passed" and leave the smoothed value unchanged,
     *                  which is safer than dividing by (or amplifying) a bogus delta.
     */
    public double update(double sample, double dtSeconds) {
        if (!initialized) {
            // The very first sample of a ride has nothing to smooth against yet. Snapping to it
            // rather than ramping up from zero means boarding mid-launch does not read as a
            // gentle start that was never physically there.
            value = sample;
            initialized = true;
            return value;
        }
        if (dtSeconds <= 0.0D) {
            return value;
        }
        double alpha = 1.0D - Math.exp(-dtSeconds / timeConstantSeconds);
        value += (sample - value) * alpha;
        return value;
    }

    public double value() {
        return value;
    }

    /** Clears the filter so the next {@link #update} snaps to its sample rather than easing in. */
    public void reset() {
        value = 0.0D;
        initialized = false;
    }
}
