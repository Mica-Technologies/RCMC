package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.micatechnologies.minecraft.rcmc.physics.block.BlockSection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A linked signal head shows what a driver approaching it needs to know: red when the block it
 * protects holds a train, yellow when the block after that does, green otherwise. And it reads
 * the blocks in the direction the train it faces is travelling, not in list order.
 */
class SignalHeadsTest {

    /** One 300-block section divided into three blocks, as /rcmc line signals does. */
    private static final List<BlockSection> LINE = Arrays.asList(
        new BlockSection("s1-b1", 1, 0, 100),
        new BlockSection("s1-b2", 1, 100, 200),
        new BlockSection("s1-b3", 1, 200, 300));

    private static IntPredicate occupied(Integer... blocks) {
        Set<Integer> set = new HashSet<>(Arrays.asList(blocks));
        return set::contains;
    }

    private static SignalAspect aspectAt(double distance, int direction, boolean closed,
                                         IntPredicate occupied) {
        int index = SignalHeads.protectedBlock(LINE, 1, distance, direction);
        return SignalHeads.aspect(LINE, occupied, index, direction, 300, closed);
    }

    @Test
    @DisplayName("red when the protected block is occupied, whatever lies beyond")
    void redWhenProtectedOccupied() {
        assertEquals(SignalAspect.RED, aspectAt(100, 1, false, occupied(1)));
        assertEquals(SignalAspect.RED, aspectAt(100, 1, false, occupied(1, 2)));
    }

    @Test
    @DisplayName("yellow when only the next block in the direction of travel is occupied")
    void yellowWhenNextOccupied() {
        assertEquals(SignalAspect.YELLOW, aspectAt(100, 1, false, occupied(2)));
    }

    @Test
    @DisplayName("green when both blocks ahead are clear, even with a train behind")
    void greenWhenClearAhead() {
        assertEquals(SignalAspect.GREEN, aspectAt(100, 1, false, occupied()));
        assertEquals(SignalAspect.GREEN, aspectAt(100, 1, false, occupied(0)));
    }

    @Test
    @DisplayName("a head facing the other way reads the blocks the other way")
    void reverseDirection() {
        // Standing at 200 facing trains that run toward 0: they enter block 1, then block 0.
        assertEquals(SignalAspect.RED, aspectAt(200, -1, false, occupied(1)));
        assertEquals(SignalAspect.YELLOW, aspectAt(200, -1, false, occupied(0)));
        assertEquals(SignalAspect.GREEN, aspectAt(200, -1, false, occupied(2)));
    }

    @Test
    @DisplayName("the last block on an open section has nothing beyond it to warn of")
    void endOfOpenSection() {
        assertEquals(SignalAspect.GREEN, aspectAt(200, 1, false, occupied(0)));
    }

    @Test
    @DisplayName("on a ring, the block after the last is the first")
    void ringWrapsAround() {
        assertEquals(SignalAspect.YELLOW, aspectAt(200, 1, true, occupied(0)));
        assertEquals(SignalAspect.YELLOW, aspectAt(100, -1, true, occupied(2)));
    }

    @Test
    @DisplayName("a head whose boundary no longer exists shows red, never clear")
    void unknownBoundaryIsRed() {
        assertEquals(-1, SignalHeads.protectedBlock(LINE, 1, 150, 1));
        assertEquals(SignalAspect.RED, aspectAt(150, 1, false, occupied()));
    }

    @Test
    @DisplayName("every block has an entry boundary in each direction")
    void boundariesCoverBothDirections() {
        List<SignalHeads.Boundary> all = SignalHeads.boundaries(LINE);
        assertEquals(6, all.size());
        for (SignalHeads.Boundary boundary : all) {
            assertEquals(boundary.blockIndex, SignalHeads.protectedBlock(LINE,
                boundary.sectionId, boundary.distance, boundary.direction));
        }
    }
}
