package com.micatechnologies.minecraft.rcmc.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The text form of an alignment that {@code /rcmc track lay} reads and {@code /rcmc track nodes}
 * writes. What matters: coordinates come through exactly (no snapping), every malformed node is
 * refused with its number, and what is written lays back as the same section.
 */
class PolylineTest {

    @Test
    @DisplayName("coordinates are taken exactly, off the block grid")
    void exactCoordinates() {
        List<Polyline.Point> points = Polyline.parse("55.25,47,-2562.75;63.1,47.05,-2523");
        assertEquals(2, points.size());
        assertEquals(new Vec3(55.25, 47, -2562.75), points.get(0).position);
        assertEquals(new Vec3(63.1, 47.05, -2523), points.get(1).position);
        assertEquals(0.0D, points.get(0).bankDegrees);
        assertNull(points.get(0).type);
    }

    @Test
    @DisplayName("bank and segment type are optional per node; separators may be ; or newlines, with spaces and comments")
    void optionalFieldsAndLayout() {
        List<Polyline.Point> points = Polyline.parse(
            "# Blue Line, track 1\n"
                + "0, 47, 0\n"
                + "10,47,0,15 ; 20,47,0,-7.5@station\n"
                + "\n"
                + "30,47,0 @Block-Brake  # end of platform\n");
        assertEquals(4, points.size());
        assertEquals(15.0D, points.get(1).bankDegrees);
        assertEquals(-7.5D, points.get(2).bankDegrees);
        assertEquals("station", points.get(2).type);
        assertEquals("block-brake", points.get(3).type);
    }

    @Test
    @DisplayName("a malformed node is refused, naming which one")
    void malformedNodesAreRefusedByNumber() {
        IllegalArgumentException twoFields = assertThrows(IllegalArgumentException.class,
            () -> Polyline.parse("0,0,0;1,2"));
        assertTrue(twoFields.getMessage().startsWith("Node 2"), twoFields.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Polyline.parse("0,0,abc"));
        assertThrows(IllegalArgumentException.class, () -> Polyline.parse("0,0,NaN"));
        assertThrows(IllegalArgumentException.class, () -> Polyline.parse("0,0,Infinity"));
        assertThrows(IllegalArgumentException.class, () -> Polyline.parse("0,0,0,200"));
        assertThrows(IllegalArgumentException.class, () -> Polyline.parse("0,0,0@"));
        assertThrows(IllegalArgumentException.class, () -> Polyline.parse(" ; # nothing\n"));
    }

    @Test
    @DisplayName("what nodes writes, lay reads back as the same section, to the bit")
    void writeThenParseRoundTrips() {
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double a = 2.0D * Math.PI * i / 12;
            // Values with long binary expansions, and a bank on some nodes only.
            nodes.add(new TrackNode(new Vec3(Math.cos(a) * 50.0D + 0.1D, 47.0D + i / 3.0D,
                Math.sin(a) * 50.0D - 6326.7D), i % 3 == 0 ? 12.5D + i / 7.0D : 0.0D, null));
        }
        TrackSection section = new TrackSection(13, nodes, true, "transit");

        List<Polyline.Point> back = Polyline.parse(Polyline.write(section));
        assertEquals(nodes.size(), back.size());
        for (int i = 0; i < nodes.size(); i++) {
            assertEquals(nodes.get(i).position(), back.get(i).position, "node " + i);
            assertEquals(nodes.get(i).bankDegrees(), back.get(i).bankDegrees, "bank " + i);
        }
    }
}
