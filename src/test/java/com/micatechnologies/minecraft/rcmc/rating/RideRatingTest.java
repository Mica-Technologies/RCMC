package com.micatechnologies.minecraft.rcmc.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Formula-level tests against hand-built {@link RideStatistics} fixtures, independent of any real
 * physics simulation. Where {@code RideRaterTest} checks the whole pipeline against real coasters,
 * these isolate the scoring math in {@link RideRating} itself — in particular the "too intense stops
 * being exciting" relationship, which is awkward to provoke reliably from a real track shape but
 * trivial to construct directly as statistics.
 */
class RideRatingTest {

    private static RideStatistics stats(double maxSpeed, double maxDrop, int inversions,
                                         double peakPosVert, double peakNegVert,
                                         double peakLat, double peakLong,
                                         double airtimeSeconds, int directionChanges,
                                         double sustainedLatG, double sustainedHighG,
                                         double length, double duration) {
        double avgSpeed = duration > 0.0D ? length / duration : 0.0D;
        return new RideStatistics(maxSpeed, avgSpeed, duration, length, maxDrop, inversions,
            peakPosVert, peakNegVert, peakLat, peakLong, airtimeSeconds, directionChanges,
            sustainedLatG, sustainedHighG, Train.Status.RUNNING, (int) (duration * 20.0D));
    }

    @Test
    @DisplayName("a ride with no drops, turns, airtime or speed rates near zero on everything")
    void baselineRideRatesNearZero() {
        // Resting baseline: 1g vertical, everything else zero — a train that never really does
        // anything.
        RideStatistics flat = stats(5.0D, 0.0D, 0, 1.0D, 1.0D, 0.0D, 0.0D, 0.0D, 0, 0.0D, 0.0D, 50.0D, 10.0D);
        RideRating rating = RideRating.from(flat);

        assertTrue(rating.excitement < 1.0D, "expected near-zero excitement, got " + rating);
        assertTrue(rating.intensity < 1.0D, "expected near-zero intensity, got " + rating);
        assertTrue(rating.nausea < 0.5D, "expected near-zero nausea, got " + rating);
        assertEquals(Verdict.LOW, rating.excitementVerdict);
        assertEquals(Verdict.LOW, rating.intensityVerdict);
        assertEquals(Verdict.LOW, rating.nauseaVerdict);
    }

    @Test
    @DisplayName("crossing into painfully intense territory reduces excitement, even with identical raw ingredients")
    void tooIntenseReducesExcitement() {
        // Same speed, drop, inversion count, length and airtime — so the same "raw" excitement
        // ingredients and the same variety count — but very different G peaks.
        RideStatistics mild = stats(
            20.0D, 20.0D, 1,
            3.0D, -0.3D, 0.5D, 0.4D,
            1.5D, 4, 2.0D, 0.5D,
            400.0D, 30.0D);
        RideStatistics extreme = stats(
            20.0D, 20.0D, 1,
            8.0D, -3.0D, 4.0D, 2.5D,
            1.5D, 4, 2.0D, 6.0D,
            400.0D, 30.0D);

        RideRating mildRating = RideRating.from(mild);
        RideRating extremeRating = RideRating.from(extreme);

        assertTrue(extremeRating.intensity > mildRating.intensity,
            "extreme fixture should score higher intensity: " + mildRating + " vs " + extremeRating);
        assertTrue(extremeRating.excitement < mildRating.excitement,
            "identical raw ingredients but painfully high G should score LOWER excitement: "
                + mildRating + " vs " + extremeRating);
        // Excitement should never be scaled all the way to zero by the intensity penalty — see
        // RatingWeights.EXCITEMENT_INTENSITY_FLOOR_MULTIPLIER.
        assertTrue(extremeRating.excitement > 0.0D);
    }

