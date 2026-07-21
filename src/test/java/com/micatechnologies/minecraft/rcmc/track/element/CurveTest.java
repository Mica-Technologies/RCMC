package com.micatechnologies.minecraft.rcmc.track.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CurveTest {

    private static ElementContext levelContext() {
        TrackFrame entry = new TrackFrame(new Vec3(0.0D, 64.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), Vec3.UP);
        return new ElementContext(entry, 0.0D, 2.0D);
    }

    @Test
    @DisplayName("a 90-degree curve of radius R ends R away perpendicular to its entry direction")
    void ninetyDegreeCurveEndsPerpendicular() {
        double radius = 50.0D;
        Curve curve = new Curve(radius, 90.0D, TurnDirection.RIGHT, 10.0D, 60.0D);
        ElementResult result = curve.generate(levelContext());

        TrackNode last = result.nodes.get(result.nodes.size() - 1);
        Vec3 displacement = last.position().subtract(new Vec3(0.0D, 64.0D, 0.0D));
        Vec3 forward = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 right = new Vec3(0.0D, 0.0D, 1.0D);

        double lateral = displacement.dot(right);
        double along = displacement.dot(forward);
        assertEquals(radius, lateral, 0.05D, "lateral offset should be about R after a 90 degree turn");
        assertEquals(radius, along, 0.05D, "forward offset should also be about R after a 90 degree turn");
    }

    @Test
    @DisplayName("every node of a circular-arc curve sits exactly radius away from the arc's center")
    void nodesStayOnTheCircle() {
        double radius = 30.0D;
        Curve curve = new Curve(radius, 140.0D, TurnDirection.LEFT, 8.0D, 60.0D);
        ElementResult result = curve.generate(levelContext());

        // Reconstruct the center the same way Curve does: entryPos - right*radius*turnSign, turnSign=+1
        // for LEFT.
        Vec3 center = new Vec3(0.0D, 64.0D, 0.0D).subtract(new Vec3(0.0D, 0.0D, 1.0D).scale(radius));
        for (TrackNode node : result.nodes) {
            assertEquals(radius, node.position().distanceTo(center), 1e-6);
        }
    }

    @Test
    @DisplayName("a left curve banks negative, a right curve banks positive, at the same magnitude")
    void bankSignFollowsDirection() {
        Curve left = new Curve(40.0D, 90.0D, TurnDirection.LEFT, 12.0D, 60.0D);
        Curve right = new Curve(40.0D, 90.0D, TurnDirection.RIGHT, 12.0D, 60.0D);

        List<TrackNode> leftNodes = left.generate(levelContext()).nodes;
        List<TrackNode> rightNodes = right.generate(levelContext()).nodes;

        double leftMidBank = leftNodes.get(leftNodes.size() / 2).bankDegrees();
        double rightMidBank = rightNodes.get(rightNodes.size() / 2).bankDegrees();

        // These signs were the other way round, and asserted the bug rather than the requirement:
        // this test was written from the implementation's convention, so it agreed with it. The
        // authority on which sign is correct is CurveBankSignTest, which measures lateral G instead
        // of inspecting a sign, and found banking was making turns ~40% worse in both directions.
        assertTrue(leftMidBank < -5.0D, "left curve should bank negative: " + leftMidBank);
        assertTrue(rightMidBank > 5.0D, "right curve should bank positive: " + rightMidBank);
        assertEquals(leftMidBank, -rightMidBank, 0.5D);
    }

    @Test
    @DisplayName("bank eases from the entry value and back to level, rather than snapping to full bank")
    void bankEasesInAndOut() {
        TrackFrame entry = new TrackFrame(new Vec3(0.0D, 64.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), Vec3.UP);
        ElementContext context = new ElementContext(entry, 20.0D, 2.0D);
        Curve curve = new Curve(40.0D, 90.0D, TurnDirection.LEFT, 12.0D, 60.0D);
        List<TrackNode> nodes = curve.generate(context).nodes;

        // First node should be close to the entry bank (20), not the target bank, and the last node
        // should be close to level (0), not still at the target bank.
        assertEquals(20.0D, nodes.get(0).bankDegrees(), 3.0D);
        assertEquals(0.0D, nodes.get(nodes.size() - 1).bankDegrees(), 1e-6);
    }

    @Test
    @DisplayName("the curve's own bank formula matches atan(v^2/(r*g)) below the clamp")
    void bankMatchesBalancedFormula() {
        double speed = 12.0D;
        double radius = 60.0D;
        double gravity = 9.81D;
        double expected = Math.toDegrees(Math.atan((speed * speed) / (radius * gravity)));

        Curve curve = new Curve(radius, 90.0D, TurnDirection.LEFT, speed, 89.0D, gravity);
        List<TrackNode> nodes = curve.generate(levelContext()).nodes;
        double midBank = nodes.get(nodes.size() / 2).bankDegrees();

        // Negative for a LEFT turn — see bankSignFollowsDirection. The magnitude is the formula's.
        assertEquals(-expected, midBank, 0.1D);
    }

    @Test
    @DisplayName("turning left rotates the exit heading toward the left (positive Z here)")
    void exitHeadingRotatesCorrectDirection() {
        Curve left = new Curve(40.0D, 90.0D, TurnDirection.LEFT, 10.0D, 60.0D);
        ElementResult result = left.generate(levelContext());
        // Left turn from forward=(1,0,0) with up=(0,1,0), right=(0,0,1): the far side (inside of the
        // turn) is -Z, so after 90 degrees left the heading should now point toward -Z.
        assertEquals(-1.0D, result.exitFrame.forward.z, 0.05D);
        assertEquals(0.0D, result.exitFrame.forward.x, 0.05D);
    }
}
