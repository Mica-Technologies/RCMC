package com.micatechnologies.minecraft.rcmc.client.render.track;

import com.micatechnologies.minecraft.rcmc.track.TrackStyleIds;

/**
 * What each track style id looks like: cross-section dimensions and electrification. The
 * client-side half of the split described in {@link TrackStyleIds} — names are common,
 * appearance lives here.
 *
 * <p>The transit gauge is wider than the coaster gauge because the rolling stock is: metro
 * bodies are ~3 blocks wide (see {@code TrainSpec}'s presets), and the coaster's 1.1-block
 * rail spread reads like a toy under them. 1.6 blocks between railheads with longer ties reads
 * as heavy rail while staying visually narrower than the body it carries — exactly like the
 * real thing (3.05 m bodies on 1.435 m gauge). Purely visual: the spline, the physics and the
 * one degree of freedom are untouched.</p>
 *
 * <p>Like everything in this package: client-only by location, but pure by construction, so
 * the styles are unit-testable.</p>
 */
final class TrackStyles {

    /** Which overhead electrification a style draws. See {@code TrackMeshBuilder.buildCatenary}. */
    enum Catenary {
        NONE,
        /** Single masts beside the track with bracket arms — light rail / open-air metro. */
        POLES,
        /** Portal (gantry) frames spanning the track — mainline electrification. */
        PORTALS,
        /** Rigid overhead conductor rail, no masts — tunnels. */
        TUNNEL
    }

    /** The classic coaster look — current dimensions, unchanged, unelectrified, no ballast. */
    static final TrackStyles COASTER =
        new TrackStyles(0.55D, 0.70D, 0.08D, 0.12D, 0.05D, 0.05D, false, Catenary.NONE);

    /** Shared transit cross-section; the three electrified variants differ only in catenary. */
    private static final TrackStyles TRANSIT = transit(Catenary.NONE);
    private static final TrackStyles TRANSIT_CATENARY = transit(Catenary.POLES);
    private static final TrackStyles TRANSIT_PORTAL = transit(Catenary.PORTALS);
    private static final TrackStyles TRANSIT_TUNNEL = transit(Catenary.TUNNEL);

    final double halfGauge;
    final double tieHalfLength;
    final double spineHalfWidth;
    final double spineHalfHeight;
    final double railHalfWidth;
    final double railHalfHeight;
    /** Whether the track sweeps a ballast bed beneath the spine — heavy rail sits on stone. */
    final boolean ballast;
    final Catenary catenary;

    private TrackStyles(double halfGauge, double tieHalfLength, double spineHalfWidth,
                        double spineHalfHeight, double railHalfWidth, double railHalfHeight,
                        boolean ballast, Catenary catenary) {
        this.halfGauge = halfGauge;
        this.tieHalfLength = tieHalfLength;
        this.spineHalfWidth = spineHalfWidth;
        this.spineHalfHeight = spineHalfHeight;
        this.railHalfWidth = railHalfWidth;
        this.railHalfHeight = railHalfHeight;
        this.ballast = ballast;
        this.catenary = catenary;
    }

    private static TrackStyles transit(Catenary catenary) {
        // Heavier everything, sized to sit under the ~3.8-wide, ~5.95-tall metro stock: 2.8 blocks
        // between railheads, long ties, a chunky spine, and visibly heavier rail. Widened from 2.0
        // when the stock was scaled up — the body must still read as overhanging its track, which
        // is what makes rolling stock look like rolling stock.
        return new TrackStyles(1.40D, 1.90D, 0.26D, 0.20D, 0.11D, 0.09D, true, catenary);
    }

    /** Resolves a section's style id. Unknown or {@code null} ids fall back to the coaster look
     *  rather than failing a render — a save from a newer build should degrade, not crash. */
    static TrackStyles of(String styleId) {
        if (styleId == null) {
            return COASTER;
        }
        // The look comes from the base id; any wire-height suffix affects only how high the
        // catenary hangs, which TrackMeshBuilder reads separately.
        switch (TrackStyleIds.baseOf(styleId)) {
            case TrackStyleIds.TRANSIT:
                return TRANSIT;
            case TrackStyleIds.TRANSIT_CATENARY:
                return TRANSIT_CATENARY;
            case TrackStyleIds.TRANSIT_PORTAL:
                return TRANSIT_PORTAL;
            case TrackStyleIds.TRANSIT_TUNNEL:
                return TRANSIT_TUNNEL;
            default:
                return COASTER;
        }
    }
}
