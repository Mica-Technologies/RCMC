package com.micatechnologies.minecraft.rcmc.track.math;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Centripetal Catmull-Rom spline through a sequence of control points.
 *
 * <p>Catmull-Rom is the right family here because it <em>interpolates</em> its control
 * points: a builder places a track node at a block position and the track goes through that
 * position, which is not true of a B-spline and only awkwardly true of a Bézier with
 * hand-managed handles.</p>
 *
 * <p><b>Why centripetal (alpha = 0.5) rather than uniform.</b> Uniform Catmull-Rom
 * (alpha = 0) forms cusps and self-intersections when control points are unevenly spaced —
 * exactly what happens when a player places a tight corner next to a long straight. A cusp
 * means the tangent reverses, which in coaster terms means a train instantaneously flips
 * direction. Centripetal parameterization is proven never to cusp or self-intersect
 * (Yuksel et al., "Parameterization and Applications of Catmull-Rom Curves"), which turns a
 * whole class of player-triggerable physics explosions into a non-issue.</p>
 *
 * <p><b>Vertical monotonicity.</b> An interpolating spline passes through its control points but
 * is not confined between them, and the classic symptom on a coaster is track sagging into the
 * ground just before a climb: the node before the climb takes a steeply upward tangent from its
 * neighbours, and the segment arriving there dips to accommodate it. Node tangents are therefore
 * clamped on the <b>Y axis only</b> using the Fritsch–Carlson condition from monotone cubic
 * interpolation. Horizontal shaping is untouched — constraining that would turn every corner into
 * a polyline — but the curve can no longer rise above or fall below the two nodes a span runs
 * between. See {@code OvershootCheck}, kept as a safety net.</p>
 *
 * <p>Evaluation is by segment: with {@code n} control points there are {@code n - 3}
 * interior segments, each spanning local parameter {@code t} in {@code [0, 1]}. The global
 * parameter {@code u} in {@code [0, 1]} maps uniformly across segments — note that this is
 * <em>not</em> arc length, so moving at constant {@code du/dt} does not mean moving at
 * constant speed. {@link ArcLengthTable} exists to fix that.</p>
 */
public final class CatmullRomSpline {

    /** Centripetal parameterization exponent. */
    private static final double ALPHA = 0.5D;

    /** Guards knot spacing against coincident control points. */
    private static final double MIN_KNOT_DELTA = 1.0e-9D;

    private final List<Vec3> points;

    /**
     * @param controlPoints at least 4 points. The first and last act as tangent handles for
     *                      the interior segments and are not themselves traversed — callers
     *                      building track from user-placed nodes should duplicate or
     *                      extrapolate the endpoints (see
     *                      {@link #withPhantomEndpoints(List)}).
     */
    public CatmullRomSpline(List<Vec3> controlPoints) {
        if (controlPoints == null || controlPoints.size() < 4) {
            throw new IllegalArgumentException(
                "Catmull-Rom needs at least 4 control points, got "
                    + (controlPoints == null ? 0 : controlPoints.size()));
        }
        this.points = Collections.unmodifiableList(new ArrayList<>(controlPoints));
    }

    /**
     * Builds a spline that passes through <em>every</em> supplied point by synthesising the
     * two phantom endpoints Catmull-Rom needs as tangent handles.
     *
     * <p>The phantoms are reflections of the second and second-to-last points through the
     * respective endpoints, which gives a natural-looking straight-ish lead-in rather than
     * the tangent kink you get from simply duplicating the endpoint.</p>
     *
     * @param throughPoints at least 2 points, all of which the curve will pass through
     */
    public static CatmullRomSpline withPhantomEndpoints(List<Vec3> throughPoints) {
        if (throughPoints == null || throughPoints.size() < 2) {
            throw new IllegalArgumentException(
                "Need at least 2 points, got " + (throughPoints == null ? 0 : throughPoints.size()));
        }
        List<Vec3> padded = new ArrayList<>(throughPoints.size() + 2);
        Vec3 first = throughPoints.get(0);
        Vec3 second = throughPoints.get(1);
        Vec3 last = throughPoints.get(throughPoints.size() - 1);
        Vec3 penultimate = throughPoints.get(throughPoints.size() - 2);

        padded.add(first.scale(2.0D).subtract(second));
        padded.addAll(throughPoints);
        padded.add(last.scale(2.0D).subtract(penultimate));
        return new CatmullRomSpline(padded);
    }

