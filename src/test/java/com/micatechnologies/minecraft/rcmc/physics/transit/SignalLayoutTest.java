package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.block.BlockSection;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Placing and removing signals one at a time, the way the transit tool does. */
class SignalLayoutTest {

    private static final double LENGTH = 400.0D;

    /** Whether every point of the section falls in exactly one block — nothing unsignalled, no overlap. */
    private static void assertTiles(List<BlockSection> blocks, int sectionId) {
        for (double d = 0.5D; d < LENGTH; d += 1.0D) {
            int inside = 0;
            for (BlockSection block : blocks) {
                if (block.contains(new TrackRef(sectionId, d))) {
                    inside++;
                }
            }
            assertEquals(1, inside, "s=" + d + " is in " + inside + " blocks");
        }
    }

    @Test
    @DisplayName("the first signal on a track splits it into two blocks that cover all of it")
    void firstSignal() {
        List<BlockSection> blocks = SignalLayout.withSignal(new ArrayList<>(), 1, LENGTH, 150.0D);
        assertNotNull(blocks);
        assertEquals(2, blocks.size());
        assertEquals(Arrays.asList(150.0D), SignalLayout.signals(blocks, 1));
        assertTiles(blocks, 1);
    }

    @Test
    @DisplayName("more signals split the block each lands in, in any order")
    void moreSignals() {
        List<BlockSection> blocks = new ArrayList<>();
        for (double at : new double[] {300.0D, 100.0D, 200.0D}) {
            blocks = SignalLayout.withSignal(blocks, 1, LENGTH, at);
        }
        assertEquals(Arrays.asList(100.0D, 200.0D, 300.0D), SignalLayout.signals(blocks, 1));
        assertTiles(blocks, 1);
        // The line signals built by /rcmc line signals (equal division) are edited the same way.
        assertEquals(4, blocks.size());
    }

    @Test
    @DisplayName("removing signals joins blocks back up, and the last leaves the track unsignalled")
    void removeSignals() {
        List<BlockSection> blocks = SignalLayout.withSignal(new ArrayList<>(), 1, LENGTH, 100.0D);
        blocks = SignalLayout.withSignal(blocks, 1, LENGTH, 250.0D);
        blocks = SignalLayout.withoutSignal(blocks, 1, 103.0D, 6.0D);
        assertEquals(Arrays.asList(250.0D), SignalLayout.signals(blocks, 1));
        assertTiles(blocks, 1);
        assertNull(SignalLayout.withoutSignal(blocks, 1, 150.0D, 6.0D), "nothing within reach of 150");
        blocks = SignalLayout.withoutSignal(blocks, 1, 250.0D, 6.0D);
        assertTrue(blocks.isEmpty(), "no signals, no blocks: " + blocks);
    }

    @Test
    @DisplayName("a signal on top of another, or at the end of the track, is refused")
    void tooClose() {
        List<BlockSection> blocks = SignalLayout.withSignal(new ArrayList<>(), 1, LENGTH, 100.0D);
        assertNull(SignalLayout.withSignal(blocks, 1, LENGTH, 103.0D));
        assertNull(SignalLayout.withSignal(blocks, 1, LENGTH, 2.0D));
        assertNull(SignalLayout.withSignal(blocks, 1, LENGTH, LENGTH - 2.0D));
    }

    @Test
    @DisplayName("editing one track leaves the line's blocks on other tracks alone")
    void otherSectionsUntouched() {
        BlockSection elsewhere = new BlockSection("s2-b1", 2, 0.0D, 80.0D);
        List<BlockSection> blocks = new ArrayList<>(Arrays.asList(elsewhere,
            new BlockSection("s2-b2", 2, 80.0D, 160.0D)));
        blocks = SignalLayout.withSignal(blocks, 1, LENGTH, 200.0D);
        assertTrue(blocks.contains(elsewhere));
        assertEquals(Arrays.asList(80.0D), SignalLayout.signals(blocks, 2));
        assertEquals(Arrays.asList(200.0D), SignalLayout.signals(blocks, 1));
        blocks = SignalLayout.withoutSignal(blocks, 1, 200.0D, 6.0D);
        assertEquals(2, blocks.size(), "section 2 kept its two blocks: " + blocks);
    }
}
