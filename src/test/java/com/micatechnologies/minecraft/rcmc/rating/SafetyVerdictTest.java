package com.micatechnologies.minecraft.rcmc.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SafetyVerdictTest {

    private static RideStatistics stats(double peakPosVert, double peakNegVert, double peakLat, double peakLong) {
        return new RideStatistics(10.0D, 5.0D, 10.0D, 100.0D, 5.0D, 0,
            peakPosVert, peakNegVert, peakLat, peakLong, 0.0D, 0, 0.0D, 0.0D, Train.Status.RUNNING, 200);
    }

    @Test
    @DisplayName("statistics within every limit are safe with no violations")
    void withinLimitsIsSafe() {
        SafetyVerdict verdict = SafetyVerdict.evaluate(stats(3.0D, -1.0D, 1.0D, 1.0D), SafetyLimits.DEFAULT);
        assertTrue(verdict.safe);
        assertTrue(verdict.violations.isEmpty());
    }

    @Test
    @DisplayName("exceeding positive vertical G alone is caught and named")
    void positiveVerticalViolationIsNamed() {
        SafetyVerdict verdict = SafetyVerdict.evaluate(stats(6.0D, -1.0D, 1.0D, 1.0D), SafetyLimits.DEFAULT);
        assertTrue(!verdict.safe);
        assertEquals(1, verdict.violations.size());
        assertTrue(verdict.violations.get(0).contains("positive vertical"));
    }

    @Test
    @DisplayName("exceeding negative vertical G (airtime) alone is caught and named")
    void negativeVerticalViolationIsNamed() {
        SafetyVerdict verdict = SafetyVerdict.evaluate(stats(3.0D, -3.0D, 1.0D, 1.0D), SafetyLimits.DEFAULT);
        assertTrue(!verdict.safe);
        assertTrue(verdict.violations.get(0).contains("negative vertical"));
    }

    @Test
    @DisplayName("exceeding lateral G alone is caught and named")
    void lateralViolationIsNamed() {
        SafetyVerdict verdict = SafetyVerdict.evaluate(stats(3.0D, -1.0D, 4.0D, 1.0D), SafetyLimits.DEFAULT);
        assertTrue(!verdict.safe);
        assertTrue(verdict.violations.get(0).contains("lateral"));
    }

    @Test
    @DisplayName("exceeding longitudinal G alone is caught and named")
    void longitudinalViolationIsNamed() {
        SafetyVerdict verdict = SafetyVerdict.evaluate(stats(3.0D, -1.0D, 1.0D, 4.0D), SafetyLimits.DEFAULT);
        assertTrue(!verdict.safe);
        assertTrue(verdict.violations.get(0).contains("longitudinal"));
    }

    @Test
    @DisplayName("exceeding every axis at once reports every violation")
    void everyAxisViolatingReportsAll() {
        SafetyVerdict verdict = SafetyVerdict.evaluate(stats(9.0D, -4.0D, 5.0D, 6.0D), SafetyLimits.DEFAULT);
        assertTrue(!verdict.safe);
        assertEquals(4, verdict.violations.size());
    }
}
