package com.micatechnologies.minecraft.rcmc.rating;

import com.micatechnologies.minecraft.rcmc.physics.Train;

/**
 * Everything {@link RideRater} measured from one design-time simulated lap of a coaster.
 *
 * <p>This is the layer between raw physics and the rating formulae in {@link RideRating}: it holds
 * plain, individually-meaningful numbers (top speed, drop height, how long the ride spends upside
 * down) rather than anything already opinionated about excitement or nausea. That split means the
 * numbers here can be sanity-checked against the physics directly — "does a 34-block first drop
 * really produce this top speed" — independently of whether the rating formulae built on top of them
 * are tuned well.</p>
 *
 * <p>Immutable, and free of Minecraft types like the rest of the simulation it is built from — see
 * {@code CLAUDE.md}. {@link Train.Status} is the one non-primitive field; it is itself Minecraft-free
 * (see {@code physics} package), so depending on it does not compromise that.</p>
 *
 * <p>Two duration-weighted fields go beyond the minimum list a Phase 8.1 rating needs, and exist
 * specifically because a bare peak value cannot tell the two apart:</p>
 * <ul>
 *   <li>{@link #sustainedLateralGSeconds} — the time-integral of {@code |lateral G|}. A single sharp
 *       unbanked corner and a long sustained unbanked helix can have the identical peak lateral G but
 *       are very different rides to sit through; only the integral distinguishes them, and it is what
 *       the nausea model in {@link RideRating} keys off primarily.</li>
 *   <li>{@link #sustainedHighGSeconds} — total time spent with any axis beyond
 *       {@code RatingWeights.INTENSITY_SUSTAINED_G_THRESHOLD} of resting. Two seconds at 4g is a
 *       different experience from an instant at 4g; this is what lets the intensity model tell them
 *       apart.</li>
 * </ul>
 */
public final class RideStatistics {

    public final double maxSpeedBlocksPerSecond;
    public final double averageSpeedBlocksPerSecond;
    public final double rideDurationSeconds;
    public final double totalLengthBlocks;

    /** Largest single cumulative descent (peak height reached minus the lowest point reached before
     *  climbing back above that peak), in blocks — the classic "biggest first drop" measure, but
     *  generalised to whichever drop in the ride is actually the largest. */
    public final double maxDropHeightBlocks;

    /** Count of maximal contiguous spans of the ride spent upside down (frame's up vector pointing
     *  more down than up). */
    public final int inversionCount;

    /** Highest vertical G reached — most strongly pressed into the seat. */
    public final double peakPositiveVerticalG;

    /** Lowest (most negative) vertical G reached — deepest airtime. May be positive if the ride never
     *  produces airtime; it is simply the minimum vertical G sample, not clamped. */
    public final double peakNegativeVerticalG;

    /** Largest {@code |lateral G|} reached, in either direction. */
    public final double peakLateralG;

    /** Largest {@code |longitudinal G|} reached, in either direction. */
    public final double peakLongitudinalG;

    /** Total time spent with vertical G below zero — see {@code GForces#isAirtime}. */
    public final double totalAirtimeSeconds;

    /** Number of times the track's horizontal turning direction (left vs. right, ignoring bank)
     *  flips, with a dead zone around dead-straight track — see
     *  {@code RatingWeights#NAUSEA_STRAIGHT_CURVATURE_EPSILON}. */
    public final int directionChangeCount;

    /** Time-integral of {@code |lateral G|} over the whole ride, in g·seconds. See the class javadoc. */
    public final double sustainedLateralGSeconds;

    /** Total seconds spent with any G axis beyond {@code RatingWeights#INTENSITY_SUSTAINED_G_THRESHOLD}
     *  of resting. See the class javadoc. */
    public final double sustainedHighGSeconds;

    /** How the simulated run ended: still {@link Train.Status#RUNNING} if the tick budget was simply
     *  exhausted or a lap was detected as complete, or a fault status if the train got stuck or ran off
     *  unconnected track before completing one. */
    public final Train.Status finalStatus;

    /** Number of physics ticks actually simulated, for diagnostics. */
    public final int ticksSimulated;

    public RideStatistics(double maxSpeedBlocksPerSecond, double averageSpeedBlocksPerSecond,
                           double rideDurationSeconds, double totalLengthBlocks,
                           double maxDropHeightBlocks, int inversionCount,
                           double peakPositiveVerticalG, double peakNegativeVerticalG,
                           double peakLateralG, double peakLongitudinalG,
                           double totalAirtimeSeconds, int directionChangeCount,
                           double sustainedLateralGSeconds, double sustainedHighGSeconds,
                           Train.Status finalStatus, int ticksSimulated) {
        this.maxSpeedBlocksPerSecond = maxSpeedBlocksPerSecond;
        this.averageSpeedBlocksPerSecond = averageSpeedBlocksPerSecond;
        this.rideDurationSeconds = rideDurationSeconds;
        this.totalLengthBlocks = totalLengthBlocks;
        this.maxDropHeightBlocks = maxDropHeightBlocks;
        this.inversionCount = inversionCount;
        this.peakPositiveVerticalG = peakPositiveVerticalG;
        this.peakNegativeVerticalG = peakNegativeVerticalG;
        this.peakLateralG = peakLateralG;
        this.peakLongitudinalG = peakLongitudinalG;
        this.totalAirtimeSeconds = totalAirtimeSeconds;
        this.directionChangeCount = directionChangeCount;
        this.sustainedLateralGSeconds = sustainedLateralGSeconds;
        this.sustainedHighGSeconds = sustainedHighGSeconds;
        this.finalStatus = finalStatus == null ? Train.Status.RUNNING : finalStatus;
        this.ticksSimulated = ticksSimulated;
    }

    /** Whether the simulated train completed the run without faulting (dead end or valleying). */
    public boolean completedWithoutFault() {
        return finalStatus == Train.Status.RUNNING;
    }

    @Override
    public String toString() {
        return "RideStatistics{maxSpeed=" + String.format("%.2f", maxSpeedBlocksPerSecond)
            + ", drop=" + String.format("%.2f", maxDropHeightBlocks)
            + ", inversions=" + inversionCount
            + ", airtime=" + String.format("%.2f", totalAirtimeSeconds)
            + ", peakVert=[" + String.format("%.2f", peakNegativeVerticalG) + ", "
            + String.format("%.2f", peakPositiveVerticalG) + "]"
            + ", peakLat=" + String.format("%.2f", peakLateralG)
            + ", peakLong=" + String.format("%.2f", peakLongitudinalG)
            + ", status=" + finalStatus + '}';
    }
}
