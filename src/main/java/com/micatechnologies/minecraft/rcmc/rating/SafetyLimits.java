package com.micatechnologies.minecraft.rcmc.rating;

/**
 * G-force ceilings beyond which a ride is flagged unsafe by {@link SafetyVerdict}.
 *
 * <p><b>Where these numbers came from.</b> The default figures below are round numbers this agent
 * recognises as commonly quoted in general coaster-enthusiast and engineering discussion — vertical
 * load up to roughly 5g, negative (airtime) load no lower than about -2g, lateral and longitudinal
 * around 2.5-3g. They are <em>not</em> sourced from a specific, verified engineering standard (such
 * as ASTM F2291) — this agent has not looked one up and read it for this task, and citing one without
 * having done that would be worse than being honest that these are sane, conservative, tune-by-feel
 * defaults. Treat {@link #DEFAULT} as a starting point, not an authoritative safety claim, and
 * override it with real figures once the project has them.</p>
 *
 * <p><b>Configurable, unlike {@link RatingWeights}.</b> Different rides (a kids' ride, a family
 * coaster, an "extreme" one) legitimately want different ceilings, so this is an instantiable value
 * class rather than a block of static constants.</p>
 *
 * <p>Consistent with the project's stance that the game tells the builder what they built rather than
 * refusing to build it: exceeding these limits does not prevent a ride from existing, it only marks
 * {@link SafetyVerdict#safe} false and bumps the intensity/nausea verdicts to
 * {@link Verdict#ULTRA_EXTREME} — see {@link RideRating}.</p>
 */
public final class SafetyLimits {

    /** Reasonable, not-derived-from-a-cited-standard defaults — see the class javadoc. */
    public static final SafetyLimits DEFAULT = new SafetyLimits(5.0D, -2.0D, 2.5D, 3.0D);

    /** Highest positive vertical G (pressed into the seat) considered safe. */
    public final double maxPositiveVerticalG;

    /** Lowest (most negative) vertical G — deepest airtime — considered safe. Negative by
     *  convention, matching {@code GForces.vertical}'s sign convention. */
    public final double maxNegativeVerticalG;

    /** Highest peak lateral G (either direction) considered safe. */
    public final double maxLateralG;

    /** Highest peak longitudinal G (either direction) considered safe. */
    public final double maxLongitudinalG;

    public SafetyLimits(double maxPositiveVerticalG, double maxNegativeVerticalG,
                         double maxLateralG, double maxLongitudinalG) {
        if (maxPositiveVerticalG <= 0.0D) {
            throw new IllegalArgumentException(
                "maxPositiveVerticalG must be positive, got " + maxPositiveVerticalG);
        }
        if (maxNegativeVerticalG >= 0.0D) {
            throw new IllegalArgumentException(
                "maxNegativeVerticalG must be negative, got " + maxNegativeVerticalG);
        }
        if (maxLateralG <= 0.0D) {
            throw new IllegalArgumentException("maxLateralG must be positive, got " + maxLateralG);
        }
        if (maxLongitudinalG <= 0.0D) {
            throw new IllegalArgumentException(
                "maxLongitudinalG must be positive, got " + maxLongitudinalG);
        }
        this.maxPositiveVerticalG = maxPositiveVerticalG;
        this.maxNegativeVerticalG = maxNegativeVerticalG;
        this.maxLateralG = maxLateralG;
        this.maxLongitudinalG = maxLongitudinalG;
    }

    @Override
    public String toString() {
        return "SafetyLimits{+vert<=" + maxPositiveVerticalG + "g, -vert>=" + maxNegativeVerticalG
            + "g, |lat|<=" + maxLateralG + "g, |long|<=" + maxLongitudinalG + "g}";
    }
}
