package com.micatechnologies.minecraft.rcmc.track.element;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.GForces;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks that a {@link Curve}'s authored bank actually cancels lateral load rather than adding to
 * it.
 *
 * <p>Written to settle a specific claim: that the element's bank sign is inverted, so banking at its
 * own computed angle makes a turn <em>worse</em>. That would be a serious defect — the entire point
 * of the prefab palette is that a builder gets a correctly banked curve without doing the maths, and
 * a curve banked the wrong way is worse than one left level, because the rider is thrown outward by
 * both the turn and the tilt.</p>
 *
 * <p>The test measures the thing that matters rather than inspecting the sign: it builds a curve,
 * evaluates the real {@link GForces} at its midpoint at the design speed, and compares against the
 * same curve left unbanked. Correct banking must move load out of the lateral axis.</p>
 */
class CurveBankSignTest {

    private static final double GRAVITY = 9.81D;
    private static final double RADIUS = 40.0D;
    private static final double DESIGN_SPEED = 20.0D;

    /** Builds a curve of the given direction, optionally stripping its authored bank. */
    private static TrackSection curve(TurnDirection direction, boolean keepBank) {
        TrackFrame start = new TrackFrame(new Vec3(0, 64, 0), new Vec3(1, 0, 0), Vec3.UP);
        ElementResult result = new Curve(RADIUS, 90.0D, direction, DESIGN_SPEED, 55.0D)
            .generate(new ElementContext(start, 0.0D));

        List<TrackNode> nodes = new ArrayList<>();
        nodes.add(new TrackNode(start.position, 0.0D, null));
        for (TrackNode node : result.nodes) {
            nodes.add(keepBank ? node : node.withBank(0.0D));
        }
        return new TrackSection(1, nodes, false, null);
    }

    /** Magnitude of lateral G at the curve's midpoint, at the speed it was banked for. */
    private static double lateralGAtMidpoint(TrackSection section) {
        double at = section.totalLength() * 0.5D;
        TrackFrame frame = section.frameAtDistance(at);

        // Curvature and its direction, from the change in tangent along the track. The same
        // approach the validator and the ride HUD use.
        double step = 0.5D;
        Vec3 before = section.tangentAtDistance(Math.max(0.0D, at - step));
        Vec3 after = section.tangentAtDistance(Math.min(section.totalLength(), at + step));
        Vec3 delta = after.subtract(before);
        double curvature = delta.length() / (2.0D * step);
        Vec3 toCentre = delta.lengthSquared() < 1.0e-12D ? null : delta.normalize();

        GForces g = GForces.at(frame, DESIGN_SPEED, curvature, toCentre, 0.0D, GRAVITY);
        return Math.abs(g.lateral);
    }

    @Test
    @DisplayName("a left curve's own banking reduces lateral load rather than increasing it")
    void leftCurveBankingHelps() {
        double banked = lateralGAtMidpoint(curve(TurnDirection.LEFT, true));
        double unbanked = lateralGAtMidpoint(curve(TurnDirection.LEFT, false));

        assertTrue(banked < unbanked,
            "banking made the turn WORSE: " + String.format("%.3f", unbanked)
                + "g unbanked vs " + String.format("%.3f", banked) + "g banked. The bank sign is "
                + "inverted — riders are thrown outward by both the turn and the tilt.");
    }

    @Test
    @DisplayName("a right curve's own banking reduces lateral load rather than increasing it")
    void rightCurveBankingHelps() {
        // Both directions, because a sign error can easily be right for one and wrong for the other.
        double banked = lateralGAtMidpoint(curve(TurnDirection.RIGHT, true));
        double unbanked = lateralGAtMidpoint(curve(TurnDirection.RIGHT, false));

        assertTrue(banked < unbanked,
            "banking made the turn WORSE: " + String.format("%.3f", unbanked)
                + "g unbanked vs " + String.format("%.3f", banked) + "g banked");
    }

    @Test
    @DisplayName("banking at the design speed cancels most of the lateral load")
    void bankingLargelyCancels() {
        // The stronger claim, and the actual promise the element makes: at the speed it was banked
        // for, the rails alone should carry the turn. Not exact — the bank eases in and out, so the
        // midpoint is close to but not precisely the ideal angle.
        for (TurnDirection direction : TurnDirection.values()) {
            double banked = lateralGAtMidpoint(curve(direction, true));
            double unbanked = lateralGAtMidpoint(curve(direction, false));
            assertTrue(banked < unbanked * 0.5D,
                direction + ": banking should remove most of the lateral load; "
                    + String.format("%.3f", unbanked) + "g -> " + String.format("%.3f", banked) + "g");
        }
    }
}
