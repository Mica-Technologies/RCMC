package com.micatechnologies.minecraft.rcmc.track;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The known track style ids — the vocabulary a section's {@code styleId} may use.
 *
 * <p>Lives in common code, deliberately split from what the styles <em>look like</em>: the
 * geometry lives client-side in {@code client.render.track.TrackStyles}, because appearance is
 * presentation, but the <em>names</em> must be validated by the server-side command and stored
 * in server-side sections — a stray {@code net.minecraft.client} reference from there is
 * exactly the class of bug the side rules exist to stop. Physics never reads a style at all: a
 * wider-looking track is still the same spline with the same one degree of freedom.</p>
 *
 * <p>The transit styles come in gauge-only and electrified flavours — the catenary is a style
 * choice per section, not a mod-wide toggle, so one park can have an electrified metro next to
 * a bare coaster (and a catenary-free subway next to that). {@code null} is the classic coaster
 * look and remains the default everywhere.</p>
 */
public final class TrackStyleIds {

    /** Wider transit track, matching the ~3-block-wide metro stock. No electrification. */
    public static final String TRANSIT = "transit";

    /** Transit track with single-pole catenary masts — light-rail / open-air metro look. */
    public static final String TRANSIT_CATENARY = "transit-catenary";

    /** Transit track with portal (gantry) frames spanning it — mainline electrification look. */
    public static final String TRANSIT_PORTAL = "transit-portal";

    /** Transit track with a rigid overhead conductor rail — tunnel electrification look. */
    public static final String TRANSIT_TUNNEL = "transit-tunnel";

    /** What {@code /rcmc style} accepts; "coaster" resolves to the {@code null} default style. */
    public static final List<String> COMMAND_CHOICES = Arrays.asList(
        "coaster", TRANSIT, TRANSIT_CATENARY, TRANSIT_PORTAL, TRANSIT_TUNNEL);

    private TrackStyleIds() {
        throw new AssertionError("No instances.");
    }

    /**
     * Normalises a command argument to a storable style id: {@code "coaster"} (and blank) to
     * {@code null}, known ids to themselves, anything else to an exception.
     */
    public static String resolve(String argument) {
        if (argument == null || argument.isEmpty() || "coaster".equalsIgnoreCase(argument)) {
            return null;
        }
        String key = argument.toLowerCase(Locale.ROOT);
        if (TRANSIT.equals(key) || TRANSIT_CATENARY.equals(key)
            || TRANSIT_PORTAL.equals(key) || TRANSIT_TUNNEL.equals(key)) {
            return key;
        }
        throw new IllegalArgumentException(
            "Unknown track style '" + argument + "' — one of " + COMMAND_CHOICES);
    }

    /** Whether this style id is one of the transit family (wider gauge). */
    public static boolean isTransit(String styleId) {
        return styleId != null && styleId.startsWith(TRANSIT);
    }
}
