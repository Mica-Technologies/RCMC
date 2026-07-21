package com.micatechnologies.minecraft.rcmc.rating;

/**
 * The three RollerCoaster-Tycoon-style numbers a ride is judged by — excitement, intensity, nausea
 * — computed from a design-time {@link RideStatistics}, plus the descriptive verdict for each and an
 * overall {@link SafetyVerdict}.
 *
 * <p><b>Deterministic by construction.</b> {@link #from} is a pure function of {@code stats} and the
 * static {@link RatingWeights} constants: no randomness, no wall-clock, no mutable state. Feed it the
 * same statistics twice and it returns equal numbers twice — the hard requirement the whole rating
 * feature exists under, since a rating has to be inspectable before a ride ever opens and must not
 * drift between a park's server and any client mirror.</p>
 *
 * <p>See {@link RatingWeights} for exactly where every coefficient in the three formulae below came
 * from, and the honest admission that "tuned by feel" is what most of them are.</p>
 */
public final class RideRating {

    public final double excitement;
    public final double intensity;
    public final double nausea;

    public final Verdict excitementVerdict;
    public final Verdict intensityVerdict;
    public final Verdict nauseaVerdict;

    public final SafetyVerdict safety;

    private RideRating(double excitement, double intensity, double nausea,
                        Verdict excitementVerdict, Verdict intensityVerdict, Verdict nauseaVerdict,
                        SafetyVerdict safety) {
        this.excitement = excitement;
        this.intensity = intensity;
        this.nausea = nausea;
        this.excitementVerdict = excitementVerdict;
        this.intensityVerdict = intensityVerdict;
        this.nauseaVerdict = nauseaVerdict;
        this.safety = safety;
    }

    /**
     * Computes a full rating from simulated statistics, using {@link SafetyLimits#DEFAULT}.
     */
    public static RideRating from(RideStatistics stats) {
        return from(stats, SafetyLimits.DEFAULT);
    }

    /**
     * Computes a full rating from simulated statistics against the given safety ceilings.
     *
     * <p>Intensity is computed before excitement because excitement's formula needs it — see
     * {@link #computeExcitement}'s comfort-multiplier term, which is what makes an overly violent
     * ride score <em>lower</em> excitement than a merely thrilling one at the same raw ingredients.</p>
     */
    public static RideRating from(RideStatistics stats, SafetyLimits limits) {
        if (stats == null) {
            throw new IllegalArgumentException("stats must not be null");
        }
        double intensity = computeIntensity(stats);
        double nausea = computeNausea(stats);
        double excitement = computeExcitement(stats, intensity);

        Verdict excitementVerdict = Verdict.bandFor(excitement, RatingWeights.EXCITEMENT_BANDS);
        Verdict intensityVerdict = Verdict.bandFor(intensity, RatingWeights.INTENSITY_BANDS);
        Verdict nauseaVerdict = Verdict.bandFor(nausea, RatingWeights.NAUSEA_BANDS);

        SafetyVerdict safety = SafetyVerdict.evaluate(stats, limits);
        if (!safety.safe) {
            // Exceeding a configured G limit is flagged, never silently absorbed or refused — see
            // SafetyVerdict's javadoc. The numeric excitement/intensity/nausea scores are left alone
            // (they are an honest reading of what the ride does), but the descriptive verdict a
            // player actually reads is bumped to the top band on both axes the limits protect.
            intensityVerdict = Verdict.ULTRA_EXTREME;
            nauseaVerdict = Verdict.ULTRA_EXTREME;
        }

        return new RideRating(excitement, intensity, nausea,
            excitementVerdict, intensityVerdict, nauseaVerdict, safety);
    }

    /**
     * Excitement: speed, drop, airtime, inversions, length and force variety all add to a raw score,
     * which is then scaled down once the ride's intensity crosses a comfort ceiling — see
     * {@link #comfortMultiplier}. A ride that is simply painful is not exciting, however extreme its
     * raw ingredients.
     */
    private static double computeExcitement(RideStatistics stats, double intensity) {
        int variety = varietyCount(stats);
        double raw = stats.maxSpeedBlocksPerSecond * RatingWeights.EXCITEMENT_SPEED_WEIGHT
            + stats.maxDropHeightBlocks * RatingWeights.EXCITEMENT_DROP_WEIGHT
            + stats.totalAirtimeSeconds * RatingWeights.EXCITEMENT_AIRTIME_WEIGHT
            + stats.inversionCount * RatingWeights.EXCITEMENT_PER_INVERSION
            + stats.totalLengthBlocks * RatingWeights.EXCITEMENT_LENGTH_WEIGHT
            + variety * RatingWeights.EXCITEMENT_PER_FORCE_TYPE;
        return Math.max(0.0D, raw * comfortMultiplier(intensity));
    }

