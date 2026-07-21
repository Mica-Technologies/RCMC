package com.micatechnologies.minecraft.rcmc.track.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HelixTest {

    private static ElementContext levelContext() {
        TrackFrame entry = new TrackFrame(new Vec3(0.0D, 64.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), Vec3.UP);
        return new ElementContext(entry, 0.0D, 2.0D);
    }

    @Test
    @DisplayName("one full revolution climbs by exactly the requested height-per-revolution")
    void oneRevolutionClimbsByHeightPerRevolution() {
        Helix helix = new Helix(15.0D, 360.0D, 20.0D, TurnDirection.RIGHT, 15.0D, 45.0D);
        ElementResult result = helix.generate(levelContext());
        assertEquals(64.0D + 20.0D, result.exitFrame.position.y, 1e-6);
    }

    @Test
    @DisplayName("one full revolution returns to the same horizontal position it started from")
    void oneRevolutionReturnsHorizontally() {
        Helix helix = new Helix(15.0D, 360.0D, 20.0D, TurnDirection.LEFT, 15.0D, 45.0D);
        ElementResult result = helix.generate(levelContext());
        assertEquals(0.0D, result.exitFrame.position.x, 1e-6);
        assertEquals(0.0D, result.exitFrame.position.z, 1e-6);
    }

    @Test
    @DisplayName("the helix's constant pitch matches atan(heightPerRevolution / (2*pi*radius))")
    void pitchMatchesRiseOverCircumference() {
        double radius = 12.0D;
        double heightPerRev = 18.0D;
        double k = heightPerRev / (2.0D * Math.PI);
        double expectedPitch = Math.atan(k / radius);

        Helix helix = new Helix(radius, 360.0D, heightPerRev, TurnDirection.RIGHT, 10.0D, 45.0D);
        ElementResult result = helix.generate(levelContext());
        double actualPitch = Math.asin(result.exitFrame.forward.y);

        assertEquals(expectedPitch, actualPitch, 1e-6);
    }

    @Test
    @DisplayName("a flat helix (zero height change per revolution) behaves like a Curve of the same radius")
    void flatHelixMatchesCurveRadius() {
        Helix helix = new Helix(25.0D, 90.0D, 0.0D, TurnDirection.RIGHT, 10.0D, 60.0D);
        ElementResult result = helix.generate(levelContext());
        Vec3 center = new Vec3(0.0D, 64.0D, 0.0D).add(new Vec3(0.0D, 0.0D, 1.0D).scale(25.0D));
        for (TrackNode node : result.nodes) {
            assertEquals(25.0D, node.position().distanceTo(center), 1e-6);
        }
    }

    @Test
    @DisplayName("bank eases in from the entry value and holds near the balanced target through the body")
    void bankEasesAndHolds() {
        TrackFrame entry = new TrackFrame(new Vec3(0.0D, 64.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), Vec3.UP);
        ElementContext context = new ElementContext(entry, 0.0D, 2.0D);
        Helix helix = new Helix(20.0D, 720.0D, 10.0D, TurnDirection.LEFT, 14.0D, 45.0D);
        List<TrackNode> nodes = helix.generate(context).nodes;

        double expectedTarget = ElementGeometry.balancedBankDegrees(14.0D, 20.0D, 9.81D, 45.0D);
        double midBank = nodes.get(nodes.size() / 2).bankDegrees();
        // Negated: a LEFT-hand helix banks negative, the same convention as Curve. See
        // CurveTest.bankSignFollowsDirection.
        assertEquals(-expectedTarget, midBank, 0.5D);

        // First node should already be ramping away from zero, well short of the full target. On
        // magnitude, so the assertion does not quietly depend on the sign the body settles at.
        assertTrue(Math.abs(nodes.get(0).bankDegrees()) < expectedTarget * 0.5D);
    }
}
