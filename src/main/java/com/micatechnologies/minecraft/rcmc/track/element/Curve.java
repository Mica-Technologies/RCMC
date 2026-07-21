package com.micatechnologies.minecraft.rcmc.track.element;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * A horizontal turn of constant radius, banked to balance lateral acceleration at a design speed.
 *
 * <p><b>The path.</b> The curve sweeps in the plane spanned by the entry frame's {@code forward} and
 * {@code right} — i.e. it turns about the entry {@code up} axis, so it stays "horizontal" relative to
 * whatever the incoming track's own up is, rather than assuming the world is level. Because the rotation
 * axis <em>is</em> the entry {@code up} vector, {@code up} is a fixed point of its own rotation and comes
 * out the far end of the curve completely unchanged — no numerical parallel transport is needed to know
 * the exit frame, only the identity {@code rotate(up, up, θ) == up}. (This is the same planar-curve fact
 * {@code TrackSection}'s roll residual exploits in reverse: a curve confined to one plane accumulates no
 * torsion, so transport of any vector reduces to a rotation about that plane's fixed normal.)</p>
 *
 * <p><b>The bank.</b> {@code atan(v^2 / (r*g))} is the angle at which the rails alone supply the
 * centripetal force — see {@link ElementGeometry#balancedBankDegrees}. It is held through the middle of
 * the curve and eased in/out at the ends via {@link ElementGeometry#easedBankDegrees}, from
 * {@code entryBankDegrees} up to the target and back down to level, rather than snapping to full bank on
 * the first generated node. Snapping would mean the roll rate is discontinuous exactly at the seam with
 * whatever precedes the curve — geometrically valid, but a jolt a rider actually feels.</p>
 *
 * <p><b>Why ease back to level at the exit rather than leaving the curve banked.</b> An element is
 * generated in isolation, with no idea what (if anything) follows it. Leaving the exit banked would make a
 * curve placed as the last element of a coaster end permanently tilted, and would silently depend on
 * whatever comes next to notice and level it out. Self-levelling makes every element independently
 * well-formed; a builder chaining curve after curve pays for it only in a few extra eased nodes at each
 * junction, which is a fair trade against a track that seizes up banked when nothing follows.</p>
 */
public final class Curve implements TrackElement {

    private final double radiusBlocks;
    private final double arcAngleDegrees;
    private final TurnDirection direction;
    private final double designSpeedBlocksPerSecond;
    private final double maxBankDegrees;
    private final double gravity;

    public Curve(double radiusBlocks, double arcAngleDegrees, TurnDirection direction,
                 double designSpeedBlocksPerSecond, double maxBankDegrees, double gravity) {
        ElementGeometry.requirePositive(radiusBlocks, "radiusBlocks");
        ElementGeometry.requirePositive(arcAngleDegrees, "arcAngleDegrees");
        ElementGeometry.requirePositive(designSpeedBlocksPerSecond, "designSpeedBlocksPerSecond");
        ElementGeometry.requirePositive(maxBankDegrees, "maxBankDegrees");
        ElementGeometry.requirePositive(gravity, "gravity");
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }
        this.radiusBlocks = radiusBlocks;
        this.arcAngleDegrees = arcAngleDegrees;
        this.direction = direction;
        this.designSpeedBlocksPerSecond = designSpeedBlocksPerSecond;
        this.maxBankDegrees = maxBankDegrees;
        this.gravity = gravity;
    }

    public Curve(double radiusBlocks, double arcAngleDegrees, TurnDirection direction,
                 double designSpeedBlocksPerSecond, double maxBankDegrees) {
        this(radiusBlocks, arcAngleDegrees, direction, designSpeedBlocksPerSecond, maxBankDegrees,
            ElementGeometry.DEFAULT_GRAVITY);
    }

    @Override
    public String id() {
        return "curve_" + direction.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String displayName() {
        return (direction == TurnDirection.LEFT ? "Left" : "Right") + " Curve";
    }

    @Override
    public ElementResult generate(ElementContext context) {
        double arcAngleRad = Math.toRadians(arcAngleDegrees);
        // turnSign is ONLY which side the arc's centre sits on. It was previously also used for the
        // bank direction on the reasoning that the two share a sign; they do not, and that produced
        // curves banked the wrong way. See the bank calculation below.
        double turnSign = direction == TurnDirection.LEFT ? 1.0D : -1.0D;

        Vec3 entryPos = context.entryFrame.position;
        Vec3 forward = context.entryFrame.forward;
        Vec3 up = context.entryFrame.up;
        Vec3 right = context.entryFrame.right;

        Vec3 v0 = right.scale(radiusBlocks * turnSign);
        Vec3 center = entryPos.subtract(v0);

        int segments = ElementGeometry.segmentCount(radiusBlocks * arcAngleRad, context.nodeSpacing, 4);

        // NEGATED against turnSign, not multiplied by it.
        //
        // These two signs were assumed to agree and do not. turnSign says which side the arc's
        // centre lies on; the bank has to lean the car TOWARD that centre, which in this frame is
        // the opposite sign. Multiplying by turnSign banked every curve outward, so a rider was
        // thrown sideways by the turn AND by the tilt — measured at 1.02g unbanked rising to 1.43g
        // "banked" on a 40-block radius at 20 blocks/s.
        //
        // The convention is pinned by GForces: for a left-hand turn, the bank that cancels lateral
        // load is negative. TrackFrame.right = forward x up, and TrackFrame.withBank rotates up
        // about forward, which together fix the sign — see GForcesTest.perfectBankRemovesLateralLoad,
        // which is the authority here because it asserts cancellation rather than a sign.
        double targetBank = -ElementGeometry.balancedBankDegrees(
            designSpeedBlocksPerSecond, radiusBlocks, gravity, maxBankDegrees) * turnSign;

        List<TrackNode> nodes = new ArrayList<>(segments);
        for (int i = 1; i <= segments; i++) {
            double t = (double) i / segments;
            double theta = arcAngleRad * t;
            Vec3 pos = center.add(ElementGeometry.rotate(v0, up, turnSign * theta));
            double bank = ElementGeometry.easedBankDegrees(t, context.entryBankDegrees, targetBank, 0.0D);
            nodes.add(new TrackNode(pos, bank, null));
        }

        Vec3 exitForward = ElementGeometry.rotate(forward, up, turnSign * arcAngleRad).normalize();
        Vec3 exitPos = nodes.get(nodes.size() - 1).position();
        TrackFrame exitFrame = new TrackFrame(exitPos, exitForward, up);
        return new ElementResult(nodes, exitFrame, 0.0D);
    }
}
