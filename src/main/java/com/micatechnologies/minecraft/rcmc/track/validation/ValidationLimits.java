package com.micatechnologies.minecraft.rcmc.track.validation;

/**
 * Configurable thresholds for {@link TrackValidator}.
 *
 * <p>Immutable, like the rest of {@code track}: {@link #DEFAULT} is a reasonable starting point
 * and every field has a {@code with*} method that returns a new instance with just that field
 * changed, so a caller can tune one number without having to restate the others.</p>
 *
 * <p><b>Where the defaults come from.</b> Each field's javadoc says so explicitly. Some are
 * grounded in published coaster-design rules of thumb; several are <em>admitted guesses</em> —
 * plausible, round numbers picked because this is a warning system, not a certified engineering
 * tool, and a slightly-wrong default only means a builder gets nudged a little early or a little
 * late. None of them are load-bearing for correctness the way the numbers in {@code physics} are;
 * they only change which {@link TrackIssue}s get reported, never the simulation itself.</p>
 *
 * <p>There are no G-force, steepness or roll-rate limits here. There were: sideways G from the
 * rail's curvature at one assumed speed, a maximum grade, and a maximum bank change per block. All
 * three judged track without its speed or its riders, and cried wolf on every correct loop and heartline roll — a rail spiralling round the
 * riders' hearts is tightly curved, and a loop is past vertical by design. The ride check
 * ({@code rating.RideCheck}) now judges G from the ride as it actually runs, at the rider. What
 * stays here is geometry that is wrong whatever the speed.</p>
 */
public final class ValidationLimits {

    /** Sensible defaults; see each field's javadoc for where its number comes from. */
    public static final ValidationLimits DEFAULT = new ValidationLimits(
        0.5D, 0.0D, 0.4D, 48.0D, 4.0D, 12.0D, 1.0D);

    private final double sampleSpacingBlocks;
    private final double cuspTangentDotThreshold;
    private final double minNodeSpacingBlocks;
    private final double maxNodeSpacingBlocks;
    private final double selfIntersectionClearanceBlocks;
    private final double selfIntersectionMinArcSeparationBlocks;
    private final double selfIntersectionSampleSpacingBlocks;

    public ValidationLimits(double sampleSpacingBlocks, double cuspTangentDotThreshold, double minNodeSpacingBlocks, double maxNodeSpacingBlocks, double selfIntersectionClearanceBlocks, double selfIntersectionMinArcSeparationBlocks, double selfIntersectionSampleSpacingBlocks) {
        this.sampleSpacingBlocks = sampleSpacingBlocks;
        this.cuspTangentDotThreshold = cuspTangentDotThreshold;
        this.minNodeSpacingBlocks = minNodeSpacingBlocks;
        this.maxNodeSpacingBlocks = maxNodeSpacingBlocks;
        this.selfIntersectionClearanceBlocks = selfIntersectionClearanceBlocks;
        this.selfIntersectionMinArcSeparationBlocks = selfIntersectionMinArcSeparationBlocks;
        this.selfIntersectionSampleSpacingBlocks = selfIntersectionSampleSpacingBlocks;
    }

    /**
     * Spacing, in blocks, at which the curve is walked for the cusp check.
     *
     * <p>0.5 blocks is deliberately the same order of magnitude as {@code TrackSection}'s own
     * frame-sampling density (2 samples/block) — fine enough to find a feature narrower than a
     * single block, coarse enough that a several-hundred-block circuit is still a few thousand
     * samples, not millions.</p>
     */
    public double sampleSpacingBlocks() {
        return sampleSpacingBlocks;
    }

    /**
     * Dot product between tangents at consecutive curve samples below which a cusp / tangent
     * reversal is flagged.
     *
     * <p>0.0 means "the tangent turned through 90° or more between two samples {@code
     * sampleSpacingBlocks} apart" — a genuinely violent direction change no legitimate coaster
     * element produces at that resolution. See {@link TrackValidator}'s class javadoc for why
     * this check exists at all despite centripetal Catmull-Rom being proven not to cusp.</p>
     */
    public double cuspTangentDotThreshold() {
        return cuspTangentDotThreshold;
    }

    /**
     * Distance, in blocks, below which two consecutive nodes are flagged as suspiciously close
     * (as opposed to exactly coincident, which is always an {@code ERROR} regardless of this
     * threshold — see {@link TrackValidator}).
     *
     * <p>Admitted guess: 0.4 of a block is smaller than any node spacing a builder would place on
     * purpose, but large enough to catch "meant to delete the old node, placed a new one on top
     * instead" mistakes that are not quite exactly coincident. It was a whole block, until the
     * inversion pieces needed nodes half a block apart — the rail spirals round the riders'
     * hearts closer than that — and every one of their nodes drew a warning.</p>
     */
    public double minNodeSpacingBlocks() {
        return minNodeSpacingBlocks;
    }

