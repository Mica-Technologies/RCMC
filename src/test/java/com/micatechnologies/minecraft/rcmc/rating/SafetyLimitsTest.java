package com.micatechnologies.minecraft.rcmc.rating;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SafetyLimitsTest {

    @Test
    @DisplayName("a non-positive positive-vertical ceiling is rejected")
    void rejectsNonPositivePositiveVertical() {
        assertThrows(IllegalArgumentException.class, () -> new SafetyLimits(0.0D, -2.0D, 2.5D, 3.0D));
    }

    @Test
    @DisplayName("a non-negative negative-vertical floor is rejected")
    void rejectsNonNegativeNegativeVertical() {
        assertThrows(IllegalArgumentException.class, () -> new SafetyLimits(5.0D, 0.5D, 2.5D, 3.0D));
    }

    @Test
    @DisplayName("a non-positive lateral ceiling is rejected")
    void rejectsNonPositiveLateral() {
        assertThrows(IllegalArgumentException.class, () -> new SafetyLimits(5.0D, -2.0D, 0.0D, 3.0D));
    }

    @Test
    @DisplayName("a non-positive longitudinal ceiling is rejected")
    void rejectsNonPositiveLongitudinal() {
        assertThrows(IllegalArgumentException.class, () -> new SafetyLimits(5.0D, -2.0D, 2.5D, -1.0D));
    }
}
