package com.micatechnologies.minecraft.rcmc.track.validation;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoublePredicate;

/**
 * Analyses a built {@link TrackSection} and reports what, if anything, is wrong with it.
 *
 * <p><b>Philosophy: this warns, it does not forbid.</b> RollerCoaster Tycoon lets a player build
 * a ride that would kill a real rider, and then tells them exactly what they built — snap
 * ratings, excitement, nausea — rather than refusing to place the track. That is far more fun
 * than a hard refusal, and it is the model this validator follows. {@link TrackIssue.Severity#ERROR}
 * is reserved for geometry that would actually break the simulation: coincident nodes, a
 * non-finite number, a tangent that reverses (which none of {@code track.math}'s geometry should
 * ever produce — see {@link #checkCusps}). Every other finding — too much lateral G, too steep a
 * drop, banking that rolls too fast, track passing close to itself — is a
 * {@link TrackIssue.Severity#WARNING}: the section is completely valid to build and ride, it is
 * just going to feel a particular way, and the builder gets to decide whether that way is
 * "thrilling" or "needs another look".</p>
 *
 * <p><b>Curvature is computed locally.</b> None of {@code track.math} exposes the curve's
 * second derivative — {@code CatmullRomSpline} only exposes position and (first-derivative)
 * tangent, and {@code docs/design/PHYSICS.md} explicitly lists curvature as "not yet implemented,
 * needed for G-forces". Rather than add a method to {@code CatmullRomSpline} (which this
 * package must not touch — track network / physics work is landing there in parallel), curvature
 * is estimated here from a finite difference of the tangent with respect to arc length:
 * {@code kappa = |dT/ds|}, radius {@code = 1/kappa}. This is a reasonable, if approximate, way to
 * get curvature out of a curve that only hands out its tangent; a precise analytic second
 * derivative from the same Barry-Goldman recurrence {@code CatmullRomSpline.tangentInSegment}
 * already differentiates would be more accurate and belongs upstream on the spline itself once
 * that class is free to change again.</p>
 *
 * <p>Pure Java, zero Minecraft types, like everything else under {@code track} — see
 * {@code CLAUDE.md}.</p>
 */
public final class TrackValidator {

    /** Below this, a node-to-node distance is treated as an exact duplicate, not just "close". */
    private static final double COINCIDENT_EPSILON = 1.0e-6D;

    /** Upper bound on samples for the continuous sweep, so a pathological length can't run away. */
    private static final int MAX_CONTINUOUS_SAMPLES = 20_000;

    /** Upper bound on samples for the O(n^2) self-intersection sweep — see {@link #checkSelfIntersection}. */
    private static final int MAX_SELF_INTERSECTION_SAMPLES = 4_000;

    private final ValidationLimits limits;

    public TrackValidator() {
        this(ValidationLimits.DEFAULT);
    }

    public TrackValidator(ValidationLimits limits) {
        if (limits == null) {
            throw new IllegalArgumentException("limits must not be null");
        }
        this.limits = limits;
    }

    /** Every issue found in {@code section}, in no particular order. Empty means a clean section. */
    public List<TrackIssue> validate(TrackSection section) {
        if (section == null) {
            throw new IllegalArgumentException("section must not be null");
        }
        List<TrackIssue> issues = new ArrayList<>();

        checkNodeSpacing(section, issues);
        if (section.totalLength() > 0.0D) {
            ContinuousSamples samples = sampleContinuous(section);
            checkCusps(samples, issues);
            checkGrade(samples, issues);
            checkLateralG(samples, issues);
            checkBankRate(samples, issues);
            checkSelfIntersection(section, issues);
        }
        return issues;
    }

    // ---- shared sampling ----------------------------------------------------------------

    /** One pass of the curve, walked at {@link ValidationLimits#sampleSpacingBlocks()}. */
    private static final class ContinuousSamples {
        final double[] s;
        final Vec3[] tangent;
        final double[] bankDegrees;

