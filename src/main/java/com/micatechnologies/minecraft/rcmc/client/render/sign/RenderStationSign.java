package com.micatechnologies.minecraft.rcmc.client.render.sign;

import com.micatechnologies.minecraft.rcmc.block.sign.TileStationSign;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import java.util.List;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

/**
 * Draws the line-map sign: the linked line's stops in route order with a "you are here" marker.
 *
 * <p>Everything shown is resolved fresh from the synced transit registry each frame — the sign
 * stores only a station name and a cycle counter, so renames, added stops and removed lines are
 * reflected the moment the registry syncs. That live lookup <em>is</em> the feature; see
 * {@code TileTransitSignBase}.</p>
 */
public class RenderStationSign extends TileEntitySpecialRenderer<TileStationSign> {

    private static final int HEADER_COLOUR = 0xFFD770;
    private static final int STOP_COLOUR = 0xF2F2F2;
    private static final int HERE_COLOUR = 0x66E866;
    private static final int MUTED_COLOUR = 0x9A9A9A;

    /** Panel accommodates a header plus this many stops before the list is elided. */
    private static final int MAX_STOPS_SHOWN = 9;

    @Override
    public void render(TileStationSign sign, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y, z + 0.5D);
        GlStateManager.rotate(-sign.facingDegrees(), 0.0F, 1.0F, 0.0F);

        SignPanels.drawPanel(0.46D, 0.95D, 2.0D, 0.035D, 0.13F, 0.14F, 0.17F);

        String[] lines = new String[MAX_STOPS_SHOWN + 2];
        int[] colours = new int[lines.length];
        buildContent(sign, lines, colours);
        SignPanels.drawLines(getFontRenderer(), lines, colours, 1.92D, 0.0085F, 0.04D);

        GlStateManager.popMatrix();
    }

    private static void buildContent(TileStationSign sign, String[] lines, int[] colours) {
        RcmcWorldState state = RcmcWorldState.of(sign.getWorld());
        if (state == null || !sign.isLinked()) {
            lines[0] = "NO STATION";
            colours[0] = MUTED_COLOUR;
            lines[1] = "link: /rcmc station";
            colours[1] = MUTED_COLOUR;
            return;
        }
        List<TransitLine> serving = state.transit().linesServing(sign.stationName());
        if (serving.isEmpty()) {
            lines[0] = sign.stationName();
            colours[0] = HEADER_COLOUR;
            lines[1] = "no lines serve";
            colours[1] = MUTED_COLOUR;
            lines[2] = "this station";
            colours[2] = MUTED_COLOUR;
            return;
        }
        TransitLine line = serving.get(Math.floorMod(sign.lineIndex(), serving.size()));
        lines[0] = line.name();
        colours[0] = HEADER_COLOUR;

        int row = 1;
        for (int i = 0; i < line.stationCount() && row < lines.length; i++) {
            TransitStation stop = line.station(i);
            boolean here = stop.name().equalsIgnoreCase(sign.stationName());
            if (i == MAX_STOPS_SHOWN - 1 && line.stationCount() > MAX_STOPS_SHOWN) {
                lines[row] = "... +" + (line.stationCount() - i) + " more";
                colours[row] = MUTED_COLOUR;
                break;
            }
            lines[row] = (here ? "> " : "- ") + stop.name();
            colours[row] = here ? HERE_COLOUR : STOP_COLOUR;
            row++;
        }
    }
}
