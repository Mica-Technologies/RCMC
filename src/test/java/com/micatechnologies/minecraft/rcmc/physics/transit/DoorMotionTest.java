package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a viewer is owed of a door: it is where the server's door is, it gets there by moving, and
 * it does so however the updates arrive. Each test drives the animation the way a real connection
 * does — a frame at a time, with updates every few ticks, late, missing, or from a slow server —
 * and measures the drawn position against the door the server actually has.
 */
class DoorMotionTest {

    private static final double RATE = 1.0D / DoorMotion.TRAVEL_TICKS;

    /** The largest move between frames {@code step} ticks apart that is still an animation. */
    private static double largestStep(double step) {
        return DoorMotion.CATCH_UP * step / DoorMotion.TRAVEL_TICKS + 1e-9D;
    }

    /** A server door opening from tick {@code start}: its fraction at tick {@code t}. */
    private static double serverOpening(double start, double t) {
        return Math.max(0.0D, Math.min(1.0D, (t - start) * RATE));
    }

    @Test
    @DisplayName("a door first drawn part way through its travel is drawn where it is")
    void firstSightIsNotEasedFromShut() {
        DoorMotion motion = new DoorMotion();
        assertEquals(0.7D, motion.advance(1000.0D, 0.7D, RATE), 1e-9D);
    }

    @Test
    @DisplayName("one update is enough: the leaves finish opening with no more arriving")
    void missedUpdatesDoNotStallTheDoor() {
        DoorMotion motion = new DoorMotion();
        motion.advance(0.0D, 0.0D, 0.0D);
        double shown = 0.0D;
        // The server starts opening at tick 10, says so once, and is not heard from again.
        for (double t = 10.0D; t <= 10.0D + DoorMotion.TRAVEL_TICKS + 1.0D; t += 0.25D) {
            shown = motion.advance(t, 0.0D, RATE);
        }
        assertEquals(1.0D, shown, 1e-6D, "the door must be open when the server's is");
    }

    @Test
    @DisplayName("with updates every five ticks the drawn door stays within a few percent of the real one")
    void tracksTheServerBetweenUpdates() {
        DoorMotion motion = new DoorMotion();
        motion.advance(0.0D, 0.0D, 0.0D);
        double sentFraction = 0.0D;
        double sentRate = 0.0D;
        double previous = 0.0D;
        double worst = 0.0D;
        for (double t = 0.0D; t <= 60.0D; t += 0.25D) {
            if (t >= 10.0D && Math.floor(t) == t && ((int) t) % 5 == 0) {
                sentFraction = serverOpening(10.0D, t);
                sentRate = sentFraction < 1.0D ? RATE : 0.0D;
            }
            double shown = motion.advance(t, sentFraction, sentRate);
            assertTrue(Math.abs(shown - previous) <= largestStep(0.25D), "jumped " + (shown - previous) + " at " + t);
            previous = shown;
            worst = Math.max(worst, Math.abs(shown - serverOpening(10.0D, t)));
        }
        assertTrue(worst < 0.05D, "drawn door was " + worst + " of its travel away from the real one");
        assertEquals(1.0D, previous, 1e-9D);
    }

    @Test
    @DisplayName("an update that arrives late is caught up with, without a jump")
    void lateUpdateIsCaughtUpSmoothly() {
        DoorMotion motion = new DoorMotion();
        motion.advance(0.0D, 0.0D, 0.0D);
        double previous = 0.0D;
        double shown = 0.0D;
        // First heard of at tick 15, when the server's door is already half open.
        for (double t = 15.0D; t <= 30.0D; t += 0.5D) {
            shown = motion.advance(t, 0.5D, RATE);
            assertTrue(Math.abs(shown - previous) <= largestStep(0.5D) || t == 15.0D,
                "jumped " + (shown - previous) + " at " + t);
            previous = shown;
        }
        // The server's door reaches fully open at tick 30; the drawn one must be there too.
        assertEquals(1.0D, shown, 1e-6D);
    }

    @Test
    @DisplayName("on a slow server an opening door never twitches back toward shut")
    void slowServerDoesNotRunTheLeavesBackwards() {
        DoorMotion motion = new DoorMotion();
        motion.advance(0.0D, 0.0D, 0.0D);
        // The server runs at half speed: each of its ticks is two of the viewer's, so the rate it
        // sends over-predicts, and every update reports less than the viewer has already drawn.
        double sentFraction = 0.0D;
        double previous = 0.0D;
        for (double t = 0.0D; t <= 80.0D; t += 0.5D) {
            if (Math.floor(t) == t && ((int) t) % 10 == 0) {
                sentFraction = Math.min(1.0D, (t / 2.0D) * RATE);
            }
            double shown = motion.advance(t, sentFraction, sentFraction < 1.0D ? RATE : 0.0D);
            assertTrue(shown >= previous - 1e-9D, "went from " + previous + " back to " + shown + " at " + t);
            previous = shown;
        }
        assertEquals(1.0D, previous, 1e-6D, "and it still ends up open");
    }

    @Test
    @DisplayName("a clock corrected backwards does not hold the door still")
    void backwardsClockKeepsTheDoorMoving() {
        DoorMotion motion = new DoorMotion();
        motion.advance(100.0D, 0.0D, 0.0D);
        double shown = 0.0D;
        double clock = 100.0D;
        // Thirty ticks of real time, the whole travel, during which the clock is set back ten
        // ticks every twenty — a world clock on a server running slow.
        for (int tick = 1; tick <= 32; tick++) {
            clock += 1.0D;
            if (tick % 20 == 0) {
                clock -= 10.0D;
            }
            shown = motion.advance(clock, 0.0D, RATE);
        }
        assertEquals(1.0D, shown, 1e-6D, "the leaves must have opened in the time they take to travel");
    }

    @Test
    @DisplayName("a train not drawn for a while is drawn afresh, not eased from where another left it")
    void staleStateIsReplaced() {
        DoorMotion motion = new DoorMotion();
        motion.advance(0.0D, 1.0D, 0.0D);
        // Much later the same id is a different train, doors shut.
        assertEquals(0.0D, motion.advance(500.0D, 0.0D, 0.0D), 1e-9D);
    }

    @Test
    @DisplayName("closing mirrors opening: shut when the server's doors are shut")
    void closingFinishesOnTime() {
        DoorMotion motion = new DoorMotion();
        motion.advance(0.0D, 1.0D, 0.0D);
        double shown = 1.0D;
        for (double t = 5.0D; t <= 5.0D + DoorMotion.TRAVEL_TICKS + 1.0D; t += 0.25D) {
            shown = motion.advance(t, 1.0D, -RATE);
        }
        assertEquals(0.0D, shown, 1e-6D, "a train must not pull away with its doors drawn open");
    }
}
