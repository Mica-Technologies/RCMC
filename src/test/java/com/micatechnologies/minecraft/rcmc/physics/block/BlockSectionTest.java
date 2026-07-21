package com.micatechnologies.minecraft.rcmc.physics.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlockSectionTest {

    @Test
    @DisplayName("contains is inclusive at both ends and exact on section id")
    void containsIsInclusiveAndSectionScoped() {
        BlockSection block = new BlockSection("b1", 1, 50.0D, 100.0D);
        assertTrue(block.contains(new TrackRef(1, 50.0D)), "inclusive at start");
        assertTrue(block.contains(new TrackRef(1, 100.0D)), "inclusive at end");
        assertTrue(block.contains(new TrackRef(1, 75.0D)));
        assertFalse(block.contains(new TrackRef(1, 49.999D)));
        assertFalse(block.contains(new TrackRef(1, 100.001D)));
        assertFalse(block.contains(new TrackRef(2, 75.0D)), "wrong section entirely");
        assertFalse(block.contains(null));
    }

    @Test
    @DisplayName("a zero-length block is legal — a single signalling point")
    void zeroLengthBlockIsLegal() {
        BlockSection block = new BlockSection("point", 1, 42.0D, 42.0D);
        assertTrue(block.contains(new TrackRef(1, 42.0D)));
        assertFalse(block.contains(new TrackRef(1, 42.001D)));
        assertEquals(0.0D, block.length(), 0.0D);
    }

    @Test
    @DisplayName("endDistance below startDistance is rejected")
    void rejectsInvertedSpan() {
        assertThrows(IllegalArgumentException.class, () -> new BlockSection("bad", 1, 100.0D, 50.0D));
    }

    @Test
    @DisplayName("a null or empty id is rejected")
    void rejectsMissingId() {
        assertThrows(IllegalArgumentException.class, () -> new BlockSection(null, 1, 0.0D, 10.0D));
        assertThrows(IllegalArgumentException.class, () -> new BlockSection("", 1, 0.0D, 10.0D));
    }

    @Test
    @DisplayName("accessors report exactly what the constructor was given")
    void accessorsRoundTrip() {
        BlockSection block = new BlockSection("brake-2", 7, 10.0D, 40.0D);
        assertEquals("brake-2", block.id());
        assertEquals(7, block.sectionId());
        assertEquals(10.0D, block.startDistance(), 0.0D);
        assertEquals(40.0D, block.endDistance(), 0.0D);
        assertEquals(30.0D, block.length(), 0.0D);
    }
}
