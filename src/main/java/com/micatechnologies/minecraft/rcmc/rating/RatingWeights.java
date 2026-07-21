package com.micatechnologies.minecraft.rcmc.rating;

/**
 * Every tunable number the excitement/intensity/nausea formulae in {@link RideRating} use, named
 * and documented in one place instead of scattered through the scoring code as magic numbers.
 *
 * <p><b>Where these numbers came from.</b> There is no published, verifiable formula for
 * RollerCoaster-Tycoon-style ratings — the original games' exact coefficients were never released,
 * and this project has not reverse-engineered them from disassembly or any other cited source. Every
 * constant below was chosen by hand to produce the right <em>shape</em> of behaviour (a taller,
 * faster ride scores higher excitement; an unbanked turn scores higher nausea than the same turn
 * banked; a violent ride scores higher intensity than a gentle one) and checked against a handful of
 * example coasters until the relative ordering looked right. That is an honest "tuned by feel," not
 * a derivation, and it is written here instead of a citation because inventing one would be worse
 * than admitting there isn't one. Expect these to be retuned as real rides get built and rated — see
 * {@code RideRaterTest}, which deliberately asserts orderings and relationships rather than exact
 * output values for exactly this reason.</p>
 *
 * <p>Grouped by which of the three scores each constant feeds. Every field is a {@code public
 * static final double} rather than an instance value — these are not meant to vary per-ride the way
 * {@link SafetyLimits} legitimately might (a kids' ride operator wanting a stricter G ceiling is a
 * real use case; a park wanting different excitement weights than every other park is not).</p>
 */
public final class RatingWeights {

    private RatingWeights() {
        throw new AssertionError("No instances.");
    }

    // ---------------------------------------------------------------------------------------
    // Excitement — rises with speed, drop, airtime, inversions, length and force variety; falls
    // off once the ride crosses from thrilling into simply hurting (see the comfort-multiplier
    // trio below, applied against the computed intensity score).
    // ---------------------------------------------------------------------------------------

    /** Excitement gained per block/s of top speed. Tuned so a brisk 20 blocks/s top speed alone
     *  contributes about 1.6 points — noticeable, not dominant. */
    public static final double EXCITEMENT_SPEED_WEIGHT = 0.08D;

    /** Excitement gained per block of the largest single drop. A 20-block first drop contributes
     *  about 1.0 point — drops matter, but height alone shouldn't carry a rating. */
    public static final double EXCITEMENT_DROP_WEIGHT = 0.05D;

    /** Excitement gained per second of total airtime (vertical G below zero). Airtime is the most
     *  sought-after sensation in the hobby (see {@code GForces}' own javadoc), so this is weighted
     *  richly per second relative to how little airtime a typical hill actually produces. */
    public static final double EXCITEMENT_AIRTIME_WEIGHT = 0.6D;

    /** Excitement gained per inversion (a maximal span of the ride spent upside down). */
    public static final double EXCITEMENT_PER_INVERSION = 1.2D;

    /** Excitement gained per block of total ride length. Deliberately small — a long ride is not
     *  automatically a good one, but an RCT-style rating does reward a circuit that isn't over in
     *  five seconds. */
    public static final double EXCITEMENT_LENGTH_WEIGHT = 0.002D;

    /** Excitement gained per distinct "kind of force" the ride exercises — see
     *  {@link RideRating}'s variety count. A ride that is only ever a fast straight line is less
     *  interesting than one that also drops, turns and inverts, even at the same peak numbers. */
    public static final double EXCITEMENT_PER_FORCE_TYPE = 0.3D;

    /**
     * Intensity score below which excitement is not penalised at all — the ride is thrilling, not
     * yet punishing.
     */
    public static final double EXCITEMENT_INTENSITY_COMFORT_CEILING = 6.0D;

    /**
     * Intensity score at and above which the excitement penalty bottoms out at
     * {@link #EXCITEMENT_INTENSITY_FLOOR_MULTIPLIER} and gets no worse. Between the ceiling and
     * this point the multiplier decays linearly.
     */
    public static final double EXCITEMENT_INTENSITY_PAIN_POINT = 12.0D;

    /**
     * Excitement is never scaled down by more than this factor, however extreme the ride —
     * a ride that hurts is less exciting, not worthless. This is the "the game tells you what you
     * built, it does not refuse to build it" principle applied to the excitement number itself: an
     * absurd ride still gets a (low, honest) excitement score rather than being clamped to zero.
     */
    public static final double EXCITEMENT_INTENSITY_FLOOR_MULTIPLIER = 0.4D;

    /** Verdict bands for excitement: five ascending thresholds carving the score into the six RCT-style
     *  adjectives (see {@link Verdict}). Tuned so the example coasters in {@code RideRaterTest} land in
     *  visibly different bands, nothing more rigorous than that. */
    public static final double[] EXCITEMENT_BANDS = {2.0D, 4.0D, 6.0D, 8.0D, 10.0D};

    // ---------------------------------------------------------------------------------------
    // Intensity — rises with peak G on every axis, sustained high-G duration, and top speed.
    // ---------------------------------------------------------------------------------------

