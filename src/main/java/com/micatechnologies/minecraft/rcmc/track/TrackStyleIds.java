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

    /**
     * Height of the contact wire above the railheads, in blocks, when a style does not say.
     *
     * <p>Ten, raised from six once the metro stock grew to a ~5-block roof: a wire that clears the
     * cars by only a block reads as a low-slung tram wire rather than as railway
     * electrification.</p>
     */
    public static final double DEFAULT_CONTACT_WIRE_HEIGHT = 10.0D;

    /**
     * Default for the tunnel style's rigid conductor, which deliberately hangs lower than open-air
     * catenary — a tunnel has no room above the train, which is the whole reason the style exists.
     * Still comfortably clear of the car roof.
     */
    public static final double DEFAULT_TUNNEL_WIRE_HEIGHT = 7.0D;

    /**
     * Bounds on an authored wire height.
     *
     * <p>The minimum is set by the stock, not by taste: a metro roof stands at
     * {@code CarSeating.METRO_ROOF_HEIGHT} and its pantograph base sits higher again, so a wire
     * much under 7 would pass through the hardware meant to collect from it. Raised from 6 when
     * the underframe went up a full block — the clearance is checked by a test for exactly this
     * reason.</p>
     */
    public static final double MIN_CONTACT_WIRE_HEIGHT = 7.0D;
    public static final double MAX_CONTACT_WIRE_HEIGHT = 15.0D;

    private TrackStyleIds() {
        throw new AssertionError("No instances.");
    }

    /**
     * Normalises a command argument to a storable style id: {@code "coaster"} (and blank) to
     * {@code null}, known ids to themselves, anything else to an exception.
     *
     * <p>An electrified style may carry a wire height as a suffix — {@code transit-catenary-12} —
     * which is how a per-section height is stored without adding a field to every section and a
     * version to the track codec. It is a string because {@code styleId} already is one, and
     * because a style with no suffix must keep meaning exactly what it meant in older saves.</p>
     */
    public static String resolve(String argument) {
        if (argument == null || argument.isEmpty() || "coaster".equalsIgnoreCase(argument)) {
            return null;
        }
        String key = argument.toLowerCase(Locale.ROOT);
        String base = baseOf(key);
        if (!TRANSIT.equals(base) && !TRANSIT_CATENARY.equals(base)
            && !TRANSIT_PORTAL.equals(base) && !TRANSIT_TUNNEL.equals(base)) {
            throw new IllegalArgumentException(
                "Unknown track style '" + argument + "' — one of " + COMMAND_CHOICES);
        }
        if (base.length() != key.length()) {
            if (TRANSIT.equals(base)) {
                throw new IllegalArgumentException(
                    "Style '" + TRANSIT + "' has no overhead wire, so it takes no height");
            }
            // Validate the suffix now, at the authoring seam, rather than letting a nonsense
            // height reach the renderer and silently fall back to the default.
            heightSuffixOf(key);
        }
        return key;
    }

    /**
     * Builds a style id carrying an explicit wire height, validating it.
     *
     * @throws IllegalArgumentException if the base style carries no wire, or the height is out of
     *                                  range
     */
    public static String withWireHeight(String baseStyleId, double height) {
        String base = baseOf(baseStyleId == null ? "" : baseStyleId.toLowerCase(Locale.ROOT));
        if (!TRANSIT_CATENARY.equals(base) && !TRANSIT_PORTAL.equals(base)
            && !TRANSIT_TUNNEL.equals(base)) {
            throw new IllegalArgumentException(baseStyleId + " has no overhead wire to raise");
        }
        if (height < MIN_CONTACT_WIRE_HEIGHT || height > MAX_CONTACT_WIRE_HEIGHT) {
            throw new IllegalArgumentException("Wire height must be between "
                + (int) MIN_CONTACT_WIRE_HEIGHT + " and " + (int) MAX_CONTACT_WIRE_HEIGHT
                + " blocks, got " + height);
        }
        return base + "-" + trimTrailingZero(height);
    }

    /** The style id with any wire-height suffix removed — what decides the <em>look</em>. */
    public static String baseOf(String styleId) {
        if (styleId == null) {
            return null;
        }
        String key = styleId.toLowerCase(Locale.ROOT);
        int dash = key.lastIndexOf('-');
        if (dash <= 0 || dash == key.length() - 1) {
            return key;
        }
        // Only a numeric tail is a height; "transit-catenary" must not become "transit".
        return isNumeric(key.substring(dash + 1)) ? key.substring(0, dash) : key;
    }

    /**
     * Height of this style's contact wire above the railheads, in blocks.
     *
     * <p>An explicit suffix wins; otherwise the style's own default. A style with no wire at all
     * returns {@code 0}, which callers should read as "draw nothing overhead".</p>
     */
    public static double contactWireHeight(String styleId) {
        String base = baseOf(styleId);
        if (base == null || TRANSIT.equals(base) || !base.startsWith(TRANSIT)) {
            return 0.0D;
        }
        String key = styleId.toLowerCase(Locale.ROOT);
        if (base.length() != key.length()) {
            double authored = heightSuffixOrDefault(key);
            if (authored > 0.0D) {
                return authored;
            }
        }
        return TRANSIT_TUNNEL.equals(base)
            ? DEFAULT_TUNNEL_WIRE_HEIGHT : DEFAULT_CONTACT_WIRE_HEIGHT;
    }

    private static double heightSuffixOf(String key) {
        double parsed = heightSuffixOrDefault(key);
        if (parsed <= 0.0D) {
            throw new IllegalArgumentException("Wire height must be a number between "
                + (int) MIN_CONTACT_WIRE_HEIGHT + " and " + (int) MAX_CONTACT_WIRE_HEIGHT
                + " blocks, in '" + key + "'");
        }
        return parsed;
    }

    /** The suffix height, or {@code 0} when absent, unparseable or out of range. */
    private static double heightSuffixOrDefault(String key) {
        int dash = key.lastIndexOf('-');
        if (dash <= 0 || dash == key.length() - 1) {
            return 0.0D;
        }
        try {
            double parsed = Double.parseDouble(key.substring(dash + 1));
            return parsed >= MIN_CONTACT_WIRE_HEIGHT && parsed <= MAX_CONTACT_WIRE_HEIGHT
                ? parsed : 0.0D;
        }
        catch (NumberFormatException e) {
            // A render must never throw on a style id from a save; the caller falls back.
            return 0.0D;
        }
    }

    private static boolean isNumeric(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isDigit(c) && c != '.') {
                return false;
            }
        }
        return !text.isEmpty();
    }

    private static String trimTrailingZero(double value) {
        return value == Math.rint(value)
            ? Integer.toString((int) value) : Double.toString(value);
    }

    /** Whether this style id is one of the transit family (wider gauge). */
    public static boolean isTransit(String styleId) {
        return styleId != null && styleId.startsWith(TRANSIT);
    }
}
