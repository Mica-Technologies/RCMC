package com.micatechnologies.minecraft.rcmc.track.validation;

/**
 * Configurable thresholds for {@link TrackValidator}.
 *
 * <p>Immutable, like the rest of {@code track}: {@link #DEFAULT} is a reasonable starting point
 * and every field has a {@code with*} method that returns a new instance with just that field
 * changed, so a caller can tune one number without having to restate the other eleven.</p>
 *
 * <p><b>Where the defaults come from.</b> Each field's javadoc says so explicitly. Some are
 * grounded in published coaster-design rules of thumb; several are <em>admitted guesses</em> —
 * plausible, round numbers picked because this is a warning system, not a certified engineering
 * tool, and a slightly-wrong default only means a builder gets nudged a little early or a little
 * late. None of them are load-bearing for correctness the way the numbers in {@code physics} are;
 * they only change which {@link TrackIssue}s get reported, never the simulation itself.</p>
 *
 * <p>{@link #gravityBlocksPerSecondSquared} deliberately duplicates {@code RcmcConfig.gravity}
 * rather than reading it: {@code RcmcConfig} pulls in Forge's {@code Configuration} type, and
 * {@code track.validation} — like {@code track.math} and {@code physics} before it — stays free
 * of Minecraft-adjacent types so it can be unit tested on a bare JVM. See {@code CLAUDE.md}.</p>
 */
public final class ValidationLimits {

    /** Sensible defaults; see each field's javadoc for where its number comes from. */
    public static final ValidationLimits DEFAULT = new ValidationLimits(
        9.81D, 20.0D, 1.8D, 80.0D, 10.0D, 0.5D, 0.0D, 1.0D, 48.0D, 4.0D, 12.0D, 1.0D);

    private final double gravityBlocksPerSecondSquared;
    private final double designSpeedBlocksPerSecond;
    private final double maxLateralGees;
    private final double maxGradeDegrees;
    private final double maxBankRateDegreesPerBlock;
    private final double sampleSpacingBlocks;
    private final double cuspTangentDotThreshold;
    private final double minNodeSpacingBlocks;
    private final double maxNodeSpacingBlocks;
    private final double selfIntersectionClearanceBlocks;
    private final double selfIntersectionMinArcSeparationBlocks;
    private final double selfIntersectionSampleSpacingBlocks;

    public ValidationLimits(double gravityBlocksPerSecondSquared, double designSpeedBlocksPerSecond,
                             double maxLateralGees, double maxGradeDegrees, double maxBankRateDegreesPerBlock,
                             double sampleSpacingBlocks, double cuspTangentDotThreshold,
                             double minNodeSpacingBlocks, double maxNodeSpacingBlocks,
                             double selfIntersectionClearanceBlocks,
                             double selfIntersectionMinArcSeparationBlocks,
                             double selfIntersectionSampleSpacingBlocks) {
        this.gravityBlocksPerSecondSquared = gravityBlocksPerSecondSquared;
        this.designSpeedBlocksPerSecond = designSpeedBlocksPerSecond;
        this.maxLateralGees = maxLateralGees;
        this.maxGradeDegrees = maxGradeDegrees;
        this.maxBankRateDegreesPerBlock = maxBankRateDegreesPerBlock;
        this.sampleSpacingBlocks = sampleSpacingBlocks;
        this.cuspTangentDotThreshold = cuspTangentDotThreshold;
        this.minNodeSpacingBlocks = minNodeSpacingBlocks;
        this.maxNodeSpacingBlocks = maxNodeSpacingBlocks;
        this.selfIntersectionClearanceBlocks = selfIntersectionClearanceBlocks;
        this.selfIntersectionMinArcSeparationBlocks = selfIntersectionMinArcSeparationBlocks;
        this.selfIntersectionSampleSpacingBlocks = selfIntersectionSampleSpacingBlocks;
    }

    /**
     * Downward acceleration in blocks/s², used only to convert curvature into a g-force for the
     * lateral-G check. Matches {@code RcmcConfig.gravity}'s default (real-world 9.81, chosen
     * there over a "floatier" tuned value precisely so 1 block reads as 1 metre) — duplicated
     * rather than shared, see the class javadoc.
     */
    public double gravityBlocksPerSecondSquared() {
        return gravityBlocksPerSecondSquared;
    }

