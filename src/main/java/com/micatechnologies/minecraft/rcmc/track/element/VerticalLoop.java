package com.micatechnologies.minecraft.rcmc.track.element;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * A full vertical loop, shaped as a teardrop rather than a circle.
 *
 * <p><b>Why not a circle — the whole point of this class.</b> A circular loop has constant curvature
 * {@code kappa} everywhere, including the instant a car arrives from straight track, where curvature is
 * zero. That is a curvature <em>step</em>: {@code kappa} jumps from 0 to {@code 1/r} in zero arc length,
 * which means lateral/vertical acceleration ({@code v^2 * kappa}) jumps too — instantaneously, in the
 * idealised geometry, and in practice over however few nodes the spline interpolation smears it across.
 * Riders feel this as a violent slam right at the bottom of the loop. Every real steel-loop coaster since
 * the 1970s (the "Anton Schwarzkopf" / clothoid loop, later refined by Werner Stengel) uses a shape whose
 * curvature <em>ramps</em> from zero instead, which is what makes it teardrop-shaped rather than round:
 * wide and gently curved low down where it meets straight track, tight at the top where the curvature
 * peaks.</p>
 *
 * <p><b>The shape used here.</b> Parameterise by arc length {@code s} in {@code [0, L]} and drive
 * curvature as {@code kappa(s) = kappa_max * sin(pi * s / L)}. This is zero at both {@code s=0} and
 * {@code s=L} — no curvature step at either the entry or the exit — and peaks at the midpoint
 * ({@code s=L/2}), which is also the geometric top of the loop by symmetry. Because
 * {@code integral(kappa(s) ds) = kappa_max * L * 2/pi} must equal {@code 2*pi} for the path to turn all
 * the way around and come back to its starting heading, {@code kappa_max = pi^2 / L} is forced — so this
 * class takes the desired radius <em>at the top</em> ({@code 1/kappa_max}) as its one parameter and
 * derives {@code L = pi^2 * topRadiusBlocks} from it, rather than taking {@code L} directly.</p>
 *
 * <p><b>Why the heading angle has a closed form but the position does not.</b> {@code kappa(s)} was chosen
 * specifically because it integrates in closed form:
 * {@code phi(s) = integral(kappa) = pi * (1 - cos(pi*s/L))}, which is exact, not sampled. But turning that
 * heading into a <em>position</em> needs {@code integral(cos(phi(s)), sin(phi(s)))}, and {@code cos}/{@code
 * sin} of a cosine has no elementary antiderivative — this is precisely the reason real Euler
 * spirals/clothoids are historically defined via the (also-numerical) Fresnel integrals rather than a
 * formula. Position is therefore obtained the same way {@code ArcLengthTable} gets distance from a spline:
 * numerically, by composite midpoint-rule integration, cheap and run once at authoring time.</p>
 *
 * <p><b>Why the loop is NOT planar, though its curvature profile is.</b> A planar loop that returns to
 * its entry height after turning through {@code 2*pi} <em>must</em> cross itself. For {@code r=6} the
 * crossing is at {@code (forward 9.0, up 1.1)}, where the track 15% of the way round meets the track 85%
 * of the way round — the entry and exit legs pass through each other. This is not an artefact of the
 * curvature profile: it is forced by the turning, and no choice of {@code kappa(s)} avoids it. Real
 * vertical loops solve it the only way it can be solved, by leaving the plane — the entry and exit legs
 * are offset sideways so they pass beside one another rather than through.</p>
 *
 * <p>So the in-plane path above is displaced along {@code right} by
 * {@code LATERAL_SEPARATION * smoothstep(s/L)}. Smoothstep is not decoration: its derivative is zero at
 * both ends, so the offset contributes nothing to the tangent at the entry or the exit. The element
 * therefore still joins straight track without a kink, and the exit frame is still the closed-form
 * rotation of the entry frame by {@code phi(L) = 2*pi} — that is, the entry frame itself, displaced.
 * Between the ends the path has genuine torsion, which is simply what a real loop has.</p>
 *
 * <p>Orientation elsewhere on the loop is still a rotation of the entry frame by {@code phi(s)} about
 * {@code right}; in particular {@code up} at the top ({@code phi = pi}) comes out as {@code -entryUp} —
 * upside down, as it should be.</p>
 *
 * <p>Bank is held at {@code entryBankDegrees} throughout: banking, as an authored roll about
 * {@code forward}, is not the mechanism that inverts a rider through a loop — parallel transport of
 * {@code up} through the vertical-plane rotation is — so a loop does not "bank" in the sense
 * {@link Curve} does. See {@link Straight} for the same pass-through convention.</p>
 */
