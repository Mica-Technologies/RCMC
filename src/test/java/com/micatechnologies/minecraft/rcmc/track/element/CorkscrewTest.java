package com.micatechnologies.minecraft.rcmc.track.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CorkscrewTest {

    private static ElementContext levelContext(double entryBank) {
        TrackFrame entry = new TrackFrame(new Vec3(0.0D, 64.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), Vec3.UP);
        return new ElementContext(entry, entryBank, 2.0D);
    }

    @Test
    @DisplayName("a positive-direction corkscrew rolls a full 360 degrees from the entry bank")
    void rollsAFullThreeSixty() {
        Corkscrew corkscrew = new Corkscrew(20.0D, 4.0D, RollDirection.POSITIVE);
        ElementResult result = corkscrew.generate(levelContext(0.0D));
        assertEquals(360.0D, result.exitBankDegrees, 1e-6);

        TrackNode last = result.nodes.get(result.nodes.size() - 1);
        assertEquals(360.0D, last.bankDegrees(), 1e-6);
    }

    @Test
    @DisplayName("a negative-direction corkscrew rolls the other way")
    void rollsTheOtherWay() {
        Corkscrew corkscrew = new Corkscrew(20.0D, 4.0D, RollDirection.NEGATIVE);
        ElementResult result = corkscrew.generate(levelContext(0.0D));
        assertEquals(-360.0D, result.exitBankDegrees, 1e-6);
    }

    @Test
    @DisplayName("roll starts from whatever bank preceded the corkscrew and adds exactly 360")
    void rollStartsFromEntryBank() {
        Corkscrew corkscrew = new Corkscrew(20.0D, 4.0D, RollDirection.POSITIVE);
        ElementResult result = corkscrew.generate(levelContext(25.0D));
        assertEquals(25.0D + 360.0D, result.exitBankDegrees, 1e-6);
    }

    @Test
    @DisplayName("bank sweeps monotonically and matches the smoothstep(t)*360 profile at every node")
    void bankSweepIsMonotonicAndMatchesProfile() {
        Corkscrew corkscrew = new Corkscrew(20.0D, 4.0D, RollDirection.POSITIVE);
        List<TrackNode> nodes = corkscrew.generate(levelContext(0.0D)).nodes;
        int segments = nodes.size();

        double previous = -1.0D;
        for (int i = 0; i < segments; i++) {
            double node = nodes.get(i).bankDegrees();
            assertTrue(node >= previous - 1e-9, "bank went backwards mid-roll");
            previous = node;

            // Node i (0-based) corresponds to t = (i+1)/segments in Corkscrew.generate.
            double t = (double) (i + 1) / segments;
            double expected = 360.0D * ElementGeometry.smoothstep(t);
            assertEquals(expected, node, 1e-6);
        }
    }

    @Test
    @DisplayName("bank crosses the halfway point (180 degrees) somewhere near the middle of the roll")
    void bankCrossesHalfwayNearMiddle() {
        Corkscrew corkscrew = new Corkscrew(20.0D, 4.0D, RollDirection.POSITIVE);
        List<TrackNode> nodes = corkscrew.generate(levelContext(0.0D)).nodes;

        int crossingIndex = -1;
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).bankDegrees() >= 180.0D) {
                crossingIndex = i;
                break;
            }
        }
        assertTrue(crossingIndex >= 0, "roll never reached the halfway point");
        double fraction = (double) crossingIndex / nodes.size();
        assertEquals(0.5D, fraction, 0.2D);
    }

    @Test
    @DisplayName("the exit position lands `length` forward and `lateralOffset` to the side")
    void exitPositionMatchesLengthAndOffset() {
        double length = 24.0D;
        double lateralOffset = 6.0D;
        Corkscrew corkscrew = new Corkscrew(length, lateralOffset, RollDirection.POSITIVE);
        ElementResult result = corkscrew.generate(levelContext(0.0D));

        assertEquals(length, result.exitFrame.position.x, 1e-9);
        assertEquals(lateralOffset, result.exitFrame.position.z, 1e-9);
    }

    @Test
    @DisplayName("forward and up pass through a corkscrew's geometric frame unchanged - the roll lives in bank")
    void frameGeometryUnchanged() {
        Corkscrew corkscrew = new Corkscrew(20.0D, 4.0D, RollDirection.POSITIVE);
        ElementResult result = corkscrew.generate(levelContext(0.0D));
        assertEquals(1.0D, result.exitFrame.forward.dot(new Vec3(1.0D, 0.0D, 0.0D)), 1e-9);
        assertEquals(1.0D, result.exitFrame.up.dot(Vec3.UP), 1e-9);
    }
}