    /** Intensity gained per g of positive vertical load above the 1g resting baseline (valleys,
     *  banked-turn load). */
    public static final double INTENSITY_VERTICAL_POSITIVE_WEIGHT = 0.7D;

    /** Intensity gained per g of negative vertical load (airtime) below zero. Weighted slightly
     *  higher than positive load: floating out of the seat reads as more intense than being pressed
     *  into it at the same magnitude, in the hobby's own vocabulary. */
    public static final double INTENSITY_VERTICAL_NEGATIVE_WEIGHT = 1.0D;

    /** Intensity gained per g of peak lateral load. */
    public static final double INTENSITY_LATERAL_WEIGHT = 1.0D;

    /** Intensity gained per g of peak longitudinal load (launches, brake runs). */
    public static final double INTENSITY_LONGITUDINAL_WEIGHT = 0.8D;

    /**
     * Deviation from resting (on any axis) beyond which a moment counts toward "sustained high-G
     * duration". Two seconds at 4g is a different experience from an instant at 4g — see
     * {@code GForces#peakMagnitude}'s own javadoc — and this is the threshold that separates a
     * passing spike from a stretch a rider has to endure.
     */
    public static final double INTENSITY_SUSTAINED_G_THRESHOLD = 2.0D;

    /** Intensity gained per second spent above {@link #INTENSITY_SUSTAINED_G_THRESHOLD}. */
    public static final double INTENSITY_SUSTAINED_HIGH_G_WEIGHT = 0.5D;

    /** Intensity gained per block/s of top speed — a small direct contribution, since speed mostly
     *  shows up already via the G-forces it produces on curves and drops. */
    public static final double INTENSITY_SPEED_WEIGHT = 0.04D;

    /** Verdict bands for intensity. See {@link #EXCITEMENT_BANDS}. */
    public static final double[] INTENSITY_BANDS = {1.5D, 3.0D, 5.0D, 7.0D, 10.0D};

    // ---------------------------------------------------------------------------------------
    // Nausea — rises with lateral G unmatched by bank (the key term), inversions, and the rate of
    // direction changes. Sustained helices fall out of the lateral-impulse term for free: a long
    // stretch of unbanked (or under-banked) turning racks up impulse exactly because it is long, no
    // separate "helix" special case needed.
    // ---------------------------------------------------------------------------------------

    /**
     * Nausea gained per g-second of lateral load impulse — {@code integral(|lateral G|, dt)} over
     * the whole ride. This is deliberately the dominant nausea term: it is the duration-weighted
     * version of "lateral G unmatched by bank" the design brief calls out as the single most
     * important driver, and weighting the time-integral rather than only the peak is what makes a
     * long unbanked helix score worse than one sharp unbanked corner of the same peak G — exactly
     * the "sustained helices" requirement.
     */
    public static final double NAUSEA_LATERAL_IMPULSE_WEIGHT = 1.2D;

    /** Small additional nausea per g of peak lateral load, on top of the impulse term — a single
     *  sharp snap matters a little even if it's brief. */
    public static final double NAUSEA_LATERAL_PEAK_WEIGHT = 0.6D;

    /** Nausea gained per inversion. */
    public static final double NAUSEA_PER_INVERSION = 1.0D;

    /** Nausea gained per direction change per second (i.e. per unit of {@code directionChangeCount /
     *  rideDurationSeconds}). Rate rather than raw count, so a longer ride with the same turning
     *  rhythm doesn't automatically score worse just for being longer. */
    public static final double NAUSEA_DIRECTION_CHANGE_RATE_WEIGHT = 3.0D;

    /**
     * Curvature magnitude, in inverse blocks, below which a point on the track is treated as
     * "straight" for direction-change counting — {@code 1/500}, i.e. a 500-block-radius curve or
     * gentler doesn't count as a turn. Guards against floating-point/spline noise on nominally
     * straight track being counted as constant micro-direction-changes.
     */
    public static final double NAUSEA_STRAIGHT_CURVATURE_EPSILON = 1.0D / 500.0D;

    /** Verdict bands for nausea. See {@link #EXCITEMENT_BANDS}. */
    public static final double[] NAUSEA_BANDS = {1.0D, 2.5D, 4.5D, 7.0D, 10.0D};

    // ---------------------------------------------------------------------------------------
    // Excitement's "variety of forces" thresholds — how large a peak on each axis has to be before
    // it counts as that axis genuinely being exercised, for the purpose of the variety bonus.
    // ---------------------------------------------------------------------------------------

    /** Above-baseline positive vertical G beyond which a ride counts as having a "valley" moment. */
    public static final double VARIETY_VERTICAL_THRESHOLD = 0.5D;

    /** Peak lateral G beyond which a ride counts as having meaningful turns. */
    public static final double VARIETY_LATERAL_THRESHOLD = 0.3D;

    /** Peak longitudinal G beyond which a ride counts as having a launch/brake moment. */
    public static final double VARIETY_LONGITUDINAL_THRESHOLD = 0.3D;
}
