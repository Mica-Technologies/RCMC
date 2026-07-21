package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JerkLimiterTest {

    private static final double TICK = 1.0D / 20.0D;

    @Test
    @DisplayName("output never changes by more than maxJerk per second, in either direction")
    void boundsTheRateOfChange() {
        JerkLimiter limiter = new JerkLimiter(2.0D, TICK);
        double maxStep = 2.0D * TICK;

        double previous = 0.0D;
        for (int i = 0; i < 50; i++) {
            // Alternate wildly between full power and full brake — the harshest command stream a
            // controller could emit.
            double command = (i % 2 == 0) ? 1.2D : -1.2D;
            double output = limiter.advance(command);
            assertTrue(Math.abs(output - previous) <= maxStep + 1e-12D,
                "output moved " + Math.abs(output - previous) + " in one tick, limit is " + maxStep);
            previous = output;
        }
    }

    @Test
    @DisplayName("a held command is reached exactly and then tracked without wobble")
    void convergesToASteadyCommand() {
        JerkLimiter limiter = new JerkLimiter(2.0D, TICK);
        double output = 0.0D;
        for (int i = 0; i < 30; i++) {
            output = limiter.advance(1.2D);
        }
        assertEquals(1.2D, output, 1e-9D, "expected the limiter to have reached the command");
        assertEquals(1.2D, limiter.advance(1.2D), 1e-9D, "expected no overshoot or wobble once converged");
    }

    @Test
    @DisplayName("ramping from rest to full power takes command/maxJerk seconds")
    void rampDurationMatchesTheJerkBound() {
        JerkLimiter limiter = new JerkLimiter(2.0D, TICK);
        int ticks = 0;
        while (limiter.advance(1.2D) < 1.2D - 1e-9D) {
            ticks++;
        }
        // 1.2 blocks/s² at 2 blocks/s³ is 0.6 s = 12 ticks; the loop counts the calls before the
        // final converging one.
        assertEquals(11, ticks);
    }

    @Test
    @DisplayName("reset snaps the output immediately, bypassing the rate limit")
    void resetBypassesTheLimit() {
        JerkLimiter limiter = new JerkLimiter(2.0D, TICK);
        for (int i = 0; i < 30; i++) {
            limiter.advance(1.2D);
        }
        limiter.reset(0.0D);
        assertEquals(0.0D, limiter.output(), 0.0D);
    }

    @Test
    @DisplayName("non-positive parameters are rejected")
    void rejectsNonPositiveParameters() {
        assertThrows(IllegalArgumentException.class, () -> new JerkLimiter(0.0D, TICK));
        assertThrows(IllegalArgumentException.class, () -> new JerkLimiter(2.0D, 0.0D));
    }
}
