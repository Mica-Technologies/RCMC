package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which side the doors open, and whose left that is.
 *
 * <p>The side is stored against the <b>track</b> and spoken against the <b>train</b>, and those are
 * opposite whenever a service runs the other way down the same track. A shuttle does that at every
 * terminus, so getting it wrong would announce the wrong side on half of all journeys — while
 * looking perfectly correct on the other half, which is the kind of bug that survives testing.</p>
 */
class DoorSideTest {

    @Test
    @DisplayName("running forward, the track's left is the rider's left")
    void forwardIsUnchanged() {
        assertEquals(DoorSide.LEFT, DoorSide.LEFT.asSeenFrom(1.0D));
        assertEquals(DoorSide.RIGHT, DoorSide.RIGHT.asSeenFrom(1.0D));
    }

    @Test
    @DisplayName("running backward, the track's left is the rider's right")
    void backwardSwaps() {
        // The whole reason the side is stored track-relative. A platform does not move when the
        // train turns round.
        assertEquals(DoorSide.RIGHT, DoorSide.LEFT.asSeenFrom(-1.0D));
        assertEquals(DoorSide.LEFT, DoorSide.RIGHT.asSeenFrom(-1.0D));
    }

    @Test
    @DisplayName("both sides are both sides whichever way round the train is")
    void bothIsDirectionless() {
        assertEquals(DoorSide.BOTH, DoorSide.BOTH.asSeenFrom(1.0D));
        assertEquals(DoorSide.BOTH, DoorSide.BOTH.asSeenFrom(-1.0D));
    }

    @Test
    @DisplayName("converting twice returns the original, so a round trip cannot drift")
    void conversionIsAnInvolution() {
        for (DoorSide side : DoorSide.values()) {
            assertEquals(side, side.asSeenFrom(-1.0D).asSeenFrom(-1.0D));
        }
    }

    @Test
    @DisplayName("BOTH opens everything; a single side opens only itself")
    void openingPredicates() {
        assertTrue(DoorSide.BOTH.opensLeft());
        assertTrue(DoorSide.BOTH.opensRight());
        assertTrue(DoorSide.LEFT.opensLeft());
        assertFalse(DoorSide.LEFT.opensRight());
        assertTrue(DoorSide.RIGHT.opensRight());
        assertFalse(DoorSide.RIGHT.opensLeft());
    }

    @Test
    @DisplayName("a station with nothing said about it opens both sides")
    void defaultIsBoth() {
        // Not LEFT, which is ordinal 0 and what a naive read of an older save would produce. Every
        // station opened both sides before the field existed; a door that refuses to open strands
        // a passenger, where one opening onto nothing is merely odd.
        TransitStation station = new TransitStation("Alewife",
            new com.micatechnologies.minecraft.rcmc.track.TrackRef(1, 10.0D));
        assertEquals(DoorSide.BOTH, station.doorSide());
        assertEquals(DoorSide.BOTH, new TransitStation("Alewife",
            new com.micatechnologies.minecraft.rcmc.track.TrackRef(1, 10.0D), null).doorSide());
    }

    @Test
    @DisplayName("an out-of-range ordinal is clamped rather than throwing on a corrupt save")
    void ordinalsAreClamped() {
        assertEquals(DoorSide.LEFT, DoorSide.byOrdinal(-5));
        assertEquals(DoorSide.BOTH, DoorSide.byOrdinal(99));
    }

    @Test
    @DisplayName("command arguments parse, and nonsense parses to nothing")
    void parsing() {
        assertEquals(DoorSide.LEFT, DoorSide.parse("left"));
        assertEquals(DoorSide.RIGHT, DoorSide.parse("RIGHT"));
        assertEquals(DoorSide.BOTH, DoorSide.parse("Both"));
        assertNull(DoorSide.parse("sideways"));
        assertNull(DoorSide.parse(null));
    }

    @Test
    @DisplayName("the entering announcement names the station and the side")
    void enteringAnnouncementText() {
        assertEquals("Entering Alewife. The doors will open on the left.",
            TransitSignText.enteringAnnouncement("Alewife", DoorSide.LEFT));
        assertEquals("Entering Harbor. The doors will open on the right.",
            TransitSignText.enteringAnnouncement("Harbor", DoorSide.RIGHT));
        assertEquals("Entering Central. The doors will open on both sides.",
            TransitSignText.enteringAnnouncement("Central", DoorSide.BOTH));
    }

    @Test
    @DisplayName("a station keeps everything but its side when the side is authored")
    void withDoorSidePreservesTheRest() {
        TransitStation station = new TransitStation("Harbor",
            new com.micatechnologies.minecraft.rcmc.track.TrackRef(3, 42.5D));
        TransitStation sided = station.withDoorSide(DoorSide.RIGHT);

        assertEquals("Harbor", sided.name());
        assertEquals(3, sided.stopPoint().sectionId());
        assertEquals(42.5D, sided.stopPoint().distance(), 1e-9D);
        assertEquals(DoorSide.RIGHT, sided.doorSide());
        assertEquals(DoorSide.BOTH, station.doorSide(), "the original must be untouched");
    }
}