    /**
     * Speed, in blocks/s, assumed when turning a measured curvature into a lateral g-force.
     *
     * <p>Track geometry alone has no notion of speed — that only exists once a train and a
     * launch/lift profile are attached. Rather than requiring a whole ride definition just to
     * validate a curve, the caller supplies one reference speed. 20 blocks/s is an admitted
     * guess at a typical mid-circuit cruising speed (roughly 72 km/h at the mod's 1 block = 1
     * metre scale) — fast enough that a tight turn shows up, slow enough not to cry wolf on
     * every helix on a modest family coaster.</p>
     */
    public double designSpeedBlocksPerSecond() {
        return designSpeedBlocksPerSecond;
    }

    /**
     * Lateral g above which a curve at {@link #designSpeedBlocksPerSecond()} is flagged.
     *
     * <p>1.8g is a commonly cited rule of thumb in coaster-design discussion for <em>sustained</em>
     * lateral acceleration comfort (as opposed to a brief peak, which riders tolerate much
     * higher) — picked as a middle-of-the-road default between "family coaster gentle" (~1g) and
     * "aggressive but still comfortable" (~2.5g). Treat it as a starting point to tune per park,
     * not a certified figure.</p>
     */
    public double maxLateralGees() {
        return maxLateralGees;
    }

    /**
     * Grade (angle from horizontal), in degrees, above which a climb or drop is flagged.
     *
     * <p>Vertical (90°) is legal on a real coaster — several production rides run true 90°
     * drops — so this default deliberately sits below that ceiling rather than at it: 80° is an
     * admitted guess at "steep enough to be worth a second look" without flagging the merely
     * tall. Because this check is only ever a {@code WARNING}, going up to and past 90° (which
     * {@code asin} of a unit tangent's y-component cannot itself exceed — a true beyond-vertical
     * element reverses horizontal direction while still descending, a distinct element this
     * check does not attempt to identify) is never treated as broken, only worth flagging.</p>
     */
    public double maxGradeDegrees() {
        return maxGradeDegrees;
    }

    /**
     * Authored bank angle rate, in degrees per block of track, above which a roll is flagged.
     *
     * <p>Admitted guess: 10°/block means a full 90° quarter-roll compressed into 9 blocks reads
     * as excessive, while the same 90° spread over 30+ blocks (a fairly typical corkscrew
     * length on a full-size coaster) does not. Rider discomfort from roll rate is a real,
     * well-known effect in coaster design, but this project has not pinned it to a specific
     * published figure — tune per park.</p>
     */
    public double maxBankRateDegreesPerBlock() {
        return maxBankRateDegreesPerBlock;
    }

