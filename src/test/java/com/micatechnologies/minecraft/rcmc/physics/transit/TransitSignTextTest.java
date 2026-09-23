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
        assertEquals("BRD", TransitSignText.stopsLabel(0, true));
        assertEquals("APPR", TransitSignText.stopsLabel(0, false),
            "abbreviated the way a real board does — it was the longest phrase here and the "
                + "one that still forced a row to page");
        assertEquals("1 stop", TransitSignText.stopsLabel(1, false));
        assertEquals("3 stops", TransitSignText.stopsLabel(3, false));
        assertNull(TransitSignText.stopsLabel(-1, false), "a service that never reaches here");
    }

    @Test
    @DisplayName("a board names the berth only for a train due at this very station")
    void stopsLabelNamesTheBerth() {
        assertEquals("BRD (2)",
            TransitSignText.stopsLabel(0, true, "2", "OUTBOUND"),
            "a train berthed here is at a berth this board can send you to");
        assertEquals("APPR (2)",
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

        assertEquals("BRD", TransitSignText.stopsLabel(0, true, "", "OUTBOUND"),
            "a single-platform station labels nothing, so there is nothing to add");
        assertEquals("BRD", TransitSignText.stopsLabel(0, true, null, "OUTBOUND"));
        assertEquals("BRD", TransitSignText.stopsLabel(0, true, "Outbound", "OUTBOUND"),
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

    @Test
    @DisplayName("at a station with several platforms the arrival call says which one")
    void announcementNamesThePlatform() {
        TransitLine line = new TransitLine("Circle", java.util.Arrays.asList(
            new TransitStation("Kingsway", new com.micatechnologies.minecraft.rcmc.track.TrackRef(1, 0.0D)),
            new TransitStation("Lakeshore", new com.micatechnologies.minecraft.rcmc.track.TrackRef(1, 50.0D))),
            false, "INBOUND", "OUTBOUND");
        String approaching = TransitSignText.announcement(line, 1, 0, false, "2");
        String arriving = TransitSignText.announcement(line, 1, 0, true, "2");
        org.junit.jupiter.api.Assertions.assertTrue(approaching.endsWith("is now approaching platform 2."), approaching);
        org.junit.jupiter.api.Assertions.assertTrue(arriving.endsWith("is now arriving at platform 2."), arriving);
        org.junit.jupiter.api.Assertions.assertFalse(
            TransitSignText.announcement(line, 1, 1, false, "2").contains("platform"),
            "a train further out has not chosen its platform here yet");
        org.junit.jupiter.api.Assertions.assertEquals(TransitSignText.announcement(line, 1, 0, false),
            TransitSignText.announcement(line, 1, 0, false, ""), "no label, no platform");
    }

    @Test
    @DisplayName("a line-map sign always shows its own station, and never more rows than it has")
    void stopWindowKeepsHereInView() {
        for (int count = 1; count <= 30; count++) {
            for (int here = 0; here < count; here++) {
                for (int rows = 3; rows <= 10; rows++) {
                    int[] w = TransitSignText.stopWindow(count, here, rows);
                    String at = count + " stops, here " + here + ", " + rows + " rows: " + w[0] + ".." + w[1];
                    org.junit.jupiter.api.Assertions.assertTrue(w[0] <= here && here <= w[1], "here cut off — " + at);
                    int used = (w[1] - w[0] + 1) + (w[0] > 0 ? 1 : 0) + (w[1] < count - 1 ? 1 : 0);
                    org.junit.jupiter.api.Assertions.assertTrue(used <= rows, "overflows — " + at);
                    org.junit.jupiter.api.Assertions.assertTrue(count > rows || (w[0] == 0 && w[1] == count - 1),
                        "a line that fits is shown whole — " + at);
                }
            }
        }
        // The case that was wrong: the 14th stop of 20, on a ten-row sign.
        int[] w = TransitSignText.stopWindow(20, 13, 10);
        org.junit.jupiter.api.Assertions.assertTrue(w[0] <= 13 && 13 <= w[1]);
    }
}
