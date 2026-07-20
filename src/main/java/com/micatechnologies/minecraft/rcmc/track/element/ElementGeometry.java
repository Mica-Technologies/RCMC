package com.micatechnologies.minecraft.rcmc.track.element;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;

/**
 * Shared numerical building blocks for the element generators in this package.
 *
 * <p>Everything here deliberately duplicates a technique already used somewhere in
 * {@code track.math} (Rodrigues rotation in {@link com.micatechnologies.minecraft.rcmc.track.math.TrackFrame}
 * and {@link com.micatechnologies.minecraft.rcmc.track.math.ParallelTransportFrames}, smoothstep easing in
 * {@code TrackSection}) rather than reaching into those classes. Those methods are {@code private} and
 * belong to a package this one must not modify while a parallel agent is working in it — see the package
 * javadoc on {@link TrackElement}. Re-deriving ~20 lines of Rodrigues algebra here is a far smaller risk
 * than a merge conflict on shared, load-bearing geometry.</p>
 */
final class ElementGeometry {

    /** Matches {@code RcmcConfig.gravity}'s default. Elements take gravity as a parameter so callers can
     * override it, but need a sane default when the caller does not care. */
    static final double DEFAULT_GRAVITY = 9.81D;

    /** Fraction of an element's angular or linear span spent easing bank in, and again easing it out. */
    static final double BANK_EASE_FRACTION = 0.25D;

    /** Refuses to synthesize more nodes than this from a single element call; guards against a
     * pathological parameter combination (e.g. huge arc angle with a tiny node spacing) silently
     * allocating gigabytes. Real coaster elements need nowhere near this many authored nodes. */
    static final int MAX_NODES = 20_000;

    private ElementGeometry() {
    }

    /**
     * Rodrigues' rotation formula: rotates {@code v} about the unit vector {@code axis} by
     * {@code angleRadians}, right-hand rule.
     *
     * <p>{@link com.micatechnologies.minecraft.rcmc.track.math.TrackFrame#withBank} and
     * {@link com.micatechnologies.minecraft.rcmc.track.math.ParallelTransportFrames}'s private
     * {@code transport} method both already implement this; this is the general form (arbitrary axis,
     * not just {@code forward}) that every element's circular/helical/spiral path needs.</p>
     */
    static Vec3 rotate(Vec3 v, Vec3 axis, double angleRadians) {
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);
        return v.scale(cos)
            .add(axis.cross(v).scale(sin))
            .add(axis.scale(axis.dot(v) * (1.0D - cos)));
    }

    /**
     * Rotation taking {@code fromTangent} to {@code toTangent}, applied to {@code up} — parallel
     * transport of a single step, identical in method to
     * {@code ParallelTransportFrames.transport}. Used only where an element's path has genuine torsion
     * (Helix, Corkscrew) and the exit "up" cannot be read off a fixed rotation axis the way it can for a
     * planar element (see {@link Curve}, {@link Slope}, {@link AirtimeHill}, {@link VerticalLoop}, each of
     * which explains in its own javadoc why its path stays in one plane).
     */
    static Vec3 transportUp(Vec3 up, Vec3 fromTangent, Vec3 toTangent) {
        Vec3 axis = fromTangent.cross(toTangent);
        double sin = axis.length();
        if (sin < 1.0e-9D) {
            return up;
        }
        double cos = fromTangent.dot(toTangent);
        double angle = Math.atan2(sin, cos);
        return rotate(up, axis.scale(1.0D / sin), angle);
    }

    /** Cubic smoothstep, {@code t} clamped to {@code [0, 1]}: zero derivative at both ends. */
    static double smoothstep(double t) {
        double c = Math.max(0.0D, Math.min(1.0D, t));
        return c * c * (3.0D - 2.0D * c);
    }

    /**
     * Bank profile for an element that authors a target bank over its middle and eases to it from
     * {@code entryBank} and back down to {@code exitBank} at the two ends.
     *
     * <p>Snapping straight to {@code targetBank} at the first node would step the roll <em>rate</em> at
     * the seam with whatever precedes the element, exactly the jolt {@code TrackSection}'s own smoothstep
     * bank interpolation is designed to avoid between authored nodes — this reuses the same idea at the
     * element level. Easing over a fixed fraction of the element (rather than, say, a fixed number of
     * blocks) means a short curve still gets a full ease-in/ease-out and a long one still gets a
     * meaningful flat hold in the middle.</p>
     *
     * @param t              fractional position along the element, {@code [0, 1]}
     * @param entryBankDeg   bank carried in from whatever precedes this element
     * @param targetBankDeg  the element's own designed bank, held through the middle
     * @param exitBankDeg    bank to arrive at by the last node
     */
    static double easedBankDegrees(double t, double entryBankDeg, double targetBankDeg, double exitBankDeg) {
        if (t < BANK_EASE_FRACTION) {
            double local = t / BANK_EASE_FRACTION;
            return entryBankDeg + (targetBankDeg - entryBankDeg) * smoothstep(local);
        }
        if (t > 1.0D - BANK_EASE_FRACTION) {
            double local = (t - (1.0D - BANK_EASE_FRACTION)) / BANK_EASE_FRACTION;
            return targetBankDeg + (exitBankDeg - targetBankDeg) * smoothstep(local);
        }
        return targetBankDeg;
    }

    /**
     * Bank angle, in degrees, that balances lateral acceleration for a car taking a horizontal curve of
     * {@code radius} at {@code speed}: {@code atan(v^2 / (r*g))}, clamped to {@code maxBankDegrees}.
     *
     * <p>This is the angle at which the track's normal force alone supplies the centripetal force, i.e.
     * zero <em>lateral</em> G felt by the rider — the textbook "no coffee spills" bank. Real coaster
     * design usually banks a little under this on purpose (some lateral G is part of the fun, and it
     * keeps the track from looking absurdly tilted on a tight, slow curve), which is exactly what the
     * clamp is for.</p>
     */
    static double balancedBankDegrees(double speed, double radius, double gravity, double maxBankDegrees) {
        double ideal = Math.toDegrees(Math.atan((speed * speed) / (radius * gravity)));
        return Math.min(ideal, maxBankDegrees);
    }

    /** Number of node-to-node segments for a path of the given length at the requested spacing, with a
     * floor (every element needs at least enough nodes to shape its bank/curvature profile) and a ceiling
     * (see {@link #MAX_NODES}). */
    static int segmentCount(double pathLength, double nodeSpacing, int minSegments) {
        if (nodeSpacing <= 0.0D) {
            throw new IllegalArgumentException("nodeSpacing must be > 0, got " + nodeSpacing);
        }
        int segments = Math.max(minSegments, (int) Math.ceil(pathLength / nodeSpacing));
        if (segments + 1 > MAX_NODES) {
            throw new IllegalArgumentException(
                "requested parameters would generate " + (segments + 1)
                    + " nodes, over the " + MAX_NODES + " safety limit");
        }
        return segments;
    }

    static void requirePositive(double value, String name) {
        if (!(value > 0.0D) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite and > 0, got " + value);
        }
    }

    static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite, got " + value);
        }
    }
}
