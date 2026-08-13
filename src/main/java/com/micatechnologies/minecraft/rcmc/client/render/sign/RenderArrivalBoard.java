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
 *   INBOUND/Ashmont    now approaching (2)
 *                      3 stops away
 *   OUTBOUND/Alewife   Boarding (1)
 * </pre>
 *
 * <p>Rows come from the synced {@link ServiceSnapshot}s: for every line serving the linked
 * station, each running service's distance is {@link ArrivalEstimator}'s stops-away, replayed
 * over the service pattern — exact and deterministic, no wall clock. A train counted zero stops
 * away is displayed as "1 stop away" (it still has this station to reach), or "Boarding" once
 * it is berthed here with its doors cycling. Amber-on-black because every real one is.</p>
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
    private static final float TEXT_SCALE = 0.026F;

    /** Height of the first line's top, below the panel's own top edge. */
    private static final double TEXT_TOP = PANEL_TOP - 0.08D;

    /**
     * How many lines fit on the screen. Two lines and four groups is more than a four-by-two panel
     * can hold, and the overflow does not stop at the panel — it carries on down past the bottom
     * edge and hangs in the air below the board, which reads as a glitch rather than as a full
     * board. Computed from the panel and the font rather than counted by hand, so raising the text
     * scale again cannot silently reintroduce it.
     */
    private static final int MAX_LINES =
        (int) ((TEXT_TOP - (PANEL_TOP - PANEL_HEIGHT)) / (10.0D * TEXT_SCALE));

    @Override
    public void render(TileArrivalBoard board, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y, z + 0.5D);
        GlStateManager.rotate(-board.facingDegrees(), 0.0F, 1.0F, 0.0F);

        SignPanels.drawPanel(PANEL_HALF_WIDTH, PANEL_TOP - PANEL_HEIGHT, PANEL_TOP,
            PANEL_HALF_THICKNESS, 0.05F, 0.05F, 0.06F);

        List<String> lines = new ArrayList<>();
        buildRows(board, lines);
        // Truncated at the end rather than while building, because the rows come out in order of
        // what a rider needs first — the station, then each direction's soonest train — so the
        // ones that fall off the bottom are the ones worth losing.
        if (lines.size() > MAX_LINES) {
            lines = lines.subList(0, MAX_LINES);
        }
        String[] text = lines.toArray(new String[0]);
        int[] colours = new int[text.length];
        for (int i = 0; i < colours.length; i++) {
            colours[i] = text[i].startsWith(" ") || Character.isDigit(text[i].charAt(0))
                ? AMBER : (i == 0 ? MUTED_COLOUR : AMBER);
        }
        SignPanels.drawLines(getFontRenderer(), text, colours, TEXT_TOP, TEXT_SCALE,
            PANEL_HALF_THICKNESS + 0.005D);

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
        Map<String, List<String>> byDirection = new LinkedHashMap<>();
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
                // The berth rides along on the snapshot, and stopsLabel shows it only for a train
                // whose next stop is this station — the one case where the berth it resolved is
                // one of ours. That is what turns an island's two rows from "a train is coming"
                // into "a train is coming, and it is the far side you want".
                String text = TransitSignText.stopsLabel(stops, snapshot.atPlatform(),
                    snapshot.platformLabel(), line.labelFor(snapshot.serviceDirection()));
                if (text == null) {
                    continue;
                }
                byDirection.get(TransitSignText.destinationLabel(line, snapshot.serviceDirection()))
                    .add(text);
            }
        }

        for (Map.Entry<String, List<String>> group : byDirection.entrySet()) {
            List<String> rows = group.getValue();
            // "Boarding" sorts before numbers by luck of the alphabet not being trusted here:
            // sort by the leading number, Boarding first.
            Collections.sort(rows, (a, b) -> Integer.compare(sortKey(a), sortKey(b)));
            if (rows.isEmpty()) {
                out.add(group.getKey() + "  --");
                continue;
            }
            for (int i = 0; i < Math.min(ROWS_PER_DIRECTION, rows.size()); i++) {
                out.add(i == 0 ? group.getKey() + "  " + rows.get(i) : "      " + rows.get(i));
            }
        }
    }

    private static int sortKey(String row) {
        if (row.startsWith("Boarding")) {
            return -1;
        }
        if (row.startsWith("now approaching")) {
            return 0;
        }
        int space = row.indexOf(' ');
        try {
            return Integer.parseInt(space < 0 ? row : row.substring(0, space));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
