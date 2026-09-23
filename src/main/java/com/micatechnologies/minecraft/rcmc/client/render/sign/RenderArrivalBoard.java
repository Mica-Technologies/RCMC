package com.micatechnologies.minecraft.rcmc.client.render.sign;

import com.micatechnologies.minecraft.rcmc.block.sign.TileArrivalBoard;
import com.micatechnologies.minecraft.rcmc.physics.transit.ArrivalEstimator;
import com.micatechnologies.minecraft.rcmc.physics.transit.ServiceSnapshot;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSignText;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

/**
 * Draws the ceiling-hung arrival board:
 *
 * <pre>
 *   IN/Ashmont              APPR (2)
 *                      3 stops away
 *   OUT/Alewife              BRD (1)
 * </pre>
 *
 * <p>Rows come from the synced {@link ServiceSnapshot}s: for every line serving the linked
 * station, each running service's distance is {@link ArrivalEstimator}'s stops-away, replayed
 * over the service pattern — exact and deterministic, no wall clock. A train counted zero stops
 * away reads "APPR" (it still has this station to reach), or "BRD" once it is berthed here with
 * its doors cycling — abbreviated as every real board abbreviates them. Amber-on-black because
 * every real one is.</p>
 *
 * <p><b>Both directions, and which berth.</b> A station is several platforms now, so a service
 * running down either side of an island targets this same named place and fills its own direction
 * group — which is what an island platform's board was always supposed to show, and could not
 * while a station had one stop point. The berth in brackets is the snapshot's own, shown only
 * against a train whose next stop is this station; {@code TransitSignText.stopsLabel} owns that
 * rule and explains it.</p>
 */
public class RenderArrivalBoard extends TileEntitySpecialRenderer<TileArrivalBoard> {

    private static final int AMBER = 0xFFB300;
    private static final int MUTED_COLOUR = 0x7A6A30;

    /** Nearest trains shown per direction, matching the reference mock. */
    private static final int ROWS_PER_DIRECTION = 2;

    /**
     * Screen size, in blocks. A real concourse board is a big panel read from across a platform,
     * and the first cut was under a block wide — legible only with your nose against it, which is
     * not what a board is for. 4 × 2 hanging from its ceiling mount reads from down the platform,
     * and is the footprint the board's blocks now occupy — see {@code ArrivalBoardStructure}, which
     * claims five columns so that a screen centred on the master is contained by whole blocks.
     * Drawn on both faces, like every real one.
     *
     * <p>{@code TileArrivalBoard.getRenderBoundingBox} must contain these; a screen this much
     * larger than its own block gets culled otherwise. The height is {@code PANEL_TOP} less a
     * whole block, which puts the bottom edge exactly on the boundary below the board's second row
     * of blocks — the art stops where {@code ArrivalBoardStructure}'s footprint stops.</p>
     */
    private static final double PANEL_HALF_WIDTH = 2.0D;

    /** Top of the screen, just under the ceiling mount so the stub still meets it. */
    private static final double PANEL_TOP = 0.97D;

    private static final double PANEL_HEIGHT = PANEL_TOP + 1.0D;

    private static final double PANEL_HALF_THICKNESS = 0.06D;

    /**
     * World units per font pixel, sized so the tallest layout — a station name, then two direction
     * groups of a label and {@link #ROWS_PER_DIRECTION} rows each — fits the panel height. Font
     * lines are 10px, so the pitch is {@code 10 × scale}.
     *
     * <p>Raised with the panel: the extra height went to legibility rather than to more rows,
     * because the complaint about this board was never that it showed too little at once. At 0.026
     * a row is about 44% taller than it was, which is the difference between reading it from the
     * far platform edge and walking up to it.</p>
     */
    private static final float MAX_TEXT_SCALE = 0.026F;

    /**
     * The smallest the text may shrink to before rows are dropped instead.
     *
     * <p>A board is read from across a platform, so there is a size below which fitting more on is
     * pointless — it fits, and nobody can read it. Half the preferred scale is about where that
     * lands; past it, losing the last row beats shrinking the first five.</p>
     */
    private static final float MIN_TEXT_SCALE = 0.013F;

    /** Height of the first line's top, below the panel's own top edge. */
    private static final double TEXT_TOP = PANEL_TOP - 0.08D;

    /**
     * Clear space kept either side of the text, so a full-width row stops short of the bezel rather
     * than running into it.
     */
    private static final double SIDE_MARGIN = 0.12D;

    /**
     * How long each page holds, in ticks — two and a half seconds.
     *
     * <p>Slow enough to finish reading a row before it changes, brisk enough that a rider glancing
     * up does not have to wait for the half they wanted. It is the cadence a real dot-matrix board
     * alternates at, for the same reason.</p>
     */
    private static final int PAGE_TICKS = 50;

    @Override
    public void render(TileArrivalBoard board, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y, z + 0.5D);
        GlStateManager.rotate(-board.facingDegrees(), 0.0F, 1.0F, 0.0F);

        SignPanels.drawPanel(PANEL_HALF_WIDTH, PANEL_TOP - PANEL_HEIGHT, PANEL_TOP,
            PANEL_HALF_THICKNESS, 0.05F, 0.05F, 0.06F);

