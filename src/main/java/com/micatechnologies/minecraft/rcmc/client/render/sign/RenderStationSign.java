package com.micatechnologies.minecraft.rcmc.client.render.sign;

import com.micatechnologies.minecraft.rcmc.block.sign.TileStationSign;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitPlatform;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSignText;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.List;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.math.BlockPos;

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
        String platform = platformHere(state, sign);
        if (platform != null) {
            lines[row] = "Platform " + platform;
            colours[row] = MUTED_COLOUR;
            row++;
        }
        int here = line.indexOfStation(sign.stationName());
        int[] window = TransitSignText.stopWindow(line.stationCount(), here, lines.length - row);
        if (window[0] > 0) {
            lines[row] = "... " + window[0] + " before";
            colours[row] = MUTED_COLOUR;
            row++;
        }
        for (int i = window[0]; i <= window[1]; i++) {
            TransitStation stop = line.station(i);
            lines[row] = (i == here ? "> " : "- ") + stop.name();
            colours[row] = i == here ? HERE_COLOUR : STOP_COLOUR;
            row++;
        }
        if (window[1] < line.stationCount() - 1) {
            lines[row] = "... +" + (line.stationCount() - 1 - window[1]) + " more";
            colours[row] = MUTED_COLOUR;
        }
    }

    /**
     * The label of the berth this sign stands at, when its station has more than one and the berth
     * has a label: the one whose stop point is nearest the sign. {@code null} otherwise.
     */
    private static String platformHere(RcmcWorldState state, TileStationSign sign) {
        TransitStation station = state.transit().station(sign.stationName());
        if (station == null || station.platforms().size() < 2) {
            return null;
        }
        BlockPos at = sign.getPos();
        String best = null;
        double bestSq = Double.MAX_VALUE;
        for (TransitPlatform platform : station.platforms()) {
            if (platform.label().isEmpty()
                || state.network().section(platform.stopPoint().sectionId()) == null) {
                continue;
            }
            Vec3 p = state.network().frameAt(platform.stopPoint()).position;
            double dx = p.x - (at.getX() + 0.5D);
            double dy = p.y - at.getY();
            double dz = p.z - (at.getZ() + 0.5D);
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq < bestSq) {
                bestSq = sq;
                best = platform.label();
            }
        }
        return best;
    }
}