    /**
     * 1.0 up to the comfort ceiling, decaying linearly to the floor multiplier by the pain point, and
     * held at the floor beyond it — see the three {@code EXCITEMENT_INTENSITY_*} constants in
     * {@link RatingWeights}.
     */
    private static double comfortMultiplier(double intensity) {
        double ceiling = RatingWeights.EXCITEMENT_INTENSITY_COMFORT_CEILING;
        double pain = RatingWeights.EXCITEMENT_INTENSITY_PAIN_POINT;
        double floor = RatingWeights.EXCITEMENT_INTENSITY_FLOOR_MULTIPLIER;
        if (intensity <= ceiling) {
            return 1.0D;
        }
        if (intensity >= pain) {
            return floor;
        }
        double t = (intensity - ceiling) / (pain - ceiling);
        return 1.0D - (1.0D - floor) * t;
    }

    /** How many distinct "kinds" of force the ride exercises meaningfully — see the
     *  {@code VARIETY_*} thresholds in {@link RatingWeights}. */
    private static int varietyCount(RideStatistics stats) {
        int count = 0;
        if (stats.peakPositiveVerticalG - 1.0D > RatingWeights.VARIETY_VERTICAL_THRESHOLD) {
            count++;
        }
        if (stats.totalAirtimeSeconds > 0.0D) {
            count++;
        }
        if (stats.peakLateralG > RatingWeights.VARIETY_LATERAL_THRESHOLD) {
            count++;
        }
        if (stats.inversionCount > 0) {
            count++;
        }
        if (stats.peakLongitudinalG > RatingWeights.VARIETY_LONGITUDINAL_THRESHOLD) {
            count++;
        }
        return count;
    }

    /** Intensity: peak G on every axis, sustained high-G duration, and top speed. */
    private static double computeIntensity(RideStatistics stats) {
        double value = Math.max(0.0D, stats.peakPositiveVerticalG - 1.0D)
                * RatingWeights.INTENSITY_VERTICAL_POSITIVE_WEIGHT
            + Math.max(0.0D, -stats.peakNegativeVerticalG) * RatingWeights.INTENSITY_VERTICAL_NEGATIVE_WEIGHT
            + stats.peakLateralG * RatingWeights.INTENSITY_LATERAL_WEIGHT
            + stats.peakLongitudinalG * RatingWeights.INTENSITY_LONGITUDINAL_WEIGHT
            + stats.sustainedHighGSeconds * RatingWeights.INTENSITY_SUSTAINED_HIGH_G_WEIGHT
            + stats.maxSpeedBlocksPerSecond * RatingWeights.INTENSITY_SPEED_WEIGHT;
        return Math.max(0.0D, value);
    }

    /**
     * Nausea: dominated by the duration-weighted lateral G impulse (see
     * {@link RideStatistics#sustainedLateralGSeconds}'s javadoc for why duration rather than peak is
     * the key driver), plus inversions and the rate of direction changes.
     */
    private static double computeNausea(RideStatistics stats) {
        double directionChangeRate = stats.rideDurationSeconds > 0.0D
            ? stats.directionChangeCount / stats.rideDurationSeconds
            : 0.0D;
        double value = stats.sustainedLateralGSeconds * RatingWeights.NAUSEA_LATERAL_IMPULSE_WEIGHT
            + stats.peakLateralG * RatingWeights.NAUSEA_LATERAL_PEAK_WEIGHT
            + stats.inversionCount * RatingWeights.NAUSEA_PER_INVERSION
            + directionChangeRate * RatingWeights.NAUSEA_DIRECTION_CHANGE_RATE_WEIGHT;
        return Math.max(0.0D, value);
    }

    @Override
    public String toString() {
        return "RideRating{excitement=" + String.format("%.2f", excitement) + " (" + excitementVerdict
            + "), intensity=" + String.format("%.2f", intensity) + " (" + intensityVerdict
            + "), nausea=" + String.format("%.2f", nausea) + " (" + nauseaVerdict
            + "), " + safety + '}';
    }
}
