package com.micatechnologies.minecraft.rcmc.physics.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Retyping one span of track in the editor gives that span over to the new hardware, and leaves
 * whatever else was laid either side of it alone.
 */
class CutAroundTest {

    private static final double TICK = 0.05D;

    @Test
    @DisplayName("retyping the middle span of a lift leaves a lift on the spans either side")
    void liftKeepsBothEnds() {
        ChainLift lift = new ChainLift(1, 10.0D, 70.0D, 5.0D, 12.0D, TICK);
        List<RideElement> left = RideElements.cutAround(lift, 1, 30.0D, 50.0D, TICK);

        assertEquals(2, left.size(), "both ends of the lift should remain");
        assertSpan(left.get(0), 10.0D, 30.0D);
        assertSpan(left.get(1), 50.0D, 70.0D);
        for (RideElement piece : left) {
            assertTrue(piece instanceof ChainLift, "a piece of a lift is still a lift");
            assertEquals(5.0D, ((ChainLift) piece).chainSpeed(), 1e-9, "the chain keeps its speed");
        }
    }

    @Test
    @DisplayName("retyping the first span of a two-span station leaves the station on the second")
    void stationLosesOnlyTheRetypedSpan() {
        StationPlatform station = new StationPlatform(1, 0.0D, 50.0D, 47.0D, 6.0D, 60, 4.0D, 6.0D, TICK);
        List<RideElement> left = RideElements.cutAround(station, 1, 0.0D, 25.0D, TICK);

        assertEquals(1, left.size());
        assertSpan(left.get(0), 25.0D, 50.0D);
        assertEquals(47.0D, ((StationPlatform) left.get(0)).stopDistance(), 1e-9,
            "the stop point was not in the cut, so it stays where it was");
    }

    @Test
    @DisplayName("a station cut in the middle keeps only the half its trains stop in")
    void stationIsNeverSplitInTwo() {
        StationPlatform station = new StationPlatform(1, 0.0D, 60.0D, 55.0D, 6.0D, 60, 4.0D, 6.0D, TICK);
        List<RideElement> left = RideElements.cutAround(station, 1, 20.0D, 30.0D, TICK);

        assertEquals(1, left.size(), "two platforms from one would be two stations");
        assertSpan(left.get(0), 30.0D, 60.0D);
    }

    @Test
    @DisplayName("a station cut through its stop keeps the longer piece, stopping trains at its exit")
    void stationCutThroughItsStopStopsAtTheExit() {
        StationPlatform station = new StationPlatform(1, 0.0D, 60.0D, 12.0D, 6.0D, 60, 4.0D, 6.0D, TICK);
        List<RideElement> left = RideElements.cutAround(station, 1, 5.0D, 20.0D, TICK);

        assertEquals(1, left.size());
        assertSpan(left.get(0), 20.0D, 60.0D);
        assertEquals(60.0D, ((StationPlatform) left.get(0)).stopDistance(), 1e-9,
            "a stop at the piece's entry leaves the train hanging out of the platform");
    }

    @Test
    @DisplayName("hardware wholly inside the retyped span goes, and hardware elsewhere is untouched")
    void insideGoesOutsideStays() {
        BrakeRun inside = new BrakeRun(1, 32.0D, 48.0D, 6.0D, 6.0D, BrakeRun.Mode.TRIM, TICK);
        assertTrue(RideElements.cutAround(inside, 1, 30.0D, 50.0D, TICK).isEmpty());

        BrakeRun elsewhere = new BrakeRun(1, 60.0D, 80.0D, 6.0D, 6.0D, BrakeRun.Mode.TRIM, TICK);
        assertSame(elsewhere, RideElements.cutAround(elsewhere, 1, 30.0D, 50.0D, TICK).get(0));

        BrakeRun otherSection = new BrakeRun(2, 32.0D, 48.0D, 6.0D, 6.0D, BrakeRun.Mode.TRIM, TICK);
        assertSame(otherSection, RideElements.cutAround(otherSection, 1, 30.0D, 50.0D, TICK).get(0));
    }

    @Test
    @DisplayName("a station on turned-round track still stops trains the same distance short of its exit")
    void turnedRoundStationKeepsItsStopBeforeTheExit() {
        // Trains ran 0 -> 50 and stopped at 47, three blocks short of the exit at 50. Turned round,
        // the platform is at 100..150 of the new track and trains leave at 150.
        StationPlatform station = new StationPlatform(1, 0.0D, 50.0D, 47.0D, 6.0D, 60, 4.0D, 6.0D, TICK);
        RideElement turned = RideElements.relocated(station, 1, 150.0D, 100.0D, true, d -> d, TICK);

        assertSpan(turned, 100.0D, 150.0D);
        assertEquals(147.0D, ((StationPlatform) turned).stopDistance(), 1e-9);
    }

    @Test
    @DisplayName("hardware moved to another section keeps its settings")
    void relocatedHardwareKeepsItsSettings() {
        ChainLift lift = new ChainLift(1, 10.0D, 40.0D, 5.0D, 12.0D, TICK);
        RideElement moved = RideElements.relocated(lift, 7, 0.0D, 30.0D, false, d -> d, TICK);

        assertEquals(7, moved.sectionId());
        assertSpan(moved, 0.0D, 30.0D);
        assertEquals(5.0D, ((ChainLift) moved).chainSpeed(), 1e-9);
    }

    private static void assertSpan(RideElement element, double start, double end) {
        assertEquals(start, element.startDistance(), 1e-9, "start");
        assertEquals(end, element.endDistance(), 1e-9, "end");
    }
}
