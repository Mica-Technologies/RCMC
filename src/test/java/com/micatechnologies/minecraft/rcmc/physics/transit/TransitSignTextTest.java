package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The words on every transit display, pinned here so a board and a speaker can never drift apart —
 * the whole reason {@link TransitSignText} exists is that they must describe the same train the
 * same way.
 */
class TransitSignTextTest {

    private static TransitStation station(String name) {
        // A station is a named point on the track; the section/distance is irrelevant to signage.
        return new TransitStation(name, new TrackRef(1, 0.0D));
    }

    private static TransitLine redLine() {
        List<TransitStation> stops = Arrays.asList(
            station("Ashmont"), station("JFK"), station("Downtown"), station("Alewife"));
        return new TransitLine("Red Line", stops, false, "INBOUND", "OUTBOUND");
    }

    private static TransitLine loopLine() {
        List<TransitStation> stops = Arrays.asList(
            station("A"), station("B"), station("C"));
        return new TransitLine("Loop", stops, true);
    }

    @Test
    @DisplayName("terminus is the far end in the current direction; a loop has none")
    void terminus() {
        TransitLine red = redLine();
        assertEquals("Alewife", red.terminusName(1), "outbound runs to the last station");
        assertEquals("Ashmont", red.terminusName(-1), "inbound runs to the first station");
        assertNull(loopLine().terminusName(1), "a loop has no terminus");
    }

    @Test
    @DisplayName("board groups by direction and destination")
    void destinationLabel() {
        assertEquals("OUT/Alewife", TransitSignText.destinationLabel(redLine(), 1),
            "the direction is cut to its stem so the terminus gets the room");
        assertEquals("IN/Ashmont", TransitSignText.destinationLabel(redLine(), -1));
        assertEquals("OUT", TransitSignText.destinationLabel(loopLine(), 1),
            "a loop falls back to the bare direction label");
    }

    @Test
    @DisplayName("a direction label that is not <X>BOUND is left exactly as authored")
    void unusualDirectionLabelsAreNotGuessedAt() {
        List<TransitStation> stops = Arrays.asList(station("Downtown"), station("Airport"));
        TransitLine line = new TransitLine("Express", stops, false, "toward Downtown",
            "toward Airport");
        assertEquals("toward Airport/Airport", TransitSignText.destinationLabel(line, 1),
            "there is no rule that shortens this without guessing, so it is not shortened");
    }

    @Test
    @DisplayName("exterior car sign shows the terminus in caps")
    void exterior() {
        assertEquals("ALEWIFE", TransitSignText.exteriorDestination(redLine(), 1));
        assertEquals("LOOP", TransitSignText.exteriorDestination(loopLine(), 1),
            "a loop shows its own name");
    }

    @Test
    @DisplayName("stop-count phrasing matches across the board's raw scale")
    void stopsLabel() {
        assertEquals("Boarding", TransitSignText.stopsLabel(0, true));
        assertEquals("Approaching", TransitSignText.stopsLabel(0, false));
        assertEquals("1 stop", TransitSignText.stopsLabel(1, false));
        assertEquals("3 stops", TransitSignText.stopsLabel(3, false));
        assertNull(TransitSignText.stopsLabel(-1, false), "a service that never reaches here");
    }

    @Test
    @DisplayName("a board names the berth only for a train due at this very station")
    void stopsLabelNamesTheBerth() {
        assertEquals("Boarding (2)",
            TransitSignText.stopsLabel(0, true, "2", "OUTBOUND"),
            "a train berthed here is at a berth this board can send you to");
        assertEquals("Approaching (2)",
            TransitSignText.stopsLabel(0, false, "2", "OUTBOUND"),
            "so is one whose next stop is here");

        // The crux. A service resolves its berth at the stop it is running to, so for a train
        // still stops away the label names a platform somewhere else on the line entirely.
        // Printing it would march riders across the concourse on somebody else's platform number.
        assertEquals("1 stop",
            TransitSignText.stopsLabel(1, false, "2", "OUTBOUND"),
            "a train one stop out has resolved a berth at that other station, not at this one");
        assertEquals("3 stops",
            TransitSignText.stopsLabel(3, false, "2", "OUTBOUND"));

        assertEquals("Boarding", TransitSignText.stopsLabel(0, true, "", "OUTBOUND"),
            "a single-platform station labels nothing, so there is nothing to add");
        assertEquals("Boarding", TransitSignText.stopsLabel(0, true, null, "OUTBOUND"));
        assertEquals("Boarding", TransitSignText.stopsLabel(0, true, "Outbound", "OUTBOUND"),
            "a berth named after the direction only repeats the heading the row sits under");
        assertNull(TransitSignText.stopsLabel(-1, false, "2", "OUTBOUND"),
            "a service that never reaches here has no row at all, berth or no berth");
    }

    @Test
    @DisplayName("in-car announcements name the stop the train is running to / has reached")
    void inCarAnnouncements() {
        assertEquals("Next stop: Alewife.",
            TransitSignText.nextStopAnnouncement("Alewife"),
            "spoken shortly after departure, naming the next stop");
        assertEquals("This is Downtown.",
            TransitSignText.arrivalAnnouncement("Downtown"),
            "spoken as the doors open, naming the station just reached");
    }

    @Test
    @DisplayName("the spoken announcement names line, direction, terminus and closeness")
    void announcement() {
        TransitLine red = redLine();
        assertEquals("The next OUTBOUND Red Line train to Alewife is now approaching.",
            TransitSignText.announcement(red, 1, 0, false));
        assertEquals("The next OUTBOUND Red Line train to Alewife is now arriving.",
            TransitSignText.announcement(red, 1, 0, true));
        assertEquals("The next OUTBOUND Red Line train to Alewife is one stop away.",
            TransitSignText.announcement(red, 1, 1, false));
        assertEquals("The next INBOUND Red Line train to Ashmont is 2 stops away.",
            TransitSignText.announcement(red, -1, 2, false));
        assertNull(TransitSignText.announcement(red, 1, -1, false));
    }
}
