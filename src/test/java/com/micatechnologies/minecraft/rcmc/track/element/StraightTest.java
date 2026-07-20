package com.micatechnologies.minecraft.rcmc.track.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StraightTest {

    private static ElementContext levelContext(double entryBank) {
        TrackFrame entry = new TrackFrame(new Vec3(0.0D, 64.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), Vec3.UP);
        return new ElementContext(entry, entryBank, 4.0D);
    }

    @Test
    @DisplayName("a straight of length L ends exactly L along the entry forward direction")
    void endsAtCorrectDistance() {
        ElementResult result = new Straight(40.0D).generate(levelContext(0.0D));

        TrackNode last = result.nodes.get(result.nodes.size() - 1);
        assertEquals(40.0D, last.position().x, 1e-9);
        assertEquals(64.0D, last.position().y, 1e-9);
        assertEquals(0.0D, last.position().z, 1e-9);

        assertEquals(40.0D, result.exitFrame.position.x, 1e-9);
    }

    @Test
    @DisplayName("banking is zero on straights when the entry bank is zero")
    void bankingIsZero() {
        ElementResult result = new Straight(20.0D).generate(levelContext(0.0D));
        for (TrackNode node : result.nodes) {
            assertEquals(0.0D, node.bankDegrees(), 1e-9);
        }
        assertEquals(0.0D, result.exitBankDegrees, 1e-9);
    }

    @Test
    @DisplayName("a straight carries whatever entry bank it is given through unchanged")
    void carriesNonZeroBankThrough() {
        ElementResult result = new Straight(20.0D).generate(levelContext(15.0D));
        for (TrackNode node : result.nodes) {
            assertEquals(15.0D, node.bankDegrees(), 1e-9);
        }
        assertEquals(15.0D, result.exitBankDegrees, 1e-9);
    }

    @Test
    @DisplayName("direction and up pass through a straight unchanged, per ParallelTransportFrames' own claim")
    void directionAndUpUnchanged() {
        ElementResult result = new Straight(20.0D).generate(levelContext(0.0D));
        assertEquals(1.0D, result.exitFrame.forward.dot(new Vec3(1.0D, 0.0D, 0.0D)), 1e-9);
        assertEquals(1.0D, result.exitFrame.up.dot(Vec3.UP), 1e-9);
    }

    @Test
    @DisplayName("node count scales with node spacing")
    void nodeCountScalesWithSpacing() {
        TrackFrame entry = new TrackFrame(new Vec3(0.0D, 64.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), Vec3.UP);
        ElementResult coarse = new Straight(40.0D).generate(new ElementContext(entry, 0.0D, 10.0D));
        ElementResult fine = new Straight(40.0D).generate(new ElementContext(entry, 0.0D, 2.0D));
        assertTrue(fine.nodes.size() > coarse.nodes.size());
    }

    @Test
    @DisplayName("rejects a non-positive length")
    void rejectsNonPositiveLength() {
        assertThrows(IllegalArgumentException.class, () -> new Straight(0.0D));
        assertThrows(IllegalArgumentException.class, () -> new Straight(-5.0D));
    }
}
