package com.micatechnologies.minecraft.rcmc.rating;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Whether a simulated ride's peak G-forces stay within {@link SafetyLimits}, and if not, why not.
 *
 * <p>Consistent with the project's stance that the game tells the builder what they built rather
 * than refusing to build it (see {@code CLAUDE.md} and {@code docs/design/PHYSICS.md}): this class
 * never prevents anything, it only reports. {@link RideRating} is the layer that acts on the report,
 * by forcing the intensity and nausea verdicts to {@link Verdict#ULTRA_EXTREME} when a ride is
 * unsafe — an honest label, not a build-time refusal.</p>
 */
public final class SafetyVerdict {

    public final boolean safe;
    public final List<String> violations;

    private SafetyVerdict(boolean safe, List<String> violations) {
        this.safe = safe;
        this.violations = Collections.unmodifiableList(violations);
    }

    /** Checks {@code stats}' peak G-forces against {@code limits} on every axis. */
    public static SafetyVerdict evaluate(RideStatistics stats, SafetyLimits limits) {
        if (stats == null) {
            throw new IllegalArgumentException("stats must not be null");
        }
        if (limits == null) {
            throw new IllegalArgumentException("limits must not be null");
        }
        List<String> violations = new ArrayList<>();

        if (stats.peakPositiveVerticalG > limits.maxPositiveVerticalG) {
            violations.add(String.format(
                "peak positive vertical G %.2fg exceeds limit %.2fg",
                stats.peakPositiveVerticalG, limits.maxPositiveVerticalG));
        }
        if (stats.peakNegativeVerticalG < limits.maxNegativeVerticalG) {
            violations.add(String.format(
                "peak negative vertical G %.2fg exceeds limit %.2fg",
                stats.peakNegativeVerticalG, limits.maxNegativeVerticalG));
        }
        if (stats.peakLateralG > limits.maxLateralG) {
            violations.add(String.format(
                "peak lateral G %.2fg exceeds limit %.2fg", stats.peakLateralG, limits.maxLateralG));
        }
        if (stats.peakLongitudinalG > limits.maxLongitudinalG) {
            violations.add(String.format(
                "peak longitudinal G %.2fg exceeds limit %.2fg",
                stats.peakLongitudinalG, limits.maxLongitudinalG));
        }

        return new SafetyVerdict(violations.isEmpty(), violations);
    }

    @Override
    public String toString() {
        return safe ? "SafetyVerdict{safe}" : "SafetyVerdict{UNSAFE: " + violations + '}';
    }
}
