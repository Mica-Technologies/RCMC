package com.micatechnologies.minecraft.rcmc.track.element;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * A change of grade over a run of track, easing smoothly between the incoming pitch and whatever pitch
 * is needed to hit the requested height change — no grade step at either end.
 *
 * <p><b>Why pitch, not height, is what gets eased.</b> "Grade" throughout the rest of this codebase means
 * {@code forward.y} — see {@code PhysicsIntegrator}'s {@code gravityAlongTrack}, which is literally
 * {@code -g * frame.forward.y}. So the quantity that must not step at the seams is <em>pitch</em>
 * ({@code asin(forward.y)}), not height directly. This element smoothsteps pitch from the entry value to
 * an exit value across the run — {@link ElementGeometry#smoothstep} has zero derivative at both ends, so
 * pitch <em>rate</em> is continuous at the joins, which is what actually removes the kink a rider feels.
 * A height-based ease (smoothstep the {@code y} coordinate directly) was the simpler alternative and was
 * rejected: it forces the *entry* grade to be flat regardless of what the incoming track was actually
 * doing, which only works by accident when a slope happens to start from level track.</p>
 *
 * <p><b>Why the exit pitch needs solving for, not computing directly.</b> Given a pitch profile
 * {@code pitch(t) = lerp_smoothstep(entryPitch, exitPitch, t)}, the height gained over the run is
 * {@code length * integral(sin(pitch(t)), t=0..1)} — no closed form, because {@code sin} of a smoothstep
 * has none. This mirrors {@code ArcLengthTable}'s own reasoning for why arc length is sampled rather than
 * solved in closed form. Since the integral is monotonically increasing in {@code exitPitch} (raising the
 * whole profile can only raise the average height rate, within the physically sane pitch range this class
 * clamps to), it is solved by bisection rather than Newton's method — slower per iteration, but immune to
 * the derivative estimate misbehaving near +/-90 degrees, and 60 iterations of a 128-sample integral is
 * still microseconds, run once at authoring time and never per tick.</p>
 */
public final class Slope implements TrackElement {

    private static final int INTEGRATION_SAMPLES = 128;
    private static final int BISECTION_ITERATIONS = 60;
    private static final double MAX_PITCH_RADIANS = Math.toRadians(85.0D);

    private final double lengthBlocks;
    private final double heightChangeBlocks;

    public Slope(double lengthBlocks, double heightChangeBlocks) {
        ElementGeometry.requirePositive(lengthBlocks, "lengthBlocks");
        ElementGeometry.requireFinite(heightChangeBlocks, "heightChangeBlocks");
        if (Math.abs(heightChangeBlocks) >= lengthBlocks) {
            throw new IllegalArgumentException(
                "heightChangeBlocks (" + heightChangeBlocks + ") cannot reach or exceed lengthBlocks ("
                    + lengthBlocks + ") - the path would have to be exactly vertical or steeper");
        }
        this.lengthBlocks = lengthBlocks;
        this.heightChangeBlocks = heightChangeBlocks;
    }

    @Override
    public String id() {
        return "slope";
    }

    @Override
    public String displayName() {
        // Signed, because "Slope" alone names an uphill and a downhill piece identically — and in a
        // piece palette the name is the only thing the builder sees before placing it.
        if (heightChangeBlocks > 0.0D) {
            return "Slope Up";
        }
        return heightChangeBlocks < 0.0D ? "Slope Down" : "Level";
    }

    @Override
    public ElementResult generate(ElementContext context) {
        Vec3 entryPos = context.entryFrame.position;
        Vec3 forward = context.entryFrame.forward;
        double entryPitch = Math.asin(Math.max(-1.0D, Math.min(1.0D, forward.y)));
        Vec3 horizontalDir = horizontalDirection(forward, context.entryFrame.right);

        double exitPitch = solveExitPitch(entryPitch);

        int segments = ElementGeometry.segmentCount(lengthBlocks, context.nodeSpacing, 4);
        double ds = lengthBlocks / segments;

        List<TrackNode> nodes = new ArrayList<>(segments);
        Vec3 pos = entryPos;
        for (int i = 1; i <= segments; i++) {
            // Midpoint rule: evaluate direction at the segment's midpoint rather than an endpoint for
            // second-order accuracy, the same accuracy-for-a-cheap-extra-evaluation trade RK2 makes over
            // forward Euler.
            double midT = (i - 0.5D) / segments;
            double midPitch = pitchAt(entryPitch, exitPitch, midT);
            Vec3 dir = horizontalDir.scale(Math.cos(midPitch)).add(Vec3.UP.scale(Math.sin(midPitch)));
            pos = pos.add(dir.scale(ds));
            nodes.add(new TrackNode(pos, context.entryBankDegrees, null));
        }

        Vec3 exitForward =
            horizontalDir.scale(Math.cos(exitPitch)).add(Vec3.UP.scale(Math.sin(exitPitch))).normalize();
        // The whole path lives in the fixed vertical plane spanned by horizontalDir and Vec3.UP, so it is
        // torsion-free: parallel transport of ANY vector along it reduces to one rigid rotation, by the
        // total pitch swept, about that plane's fixed normal. No incremental sampling needed (contrast
        // Helix/Corkscrew, which are genuinely non-planar and transport "up" numerically).
        Vec3 planeNormal = horizontalDir.cross(Vec3.UP);
        Vec3 exitUp = ElementGeometry.rotate(context.entryFrame.up, planeNormal, exitPitch - entryPitch);

        TrackFrame exitFrame = new TrackFrame(pos, exitForward, exitUp);
        return new ElementResult(nodes, exitFrame, context.entryBankDegrees);
    }

    private static double pitchAt(double entryPitch, double exitPitch, double t) {
        return entryPitch + (exitPitch - entryPitch) * ElementGeometry.smoothstep(t);
    }

    private double solveExitPitch(double entryPitch) {
        double lo = -MAX_PITCH_RADIANS;
        double hi = MAX_PITCH_RADIANS;
        double heightAtLo = integrateHeight(entryPitch, lo);
        double heightAtHi = integrateHeight(entryPitch, hi);
        // Clamp the target into what is actually reachable within the pitch cap, rather than letting
        // bisection run off searching for an unreachable root.
        double target = Math.max(heightAtLo, Math.min(heightAtHi, heightChangeBlocks));

        for (int i = 0; i < BISECTION_ITERATIONS; i++) {
            double mid = (lo + hi) / 2.0D;
            double h = integrateHeight(entryPitch, mid);
            if (h < target) {
                lo = mid;
            }
            else {
                hi = mid;
            }
        }
        return (lo + hi) / 2.0D;
    }

    /** Composite midpoint-rule integral of {@code sin(pitch(t))} over the run, i.e. total height gained. */
    private double integrateHeight(double entryPitch, double exitPitch) {
        double stepDs = lengthBlocks / INTEGRATION_SAMPLES;
        double acc = 0.0D;
        for (int i = 0; i < INTEGRATION_SAMPLES; i++) {
            double t = (i + 0.5D) / INTEGRATION_SAMPLES;
            acc += Math.sin(pitchAt(entryPitch, exitPitch, t)) * stepDs;
        }
        return acc;
    }

    /** Horizontal (compass) direction of travel, i.e. {@code forward} with its vertical component
     * removed. Degenerates only when the incoming track is already exactly vertical, in which case there
     * is no heading to preserve and {@code right}'s horizontal projection is used instead. */
    private static Vec3 horizontalDirection(Vec3 forward, Vec3 fallbackRight) {
        Vec3 horiz = forward.subtract(Vec3.UP.scale(forward.y));
        if (horiz.lengthSquared() > 1.0e-12D) {
            return horiz.normalize();
        }
        Vec3 rightHoriz = fallbackRight.subtract(Vec3.UP.scale(fallbackRight.y));
        if (rightHoriz.lengthSquared() > 1.0e-12D) {
            return rightHoriz.normalize();
        }
        return new Vec3(1.0D, 0.0D, 0.0D);
    }
}