    /**
     * Builds a spline that passes through every supplied point and <em>closes back on itself</em>,
     * for a circuit.
     *
     * <p>Where {@link #withPhantomEndpoints} synthesises fake endpoints, this wraps: the control
     * list becomes {@code [last, p0..pn-1, p0, p1]}. Every node therefore has real neighbours on
     * both sides, so the closing join is tangent-continuous by construction rather than by
     * after-the-fact correction — there is no seam to fix in position or direction.</p>
     *
     * <p>The result has exactly {@code n} segments for {@code n} points (one per node-to-node
     * span, <em>including</em> the closing span), so node {@code i} sits at {@code u = i / n} and
     * {@code u = 1} maps back onto node 0.</p>
     *
     * <p>Note this fixes position and tangent continuity only. Frame <em>roll</em> still needs the
     * parallel-transport residual distributed around the loop — see
     * {@code TrackSection}, which is the layer that knows a circuit is closed.</p>
     *
     * @param loopPoints at least 3 distinct points, in order around the circuit. Do <em>not</em>
     *                   repeat the first point at the end; the wrap is implicit.
     */
    public static CatmullRomSpline closed(List<Vec3> loopPoints) {
        if (loopPoints == null || loopPoints.size() < 3) {
            throw new IllegalArgumentException(
                "A closed loop needs at least 3 points, got " + (loopPoints == null ? 0 : loopPoints.size()));
        }
        int n = loopPoints.size();
        List<Vec3> wrapped = new ArrayList<>(n + 3);
        wrapped.add(loopPoints.get(n - 1));
        wrapped.addAll(loopPoints);
        wrapped.add(loopPoints.get(0));
        wrapped.add(loopPoints.get(1));
        return new CatmullRomSpline(wrapped);
    }

    /** Number of traversable segments. */
    public int segmentCount() {
        return points.size() - 3;
    }

    /**
     * Position at global parameter {@code u}, clamped to {@code [0, 1]}.
     *
     * <p>{@code u} is uniform in segment index, not in arc length.</p>
     */
    public Vec3 positionAt(double u) {
        int segment = segmentFor(u);
        return positionInSegment(segment, localT(u, segment));
    }

    /**
     * Unit tangent (direction of travel) at global parameter {@code u}.
     *
     * <p>Computed analytically from the Hermite basis rather than by finite differences, so it
     * stays exact at segment boundaries where a finite difference would straddle two segments and
     * smear the direction. Both segments meeting at a node derive that node's tangent from the same
     * neighbours, so the curve is C¹ there — including across a closed circuit's seam.</p>
     */
    public Vec3 tangentAt(double u) {
        int segment = segmentFor(u);
        return tangentInSegment(segment, localT(u, segment)).normalize();
    }

    /**
     * The raw (un-normalised) derivative {@code dr/du} at global parameter {@code u}.
     *
     * <p>{@link #tangentAt} normalises, which is what callers almost always want but throws away
     * the magnitude — and the magnitude is exactly what curvature needs. Exposed separately rather
     * than making {@code tangentAt} return an unnormalised vector, because a caller silently
     * receiving a non-unit "tangent" is a much nastier bug than an extra method.</p>
     */
    public Vec3 derivativeAt(double u) {
        int segment = segmentFor(u);
        // Scale from the per-segment local parameter to the global one: t = u*segmentCount - i,
        // so dt/du = segmentCount and dr/du = (dr/dt)*segmentCount. Returning the local derivative
        // here is a trap — curvature differences this against the GLOBAL parameter, and mixing the
        // two silently inflates the result by exactly segmentCount, which looks like a plausible
        // number rather than an obvious error.
        return tangentInSegment(segment, localT(u, segment)).scale(segmentCount());
    }

