package com.micatechnologies.minecraft.rcmc.client.render.sign;

import com.micatechnologies.minecraft.rcmc.block.sign.TileArrivalBoard;
import com.micatechnologies.minecraft.rcmc.physics.transit.ArrivalEstimator;
import com.micatechnologies.minecraft.rcmc.physics.transit.ServiceSnapshot;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine;
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
 *   INBOUND   1 stop away
 *             3 stops away
 *   OUTBOUND  3 stops away
 * </pre>
 *
 * <p>Rows come from the synced {@link ServiceSnapshot}s: for every line serving the linked
 * station, each running service's distance is {@link ArrivalEstimator}'s stops-away, replayed
 * over the service pattern — exact and deterministic, no wall clock. A train counted zero stops
 * away is displayed as "1 stop away" (it still has this station to reach), or "Boarding" once
 * it is berthed here with its doors cycling. Amber-on-black because every real one is.</p>
 */
public class RenderArrivalBoard extends TileEntitySpecialRenderer<TileArrivalBoard> {

    private static final int AMBER = 0xFFB300;
    private static final int MUTED_COLOUR = 0x7A6A30;

    /** Nearest trains shown per direction, matching the reference mock. */
    private static final int ROWS_PER_DIRECTION = 2;

    /**
     * Screen size, in blocks. A real concourse board is a big panel read from across a platform,
     * and the first cut was under a block wide — legible only with your nose against it, which is
     * not what a board is for. 3.2 × 1.5 hanging from its ceiling mount reads from down the
     * platform. Drawn on both faces, like every real one.
     *
     * <p>{@code TileArrivalBoard.getRenderBoundingBox} must contain these; a screen this much
     * larger than its own block gets culled otherwise.</p>
     */
    private static final double PANEL_HALF_WIDTH = 1.6D;
    private static final double PANEL_HEIGHT = 1.5D;

    /** Top of the screen, just under the ceiling mount so the stub still meets it. */
    private static final double PANEL_TOP = 0.97D;

    private static final double PANEL_HALF_THICKNESS = 0.06D;

    /**
     * World units per font pixel, sized so the tallest layout — a station name, then two direction
     * groups of a label and {@link #ROWS_PER_DIRECTION} rows each — fits the panel height. Font
     * lines are 10px, so the pitch is {@code 10 × scale}.
     */
    private static final float TEXT_SCALE = 0.018F;

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
        String[] text = lines.toArray(new String[0]);
        int[] colours = new int[text.length];
        for (int i = 0; i < colours.length; i++) {
            colours[i] = text[i].startsWith(" ") || Character.isDigit(text[i].charAt(0))
                ? AMBER : (i == 0 ? MUTED_COLOUR : AMBER);
        }
        SignPanels.drawLines(getFontRenderer(), text, colours, PANEL_TOP - 0.08D, TEXT_SCALE,
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

        // Group arrivals by direction label across every line serving this station. Insertion
        // order keeps OUTBOUND/INBOUND stable per line definition rather than shuffling per frame.
        Map<String, List<String>> byDirection = new LinkedHashMap<>();
        for (TransitLine line : serving) {
            int stationIndex = line.indexOfStation(board.stationName());
            for (int direction : new int[] {1, -1}) {
                byDirection.computeIfAbsent(line.labelFor(direction), k -> new ArrayList<>());
            }
            for (ServiceSnapshot snapshot : state.serviceSnapshots()) {
                if (!snapshot.lineName().equalsIgnoreCase(line.name())) {
                    continue;
                }
                int stops = ArrivalEstimator.stopsAway(line, snapshot.serviceDirection(),
                    snapshot.nextStopIndex(), stationIndex);
                if (stops < 0) {
                    continue;
                }
                String text;
                if (stops == 0 && snapshot.atPlatform()) {
                    text = "Boarding";
                } else {
                    // A train running to this station still has one stop to make — this one.
                    int display = stops + 1;
                    text = display + (display == 1 ? " stop away" : " stops away");
                }
                byDirection.get(line.labelFor(snapshot.serviceDirection())).add(text);
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
        int space = row.indexOf(' ');
        try {
            return Integer.parseInt(space < 0 ? row : row.substring(0, space));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
