package com.micatechnologies.minecraft.rcmc.track.math;

/**
 * Arc-length reparameterization of a {@link CatmullRomSpline}.
 *
 * <p><b>The problem this solves.</b> A spline's native parameter {@code u} is not distance.
 * Advancing {@code u} at a constant rate makes a train crawl through wide curves and rocket
 * through tight ones — the exact opposite of physical behaviour, and immediately obvious to
 * anyone riding it. The physics simulation therefore works in one honest variable, distance
 * along the track {@code s} (in blocks), and this table converts {@code s} back to the
 * {@code u} the spline needs.</p>
 *
 * <p><b>How.</b> Sample the curve at {@code samplesPerSegment} uniform steps in {@code u},
 * accumulate chord lengths into a monotonically increasing table, then answer
 * {@link #paramAtDistance(double)} by binary search plus linear interpolation between
 * neighbouring samples. Chord length always <em>under</em>-estimates true arc length; the
 * error falls off as O(1/n²), so the default sampling is well past the point where it
 * matters at block scale, and any residual bias is a constant scale factor rather than a
 * position drift.</p>
 *
 * <p>Immutable and thread-safe once constructed. Build it once when a track section is
 * created or edited, never per tick — construction is O(samples), lookup is O(log n).</p>
 */
public final class ArcLengthTable {

    /**
     * Samples per spline segment. 64 puts sample spacing well under a block for realistic
     * node spacing, which keeps the chord-vs-arc error far below anything a rider could
     * perceive while keeping the table small enough to hold for every track section in a
     * loaded park.
     */
    public static final int DEFAULT_SAMPLES_PER_SEGMENT = 64;

    private final CatmullRomSpline spline;

    /** {@code u} value of each sample; strictly increasing from 0 to 1. */
    private final double[] params;

    /** Cumulative arc length at each sample; non-decreasing, {@code lengths[0] == 0}. */
    private final double[] lengths;

    public ArcLengthTable(CatmullRomSpline spline) {
        this(spline, DEFAULT_SAMPLES_PER_SEGMENT);
    }

    public ArcLengthTable(CatmullRomSpline spline, int samplesPerSegment) {
        if (spline == null) {
            throw new IllegalArgumentException("spline must not be null");
        }
        if (samplesPerSegment < 1) {
            throw new IllegalArgumentException("samplesPerSegment must be >= 1, got " + samplesPerSegment);
        }
        this.spline = spline;

        int sampleCount = spline.segmentCount() * samplesPerSegment + 1;
        this.params = new double[sampleCount];
        this.lengths = new double[sampleCount];

        Vec3 previous = spline.positionAt(0.0D);
        params[0] = 0.0D;
        lengths[0] = 0.0D;
        for (int i = 1; i < sampleCount; i++) {
            double u = (double) i / (sampleCount - 1);
            Vec3 current = spline.positionAt(u);
            params[i] = u;
            lengths[i] = lengths[i - 1] + current.distanceTo(previous);
            previous = current;
        }
    }

    /** Total length of the curve in blocks. */
    public double totalLength() {
        return lengths[lengths.length - 1];
    }

    /**
     * Spline parameter {@code u} at distance {@code s} blocks along the curve.
     *
     * <p>{@code s} is clamped to {@code [0, totalLength()]}. Clamping rather than wrapping is
     * intentional: a circuit that closes on itself is modelled by the track network joining
     * two section ends, not by this table wrapping, so silently wrapping here would hide a
     * train running off the end of an unconnected section.</p>
     */
    public double paramAtDistance(double s) {
        double total = totalLength();
        if (s <= 0.0D || total == 0.0D) {
            return 0.0D;
        }
        if (s >= total) {
            return 1.0D;
        }

        int lo = 0;
        int hi = lengths.length - 1;
        while (lo + 1 < hi) {
            int mid = (lo + hi) >>> 1;
            if (lengths[mid] <= s) {
                lo = mid;
            }
            else {
                hi = mid;
            }
        }

        double span = lengths[hi] - lengths[lo];
        // Zero span means coincident samples (duplicated control points); fall back to the
        // lower parameter rather than dividing by zero.
        double frac = span < 1.0e-12D ? 0.0D : (s - lengths[lo]) / span;
        return params[lo] + (params[hi] - params[lo]) * frac;
    }

    /** Position at distance {@code s} blocks along the curve. */
    public Vec3 positionAtDistance(double s) {
        return spline.positionAt(paramAtDistance(s));
    }

    /** Unit direction of travel at distance {@code s} blocks along the curve. */
    public Vec3 tangentAtDistance(double s) {
        return spline.tangentAt(paramAtDistance(s));
    }

    public CatmullRomSpline spline() {
        return spline;
    }
}