    @Test
    @DisplayName("nausea rises with sustained lateral G impulse, holding everything else fixed")
    void nauseaRisesWithSustainedLateralImpulse() {
        RideStatistics low = stats(15.0D, 10.0D, 0, 1.5D, 0.8D, 0.4D, 0.2D, 0.0D, 2, 1.0D, 0.0D, 200.0D, 20.0D);
        RideStatistics high = stats(15.0D, 10.0D, 0, 1.5D, 0.8D, 0.4D, 0.2D, 0.0D, 2, 10.0D, 0.0D, 200.0D, 20.0D);

        assertTrue(RideRating.from(high).nausea > RideRating.from(low).nausea);
    }

    @Test
    @DisplayName("nausea rises with direction-change rate, holding everything else fixed")
    void nauseaRisesWithDirectionChangeRate() {
        RideStatistics few = stats(15.0D, 10.0D, 0, 1.5D, 0.8D, 0.4D, 0.2D, 0.0D, 1, 1.0D, 0.0D, 200.0D, 20.0D);
        RideStatistics many = stats(15.0D, 10.0D, 0, 1.5D, 0.8D, 0.4D, 0.2D, 0.0D, 20, 1.0D, 0.0D, 200.0D, 20.0D);

        assertTrue(RideRating.from(many).nausea > RideRating.from(few).nausea);
    }

    @Test
    @DisplayName("exceeding a safety limit forces the intensity and nausea verdicts to ULTRA_EXTREME")
    void unsafeStatsForceUltraExtremeVerdicts() {
        RideStatistics dangerous = stats(30.0D, 20.0D, 0, 2.0D, 0.5D, 6.0D /* over 2.5g default */, 0.5D,
            0.0D, 2, 3.0D, 0.0D, 300.0D, 20.0D);

        RideRating rating = RideRating.from(dangerous);

        assertTrue(!rating.safety.safe);
        assertEquals(Verdict.ULTRA_EXTREME, rating.intensityVerdict);
        assertEquals(Verdict.ULTRA_EXTREME, rating.nauseaVerdict);
    }

    @Test
    @DisplayName("staying within configured safety limits is reported as safe")
    void safeStatsAreReportedSafe() {
        RideStatistics gentle = stats(10.0D, 5.0D, 0, 1.5D, 0.5D, 0.5D, 0.3D, 0.0D, 1, 0.5D, 0.0D, 100.0D, 15.0D);
        RideRating rating = RideRating.from(gentle);

        assertTrue(rating.safety.safe);
        assertTrue(rating.safety.violations.isEmpty());
    }

    @Test
    @DisplayName("custom, stricter safety limits can flag a ride the defaults would consider safe")
    void customSafetyLimitsAreConfigurable() {
        RideStatistics moderate = stats(10.0D, 5.0D, 0, 3.0D, 0.5D, 1.0D, 0.3D, 0.0D, 1, 0.5D, 0.0D, 100.0D, 15.0D);
        SafetyLimits strict = new SafetyLimits(2.0D, -1.0D, 0.8D, 1.0D);

        RideRating underDefault = RideRating.from(moderate);
        RideRating underStrict = RideRating.from(moderate, strict);

        assertTrue(underDefault.safety.safe, "should be safe under the default limits");
        assertTrue(!underStrict.safety.safe, "should be unsafe under the stricter, configured limits");
    }

    @Test
    @DisplayName("rating the same statistics object twice is bit-identical")
    void ratingFormulaIsDeterministic() {
        RideStatistics stats = stats(18.0D, 22.0D, 1, 3.2D, -0.8D, 1.1D, 0.6D, 1.0D, 6, 4.0D, 1.0D, 350.0D, 25.0D);

        RideRating a = RideRating.from(stats);
        RideRating b = RideRating.from(stats);

        assertEquals(a.excitement, b.excitement, 0.0D);
        assertEquals(a.intensity, b.intensity, 0.0D);
        assertEquals(a.nausea, b.nausea, 0.0D);
        assertEquals(a.excitementVerdict, b.excitementVerdict);
        assertEquals(a.intensityVerdict, b.intensityVerdict);
        assertEquals(a.nauseaVerdict, b.nauseaVerdict);
    }
}
