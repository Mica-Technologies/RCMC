package com.micatechnologies.minecraft.rcmc.physics.transit;

/**
 * Answers the arrival board's one question: how many stops away is a train from a station?
 *
 * <p>"Stops away" rather than minutes, on purpose. It is exact and deterministic — computed by
 * replaying the service pattern ({@link LineService#advanceToNextStop}'s exact stepping rules:
 * loop wrap, shuttle terminus bounce) from the train's next stop until it lands on the queried
 * station — where a minutes estimate would need speeds, dwell assumptions and a wall clock.
 * Minutes are layered on top by {@link #secondsToStations}, from leg times the line's trains have
 * been measured running ({@link LineTimings}); stops stay the truth underneath them, and the
 * fallback wherever a leg has not been timed.</p>
 *
 * <p>Pure static function over pure types; the board renderer calls it per frame and the
 * physics never does.</p>
 */
public final class ArrivalEstimator {

    private ArrivalEstimator() {
    }

    /**
     * Stops between a service's next stop and {@code stationIndex}, following the line's
     * service pattern. Zero means "this station is the very next stop". Returns {@code -1} if
     * the pattern never reaches the station — impossible on a well-formed line, but returned
     * rather than looped on, in the spirit of every other bounded walk in this codebase.
     *
     * @param serviceDirection the service's current direction, {@code +1} or {@code -1}
     * @param nextStopIndex    the stop the service is currently running to
     * @param stationIndex     the stop the board is asking about
     */
    public static int stopsAway(TransitLine line, int serviceDirection, int nextStopIndex,
                                int stationIndex) {
        long walk = walk(line, serviceDirection, nextStopIndex, stationIndex);
        return walk < 0 ? -1 : (int) (walk >> 1);
    }

    /**
     * The service direction the train will be running in when it calls at {@code stationIndex} —
     * which is not always the direction it is running in now.
     *
     * <p>A train that turns back at a terminus before it reaches this station arrives the other way.
     * On a turnback line that is the ordinary case for half the trains a board lists, and grouping
     * them by their current direction put a train due outbound under the inbound heading until it
     * had turned, then moved it across: a row that jumps headings mid-approach. Boards and speakers
     * group and announce by this instead.</p>
     *
     * @return {@code +1} or {@code -1}, or {@code 0} if the pattern never reaches the station
     */
    public static int arrivalDirection(TransitLine line, int serviceDirection, int nextStopIndex,
                                       int stationIndex) {
        long walk = walk(line, serviceDirection, nextStopIndex, stationIndex);
        if (walk < 0) {
            return 0;
        }
        return (walk & 1L) == 0L ? 1 : -1;
    }

    /**
     * Replays the service pattern to {@code stationIndex}: the stop count in the high bits and the
     * direction on arrival in the low bit ({@code 0} = {@code +1}), or {@code -1} if never reached.
     * One walk for both answers, so the count and the direction can never disagree about which
     * visit to the station they describe.
     */
    private static long walk(TransitLine line, int serviceDirection, int nextStopIndex,
                             int stationIndex) {
        if (line == null) {
            throw new IllegalArgumentException("line is required");
        }
        int count = line.stationCount();
        if (nextStopIndex < 0 || nextStopIndex >= count
            || stationIndex < 0 || stationIndex >= count) {
            return -1L;
        }
        int index = nextStopIndex;
        int direction = serviceDirection >= 0 ? 1 : -1;
        // A full out-and-back visits every stop at most twice; 2·count steps covers any pattern.
        for (int steps = 0; steps <= 2 * count; steps++) {
            if (index == stationIndex) {
                return ((long) steps << 1) | (direction > 0 ? 0L : 1L);
            }
            int next = index + direction;
            if (line.isLoop()) {
                index = Math.floorMod(next, count);
                continue;
            }
            if (next < 0 || next >= count) {
                // Terminus bounce, exactly as LineService reverses.
                direction = -direction;
                next = index + direction;
            }
            index = next;
        }
        return -1L;
    }