        ContinuousSamples(double[] s, Vec3[] tangent, double[] bankDegrees) {
            this.s = s;
            this.tangent = tangent;
            this.bankDegrees = bankDegrees;
        }
    }

    /**
     * Walks the whole section once, collecting the tangent and authored bank at each sample.
     * Every check below (grade, curvature/lateral-G, bank rate, cusp) reads from this one pass
     * instead of re-sampling the curve, since {@code tangentAtDistance} is not free — it walks
     * the arc-length table's binary search and evaluates the spline.
     */
    private ContinuousSamples sampleContinuous(TrackSection section) {
        double total = section.totalLength();
        double spacing = Math.max(limits.sampleSpacingBlocks(), 1.0e-6D);
        int count = (int) Math.min(MAX_CONTINUOUS_SAMPLES, Math.ceil(total / spacing) + 1.0D);
        count = Math.max(count, 2);

        double[] s = new double[count];
        Vec3[] tangent = new Vec3[count];
        double[] bank = new double[count];
        for (int i = 0; i < count; i++) {
            // Last sample lands exactly on totalLength rather than overshooting past it, even
            // though that makes the final step shorter than `spacing` — see the endpoint note
            // on checkGrade/checkLateralG for why treating that stub step like any other is fine.
            double distance = (i == count - 1) ? total : Math.min(i * spacing, total);
            s[i] = distance;
            tangent[i] = section.tangentAtDistance(distance);
            bank[i] = Math.toDegrees(section.bankRadiansAt(distance));
        }
        return new ContinuousSamples(s, tangent, bank);
    }

    // ---- excessive curvature / minimum radius --------------------------------------------

    /**
     * Flags points where the curve's radius of curvature is tight enough that, at
     * {@link ValidationLimits#designSpeedBlocksPerSecond()}, the resulting lateral acceleration
     * {@code v^2/r} would exceed {@link ValidationLimits#maxLateralGees()}.
     *
     * <p>Curvature {@code kappa = |dT/ds|} is estimated by a central (forward/backward at the
     * two ends) finite difference of the sampled tangents — see the class javadoc for why this
     * is computed here instead of upstream on {@code CatmullRomSpline}. At a closed circuit's
     * wrap point this slightly under-samples across the seam rather than blending across it,
     * which is a minor approximation acceptable for a coarse rider-comfort screen.</p>
     */
    private void checkLateralG(ContinuousSamples samples, List<TrackIssue> out) {
        int n = samples.s.length;
        double[] lateralG = new double[n];
        double v2 = limits.designSpeedBlocksPerSecond() * limits.designSpeedBlocksPerSecond();
        double g = limits.gravityBlocksPerSecondSquared();

        for (int i = 0; i < n; i++) {
            double kappa = curvatureAt(samples, i);
            double value = g > 0.0D ? (v2 * kappa) / g : 0.0D;
            lateralG[i] = sanitize(value, samples.s[i], "TRACK_NON_FINITE_CURVATURE",
                "Curvature computation produced a non-finite value", out);
        }

        DoublePredicate exceeds = value -> value > limits.maxLateralGees();
        scanRuns(samples.s, lateralG, exceeds, (distance, value) -> {
            double radius = value > 0.0D ? (v2 / (value * g)) : Double.POSITIVE_INFINITY;
            String message = String.format(
                "At %.1f blocks/s this curve implies %.2fg of lateral acceleration (radius ~ %.1f "
                    + "blocks), above the configured limit of %.2fg.",
                limits.designSpeedBlocksPerSecond(), value, radius, limits.maxLateralGees());
            return new TrackIssue(TrackIssue.Severity.WARNING, "TRACK_EXCESSIVE_LATERAL_G", message,
                distance, value);
        }, out);
    }

