package com.micatechnologies.minecraft.rcmc.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Per-section contact wire height, carried as a suffix on the style id.
 *
 * <p>The suffix is load-bearing in two directions at once: it must be authored and validated at the
 * command seam, and it must be read by a renderer that may never throw on a style id from a save.
 * Both halves are tested here, including the ones that decide what an <em>older</em> save means.</p>
 */
class TrackStyleWireHeightTest {

    @Test
    @DisplayName("a style with no suffix uses the default height")
    void defaultHeight() {
        assertEquals(TrackStyleIds.DEFAULT_CONTACT_WIRE_HEIGHT,
            TrackStyleIds.contactWireHeight(TrackStyleIds.TRANSIT_CATENARY), 1e-9D);
        assertEquals(TrackStyleIds.DEFAULT_CONTACT_WIRE_HEIGHT,
            TrackStyleIds.contactWireHeight(TrackStyleIds.TRANSIT_PORTAL), 1e-9D);
    }

    @Test
    @DisplayName("the default overhead wire is 10 blocks up")
    void defaultIsTen() {
        assertEquals(10.0D, TrackStyleIds.DEFAULT_CONTACT_WIRE_HEIGHT, 1e-9D);
    }

    @Test
    @DisplayName("a tunnel's rigid conductor hangs lower than open-air catenary")
    void tunnelHangsLower() {
        double tunnel = TrackStyleIds.contactWireHeight(TrackStyleIds.TRANSIT_TUNNEL);

        assertEquals(TrackStyleIds.DEFAULT_TUNNEL_WIRE_HEIGHT, tunnel, 1e-9D);
        assertTrue(tunnel < TrackStyleIds.DEFAULT_CONTACT_WIRE_HEIGHT,
            "a tunnel has no room above the train — that is the point of the style");
    }

    @Test
    @DisplayName("styles with no overhead wire report no height")
    void unelectrifiedStylesHaveNoWire() {
        assertEquals(0.0D, TrackStyleIds.contactWireHeight(TrackStyleIds.TRANSIT), 1e-9D);
        assertEquals(0.0D, TrackStyleIds.contactWireHeight(null), 1e-9D);
    }

    @Test
    @DisplayName("an authored height is read back")
    void authoredHeightRoundTrips() {
        String styled = TrackStyleIds.withWireHeight(TrackStyleIds.TRANSIT_CATENARY, 12.0D);

        assertEquals(12.0D, TrackStyleIds.contactWireHeight(styled), 1e-9D);
        assertEquals(TrackStyleIds.TRANSIT_CATENARY, TrackStyleIds.baseOf(styled),
            "the look must survive the height being attached to it");
    }

    @Test
    @DisplayName("the base style id is not mistaken for a height suffix")
    void baseIdIsNotSplitOnItsOwnDash() {
        assertEquals(TrackStyleIds.TRANSIT_CATENARY,
            TrackStyleIds.baseOf(TrackStyleIds.TRANSIT_CATENARY),
            "'transit-catenary' must not degrade to 'transit' — that would silently drop the wires");
        assertEquals(TrackStyleIds.TRANSIT, TrackStyleIds.baseOf(TrackStyleIds.TRANSIT));
    }

    @Test
    @DisplayName("heights outside the allowed range are refused when authored")
    void outOfRangeHeightsAreRefused() {
        assertThrows(IllegalArgumentException.class,
            () -> TrackStyleIds.withWireHeight(TrackStyleIds.TRANSIT_CATENARY, 3.0D));
        assertThrows(IllegalArgumentException.class,
            () -> TrackStyleIds.withWireHeight(TrackStyleIds.TRANSIT_CATENARY, 40.0D));
    }

    @Test
    @DisplayName("a style with no wire cannot be given a height")
    void unelectrifiedStyleRefusesAHeight() {
        assertThrows(IllegalArgumentException.class,
            () -> TrackStyleIds.withWireHeight(TrackStyleIds.TRANSIT, 10.0D));
        assertThrows(IllegalArgumentException.class,
            () -> TrackStyleIds.resolve(TrackStyleIds.TRANSIT + "-10"));
    }

    @Test
    @DisplayName("the command accepts a suffixed style, and rejects a nonsense one")
    void resolveValidatesSuffixes() {
        assertEquals("transit-catenary-8", TrackStyleIds.resolve("transit-catenary-8"));
        assertEquals("transit-catenary-8", TrackStyleIds.resolve("TRANSIT-CATENARY-8"));
        assertThrows(IllegalArgumentException.class,
            () -> TrackStyleIds.resolve("transit-catenary-99"));
        assertThrows(IllegalArgumentException.class, () -> TrackStyleIds.resolve("monorail"));
        assertNull(TrackStyleIds.resolve("coaster"));
    }

    @Test
    @DisplayName("a garbled height from a save falls back rather than throwing mid-render")
    void garbledHeightDegrades() {
        // A renderer must never throw on a style id it does not understand — a save from a newer
        // build should degrade to something drawable.
        assertEquals(TrackStyleIds.DEFAULT_CONTACT_WIRE_HEIGHT,
            TrackStyleIds.contactWireHeight("transit-catenary-banana"), 1e-9D);
        assertEquals(TrackStyleIds.DEFAULT_CONTACT_WIRE_HEIGHT,
            TrackStyleIds.contactWireHeight("transit-catenary-999"), 1e-9D);
    }

    @Test
    @DisplayName("every authored height clears the metro car roof")
    void everyAllowedHeightClearsTheStock() {
        // Measured against the stock's own height rather than a copy of it, so raising the car —
        // as the underframe change did — makes this test object rather than quietly pass.
        double roof = com.micatechnologies.minecraft.rcmc.physics.CarSeating.METRO_ROOF_HEIGHT;
        assertTrue(TrackStyleIds.MIN_CONTACT_WIRE_HEIGHT > roof,
            "the minimum wire height must clear the car roof");
        assertTrue(TrackStyleIds.DEFAULT_TUNNEL_WIRE_HEIGHT > roof,
            "even the tunnel conductor must clear the car roof");
    }
}
