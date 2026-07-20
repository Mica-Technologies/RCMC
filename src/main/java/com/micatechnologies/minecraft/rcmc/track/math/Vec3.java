package com.micatechnologies.minecraft.rcmc.track.math;

/**
 * Immutable double-precision 3-vector.
 *
 * <p>Deliberately not {@code net.minecraft.util.math.Vec3d}. Everything in
 * {@code rcmc.track.math} is plain Java with no Minecraft types so the geometry can be unit
 * tested on a bare JVM — no game instance, no bootstrap, no registries. That constraint is
 * load-bearing: spline evaluation and arc-length parameterization are the two things most
 * likely to be subtly wrong, and they are only cheap to test while they stay pure. Convert
 * to {@code Vec3d} at the boundary, in the entity/render layer.</p>
 *
 * <p>Vec3d is also single-purpose-lossy for our needs in one respect: we accumulate arc
 * length over hundreds of spline samples, where float-ish error compounds visibly into
 * train position drift over a long circuit.</p>
 */
public final class Vec3 {

    public static final Vec3 ZERO = new Vec3(0.0D, 0.0D, 0.0D);
    public static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

    public final double x;
    public final double y;
    public final double z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3 add(Vec3 o) {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    public Vec3 subtract(Vec3 o) {
        return new Vec3(x - o.x, y - o.y, z - o.z);
    }

    public Vec3 scale(double s) {
        return new Vec3(x * s, y * s, z * s);
    }

    public double dot(Vec3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Vec3 cross(Vec3 o) {
        return new Vec3(
            y * o.z - z * o.y,
            z * o.x - x * o.z,
            x * o.y - y * o.x);
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public double distanceTo(Vec3 o) {
        return subtract(o).length();
    }

    /**
     * Unit vector in the same direction, or {@link #ZERO} for a zero-length input.
     *
     * <p>Returning zero rather than NaN matters: a spline with two coincident control points
     * produces a zero tangent, and a NaN propagating from there ends up in an entity's
     * position, which Minecraft turns into a hard-to-trace "entity disappeared" bug rather
     * than an exception.</p>
     */
    public Vec3 normalize() {
        double len = length();
        return len == 0.0D ? ZERO : scale(1.0D / len);
    }

    /** Linear interpolation; {@code t} is not clamped. */
    public Vec3 lerp(Vec3 o, double t) {
        return new Vec3(
            x + (o.x - x) * t,
            y + (o.y - y) * t,
            z + (o.z - z) * t);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Vec3)) {
            return false;
        }
        Vec3 o = (Vec3) obj;
        return Double.compare(x, o.x) == 0 && Double.compare(y, o.y) == 0 && Double.compare(z, o.z) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(x);
        result = 31 * result + Double.hashCode(y);
        result = 31 * result + Double.hashCode(z);
        return result;
    }

    @Override
    public String toString() {
        return "Vec3(" + x + ", " + y + ", " + z + ")";
    }
}