    /** {@code |dT/ds|} at sample {@code i}, via central difference where a neighbour exists on both sides. */
    private static double curvatureAt(ContinuousSamples samples, int i) {
        int n = samples.s.length;
        int lo = Math.max(0, i - 1);
        int hi = Math.min(n - 1, i + 1);
        if (lo == hi) {
            return 0.0D;
        }
        double ds = samples.s[hi] - samples.s[lo];
        if (ds <= 0.0D) {
            return 0.0D;
        }
        Vec3 dT = samples.tangent[hi].subtract(samples.tangent[lo]);
        return dT.length() / ds;
    }

    // ---- excessive grade ------------------------------------------------------------------

    /**
     * Flags climbs/drops steeper than {@link ValidationLimits#maxGradeDegrees()}.
     *
     * <p>Grade is {@code asin(forward.y)} in degrees — exactly the angle {@code PhysicsIntegrator}
     * already uses to project gravity onto the track ({@code a_gravity = -g * sin(grade)}), so
     * "grade" here means the same thing it means to the physics layer. 90 degrees (a true
     * vertical drop) is legal on a real coaster and is never itself an error here, only a
     * warning if it clears the configured threshold — see {@link ValidationLimits#maxGradeDegrees()}
     * for why the default sits below 90 anyway. A true beyond-vertical element (horizontal
     * direction reversing while still descending, e.g. an overhanging drop) is a distinct
     * element this scalar angle cannot represent and this check does not attempt to detect.</p>
     */
    private void checkGrade(ContinuousSamples samples, List<TrackIssue> out) {
        int n = samples.s.length;
        double[] gradeDegrees = new double[n];
        for (int i = 0; i < n; i++) {
            double y = Math.max(-1.0D, Math.min(1.0D, samples.tangent[i].y));
            gradeDegrees[i] = Math.toDegrees(Math.asin(y));
        }

        DoublePredicate exceeds = value -> Math.abs(value) > limits.maxGradeDegrees();
        scanRuns(samples.s, gradeDegrees, exceeds, (distance, value) -> {
            String message = String.format(
                "Grade reaches %.1f degrees here, steeper than the configured maximum of %.1f "
                    + "degrees (90 degrees, i.e. vertical, is legal on a coaster and not itself flagged).",
                value, limits.maxGradeDegrees());
            return new TrackIssue(TrackIssue.Severity.WARNING, "TRACK_EXCESSIVE_GRADE", message,
                distance, value);
        }, out);
    }

    // ---- excessive bank rate ---------------------------------------------------------------

    /**
     * Flags authored bank rolling faster than {@link ValidationLimits#maxBankRateDegreesPerBlock()}
     * per block of track.
     *
     * <p>Deliberately reads {@code TrackSection.bankRadiansAt}, the <em>authored</em> bank, not
     * the frame's total roll (which also includes the small, linearly-distributed closed-circuit
     * residual correction from {@code TrackSection.rollCorrectionAt}). That residual is spread
     * evenly over the whole circuit specifically to be imperceptible — see
     * {@code docs/design/TRACK_GEOMETRY.md}'s "known limitation" section — so folding it into
     * this check would only ever add noise, never catch a real problem. What this check is
     * actually for is a builder rolling a barrel roll or corkscrew faster than is comfortable,
     * which is entirely about the authored bank curve.</p>
     */
    private void checkBankRate(ContinuousSamples samples, List<TrackIssue> out) {
        int n = samples.s.length;
        double[] bankRate = new double[n];
        for (int i = 0; i < n; i++) {
            int lo = Math.max(0, i - 1);
            int hi = Math.min(n - 1, i + 1);
            double ds = samples.s[hi] - samples.s[lo];
            bankRate[i] = ds > 0.0D ? (samples.bankDegrees[hi] - samples.bankDegrees[lo]) / ds : 0.0D;
        }

        DoublePredicate exceeds = value -> Math.abs(value) > limits.maxBankRateDegreesPerBlock();
        scanRuns(samples.s, bankRate, exceeds, (distance, value) -> {
            String message = String.format(
                "Bank angle changes at %.1f degrees/block here, above the configured maximum of "
                    + "%.1f degrees/block.",
                value, limits.maxBankRateDegreesPerBlock());
            return new TrackIssue(TrackIssue.Severity.WARNING, "TRACK_EXCESSIVE_BANK_RATE", message,
                distance, value);
        }, out);
    }

