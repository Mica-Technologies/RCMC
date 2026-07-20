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
     * How far this frame is rolled about its own forward axis relative to level, in degrees.
     * Positive means banked to the right.
     *
     * <p>"Level" is the orientation whose {@code up} is as close to world-up as the direction of
     * travel permits — world-up projected into the plane perpendicular to {@link #forward}. That
     * matches how {@link #fallbackUp} constructs an up vector, so a car on flat unbanked track
     * reads as exactly zero rather than a small residue.</p>
     *
     * <p>Lives here rather than in the rider-camera code that first needed it because it is pure
     * geometry with no Minecraft types, which is what lets it be tested on a bare JVM — and it is
     * the sort of signed-angle calculation where losing the sign is both easy and invisible until
     * a rider banks the wrong way. It is equally what the G-force work (Phase 6.1) will need to
     * compare authored bank against required bank.</p>
     *
     * <p>Returns 0 for perfectly vertical track, where there is no level reference to measure
     * against.</p>
     */
    public double rollDegreesFromLevel() {
        Vec3 levelUp = Vec3.UP.subtract(forward.scale(Vec3.UP.dot(forward)));
        if (levelUp.lengthSquared() < 1.0e-6D) {
            return 0.0D;
        }
        levelUp = levelUp.normalize();

        // atan2 of the rotation's sine and cosine components, rather than acos plus a separate
        // sign recovery. Two reasons, both mattering here:
        //
        //  - acos is ill-conditioned near 0 and 180 degrees: acos(1-e) ~ sqrt(2e), so ordinary
        //    double error in the dot product blows up to ~1e-6 degrees of phantom roll on track
        //    that is dead level. Small, but this feeds a camera every frame, and it is exactly
        //    the range most track sits in.
        //  - atan2 yields the signed angle directly, so there is no second step to get wrong.
        //    Sign recovery bolted onto an unsigned magnitude is the classic way for banking to
        //    come out identical in both directions.
        double sin = levelUp.cross(up).dot(forward);
        double cos = levelUp.dot(up);
        return Math.toDegrees(Math.atan2(sin, cos));
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