    /**
     * Seconds until a service reaches each station of its line, on its next visit — what an arrival
     * board shows as minutes. {@code -1} for a station it cannot yet estimate.
     *
     * <p>Built from {@link LineTimings}: legs the line's trains have actually been timed running,
     * added up along the same service pattern {@link #stopsAway} replays. The leg the train is on
     * now counts from when it arrived at its last stop, so a train that has been running a while is
     * nearer than a train that has just left. A leg nobody has timed yet ends the estimate there,
     * and every station past it stays {@code -1} — a board then falls back to counting stops rather
     * than guessing.</p>
     *
     * @param approaching          whether the train is running to {@code nextStopIndex}, rather than
     *                             standing at it
     * @param ticksSinceArrival    ticks since it last arrived at a stop, or negative if unknown —
     *                             the first stop after entering service
     * @param fallbackTicksToNext  an estimate for reaching {@code nextStopIndex} when the leg is
     *                             untimed or already overrun, or negative for none
     */
    public static double[] secondsToStations(TransitLine line, LineTimings timings,
                                             int serviceDirection, int nextStopIndex,
                                             boolean approaching, long ticksSinceArrival,
                                             double fallbackTicksToNext, double tickSeconds) {
        int count = line.stationCount();
        double[] seconds = new double[count];
        java.util.Arrays.fill(seconds, -1.0D);
        if (timings == null || timings.stationCount() != count
            || nextStopIndex < 0 || nextStopIndex >= count) {
            return seconds;
        }
        int index = nextStopIndex;
        int direction = serviceDirection >= 0 ? 1 : -1;
        double since = Math.max(0L, ticksSinceArrival);
        double ticks;
        if (!approaching) {
            ticks = 0.0D;
        }
        else {
            double leg = timings.legTicks(index, direction);
            ticks = leg >= 0.0D && ticksSinceArrival >= 0 ? leg - since : -1.0D;
            if (ticks < 0.0D) {
                // Untimed, or running late on it: the train's own distance is the better guess.
                ticks = fallbackTicksToNext;
            }
            if (ticks < 0.0D) {
                return seconds;
            }
        }
        seconds[index] = ticks * tickSeconds;
        // Every later leg is timed from the arrival that began it. Standing at a stop, that arrival
        // is this one, already `since` ticks ago; running, it is the next stop, `ticks` from now.
        double clock = approaching ? ticks : -since;
        for (int steps = 0; steps < 2 * count; steps++) {
            int next = index + direction;
            if (line.isLoop()) {
                next = Math.floorMod(next, count);
            }
            else if (next < 0 || next >= count) {
                direction = -direction;
                next = index + direction;
            }
            index = next;
            double leg = timings.legTicks(index, direction);
            if (leg < 0.0D) {
                break;
            }
            clock += leg;
            if (seconds[index] < 0.0D) {
                seconds[index] = Math.max(0.0D, clock) * tickSeconds;
            }
        }
        return seconds;
    }

    /**
     * Seconds until a train {@code distanceRemaining} away, doing {@code speed}, comes to a stop.
     *
     * <p><b>Why time and not distance.</b> A platform announcement should <em>finish</em> as the
     * train pulls in. The station speaker used a fixed 30-block threshold, and a distance is simply
     * not an amount of time: it is however long the train takes to cover it, which depends on how
     * fast it is going and how hard it brakes. On the stock metro preset 30 blocks happens to leave
     * about seven seconds, which is adequate; firm the braking up and the same 30 blocks leaves
     * under five, and the call is still being spoken as the doors open. It also cannot account for
     * how long the sentence itself takes to say.</p>
     *
     * <p>Two regimes, continuous at the boundary:</p>
     * <ul>
     *   <li><b>Already braking</b> ({@code d <= v²/2a}) — constant deceleration to rest covers
     *       {@code d} at a mean of {@code v/2}, so it takes {@code 2d/v}.</li>
     *   <li><b>Still cruising</b> — {@code d/v} to reach the brake point plus {@code v/2a} for the
     *       braking itself, i.e. {@code d/v + v/2a}.</li>
     * </ul>
     *
     * <p>Infinite for a train that is not moving: a train dwelling at the previous platform has no
     * arrival time here yet, and announcing one would be a guess. As it pulls away the estimate
     * falls smoothly from infinity, so the announcement fires the moment it is genuinely due.</p>
     *
     * @param distanceRemaining track distance to the stop point, in blocks; {@code <= 0} means
     *                          it is already there
     * @param speed             current speed in blocks/s; sign is ignored
     * @param brakeDeceleration service braking rate in blocks/s², positive
     */
    public static double secondsToArrival(double distanceRemaining, double speed,
                                          double brakeDeceleration) {
        if (distanceRemaining <= 0.0D) {
            return 0.0D;
        }
        double v = Math.abs(speed);
        if (v <= MOVING_SPEED) {
            return Double.POSITIVE_INFINITY;
        }
        if (brakeDeceleration <= 0.0D) {
            return distanceRemaining / v;
        }
        double brakingDistance = v * v / (2.0D * brakeDeceleration);
        if (distanceRemaining <= brakingDistance) {
            return 2.0D * distanceRemaining / v;
        }
        return distanceRemaining / v + v / (2.0D * brakeDeceleration);
    }

    /** Below this a train counts as stopped rather than approaching slowly. */
    private static final double MOVING_SPEED = 0.05D;
}
