package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every kind of line can be built by a player, not just by the demo.
 *
 * <p>Found in review: {@code line create} and the transit tool only knew loop and shuttle, so a
 * turnback line — the kind that lets inbound and outbound run at once — could only come from
 * {@code /rcmc metrodemo underground}.</p>
 */
class LineKindTest {

    private static List<TransitStation> stops() {
        return Arrays.asList(new TransitStation("A", new TrackRef(1, 10)),
            new TransitStation("B", new TrackRef(1, 90)));
    }

    @Test
    @DisplayName("each kind a builder can name produces that kind of line")
    void kindsRoundTrip() {
        for (TransitLine.Kind kind : TransitLine.Kind.values()) {
            assertEquals(kind, TransitLine.of("L", stops(), kind).kind());
            assertEquals(kind, TransitLine.Kind.byLabel(kind.label().toUpperCase()));
        }
        assertNull(TransitLine.Kind.byLabel("circle"));
    }

    @Test
    @DisplayName("a turnback line turns round on loops and is not itself a loop")
    void turnbackIsOutAndBack() {
        TransitLine line = TransitLine.of("L", stops(), TransitLine.Kind.TURNBACK);
        assertTrue(line.turnsBackOnLoop());
        assertFalse(line.isLoop());
    }

    @Test
    @DisplayName("the transit tool's V key reaches every kind and comes back round")
    void cycleVisitsAll() {
        TransitLine.Kind kind = TransitLine.Kind.SHUTTLE;
        java.util.Set<TransitLine.Kind> seen = new java.util.HashSet<>();
        for (int i = 0; i < 3; i++) {
            kind = kind.next();
            seen.add(kind);
        }
        assertEquals(3, seen.size());
        assertEquals(TransitLine.Kind.SHUTTLE, kind);
    }
}
