package com.micatechnologies.minecraft.rcmc.track.math;

/**
 * An orthonormal frame at a point on the track: where the rails are, which way is forward,
 * and which way is "up" for a car sitting on them.
 *
 * <p>This is the handoff format between the geometry layer and everything downstream —
 * the car renderer orients its model from it, the rider camera derives yaw/pitch/roll from
 * it, and the physics layer projects gravity onto {@link #forward} to get the along-track
 * acceleration.</p>
 *
 * <p><b>Why not a Frenet frame.</b> The textbook Frenet-Serret frame derives "up" from the
 * curve's second derivative (the normal). That has two fatal properties for a coaster: the
 * normal is undefined on any straight section (zero curvature — so the frame is undefined
 * exactly where track is most common), and it flips 180° through an inflection point, which
 * would snap a train upside down mid-transition. The fix is a <em>parallel transport</em>
 * frame: carry the previous frame forward along the curve, rotating it by the minimum amount
 * needed to stay perpendicular to the new tangent. It is defined everywhere, never flips, and
 * accumulates no twist. See {@link ParallelTransportFrames}.</p>
 *
 * <p>Track banking is then applied <em>on top of</em> the transported frame as an explicit
 * roll about {@link #forward}, which is what makes bank angle an authored, inspectable
 * property of the track rather than an emergent accident of its curvature.</p>
 */
public final class TrackFrame {

    /** Point on the track centreline. */
    public final Vec3 position;

    /** Unit vector along the direction of increasing distance. */
    public final Vec3 forward;

    /** Unit vector out of the top of the car; perpendicular to {@link #forward}. */
    public final Vec3 up;

    /** Unit vector out of the car's right side; equals {@code forward × up}. */
    public final Vec3 right;

    public TrackFrame(Vec3 position, Vec3 forward, Vec3 up) {
        this.position = position;
        this.forward = forward.normalize();
        // Re-orthogonalise `up` against `forward` rather than trusting the caller: transported
        // frames drift out of orthogonality by accumulated floating-point error over a long
        // circuit, and a non-orthogonal frame shears the rendered car model.
        Vec3 orthoUp = up.subtract(this.forward.scale(up.dot(this.forward))).normalize();
        this.up = orthoUp.lengthSquared() == 0.0D ? fallbackUp(this.forward) : orthoUp;
        this.right = this.forward.cross(this.up).normalize();
    }

    /**
     * Copy of this frame rolled about {@link #forward} by {@code radians} (positive = banking
     * to the right, i.e. the outside of a left-hand curve rises).
     */
    public TrackFrame withBank(double radians) {
        if (radians == 0.0D) {
            return this;
        }
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        // Rodrigues rotation of `up` about the unit axis `forward`. The (axis · up) term of
        // the general formula is zero because the frame is orthonormal, so it drops out.
        Vec3 rotatedUp = up.scale(cos).add(forward.cross(up).scale(sin));
        return new TrackFrame(position, forward, rotatedUp);
    }

    /**
     * Any unit vector perpendicular to {@code forward}, used when {@code up} degenerates.
     * Picks world-up unless the track is vertical, in which case it picks world-north.
     */
    private static Vec3 fallbackUp(Vec3 forward) {
        Vec3 candidate = Math.abs(forward.y) > 0.999D ? new Vec3(0.0D, 0.0D, 1.0D) : Vec3.UP;
        return candidate.subtract(forward.scale(candidate.dot(forward))).normalize();
    }

    @Override
    public String toString() {
        return "TrackFrame{pos=" + position + ", fwd=" + forward + ", up=" + up + '}';
    }
}
