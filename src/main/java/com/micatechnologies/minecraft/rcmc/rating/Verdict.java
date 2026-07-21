package com.micatechnologies.minecraft.rcmc.rating;

/**
 * RCT-style descriptive adjective for a numeric rating: the words a player actually reads, rather
 * than the raw number.
 */
public enum Verdict {
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH,
    EXTREME,
    ULTRA_EXTREME;

    /**
     * Which band {@code value} falls into, given five ascending thresholds that split the number
     * line into six bands: {@code < bands[0]} is {@link #LOW}, {@code >= bands[4]} is
     * {@link #ULTRA_EXTREME}, and so on in between.
     *
     * @param bands exactly five strictly-ascending thresholds — see {@code RatingWeights}' band
     *              arrays for the values actually used
     */
    static Verdict bandFor(double value, double[] bands) {
        if (bands == null || bands.length != 5) {
            throw new IllegalArgumentException("bands must have exactly 5 thresholds");
        }
        if (value < bands[0]) {
            return LOW;
        }
        if (value < bands[1]) {
            return MEDIUM;
        }
        if (value < bands[2]) {
            return HIGH;
        }
        if (value < bands[3]) {
            return VERY_HIGH;
        }
        if (value < bands[4]) {
            return EXTREME;
        }
        return ULTRA_EXTREME;
    }
}
