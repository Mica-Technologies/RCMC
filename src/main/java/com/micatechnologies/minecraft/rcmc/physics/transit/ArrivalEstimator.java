package com.micatechnologies.minecraft.rcmc.physics.transit;

/**
 * Answers the arrival board's one question: how many stops away is a train from a station?
 *
 * <p>"Stops away" rather than minutes, on purpose. It is exact and deterministic — computed by
 * replaying the service pattern ({@link LineService#advanceToNextStop}'s exact stepping rules:
 * loop wrap, shuttle terminus bounce) from the train's next stop until it lands on the queried
 * station — where a minutes estimate would need speeds, dwell assumptions and a wall clock. A
 * minutes overlay can come later as pure presentation; this stays the truth underneath it.</p>
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