    // ---- cusp / tangent reversal (backstop) ------------------------------------------------

    /**
     * Flags a tangent that reverses direction between two adjacent samples — i.e. a cusp.
     *
     * <p><b>This should never fire.</b> Centripetal (alpha = 0.5) Catmull-Rom is proven never to
     * produce a cusp or self-intersection within a segment (Yuksel, Schaefer &amp; Keyser,
     * "Parameterization and Applications of Catmull-Rom Curves" — the same result
     * {@code CatmullRomSpline}'s own javadoc cites for choosing centripetal parameterization in
     * the first place). This check exists purely as cheap insurance against a future change to
     * that parameterization — someone tuning alpha, swapping the curve family, or introducing a
     * new interpolation mode — silently reintroducing the exact failure mode centripetal
     * Catmull-Rom was chosen to rule out: a tangent reversal that would make a train
     * instantaneously flip direction, which is a physics detonation, not a rider discomfort.
     * That is why this is the one purely-geometric check classified {@code ERROR} rather than
     * {@code WARNING} — if it ever fires, the simulation genuinely cannot be trusted at that
     * point, not merely "uncomfortable to ride".</p>
     */
    private void checkCusps(ContinuousSamples samples, List<TrackIssue> out) {
        int n = samples.tangent.length;
        if (n < 2) {
            return;
        }
        double[] midpoints = new double[n - 1];
        double[] dot = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            midpoints[i] = (samples.s[i] + samples.s[i + 1]) / 2.0D;
            dot[i] = sanitize(samples.tangent[i].dot(samples.tangent[i + 1]), midpoints[i],
                "TRACK_NON_FINITE_TANGENT", "Tangent dot product was non-finite", out);
        }