    /**
     * Curvature {@code kappa} at global parameter {@code u}, in inverse blocks. The radius of the
     * circle that best fits the curve there is {@code 1/kappa}.
     *
     * <p>Uses the standard {@code |r' x r''| / |r'|^3}. The first derivative is analytic (from the
     * same Barry–Goldman recurrence as the tangent); the second is a central difference <em>of
     * that analytic first derivative</em>, which is a deliberate middle path. Differentiating the
     * recurrence twice by hand is possible but the algebra is long and a mistake in it would be
     * silent — plausible values, wrong by a factor. Differencing positions twice, at the other
     * extreme, squares the cancellation error and is visibly noisy. Differencing an exact first
     * derivative once costs one extra evaluation and is accurate to well under a percent, which is
     * verified against a circle's known {@code 1/R} in the tests.</p>
     *
     * <p>Needed by the G-force model (vertical and lateral load both scale with {@code v²·kappa})
     * and by track validation, which previously estimated it locally for want of this method.</p>
     */
    public double curvatureAt(double u) {
        // Step chosen well above the point where cancellation in the difference dominates, and
        // well below the scale on which curvature varies along a segment.
        double h = 1.0e-4D;
        double lo = Math.max(0.0D, u - h);
        double hi = Math.min(1.0D, u + h);
        if (hi - lo < 1.0e-12D) {
            return 0.0D;
        }

        Vec3 first = derivativeAt(u);
        Vec3 second = derivativeAt(hi).subtract(derivativeAt(lo)).scale(1.0D / (hi - lo));

        double speed = first.length();
        if (speed < 1.0e-9D) {
            // A stationary point in the parameterisation has no defined curvature; zero keeps it
            // out of the physics rather than propagating an infinity into a rider's G reading.
            return 0.0D;
        }
        return first.cross(second).length() / (speed * speed * speed);
    }

    private int segmentFor(double u) {
        double clamped = Math.max(0.0D, Math.min(1.0D, u));
        int segment = (int) (clamped * segmentCount());
        return Math.min(segment, segmentCount() - 1);
    }

    private double localT(double u, int segment) {
        double clamped = Math.max(0.0D, Math.min(1.0D, u));
        return clamped * segmentCount() - segment;
    }

    /**
     * Barry-Goldman pyramidal evaluation of segment {@code i} (between control points
     * {@code i+1} and {@code i+2}) at local parameter {@code t} in {@code [0, 1]}.
     *
     * <p>This formulation is used rather than the more familiar basis-matrix form because
     * the basis matrix only exists for the uniform (alpha = 0) case; non-uniform knots
     * require the recurrence.</p>
     */
    /**
     * Evaluates segment {@code i} at local parameter {@code t} in {@code [0, 1]} as a cubic
     * Hermite curve through its two endpoints, using tangents derived from the centripetal knot
     * spacing and then clamped for vertical monotonicity.
     *
     * <p><b>Why Hermite rather than the Barry–Goldman recurrence this used to use.</b> Both
     * describe the same curve for unclamped Catmull-Rom, but the recurrence never forms an explicit
     * per-node tangent — it blends control points directly — so there is nothing to clamp. Writing
     * the segment as a Hermite with named endpoint tangents makes the tangent a value we can bound,
     * which is the whole point.</p>
     */
    private Vec3 positionInSegment(int i, double t) {
        Segment segment = segmentAt(i);
        double u = t;
        double u2 = u * u;
        double u3 = u2 * u;

        double h00 = 2.0D * u3 - 3.0D * u2 + 1.0D;
        double h10 = u3 - 2.0D * u2 + u;
        double h01 = -2.0D * u3 + 3.0D * u2;
        double h11 = u3 - u2;

        return segment.p1.scale(h00)
            .add(segment.m1.scale(h10 * segment.span))
            .add(segment.p2.scale(h01))
            .add(segment.m2.scale(h11 * segment.span));
    }

    /** Derivative of {@link #positionInSegment} with respect to the local parameter. */
    private Vec3 tangentInSegment(int i, double t) {
        Segment segment = segmentAt(i);
        double u = t;
        double u2 = u * u;

        double dh00 = 6.0D * u2 - 6.0D * u;
        double dh10 = 3.0D * u2 - 4.0D * u + 1.0D;
        double dh01 = -6.0D * u2 + 6.0D * u;
        double dh11 = 3.0D * u2 - 2.0D * u;

        return segment.p1.scale(dh00)
            .add(segment.m1.scale(dh10 * segment.span))
            .add(segment.p2.scale(dh01))
            .add(segment.m2.scale(dh11 * segment.span));
    }

    /** One segment's endpoints, endpoint tangents (per unit knot parameter), and knot span. */
    private static final class Segment {
        final Vec3 p1;
        final Vec3 p2;
        final Vec3 m1;
        final Vec3 m2;
        final double span;

