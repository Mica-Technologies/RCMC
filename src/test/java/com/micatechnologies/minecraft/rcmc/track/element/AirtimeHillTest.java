package com.micatechnologies.minecraft.rcmc.track.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AirtimeHillTest {

    private static ElementContext levelContext() {
        TrackFrame entry = new TrackFrame(new Vec3(0.0D, 64.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), Vec3.UP);
        return new ElementContext(entry, 0.0D, 2.0D);
    }

    @Test
    @DisplayName("forDesignSpeed derives the free-fall radius for a zero-G (floater) target")
    void floaterRadiusMatchesFreeFallFormula() {
        double speed = 20.0D;
        double gravity = 9.81D;
        double expectedRadius = (speed * speed) / gravity;

        AirtimeHill hill = AirtimeHill.forDesignSpeed(speed, 0.0D, 30.0D, gravity);
        // Reconstruct the radius indirectly: every node sits exactly `radius` from the arc's center,
        // entryPos - up*radius, so measure it back out.
        ElementResult result = hill.generate(levelContext());
        Vec3 center = new Vec3(0.0D, 64.0D, 0.0D).subtract(Vec3.UP.scale(expectedRadius));
        for (TrackNode node : result.nodes) {
            assertEquals(expectedRadius, node.position().distanceTo(center), 1e-6);
        }
    }

    @Test
    @DisplayName("every node of the crest sits exactly radius away from the arc's center")
    void nodesStayOnTheCircle() {
        double radius = 45.0D;
        AirtimeHill hill = new AirtimeHill(radius, 40.0D);
        ElementResult result = hill.generate(levelContext());
        Vec3 center = new Vec3(0.0D, 64.0D, 0.0D).subtract(Vec3.UP.scale(radius));
        for (TrackNode node : result.nodes) {
            assertEquals(radius, node.position().distanceTo(center), 1e-6);
        }
    }

    @Test
    @DisplayName("a crest of arc angle A pitches the exit forward down by exactly A from a level entry")
    void exitPitchMatchesArcAngle() {
        double crestArcDegrees = 25.0D;
        AirtimeHill hill = new AirtimeHill(50.0D, crestArcDegrees);
        ElementResult result = hill.generate(levelContext());
        double exitPitchDegrees = Math.toDegrees(Math.asin(result.exitFrame.forward.y));
        assertEquals(-crestArcDegrees, exitPitchDegrees, 0.05D);
    }

    @Test
    @DisplayName("bank passes through a crest unchanged - it is a vertical-plane maneuver, not a turn")
    void bankPassesThrough() {
        TrackFrame entry = new TrackFrame(new Vec3(0.0D, 64.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), Vec3.UP);
        ElementContext context = new ElementContext(entry, 8.0D, 2.0D);
        for (TrackNode node : new AirtimeHill(40.0D, 20.0D).generate(context).nodes) {
            assertEquals(8.0D, node.bankDegrees(), 1e-9);
        }
    }

    @Test
    @DisplayName("rejects a target G at or above 1, which would not produce airtime")
    void rejectsNonAirtimeGFactor() {
        assertThrows(IllegalArgumentException.class,
            () -> AirtimeHill.forDesignSpeed(20.0D, 1.0D, 30.0D, 9.81D));
        assertThrows(IllegalArgumentException.class,
            () -> AirtimeHill.forDesignSpeed(20.0D, 1.5D, 30.0D, 9.81D));
    }
}
