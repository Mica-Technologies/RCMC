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
     * <p>Computed analytically from the Barry-Goldman recurrence rather than by finite
     * differences, so it stays exact at segment boundaries where a finite difference would
     * straddle two segments and smear the direction.</p>
     */
    public Vec3 tangentAt(double u) {
        int segment = segmentFor(u);
        return tangentInSegment(segment, localT(u, segment)).normalize();
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
    private Vec3 positionInSegment(int i, double t) {
        Vec3 p0 = points.get(i);
        Vec3 p1 = points.get(i + 1);
        Vec3 p2 = points.get(i + 2);
        Vec3 p3 = points.get(i + 3);

        double t0 = 0.0D;
        double t1 = t0 + knotDelta(p0, p1);
        double t2 = t1 + knotDelta(p1, p2);
        double t3 = t2 + knotDelta(p2, p3);

        double tt = t1 + t * (t2 - t1);

        Vec3 a1 = lerpKnot(p0, p1, t0, t1, tt);
        Vec3 a2 = lerpKnot(p1, p2, t1, t2, tt);
        Vec3 a3 = lerpKnot(p2, p3, t2, t3, tt);
        Vec3 b1 = lerpKnot(a1, a2, t0, t2, tt);
        Vec3 b2 = lerpKnot(a2, a3, t1, t3, tt);
        return lerpKnot(b1, b2, t1, t2, tt);
    }

    /**
     * Derivative of {@link #positionInSegment} with respect to the local parameter, obtained
     * by differentiating the same recurrence via the product rule.
     */
    private Vec3 tangentInSegment(int i, double t) {
        Vec3 p0 = points.get(i);
        Vec3 p1 = points.get(i + 1);
        Vec3 p2 = points.get(i + 2);
        Vec3 p3 = points.get(i + 3);

        double t0 = 0.0D;
        double t1 = t0 + knotDelta(p0, p1);
        double t2 = t1 + knotDelta(p1, p2);
        double t3 = t2 + knotDelta(p2, p3);

        double tt = t1 + t * (t2 - t1);

        Vec3 a1 = lerpKnot(p0, p1, t0, t1, tt);
        Vec3 a2 = lerpKnot(p1, p2, t1, t2, tt);
        Vec3 a3 = lerpKnot(p2, p3, t2, t3, tt);
        Vec3 da1 = p1.subtract(p0).scale(1.0D / (t1 - t0));
        Vec3 da2 = p2.subtract(p1).scale(1.0D / (t2 - t1));
        Vec3 da3 = p3.subtract(p2).scale(1.0D / (t3 - t2));

        Vec3 b1 = lerpKnot(a1, a2, t0, t2, tt);
        Vec3 b2 = lerpKnot(a2, a3, t1, t3, tt);
        Vec3 db1 = derivLerp(a1, a2, da1, da2, t0, t2, tt);
        Vec3 db2 = derivLerp(a2, a3, da2, da3, t1, t3, tt);

        // Chain rule: d/dt = d/dtt * (t2 - t1)
        return derivLerp(b1, b2, db1, db2, t1, t2, tt).scale(t2 - t1);
    }

    private static Vec3 lerpKnot(Vec3 a, Vec3 b, double ta, double tb, double t) {
        double span = tb - ta;
        if (span < MIN_KNOT_DELTA) {
            return a;
        }
        return a.scale((tb - t) / span).add(b.scale((t - ta) / span));
    }

    /** Derivative w.r.t. {@code t} of {@link #lerpKnot}, given the operands' own derivatives. */
    private static Vec3 derivLerp(Vec3 a, Vec3 b, Vec3 da, Vec3 db, double ta, double tb, double t) {
        double span = tb - ta;
        if (span < MIN_KNOT_DELTA) {
            return da;
        }
        Vec3 term = b.subtract(a).scale(1.0D / span);
        Vec3 interpolatedDeriv = da.scale((tb - t) / span).add(db.scale((t - ta) / span));
        return term.add(interpolatedDeriv);
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