    /**
     * Spacing, in blocks, at which the curve is walked for the grade, curvature/lateral-G, bank
     * rate, and cusp checks.
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
     * <p>Admitted guess: 1 block is smaller than any node spacing a builder would place on
     * purpose, but large enough to catch "meant to delete the old node, placed a new one on top
     * instead" mistakes that are not quite exactly coincident.</p>
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
     * blocks is an admitted guess, chosen well above the tightest turn radius this validator
     * itself would already be warning about via the lateral-G check.</p>
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

    public ValidationLimits withGravityBlocksPerSecondSquared(double newValue) {
        return new ValidationLimits(newValue, designSpeedBlocksPerSecond, maxLateralGees, maxGradeDegrees,
            maxBankRateDegreesPerBlock, sampleSpacingBlocks, cuspTangentDotThreshold, minNodeSpacingBlocks,
            maxNodeSpacingBlocks, selfIntersectionClearanceBlocks, selfIntersectionMinArcSeparationBlocks,
            selfIntersectionSampleSpacingBlocks);
    }

    public ValidationLimits withDesignSpeedBlocksPerSecond(double newValue) {
        return new ValidationLimits(gravityBlocksPerSecondSquared, newValue, maxLateralGees, maxGradeDegrees,
            maxBankRateDegreesPerBlock, sampleSpacingBlocks, cuspTangentDotThreshold, minNodeSpacingBlocks,
            maxNodeSpacingBlocks, selfIntersectionClearanceBlocks, selfIntersectionMinArcSeparationBlocks,
            selfIntersectionSampleSpacingBlocks);
    }

    public ValidationLimits withMaxLateralGees(double newValue) {
        return new ValidationLimits(gravityBlocksPerSecondSquared, designSpeedBlocksPerSecond, newValue,
            maxGradeDegrees, maxBankRateDegreesPerBlock, sampleSpacingBlocks, cuspTangentDotThreshold,
            minNodeSpacingBlocks, maxNodeSpacingBlocks, selfIntersectionClearanceBlocks,
            selfIntersectionMinArcSeparationBlocks, selfIntersectionSampleSpacingBlocks);
    }

    public ValidationLimits withMaxGradeDegrees(double newValue) {
        return new ValidationLimits(gravityBlocksPerSecondSquared, designSpeedBlocksPerSecond, maxLateralGees,
            newValue, maxBankRateDegreesPerBlock, sampleSpacingBlocks, cuspTangentDotThreshold,
            minNodeSpacingBlocks, maxNodeSpacingBlocks, selfIntersectionClearanceBlocks,
            selfIntersectionMinArcSeparationBlocks, selfIntersectionSampleSpacingBlocks);
    }

    public ValidationLimits withMaxBankRateDegreesPerBlock(double newValue) {
        return new ValidationLimits(gravityBlocksPerSecondSquared, designSpeedBlocksPerSecond, maxLateralGees,
            maxGradeDegrees, newValue, sampleSpacingBlocks, cuspTangentDotThreshold, minNodeSpacingBlocks,
            maxNodeSpacingBlocks, selfIntersectionClearanceBlocks, selfIntersectionMinArcSeparationBlocks,
            selfIntersectionSampleSpacingBlocks);
    }

    public ValidationLimits withSampleSpacingBlocks(double newValue) {
        return new ValidationLimits(gravityBlocksPerSecondSquared, designSpeedBlocksPerSecond, maxLateralGees,
            maxGradeDegrees, maxBankRateDegreesPerBlock, newValue, cuspTangentDotThreshold, minNodeSpacingBlocks,
            maxNodeSpacingBlocks, selfIntersectionClearanceBlocks, selfIntersectionMinArcSeparationBlocks,
            selfIntersectionSampleSpacingBlocks);
    }

    public ValidationLimits withCuspTangentDotThreshold(double newValue) {
        return new ValidationLimits(gravityBlocksPerSecondSquared, designSpeedBlocksPerSecond, maxLateralGees,
            maxGradeDegrees, maxBankRateDegreesPerBlock, sampleSpacingBlocks, newValue, minNodeSpacingBlocks,
            maxNodeSpacingBlocks, selfIntersectionClearanceBlocks, selfIntersectionMinArcSeparationBlocks,
            selfIntersectionSampleSpacingBlocks);
    }

    public ValidationLimits withMinNodeSpacingBlocks(double newValue) {
        return new ValidationLimits(gravityBlocksPerSecondSquared, designSpeedBlocksPerSecond, maxLateralGees,
            maxGradeDegrees, maxBankRateDegreesPerBlock, sampleSpacingBlocks, cuspTangentDotThreshold, newValue,
            maxNodeSpacingBlocks, selfIntersectionClearanceBlocks, selfIntersectionMinArcSeparationBlocks,
            selfIntersectionSampleSpacingBlocks);
    }

    public ValidationLimits withMaxNodeSpacingBlocks(double newValue) {
        return new ValidationLimits(gravityBlocksPerSecondSquared, designSpeedBlocksPerSecond, maxLateralGees,
            maxGradeDegrees, maxBankRateDegreesPerBlock, sampleSpacingBlocks, cuspTangentDotThreshold,
            minNodeSpacingBlocks, newValue, selfIntersectionClearanceBlocks, selfIntersectionMinArcSeparationBlocks,
            selfIntersectionSampleSpacingBlocks);
    }

    public ValidationLimits withSelfIntersectionClearanceBlocks(double newValue) {
        return new ValidationLimits(gravityBlocksPerSecondSquared, designSpeedBlocksPerSecond, maxLateralGees,
            maxGradeDegrees, maxBankRateDegreesPerBlock, sampleSpacingBlocks, cuspTangentDotThreshold,
            minNodeSpacingBlocks, maxNodeSpacingBlocks, newValue, selfIntersectionMinArcSeparationBlocks,
            selfIntersectionSampleSpacingBlocks);
    }

    public ValidationLimits withSelfIntersectionMinArcSeparationBlocks(double newValue) {
        return new ValidationLimits(gravityBlocksPerSecondSquared, designSpeedBlocksPerSecond, maxLateralGees,
            maxGradeDegrees, maxBankRateDegreesPerBlock, sampleSpacingBlocks, cuspTangentDotThreshold,
            minNodeSpacingBlocks, maxNodeSpacingBlocks, selfIntersectionClearanceBlocks, newValue,
            selfIntersectionSampleSpacingBlocks);
    }

    public ValidationLimits withSelfIntersectionSampleSpacingBlocks(double newValue) {
        return new ValidationLimits(gravityBlocksPerSecondSquared, designSpeedBlocksPerSecond, maxLateralGees,
            maxGradeDegrees, maxBankRateDegreesPerBlock, sampleSpacingBlocks, cuspTangentDotThreshold,
            minNodeSpacingBlocks, maxNodeSpacingBlocks, selfIntersectionClearanceBlocks,
            selfIntersectionMinArcSeparationBlocks, newValue);
    }
}
