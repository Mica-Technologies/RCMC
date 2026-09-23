package com.micatechnologies.minecraft.rcmc.track.element;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * A horizontal turn, entered and left through clothoids, banked to balance lateral acceleration at a
 * design speed.
 *
 * <p><b>The path.</b> The curve sweeps in the plane spanned by the entry frame's {@code forward} and
 * {@code right} — it turns about the entry {@code up} axis, so it stays "horizontal" relative to
 * whatever the incoming track's own up is, rather than assuming the world is level. Because the
 * rotation axis <em>is</em> the entry {@code up}, {@code up} comes out the far end unchanged, and the
 * exit frame is known exactly without transporting anything.</p>
 *
 * <p><b>Why not a circular arc.</b> It was one. A circle has its full curvature from its first block,
 * so a train arriving from straight track is hit by the whole turn at once, before any bank can lean
 * into it: at 20 blocks/s on a 40-block radius, nearly 1 g sideways in the first few blocks. Real
 * track eases in through a clothoid (Euler spiral), whose curvature grows in proportion to distance.
 * Here curvature eases from zero to {@code 1/radius} over the first
 * {@link #TRANSITION_FRACTION} of the curve, holds, and ramps back to zero over the last. The curve
 * still ends exactly where the circular arc of {@code radiusBlocks} would — builders fit pieces by
 * where they end — so its middle is a little tighter than {@code radiusBlocks} (about 0.8 of it at
 * 90°), and the bank follows the true curvature there.</p>
 *
 * <p><b>The bank follows the curvature.</b> Each node is banked at
 * {@code atan(v^2 * kappa / g)} for the curvature at that node — see
 * {@link ElementGeometry#balancedBankDegrees} — so bank and curve arrive together and a rider feels
 * the turn only as weight in the seat. Any bank the curve was entered with fades out over the
 * entry transition. The curve always leaves level and straight: an element is generated in isolation,
 * with no idea what follows it, and a track left banked when nothing follows would seize up tilted.</p>
 */
public final class Curve implements TrackElement {

    /** Share of the curve's length spent easing curvature in, and again easing it out. */
    static final double TRANSITION_FRACTION = 0.25D;

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

        double transition = TRANSITION_FRACTION;
        // The transitions swing the curve wider than a circle of the same radius. A builder fits
        // pieces by where they end, so tighten the middle until the curve ends exactly where the
        // circular arc of radiusBlocks would: the shape scales with its radius, so one ratio does it.
        double radius = radiusBlocks * 2.0D * Math.sin(arcAngleRad / 2.0D)
            / chordPerRadius(arcAngleRad, transition);
        double length = radius * arcAngleRad / (1.0D - transition);
        // Counted along the rail, which runs outside the hearts' path once the curve banks.
        int segments = ElementGeometry.segmentCount(length * (1.0D + HeartlineShaper.HEART_HEIGHT / radius),
            context.nodeSpacing, 4);

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
        double bankSign = -turnSign;

        // Heading is the integral of curvature; position the integral of heading. Neither has a
        // closed form through the transitions, so both are stepped finely and sampled at each node.
        // The path is the riders' hearts', HEART_HEIGHT above where a level rail would run: the
        // track banks about them, and HeartlineShaper moves the rail to suit.
        Vec3 lift = up.scale(HeartlineShaper.HEART_HEIGHT);
        List<Vec3> hearts = new ArrayList<>(segments);
        List<Vec3> ups = new ArrayList<>(segments);
        int stepsPerSegment = Math.max(8, (int) Math.ceil(length / segments / 0.25D));
        double ds = length / (segments * stepsPerSegment);
        Vec3 pos = entryPos;
        double s = 0.0D;
        for (int i = 1; i <= segments; i++) {
            for (int k = 0; k < stepsPerSegment; k++) {
                double heading = turnSign * headingAt(s + ds / 2.0D, length, transition, radius);
                pos = pos.add(ElementGeometry.rotate(forward, up, heading).scale(ds));
                s += ds;
            }
            double kappa = curvatureShare(s, length, transition) / radius;
            double bank = bankSign * ElementGeometry.balancedBankDegrees(designSpeedBlocksPerSecond,
                1.0D / Math.max(kappa, 1.0e-9D), gravity, maxBankDegrees);
            double fade = s < length * transition ? 1.0D - ElementGeometry.smoothstep(s / (length * transition)) : 0.0D;
            if (i == segments) {
                // The exit itself, where curvature and bank are back to zero.
                bank = 0.0D;
                fade = 0.0D;
            }
            Vec3 tangent = ElementGeometry.rotate(forward, up, turnSign * headingAt(s, length, transition, radius));
            hearts.add(pos.add(lift));
            ups.add(HeartlineShaper.bankedUp(tangent, up, bank + context.entryBankDegrees * fade));
        }
        Vec3 exitForward = ElementGeometry.rotate(forward, up, turnSign * arcAngleRad).normalize();
        return HeartlineShaper.shape(context, hearts, ups, exitForward);
    }

    /** Straight-line distance from entry to exit of a curve of radius 1, by the same stepping. */
    private static double chordPerRadius(double arcAngleRad, double transition) {
        double length = arcAngleRad / (1.0D - transition);
        int steps = 2000;
        double ds = length / steps;
        double x = 0.0D;
        double y = 0.0D;
        for (int k = 0; k < steps; k++) {
            double heading = headingAt((k + 0.5D) * ds, length, transition, 1.0D);
            x += Math.cos(heading) * ds;
            y += Math.sin(heading) * ds;
        }
        return Math.hypot(x, y);
    }

    /**
     * Curvature at {@code s}, as a share of the full {@code 1/radius}: eased in and out along a
     * smoothstep. Not a straight ramp, the textbook clothoid: the bank follows the curvature, and a
     * straight ramp starts the roll at full rate from a standstill — nothing on the rail, but a
     * sideways throw at the rider's heart (see {@code physics.Heartline}). The smoothstep starts
     * both from rest, and turns through the same angle as the straight ramp.
     */
    private static double curvatureShare(double s, double length, double transition) {
        double ramp = length * transition;
        if (s < ramp) {
            return ElementGeometry.smoothstep(Math.max(0.0D, s / ramp));
        }
        if (s > length - ramp) {
            return ElementGeometry.smoothstep(Math.max(0.0D, (length - s) / ramp));
        }
        return 1.0D;
    }

    /** Heading turned by {@code s}, radians: the integral of {@link #curvatureShare} over radius. */
    private static double headingAt(double s, double length, double transition, double radius) {
        double ramp = length * transition;
        double full = ramp / 2.0D;
        double turned;
        // The smoothstep's integral, x^3 - x^4 / 2, is ramp / 2 over a whole transition.
        if (s < ramp) {
            double x = s / ramp;
            turned = ramp * (x * x * x - x * x * x * x / 2.0D);
        } else if (s <= length - ramp) {
            turned = full + (s - ramp);
        } else {
            double x = (length - s) / ramp;
            turned = (length - ramp) - ramp * (x * x * x - x * x * x * x / 2.0D);
        }
        return turned / radius;
    }
}
