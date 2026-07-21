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