    /**
     * Distance, in blocks, above which two consecutive nodes are flagged as suspiciously far
     * apart.
     *
     * <p>Catmull-Rom interpolates the nodes exactly but is otherwise free to do whatever it
     * wants between them; wide spacing means the curve's shape between two widely-spaced nodes
     * is effectively un-authored and can bulge in ways the builder never saw in the editor.
     * 48 blocks is an admitted guess — generous enough not to flag a long, deliberately straight
     * run (which bulges trivially, since Catmull-Rom is exact on collinear points), but tight
     * enough to catch nodes placed carelessly far apart on a curved stretch.</p>
     */
    public double maxNodeSpacingBlocks() {
        return maxNodeSpacingBlocks;
    }

    /**
     * Minimum clearance, in blocks, the track must keep from itself at points far apart in arc
     * length. Closer than this is flagged as a possible self-intersection.
     *
     * <p>Admitted guess: 4 blocks is meant to comfortably fit two passes of track plus
     * supporting structure — not a measured rail gauge or clearance envelope, since neither
     * exists yet at this layer (see {@code docs/design/TRACK_GEOMETRY.md}'s "what is not here
     * yet" — track styles and rail gauge are unmodelled).</p>
     */
    public double selfIntersectionClearanceBlocks() {
        return selfIntersectionClearanceBlocks;
    }

    /**
     * Minimum separation, in arc length blocks, before two points on the curve are even
     * considered for the self-intersection check.
     *
     * <p>This is the guard against the trivial false positive: any two points a fraction of a
     * block apart in arc length are also close in space, on every curve, always — that is what
     * "continuous curve" means, not a defect. Only points that are far apart <em>along the
     * track</em> but close <em>in space</em> indicate the track looping back on itself. 12
     * blocks is an admitted guess, chosen well above the tightest turn radius a ride would
     * sensibly use.</p>
     */
    public double selfIntersectionMinArcSeparationBlocks() {
        return selfIntersectionMinArcSeparationBlocks;
    }

    /**
     * Sampling spacing, in blocks, used to walk the curve for the self-intersection check.
     *
     * <p>The check compares every sampled point against every other (see {@link TrackValidator}
     * for why that O(n²) approach is acceptable here). 1 block keeps the sample count, and so the
     * comparison count, manageable for realistic circuit lengths while still being fine enough
     * to catch a close pass.</p>
     */
    public double selfIntersectionSampleSpacingBlocks() {
        return selfIntersectionSampleSpacingBlocks;
    }

    public ValidationLimits withSampleSpacingBlocks(double newValue) {
        return new ValidationLimits(newValue, cuspTangentDotThreshold, minNodeSpacingBlocks, maxNodeSpacingBlocks, selfIntersectionClearanceBlocks, selfIntersectionMinArcSeparationBlocks, selfIntersectionSampleSpacingBlocks);
    }

    public ValidationLimits withCuspTangentDotThreshold(double newValue) {
        return new ValidationLimits(sampleSpacingBlocks, newValue, minNodeSpacingBlocks, maxNodeSpacingBlocks, selfIntersectionClearanceBlocks, selfIntersectionMinArcSeparationBlocks, selfIntersectionSampleSpacingBlocks);
    }

    public ValidationLimits withMinNodeSpacingBlocks(double newValue) {
        return new ValidationLimits(sampleSpacingBlocks, cuspTangentDotThreshold, newValue, maxNodeSpacingBlocks, selfIntersectionClearanceBlocks, selfIntersectionMinArcSeparationBlocks, selfIntersectionSampleSpacingBlocks);
    }

    public ValidationLimits withMaxNodeSpacingBlocks(double newValue) {
        return new ValidationLimits(sampleSpacingBlocks, cuspTangentDotThreshold, minNodeSpacingBlocks, newValue, selfIntersectionClearanceBlocks, selfIntersectionMinArcSeparationBlocks, selfIntersectionSampleSpacingBlocks);
    }

    public ValidationLimits withSelfIntersectionClearanceBlocks(double newValue) {
        return new ValidationLimits(sampleSpacingBlocks, cuspTangentDotThreshold, minNodeSpacingBlocks, maxNodeSpacingBlocks, newValue, selfIntersectionMinArcSeparationBlocks, selfIntersectionSampleSpacingBlocks);
    }

    public ValidationLimits withSelfIntersectionMinArcSeparationBlocks(double newValue) {
        return new ValidationLimits(sampleSpacingBlocks, cuspTangentDotThreshold, minNodeSpacingBlocks, maxNodeSpacingBlocks, selfIntersectionClearanceBlocks, newValue, selfIntersectionSampleSpacingBlocks);
    }

    public ValidationLimits withSelfIntersectionSampleSpacingBlocks(double newValue) {
        return new ValidationLimits(sampleSpacingBlocks, cuspTangentDotThreshold, minNodeSpacingBlocks, maxNodeSpacingBlocks, selfIntersectionClearanceBlocks, selfIntersectionMinArcSeparationBlocks, newValue);
    }
}
