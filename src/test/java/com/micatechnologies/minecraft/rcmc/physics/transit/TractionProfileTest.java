package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TractionProfileTest {

    /** Base speed = 18/1.2 = 15, comfortably inside the working range. */
    private static final TractionProfile PROFILE = new TractionProfile(1.2D, 18.0D, 30.0D);

    @Test
    @DisplayName("below base speed the motor is torque-limited: full force regardless of speed")
    void constantForceBelowBaseSpeed() {
        assertEquals(1.2D, PROFILE.availableAcceleration(0.0D), 1e-12D);
        assertEquals(1.2D, PROFILE.availableAcceleration(7.5D), 1e-12D);
        assertEquals(1.2D, PROFILE.availableAcceleration(15.0D), 1e-12D);
    }

    @Test
    @DisplayName("above base speed the motor is power-limited: force falls off as P/v")
    void constantPowerAboveBaseSpeed() {
        assertEquals(18.0D / 20.0D, PROFILE.availableAcceleration(20.0D), 1e-12D);
        assertEquals(18.0D / 29.0D, PROFILE.availableAcceleration(29.0D), 1e-12D);
    }

    @Test
    @DisplayName("the two regions meet continuously at base speed")
    void continuousAtBaseSpeed() {
        double base = PROFILE.baseSpeed();
        assertEquals(15.0D, base, 1e-12D);
        assertEquals(PROFILE.availableAcceleration(base - 1e-9D),
            PROFILE.availableAcceleration(base + 1e-9D), 1e-6D);
    }

    @Test
    @DisplayName("propulsion cuts out entirely at and above max service speed")
    void cutsOutAtMaxServiceSpeed() {
        assertEquals(0.0D, PROFILE.availableAcceleration(30.0D), 0.0D);
        assertEquals(0.0D, PROFILE.availableAcceleration(45.0D), 0.0D);
    }

    @Test
    @DisplayName("the curve is symmetric in direction: reverse running has the same traction")
    void symmetricInDirection() {
        assertEquals(PROFILE.availableAcceleration(10.0D), PROFILE.availableAcceleration(-10.0D), 0.0D);
        assertEquals(PROFILE.availableAcceleration(20.0D), PROFILE.availableAcceleration(-20.0D), 0.0D);
    }

    @Test
    @DisplayName("a profile whose base speed exceeds max service speed is legal: constant force throughout")
    void constantForceProfileIsLegal() {
        TractionProfile flat = new TractionProfile(1.0D, 100.0D, 20.0D);
        assertEquals(1.0D, flat.availableAcceleration(19.9D), 1e-12D);
        assertEquals(0.0D, flat.availableAcceleration(20.0D), 0.0D);
    }

    @Test
    @DisplayName("non-positive parameters are rejected")
    void rejectsNonPositiveParameters() {
        assertThrows(IllegalArgumentException.class, () -> new TractionProfile(0.0D, 18.0D, 30.0D));
        assertThrows(IllegalArgumentException.class, () -> new TractionProfile(1.2D, -1.0D, 30.0D));
        assertThrows(IllegalArgumentException.class, () -> new TractionProfile(1.2D, 18.0D, 0.0D));
    }
}
