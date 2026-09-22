package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArrivalEstimatorTest {

    private static TransitLine line(boolean loop) {
        return new TransitLine("L", Arrays.asList(
            new TransitStation("A", new TrackRef(1, 100.0D)),
            new TransitStation("B", new TrackRef(1, 300.0D)),
            new TransitStation("C", new TrackRef(1, 500.0D)),
            new TransitStation("D", new TrackRef(1, 700.0D))), loop);
    }

    @Test
    @DisplayName("zero means the queried station is the very next stop")
    void zeroMeansNextStop() {
        assertEquals(0, ArrivalEstimator.stopsAway(line(false), 1, 2, 2));
    }

    @Test
    @DisplayName("counting forward along the service direction is a plain difference")
    void forwardCount() {
        assertEquals(2, ArrivalEstimator.stopsAway(line(false), 1, 1, 3));
        assertEquals(2, ArrivalEstimator.stopsAway(line(false), -1, 2, 0));
    }

    @Test
    @DisplayName("a shuttle service reaches a station behind it by bouncing off the terminus")
    void shuttleBouncesAtTheTerminus() {
        // Outbound at C, asking about A: C -> D (1) -> reverse -> C (2) -> B (3) -> A (4).
        assertEquals(4, ArrivalEstimator.stopsAway(line(false), 1, 2, 0));
        // Inbound at B, asking about D: B -> A (1) -> reverse -> B (2) -> C (3) -> D (4).
        assertEquals(4, ArrivalEstimator.stopsAway(line(false), -1, 1, 3));
    }

    @Test
    @DisplayName("a loop service reaches a station behind it by going the long way around")
    void loopWrapsAround() {
        // Forward at C on a loop, asking about A: C -> D (1) -> A (2).
        assertEquals(2, ArrivalEstimator.stopsAway(line(true), 1, 2, 0));
        assertEquals(3, ArrivalEstimator.stopsAway(line(true), 1, 1, 0));
    }

    /**
     * Found in play on the Circle Line: a train heading inbound past Exchange was listed on
     * Exchange's board under IN, then vanished from IN and reappeared under OUT once it had turned
     * back at the terminus. It was going to call at Exchange outbound all along; the board filed it
     * under the direction it was travelling at the time, not the one it would arrive in.
     */
    @Test
    @DisplayName("a train that turns back before it gets here arrives in the other direction")
    void arrivalDirectionFollowsTheTurnback() {
        // Outbound (+1) running to C, asking about A: it turns back at D and arrives inbound.
        assertEquals(-1, ArrivalEstimator.arrivalDirection(line(false), 1, 2, 0));
        // Inbound (-1) running to B, asking about D: it turns back at A and arrives outbound.
        assertEquals(1, ArrivalEstimator.arrivalDirection(line(false), -1, 1, 3));
        // The Circle case itself: inbound, running to A, asking about B, the stop it just left.
        assertEquals(1, ArrivalEstimator.arrivalDirection(line(false), -1, 0, 1));
    }

    @Test
    @DisplayName("a train that reaches here without turning back arrives the way it is going")
    void arrivalDirectionWithoutATurnback() {
        assertEquals(1, ArrivalEstimator.arrivalDirection(line(false), 1, 1, 3));
        assertEquals(-1, ArrivalEstimator.arrivalDirection(line(false), -1, 2, 0));
        assertEquals(1, ArrivalEstimator.arrivalDirection(line(false), 1, 2, 2),
            "this station next: the current direction");
    }

    @Test
    @DisplayName("a terminus is arrived at in the direction that runs into it")
    void arrivalDirectionAtATerminus() {
        assertEquals(1, ArrivalEstimator.arrivalDirection(line(false), 1, 1, 3));
        assertEquals(-1, ArrivalEstimator.arrivalDirection(line(false), 1, 2, 0));
    }

    @Test
    @DisplayName("a loop has no turnback, so the direction never changes")
    void arrivalDirectionOnALoop() {
        assertEquals(1, ArrivalEstimator.arrivalDirection(line(true), 1, 2, 0));
        assertEquals(-1, ArrivalEstimator.arrivalDirection(line(true), -1, 1, 3));
    }

    @Test
    @DisplayName("an unreachable station has no arrival direction")
    void arrivalDirectionUnreachable() {
        assertEquals(0, ArrivalEstimator.arrivalDirection(line(false), 1, 9, 0));
    }

    @Test
    @DisplayName("out-of-range indices answer -1 rather than throwing in a render path")
    void outOfRangeAnswersMinusOne() {
        assertEquals(-1, ArrivalEstimator.stopsAway(line(false), 1, 9, 0));
        assertEquals(-1, ArrivalEstimator.stopsAway(line(false), 1, 0, 9));
    }

    @Test
    @DisplayName("indexOfStation resolves names case-insensitively")
    void indexOfStationIsCaseInsensitive() {
        assertEquals(2, line(false).indexOfStation("c"));
        assertEquals(-1, line(false).indexOfStation("nowhere"));
    }
}