        List<String> rows = new ArrayList<>();
        buildRows(board, rows);
        // Sized to the panel rather than drawn at a fixed size, and anything still too wide takes
        // turns in place instead of running off the edge — see SignTextFit. An interchange's rows
        // are nearly three times the width of an ordinary station's and the same screen holds both.
        final net.minecraft.client.gui.FontRenderer font = RcmcFonts.dotMatrix();
        SignTextFit.Layout layout = SignTextFit.fit(rows, font::getStringWidth,
            2.0D * PANEL_HALF_WIDTH - 2.0D * SIDE_MARGIN,
            TEXT_TOP - (PANEL_TOP - PANEL_HEIGHT),
            MAX_TEXT_SCALE, MIN_TEXT_SCALE);

        // Paged off world time, not a client clock: every board in the world turns over on the same
        // beat, and two players standing at one board see the same page. A board with nothing to
        // page never moves, because pageCount is then 1 and this is always zero.
        long now = board.getWorld() == null ? 0L : board.getWorld().getTotalWorldTime();
        int page = (int) Math.floorMod(now / PAGE_TICKS, (long) layout.pageCount());
        String[] text = layout.frame(page).toArray(new String[0]);
        int[] colours = new int[text.length];
        for (int i = 0; i < colours.length; i++) {
            colours[i] = text[i].isEmpty() || text[i].startsWith(" ")
                || Character.isDigit(text[i].charAt(0))
                ? AMBER : (i == 0 ? MUTED_COLOUR : AMBER);
        }
        SignPanels.drawJustifiedLines(font, text, colours, TEXT_TOP, layout.scale,
            PANEL_HALF_THICKNESS + 0.005D, PANEL_HALF_WIDTH - SIDE_MARGIN);

        GlStateManager.popMatrix();
    }

    private static void buildRows(TileArrivalBoard board, List<String> out) {
        RcmcWorldState state = RcmcWorldState.of(board.getWorld());
        if (state == null || !board.isLinked()) {
            out.add("NOT IN SERVICE");
            return;
        }
        List<TransitLine> serving = state.transit().linesServing(board.stationName());
        if (serving.isEmpty()) {
            out.add(board.stationName());
            out.add("NO SERVICE");
            return;
        }
        out.add(board.stationName());

        // Group arrivals by direction-and-destination across every line serving this station —
        // "OUTBOUND/Alewife" — so a rider sees where each train is bound, not just which way. The
        // label, the row phrasing and the speaker announcement all come from TransitSignText, so a
        // board and a speaker can never describe the same train differently. Insertion order keeps
        // the groups stable per line definition rather than shuffling per frame.
        Map<String, List<Row>> byDirection = new LinkedHashMap<>();
        for (TransitLine line : serving) {
            int stationIndex = line.indexOfStation(board.stationName());
            for (int direction : new int[] {1, -1}) {
                byDirection.computeIfAbsent(TransitSignText.destinationLabel(line, direction),
                    k -> new ArrayList<>());
            }
            for (ServiceSnapshot snapshot : state.serviceSnapshots()) {
                if (!snapshot.lineName().equalsIgnoreCase(line.name())) {
                    continue;
                }
                int stops = ArrivalEstimator.stopsAway(line, snapshot.serviceDirection(),
                    snapshot.nextStopIndex(), stationIndex);
                // Filed under the direction it will ARRIVE in, not the one it is running in now:
                // a train that turns back at a terminus first calls here the other way, and grouping
                // it by its current direction moved its row across headings mid-approach.
                int arriving = ArrivalEstimator.arrivalDirection(line, snapshot.serviceDirection(),
                    snapshot.nextStopIndex(), stationIndex);
                if (arriving == 0) {
                    continue;
                }
                // The berth rides along on the snapshot, and stopsLabel shows it only for a train
                // whose next stop is this station — the one case where the berth it resolved is
                // one of ours. That is what turns an island's two rows from "a train is coming"
                // into "a train is coming, and it is the far side you want".
                double seconds = snapshot.secondsTo(stationIndex);
                String text = TransitSignText.arrivalLabel(stops, snapshot.atPlatform(),
                    snapshot.platformLabel(), line.labelFor(arriving), seconds);
                if (text == null) {
                    continue;
                }
                byDirection.get(TransitSignText.destinationLabel(line, arriving))
                    .add(new Row(text, Row.order(stops, snapshot.atPlatform(), seconds)));
            }
        }

        for (Map.Entry<String, List<Row>> group : byDirection.entrySet()) {
            List<Row> rows = group.getValue();
            Collections.sort(rows, (a, b) -> Double.compare(a.order, b.order));
            if (rows.isEmpty()) {
                out.add(group.getKey() + "  --");
                continue;
            }
            for (int i = 0; i < Math.min(ROWS_PER_DIRECTION, rows.size()); i++) {
                String text = rows.get(i).text;
                out.add(i == 0 ? group.getKey() + "  " + text : "      " + text);
            }
        }
    }

    /**
     * One row's text and where it sorts. Ordered by what the row means, not by parsing what it says:
     * a board can mix "3 min" rows with "2 stops" rows for a line whose far legs are not timed yet.
     */
    private static final class Row {
        final String text;
        final double order;

        Row(String text, double order) {
            this.text = text;
            this.order = order;
        }

        /** Boarding first, then approaching, then soonest by time, then untimed by stops. */
        static double order(int stops, boolean atPlatform, double seconds) {
            if (stops == 0) {
                return atPlatform ? -2.0D : -1.0D;
            }
            return seconds >= 0.0D ? seconds : 1.0e9D + stops;
        }
    }
}