        DoublePredicate reversed = value -> value < limits.cuspTangentDotThreshold();
        scanRuns(midpoints, dot, reversed, (distance, value) -> {
            double clamped = Math.max(-1.0D, Math.min(1.0D, value));
            double angleDegrees = Math.toDegrees(Math.acos(clamped));
            String message = String.format(
                "Tangent direction changes by %.1f degrees between two adjacent samples "
                    + "(spacing ~%.2f blocks) — this is a cusp, which centripetal Catmull-Rom "
                    + "should never produce. If this fires, the curve's parameterization changed "
                    + "and the physics can no longer be trusted here.",
                angleDegrees, limits.sampleSpacingBlocks());
            return new TrackIssue(TrackIssue.Severity.ERROR, "TRACK_CUSP", message, distance, angleDegrees);
        }, out);
    }

    // ---- node spacing -----------------------------------------------------------------------

    /**
     * Flags node-to-node spacing that is either degenerate (coincident nodes — an {@code ERROR},
     * since {@code CatmullRomSpline} only avoids dividing by zero on this by flooring the knot
     * spacing, it does not make the segment meaningful) or implausible in either direction:
     * too close is very likely a placement mistake, and too far apart leaves a long stretch of
     * curve effectively un-authored, free to bulge between the two nodes in a way the builder
     * never saw. See {@link ValidationLimits#minNodeSpacingBlocks()} and
     * {@link ValidationLimits#maxNodeSpacingBlocks()} for where those two numbers come from.
     */
    private void checkNodeSpacing(TrackSection section, List<TrackIssue> out) {
        List<TrackNode> nodes = section.nodes();
        int count = nodes.size();
        int pairs = section.isClosed() ? count : count - 1;

        for (int i = 0; i < pairs; i++) {
            TrackNode a = nodes.get(i);
            TrackNode b = nodes.get((i + 1) % count);
            double distance = a.position().distanceTo(b.position());
            double at = section.nodeDistance(i);

            if (!Double.isFinite(distance)) {
                out.add(new TrackIssue(TrackIssue.Severity.ERROR, "TRACK_NON_FINITE_NODE_SPACING",
                    "Distance between consecutive nodes is not finite.", at, distance));
            }
            else if (distance <= COINCIDENT_EPSILON) {
                out.add(new TrackIssue(TrackIssue.Severity.ERROR, "TRACK_NODE_COINCIDENT",
                    "Two consecutive nodes are coincident (or effectively so) — the segment "
                        + "between them is degenerate.", at, distance));
            }
            else if (distance < limits.minNodeSpacingBlocks()) {
                String message = String.format(
                    "Consecutive nodes are only %.2f blocks apart, below the configured minimum "
                        + "of %.2f blocks.", distance, limits.minNodeSpacingBlocks());
                out.add(new TrackIssue(TrackIssue.Severity.WARNING, "TRACK_NODE_TOO_CLOSE", message,
                    at, distance));
            }
            else if (distance > limits.maxNodeSpacingBlocks()) {
                String message = String.format(
                    "Consecutive nodes are %.1f blocks apart, above the configured maximum of "
                        + "%.1f blocks — the curve between them is unconstrained and may bulge "
                        + "unpredictably.", distance, limits.maxNodeSpacingBlocks());
                out.add(new TrackIssue(TrackIssue.Severity.WARNING, "TRACK_NODE_TOO_FAR", message,
                    at, distance));
            }
        }
    }

    // ---- self-intersection / clearance -------------------------------------------------------

    /**
     * Flags points where the track passes within {@link ValidationLimits#selfIntersectionClearanceBlocks()}
     * of itself at a point substantially further away <em>along the track</em> than
     * {@link ValidationLimits#selfIntersectionMinArcSeparationBlocks()} — i.e. the track looping
     * back and nearly touching (or crossing) an earlier or later part of itself.
     *
     * <p>The arc-length guard is the whole point: any two points a fraction of a block apart in
     * arc length are also close in space on every continuous curve, always — that is not a
     * defect, it is what "continuous" means. Only a large arc-length separation combined with a
     * small spatial distance indicates a genuine near-miss or crossing. On a closed circuit, arc
     * separation wraps around the seam ({@code min(|si - sj|, total - |si - sj|)}), so a sample
     * near the start and one near the end of the same lap are correctly treated as adjacent, not
     * as a false-positive self-intersection.</p>
     *
     * <p><b>Complexity: naive O(n^2) over sampled points, and that is a deliberate, acceptable
     * choice here.</b> This runs once when a section is built or edited, not per tick, and
     * realistic circuit lengths (a few hundred to a couple thousand blocks) keep the sample
     * count — and so the comparison count — comfortably fast; {@link #MAX_SELF_INTERSECTION_SAMPLES}
     * exists only to put a hard ceiling on pathological input. A real implementation for very
     * long circuits would bucket samples into a spatial hash keyed by rounded position (cell size
     * ~ the clearance distance), so each sample only compares against the handful of samples in
     * its own cell and the 26 neighbouring cells instead of every other sample — turning this
     * into an amortized O(n) scan. That is not worth the added complexity for a design-time
     * warning check at the lengths this mod's tracks actually reach.</p>
     */
    private void checkSelfIntersection(TrackSection section, List<TrackIssue> out) {
        double total = section.totalLength();
        double spacing = Math.max(limits.selfIntersectionSampleSpacingBlocks(), 1.0e-6D);
        int n = (int) Math.min(MAX_SELF_INTERSECTION_SAMPLES, Math.ceil(total / spacing) + 1.0D);
        n = Math.max(n, 2);
        boolean closed = section.isClosed();

        double[] s = new double[n];
        Vec3[] position = new Vec3[n];
        for (int i = 0; i < n; i++) {
            double distance = (i == n - 1) ? total : Math.min(i * spacing, total);
            // On a closed circuit the sample at `total` is the same physical point as the sample
            // at 0; skip it here so the O(n^2) sweep below doesn't compare a point against its
            // own wrapped duplicate and report a trivial zero-distance "self-intersection".
            if (closed && i == n - 1 && n > 1) {
                s[i] = s[0];
                position[i] = position[0];
                continue;
            }
            s[i] = distance;
            position[i] = section.positionAtDistance(distance);
        }

        double minArcSeparation = limits.selfIntersectionMinArcSeparationBlocks();
        double clearance = limits.selfIntersectionClearanceBlocks();

        double[] bestDistance = new double[n];
        int[] bestPartner = new int[n];
        for (int i = 0; i < n; i++) {
            double best = Double.POSITIVE_INFINITY;
            int bestJ = -1;
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }
                double arcSeparation = Math.abs(s[i] - s[j]);
                if (closed) {
                    arcSeparation = Math.min(arcSeparation, total - arcSeparation);
                }
                if (arcSeparation < minArcSeparation) {
                    continue;
                }
                double spatialDistance = position[i].distanceTo(position[j]);
                if (spatialDistance < best) {
                    best = spatialDistance;
                    bestJ = j;
                }
            }
            bestDistance[i] = best;
            bestPartner[i] = bestJ;
        }

        // Merge contiguous runs of flagged samples, keeping the closest (worst) pair per run —
        // otherwise a single close pass gets reported once per sample instead of once overall.
        int i = 0;
        while (i < n) {
            if (bestPartner[i] >= 0 && bestDistance[i] < clearance) {
                int peak = i;
                int j = i;
                while (j < n && bestPartner[j] >= 0 && bestDistance[j] < clearance) {
                    if (bestDistance[j] < bestDistance[peak]) {
                        peak = j;
                    }
                    j++;
                }
                double distance = s[peak];
                double otherDistance = s[bestPartner[peak]];
                double value = bestDistance[peak];
                String message = String.format(
                    "Track passes within %.2f blocks of itself here and again at s=%.1f blocks "
                        + "(below the configured clearance of %.2f blocks).",
                    value, otherDistance, clearance);
                out.add(new TrackIssue(TrackIssue.Severity.WARNING, "TRACK_SELF_INTERSECTION", message,
                    distance, value));
                i = j;
            }
            else {
                i++;
            }
        }
    }

    // ---- shared run-merging ----------------------------------------------------------------

    @FunctionalInterface
    private interface IssueFactory {
        TrackIssue make(double distance, double value);
    }

    /**
     * Scans a sampled scalar for contiguous runs where {@code exceeds} is true, and emits one
     * issue per run — at the sample with the largest magnitude within that run — rather than one
     * issue per flagged sample. A single steep hill or tight turn spans many samples; reporting
     * each one separately would flood the result with duplicates describing the same feature.
     */
    private static void scanRuns(double[] s, double[] value, DoublePredicate exceeds,
                                  IssueFactory factory, List<TrackIssue> out) {
        int n = value.length;
        int i = 0;
        while (i < n) {
            if (exceeds.test(value[i])) {
                int peak = i;
                int j = i;
                while (j < n && exceeds.test(value[j])) {
                    if (Math.abs(value[j]) > Math.abs(value[peak])) {
                        peak = j;
                    }
                    j++;
                }
                out.add(factory.make(s[peak], value[peak]));
                i = j;
            }
            else {
                i++;
            }
        }
    }

    /**
     * Guards a computed metric against NaN/Infinity before it reaches a comfort check. A
     * non-finite number here means the geometry itself is broken (not merely uncomfortable), so
     * it is reported as an {@code ERROR} once and replaced with 0 so the rest of the sweep can
     * continue without propagating the NaN into every subsequent run-merge comparison.
     */
    private static double sanitize(double value, double distance, String code, String message,
                                    List<TrackIssue> out) {
        if (Double.isFinite(value)) {
            return value;
        }
        out.add(new TrackIssue(TrackIssue.Severity.ERROR, code, message, distance, value));
        return 0.0D;
    }
}