        Segment(Vec3 p1, Vec3 p2, Vec3 m1, Vec3 m2, double span) {
            this.p1 = p1;
            this.p2 = p2;
            this.m1 = m1;
            this.m2 = m2;
            this.span = span;
        }
    }

    private Segment segmentAt(int i) {
        Vec3 p0 = points.get(i);
        Vec3 p1 = points.get(i + 1);
        Vec3 p2 = points.get(i + 2);
        Vec3 p3 = points.get(i + 3);

        double d01 = knotDelta(p0, p1);
        double d12 = knotDelta(p1, p2);
        double d23 = knotDelta(p2, p3);

        Vec3 m1 = nodeTangent(p0, p1, p2, d01, d12);
        Vec3 m2 = nodeTangent(p1, p2, p3, d12, d23);

        m1 = clampVerticalTangent(m1, (p1.y - p0.y) / d01, (p2.y - p1.y) / d12);
        m2 = clampVerticalTangent(m2, (p2.y - p1.y) / d12, (p3.y - p2.y) / d23);

        return new Segment(p1, p2, m1, m2, d12);
    }

    /**
     * The non-uniform Catmull-Rom tangent at the middle point: the two adjacent secants, each
     * weighted by the <em>opposite</em> knot interval.
     *
     * <p>Weighting by the opposite interval is what makes this reduce to the familiar
     * {@code (p2 - p0) / 2} when the knots are evenly spaced, while still leaning toward the
     * closer neighbour when they are not — which is the entire reason for centripetal spacing.</p>
     */
    private static Vec3 nodeTangent(Vec3 previous, Vec3 at, Vec3 next, double before, double after) {
        Vec3 secondBefore = at.subtract(previous).scale(1.0D / before);
        Vec3 secondAfter = next.subtract(at).scale(1.0D / after);
        double total = before + after;
        return secondBefore.scale(after / total).add(secondAfter.scale(before / total));
    }

    /**
     * Clamps a tangent's vertical component so the curve cannot overshoot its own endpoints
     * vertically — the Fritsch–Carlson condition from monotone cubic interpolation.
     *
     * <p><b>The problem this solves.</b> An interpolating spline passes through its control points
     * but is not confined between them. A node sitting between a level run and a steep climb gets a
     * sharply upward tangent from its neighbours, and the segment <em>arriving</em> at that node
     * has to finish with that tangent while still passing through both endpoints — so it dips below
     * first. On a coaster that reads as track sagging into the ground before every hill, and it was
     * reported from a real build as the height offset being ignored. It was not: it was being
     * honoured rather too enthusiastically.</p>
     *
     * <p><b>The rule.</b> Where the two adjacent secants disagree in sign the node is a local peak
     * or trough, and a curve through it must be flat there or it will overshoot — so the tangent is
     * zeroed. Where they agree, the tangent is capped at three times the shallower secant, which is
     * the classical bound guaranteeing the cubic stays monotone across the span.</p>
     *
     * <p><b>Vertical only, deliberately.</b> Horizontal overshoot is what makes a curve a curve —
     * constraining it would turn every corner into a polyline. Only the vertical axis has a floor
     * to hit.</p>
     */
    private static Vec3 clampVerticalTangent(Vec3 tangent, double secantBefore, double secantAfter) {
        if (secantBefore * secantAfter <= 0.0D) {
            // A local extremum, or a flat neighbour. Any non-zero slope here overshoots.
            return new Vec3(tangent.x, 0.0D, tangent.z);
        }
        double limit = 3.0D * Math.min(Math.abs(secantBefore), Math.abs(secantAfter));
        if (Math.abs(tangent.y) <= limit) {
            return tangent;
        }
        return new Vec3(tangent.x, Math.signum(tangent.y) * limit, tangent.z);
    }

    /**
     * Knot spacing between two control points: {@code |p1 - p0|^alpha}.
     *
     * <p>Floored at {@link #MIN_KNOT_DELTA} so coincident control points — which players
     * <em>will</em> create by double-placing a node — degrade to a flat segment instead of
     * dividing by zero.</p>
     */
    private static double knotDelta(Vec3 a, Vec3 b) {
        return Math.max(Math.pow(a.distanceTo(b), ALPHA), MIN_KNOT_DELTA);
    }
}
