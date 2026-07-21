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
        if (line == null) {
            throw new IllegalArgumentException("line is required");
        }
        int count = line.stationCount();
        if (nextStopIndex < 0 || nextStopIndex >= count
            || stationIndex < 0 || stationIndex >= count) {
            return -1;
        }
        int index = nextStopIndex;
        int direction = serviceDirection >= 0 ? 1 : -1;
        // A full out-and-back visits every stop at most twice; 2·count steps covers any pattern.
        for (int steps = 0; steps <= 2 * count; steps++) {
            if (index == stationIndex) {
                return steps;
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
        return -1;
    }
}
