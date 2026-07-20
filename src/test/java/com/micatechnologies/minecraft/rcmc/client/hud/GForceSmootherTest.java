package com.micatechnologies.minecraft.rcmc.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link GForceSmoother} on a bare JVM — this is exactly the "pure logic" piece of the
 * rider-feedback work that can be unit tested without a running game instance.
 */
class GForceSmootherTest {

    private static final double TAU = 1.0D;

    @Test
    @DisplayName("first update snaps to the sample instead of ramping from zero")
    void firstUpdateSnapsToSample() {
        GForceSmoother smoother = new GForceSmoother(TAU);
        assertEquals(5.0D, smoother.update(5.0D, 0.05D), 1e-9,
            "boarding mid-launch should not read as a gentle ramp from zero");
    }

    @Test
    @DisplayName("a single-tick spike moves the smoothed value only a small fraction of the way")
    void singleTickSpikeIsDamped() {
        GForceSmoother smoother = new GForceSmoother(TAU);
        smoother.update(1.0D, 0.05D); // establish a baseline at rest (1g)

        double dt = 1.0D / 20.0D; // one client tick
        double afterSpike = smoother.update(10.0D, dt);

        // alpha = 1 - exp(-dt/tau) is small for dt << tau, so the output should still be much
        // closer to the old value (1) than the new spike (10).
        assertTrue(afterSpike < 2.0D,
            "a single 50ms-tick spike to 10g should not pull a 1-second-time-constant filter "
                + "past ~2g, got " + afterSpike);
    }

    @Test
    @DisplayName("held sustained load converges to the input over several time constants")
    void sustainedLoadConverges() {
        GForceSmoother smoother = new GForceSmoother(TAU);
        smoother.update(1.0D, 0.05D);

        double dt = 1.0D / 20.0D;
        double value = 1.0D;
        // Hold a step input of 4.5g for 5 tau worth of ticks.
        for (int i = 0; i < (int) (5.0D * TAU / dt); i++) {
            value = smoother.update(4.5D, dt);
        }
        assertEquals(4.5D, value, 0.05D, "sustained load should converge to the held value");
    }

    @Test
    @DisplayName("after one time constant, ~63% of the gap to a step input is covered")
    void matchesFirstOrderLagMath() {
        GForceSmoother smoother = new GForceSmoother(TAU);
        smoother.update(0.0D, 0.05D);
        double afterOneTau = smoother.update(1.0D, TAU);
        assertEquals(1.0D - Math.exp(-1.0D), afterOneTau, 1e-9);
    }

    @Test
    @DisplayName("a non-positive dt leaves the value unchanged rather than throwing or blowing up")
    void nonPositiveDtIsIgnored() {
        GForceSmoother smoother = new GForceSmoother(TAU);
        smoother.update(2.0D, 0.05D);
        assertEquals(2.0D, smoother.update(9.0D, 0.0D), 1e-9);
        assertEquals(2.0D, smoother.update(9.0D, -1.0D), 1e-9);
    }

    @Test
    @DisplayName("reset() clears the filter so the next sample snaps rather than easing in")
    void resetClearsState() {
        GForceSmoother smoother = new GForceSmoother(TAU);
        smoother.update(5.0D, 0.05D);
        smoother.reset();
        assertEquals(0.0D, smoother.value(), 1e-9);
        assertEquals(3.0D, smoother.update(3.0D, 0.05D), 1e-9);
    }

    @Test
    @DisplayName("a non-positive time constant is rejected at construction")
    void rejectsNonPositiveTimeConstant() {
        assertThrows(IllegalArgumentException.class, () -> new GForceSmoother(0.0D));
        assertThrows(IllegalArgumentException.class, () -> new GForceSmoother(-1.0D));
    }
}
