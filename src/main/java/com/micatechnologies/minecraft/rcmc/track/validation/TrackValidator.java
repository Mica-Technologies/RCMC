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
 * ever produce — see {@link #checkCusps}). Every other finding — nodes too close or too far apart,
 * track passing close to itself — is a
 * {@link TrackIssue.Severity#WARNING}: the section is completely valid to build and ride, it is
 * just going to feel a particular way, and the builder gets to decide whether that way is
 * "thrilling" or "needs another look".</p>
 *
 * <p><b>No G-force, steepness or roll-rate checks.</b> There were: sideways G from the rail's
 * curvature at one assumed speed, a maximum grade, and a maximum bank change per block. All three
 * judged track without its speed or its riders, and warned on every correct loop and heartline
 * roll — a rail spiralling round the riders' hearts is
 * tightly curved, and a loop goes past vertical by design. {@code rating.RideCheck} judges G from
 * the ride as it actually runs, measured at the rider. This keeps to geometry that is wrong
 * whatever the speed.</p>
 *
 * <p>Pure Java, zero Minecraft types, like everything else under {@code track} — see
 * {@code CLAUDE.md}.</p>
 */
public final class TrackValidator {

    /** Below this, a node-to-node distance is treated as an exact duplicate, not just "close". */
    private static final double COINCIDENT_EPSILON = 1.0e-6D;

    /**
     * Upper bound on samples for the continuous sweep, so a pathological length cannot run away.
     * Past it the spacing widens to cover the whole section. It used to stop sampling at the cap
     * and jump to the end instead, so a 23 km circuit was checked for its first 10 km only, and
     * the jump from there to the start (heading the other way) read as a 180-degree cusp.
     */
    private static final int MAX_CONTINUOUS_SAMPLES = 200_000;

    /** Upper bound on samples for the self-intersection sweep, which widens its spacing past it. */
    private static final int MAX_SELF_INTERSECTION_SAMPLES = 200_000;

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
            // Vertical overshoot is checked per node-to-node span rather than from the continuous
            // samples, because the thing being measured is the curve relative to ITS ENDPOINTS —
            // a property of each span, not of any single point on it.
            issues.addAll(new OvershootCheck().check(section));
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
     * The cusp check reads from this one pass
     * instead of re-sampling the curve, since {@code tangentAtDistance} is not free — it walks
     * the arc-length table's binary search and evaluates the spline.
     */
    private ContinuousSamples sampleContinuous(TrackSection section) {
        double total = section.totalLength();
        double spacing = Math.max(limits.sampleSpacingBlocks(), total / (MAX_CONTINUOUS_SAMPLES - 1));
        spacing = Math.max(spacing, 1.0e-6D);
        int count = (int) Math.min(MAX_CONTINUOUS_SAMPLES, Math.ceil(total / spacing) + 1.0D);
        count = Math.max(count, 2);

        double[] s = new double[count];
        Vec3[] tangent = new Vec3[count];
        double[] bank = new double[count];
        for (int i = 0; i < count; i++) {
            // Last sample lands exactly on totalLength rather than overshooting past it, even
            // though that makes the final step shorter than `spacing` — see the endpoint note
            // below for why treating that stub step like any other is fine.
            double distance = (i == count - 1) ? total : Math.min(i * spacing, total);
            s[i] = distance;
            tangent[i] = section.tangentAtDistance(distance);
            bank[i] = Math.toDegrees(section.bankRadiansAt(distance));
        }
        return new ContinuousSamples(s, tangent, bank);
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
     * <p><b>Complexity: linear.</b> Samples are bucketed in a grid with cells the clearance wide,
     * and each compares only against the 27 cells around it. This was an all-pairs sweep, capped
     * at 4,000 samples on the reasoning that tracks are a few thousand blocks long; a city metro
     * line is twenty kilometres, so it was both slow and checked only its first few.</p>
     */
    private void checkSelfIntersection(TrackSection section, List<TrackIssue> out) {
        double total = section.totalLength();
        double spacing = Math.max(limits.selfIntersectionSampleSpacingBlocks(),
            total / (MAX_SELF_INTERSECTION_SAMPLES - 1));
        spacing = Math.max(spacing, 1.0e-6D);
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

        // Only a partner closer than `clearance` can be reported, and with cells at least that
        // wide every such partner lies in the 27 cells around a sample. So comparing against those
        // alone finds exactly the pairs the all-pairs sweep did, in time linear in the length:
        // all pairs was a quarter-second per commit at 4,000 samples and could not go higher.
        double cell = Math.max(clearance, 1.0e-3D);
        java.util.Map<Long, java.util.List<Integer>> grid = new java.util.HashMap<>();
        for (int i = 0; i < n; i++) {
            grid.computeIfAbsent(cellKey(position[i], cell, 0, 0, 0), k -> new java.util.ArrayList<>()).add(i);
        }

        double[] bestDistance = new double[n];
        int[] bestPartner = new int[n];
        for (int i = 0; i < n; i++) {
            double best = Double.POSITIVE_INFINITY;
            int bestJ = -1;
            java.util.List<Integer> near = new java.util.ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        java.util.List<Integer> members = grid.get(cellKey(position[i], cell, dx, dy, dz));
                        if (members != null) {
                            near.addAll(members);
                        }
                    }
                }
            }
            for (int j : near) {
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

    /** The grid cell of {@code p}, offset by whole cells; 21 bits an axis. */
    private static long cellKey(Vec3 p, double cell, int dx, int dy, int dz) {
        long x = ((long) Math.floor(p.x / cell) + dx) & 0x1FFFFFL;
        long y = ((long) Math.floor(p.y / cell) + dy) & 0x1FFFFFL;
        long z = ((long) Math.floor(p.z / cell) + dz) & 0x1FFFFFL;
        return (x << 42) | (y << 21) | z;
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
