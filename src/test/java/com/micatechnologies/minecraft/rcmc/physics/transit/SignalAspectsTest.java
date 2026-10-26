package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.block.BlockSection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Two- and four-aspect heads, approach lighting and redstone control. The rule throughout: a head
 * that cannot express what lies ahead shows something more restrictive, never less.
 */
class SignalAspectsTest {

    /** Five 100-block blocks along one 500-block open section. */
    private static final List<BlockSection> LINE = Arrays.asList(
        new BlockSection("b1", 1, 0, 100),
        new BlockSection("b2", 1, 100, 200),
        new BlockSection("b3", 1, 200, 300),
        new BlockSection("b4", 1, 300, 400),
        new BlockSection("b5", 1, 400, 500));

    private static IntPredicate occupied(Integer... blocks) {
        Set<Integer> set = new HashSet<>(Arrays.asList(blocks));
        return set::contains;
    }

    private static SignalAspect at(int index, int direction, int aspects, IntPredicate occupied) {
        return SignalHeads.aspect(LINE, occupied, index, direction, 500, false, aspects);
    }

    @Test
    @DisplayName("four-aspect: red, yellow, double yellow, green as a train recedes")
    void fourAspectSequence() {
        // Head at the start of b1, running forward.
        assertEquals(SignalAspect.RED, at(0, 1, 4, occupied(0)));
        assertEquals(SignalAspect.YELLOW, at(0, 1, 4, occupied(1)));
        assertEquals(SignalAspect.DOUBLE_YELLOW, at(0, 1, 4, occupied(2)));
        assertEquals(SignalAspect.GREEN, at(0, 1, 4, occupied(3)));
        assertEquals(SignalAspect.GREEN, at(0, 1, 4, occupied()));
    }

    @Test
    @DisplayName("four-aspect reads the blocks the way its trains travel")
    void fourAspectReverse() {
        // Head at the end of b5 for trains running in reverse: b4 is next, then b3.
        assertEquals(SignalAspect.DOUBLE_YELLOW, at(4, -1, 4, occupied(2)));
        assertEquals(SignalAspect.GREEN, at(4, -1, 4, occupied(0)));
    }

    @Test
    @DisplayName("three-aspect never shows double yellow, two-aspect never caution")
    void fewerAspects() {
        assertEquals(SignalAspect.GREEN, at(0, 1, 3, occupied(2)));
        assertEquals(SignalAspect.GREEN, at(0, 1, 2, occupied(1)));
        assertEquals(SignalAspect.RED, at(0, 1, 2, occupied(0)));
    }

    @Test
    @DisplayName("an aspect a head cannot show is shown as the next more restrictive one")
    void withinIsNeverLessRestrictive() {
        assertEquals(SignalAspect.YELLOW, SignalAspect.DOUBLE_YELLOW.within(3));
        assertEquals(SignalAspect.RED, SignalAspect.YELLOW.within(2));
        assertEquals(SignalAspect.RED, SignalAspect.DOUBLE_YELLOW.within(2));
        assertEquals(SignalAspect.GREEN, SignalAspect.GREEN.within(2));
        for (int aspects = 2; aspects <= 4; aspects++) {
            for (SignalAspect asked : SignalAspect.values()) {
                assertTrue(asked.within(aspects).rank() <= asked.rank(), asked + " within " + aspects);
            }
        }
    }

    @Test
    @DisplayName("the right-click cycle visits exactly the aspects a head can show")
    void cycle() {
        assertEquals(SignalAspect.GREEN, SignalAspect.RED.next(2));
        assertEquals(SignalAspect.RED, SignalAspect.GREEN.next(2));
        assertEquals(SignalAspect.YELLOW, SignalAspect.RED.next(3));
        assertEquals(SignalAspect.DOUBLE_YELLOW, SignalAspect.YELLOW.next(4));
        assertEquals(SignalAspect.GREEN, SignalAspect.DOUBLE_YELLOW.next(4));
        assertEquals(SignalAspect.RED, SignalAspect.GREEN.next(4));
    }

    @Test
    @DisplayName("redstone: off is stop, full is clear, part is caution")
    void redstone() {
        assertEquals(SignalAspect.RED, SignalAspect.fromRedstone(0, 4));
        assertEquals(SignalAspect.YELLOW, SignalAspect.fromRedstone(5, 4));
        assertEquals(SignalAspect.DOUBLE_YELLOW, SignalAspect.fromRedstone(10, 4));
        assertEquals(SignalAspect.YELLOW, SignalAspect.fromRedstone(10, 3));
        assertEquals(SignalAspect.GREEN, SignalAspect.fromRedstone(15, 3));
        assertEquals(SignalAspect.GREEN, SignalAspect.fromRedstone(3, 2), "any power clears a two-aspect head");
        assertEquals(SignalAspect.RED, SignalAspect.fromRedstone(0, 2));
    }

    @Test
    @DisplayName("approach lighting: lit only while the block before the head holds a train")
    void approach() {
        // Head at the start of b3 (index 2), forward: the approach block is b2.
        assertTrue(SignalHeads.approached(LINE, occupied(1), 2, 1, 500, false));
        assertFalse(SignalHeads.approached(LINE, occupied(2), 2, 1, 500, false),
            "a train past the head no longer approaches it");
        assertFalse(SignalHeads.approached(LINE, occupied(3), 2, 1, 500, false));
        // Reverse-running head at the end of b3: its approach block is b4.
        assertTrue(SignalHeads.approached(LINE, occupied(3), 2, -1, 500, false));
        assertFalse(SignalHeads.approached(LINE, occupied(), 0, 1, 500, false),
            "nothing before the first block");
    }
}