public final class VerticalLoop implements TrackElement {

    private static final int SEGMENTS_MIN = 16;

    /**
     * How far the exit leg is displaced sideways from the entry leg, in blocks.
     *
     * <p>Absolute rather than proportional to the radius, because the thing it has to clear is
     * absolute: rendered track is {@code 2 * TIE_HALF_LENGTH_R = 1.4} blocks wide whatever the loop's
     * size. Smoothstep delivers about 88% of this at the crossing point (it is ~6% of the way up at
     * 15% round and ~94% at 85% round), so 2.6 leaves roughly 2.3 blocks between the legs — comfortably
     * more than the 1.4 they occupy, with room for the car bodies that overhang them.</p>
     */
    private static final double LATERAL_SEPARATION = 2.6D;

    private final double topRadiusBlocks;

    public VerticalLoop(double topRadiusBlocks) {
        ElementGeometry.requirePositive(topRadiusBlocks, "topRadiusBlocks");
        this.topRadiusBlocks = topRadiusBlocks;
    }

    @Override
    public String id() {
        return "vertical_loop";
    }

    @Override
    public String displayName() {
        return "Vertical Loop";
    }

    @Override
    public ElementResult generate(ElementContext context) {
        double length = Math.PI * Math.PI * topRadiusBlocks;

        Vec3 entryPos = context.entryFrame.position;
        Vec3 forward = context.entryFrame.forward;
        Vec3 up = context.entryFrame.up;
        Vec3 right = context.entryFrame.right;

        int segments = ElementGeometry.segmentCount(length, context.nodeSpacing, SEGMENTS_MIN);
        double ds = length / segments;

        List<TrackNode> nodes = new ArrayList<>(segments);
        // `planar` walks the in-plane path; each node is that point pushed sideways. Keeping the two
        // separate matters: the offset must not feed back into the integration, or the heading would
        // start chasing its own displacement.
        Vec3 planar = entryPos;
        for (int i = 1; i <= segments; i++) {
            double midS = (i - 0.5D) * ds;
            double midPhi = heading(midS, length);
            Vec3 dir = ElementGeometry.rotate(forward, right, midPhi);
            planar = planar.add(dir.scale(ds));
            Vec3 placed = planar.add(right.scale(lateralOffset(i * ds, length)));
            nodes.add(new TrackNode(placed, context.entryBankDegrees, null));
        }

        double exitPhi = heading(length, length);
        Vec3 exitForward = ElementGeometry.rotate(forward, right, exitPhi).normalize();
        Vec3 exitUp = ElementGeometry.rotate(up, right, exitPhi);
        Vec3 exitPos = nodes.get(nodes.size() - 1).position();

        TrackFrame exitFrame = new TrackFrame(exitPos, exitForward, exitUp);
        return new ElementResult(nodes, exitFrame, context.entryBankDegrees);
    }

    /** Closed-form heading swept by arc length {@code s} into a loop of total length {@code length} — see
     * the class javadoc for the derivation from the sine curvature profile. */
    private static double heading(double s, double length) {
        return Math.PI * (1.0D - Math.cos(Math.PI * s / length));
    }

    /**
     * Sideways displacement at arc length {@code s}, easing from 0 at the entry to
     * {@link #LATERAL_SEPARATION} at the exit.
     *
     * <p>Smoothstep {@code t*t*(3-2t)} has zero derivative at {@code t=0} and {@code t=1}, which is the
     * whole reason it is used here rather than a straight ramp: a linear offset would tilt the tangent
     * sideways at the entry and exit, putting a kink into the join with whatever track adjoins the
     * loop.</p>
     */
    private static double lateralOffset(double s, double length) {
        double t = s / length;
        return LATERAL_SEPARATION * t * t * (3.0D - 2.0D * t);
    }
}
