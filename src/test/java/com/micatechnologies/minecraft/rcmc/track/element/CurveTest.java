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
    @DisplayName("taken at its design speed, a curve is felt only as weight in the seat: never sideways")
    void designSpeedFeelsNoSideLoad() {
        // It was a circular arc, which has its whole curvature from the first block: a train
        // arriving from straight track took nearly 1 g sideways before any bank could lean into it.
        double speed = 20.0D;
        // Straight track either side, at the builder's own node spacing, as a builder would lay it.
        java.util.List<TrackNode> nodes = new java.util.ArrayList<>();
        ElementContext start = new ElementContext(new com.micatechnologies.minecraft.rcmc.track.math.TrackFrame(
            new Vec3(-20.0D, 64.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), Vec3.UP), 0.0D);
        nodes.add(new TrackNode(start.entryFrame.position));
        ElementResult in = new Straight(20.0D).generate(start);
        nodes.addAll(in.nodes);
        ElementResult curve = new Curve(40.0D, 150.0D, TurnDirection.RIGHT, speed, 60.0D)
            .generate(in.asNextContext(ElementContext.DEFAULT_NODE_SPACING));
        nodes.addAll(curve.nodes);
        nodes.addAll(new Straight(20.0D).generate(curve.asNextContext(ElementContext.DEFAULT_NODE_SPACING)).nodes);
        com.micatechnologies.minecraft.rcmc.track.TrackSection section =
            new com.micatechnologies.minecraft.rcmc.track.TrackSection(1, nodes, false, null);
        double worst = 0.0D;
        double at = 0.0D;
        for (double s = 1.0D; s < section.totalLength() - 1.0D; s += 0.5D) {
            double lateral = Math.abs(com.micatechnologies.minecraft.rcmc.physics.Heartline
                .forces(section, s, speed, 0.0D, 9.81D).lateral);
            if (lateral > worst) {
                worst = lateral;
                at = s;
            }
        }
        assertTrue(worst < 0.25D, "sideways load reached " + worst + " g at s=" + at + " of "
            + section.totalLength());
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
        // should be level, not still at the target bank. Level to the rider, that is: the rail of a
        // heartlined curve rises a little as it banks, which twists the transported frame by a
        // fraction of a degree, and the exit bank is whatever takes that back out.
        assertEquals(20.0D, nodes.get(0).bankDegrees(), 3.0D);
        ElementResult result = curve.generate(context);
        Vec3 riderUp = result.exitFrame.withBank(Math.toRadians(result.exitBankDegrees)).up;
        assertEquals(1.0D, riderUp.dot(Vec3.UP), 1e-4, "the rider leaves level: " + riderUp);
        assertEquals(0.0D, nodes.get(nodes.size() - 1).bankDegrees(), 0.5D);
    }

    @Test
    @DisplayName("the curve's own bank formula matches atan(v^2/(r*g)) below the clamp, r as built")
    void bankMatchesBalancedFormula() {
        double speed = 12.0D;
        double gravity = 9.81D;

        Curve curve = new Curve(60.0D, 90.0D, TurnDirection.LEFT, speed, 89.0D, gravity);
        List<TrackNode> nodes = curve.generate(levelContext()).nodes;
        int mid = nodes.size() / 2;
        double midBank = nodes.get(mid).bankDegrees();
        // The radius the middle was actually built at — tighter than the nominal 60, since the curve
        // is fitted to end where a circle of 60 would (see Curve) — from the circle through three
        // consecutive nodes.
        Vec3 a = nodes.get(mid - 1).position();
        Vec3 b = nodes.get(mid).position();
        Vec3 c = nodes.get(mid + 1).position();
        double radius = a.distanceTo(b) * b.distanceTo(c) * c.distanceTo(a)
            / (2.0D * b.subtract(a).cross(c.subtract(a)).length());
        assertTrue(radius < 58.0D, "the middle is tighter than the nominal radius: " + radius);
        double expected = Math.toDegrees(Math.atan((speed * speed) / (radius * gravity)));

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
