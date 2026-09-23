package com.micatechnologies.minecraft.rcmc.client.render.sign;

import com.micatechnologies.minecraft.rcmc.block.TileRideSign;
import java.util.Locale;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

/**
 * Draws a ride sign: the ride's name and status, and its rating as a guest would read it on the
 * board by the queue.
 *
 * <p>Everything shown comes from what the server last sent the sign ({@link TileRideSign}); coaster
 * ride state is not otherwise on the client. Rows with a value are justified — the label flush
 * left, the number flush right — as the arrival boards are.</p>
 */
public class RenderRideSign extends TileEntitySpecialRenderer<TileRideSign> {

    private static final int NAME_COLOUR = 0xFFD770;
    private static final int OPEN_COLOUR = 0x66E866;
    private static final int CLOSED_COLOUR = 0xE86666;
    private static final int TEST_COLOUR = 0xE8C866;
    private static final int TEXT_COLOUR = 0xF2F2F2;
    private static final int MUTED_COLOUR = 0x9A9A9A;

    private static final double HALF_WIDTH = 0.62D;

    @Override
    public void render(TileRideSign sign, double x, double y, double z, float partialTicks, int destroyStage,
                       float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y, z + 0.5D);
        GlStateManager.rotate(-sign.facingDegrees(), 0.0F, 1.0F, 0.0F);

        SignPanels.drawPanel(HALF_WIDTH, 0.95D, 2.15D, 0.035D, 0.10F, 0.12F, 0.18F);

        String[] lines = new String[9];
        int[] colours = new int[lines.length];
        int row = 0;
        lines[row] = sign.name().isEmpty() ? "RIDE SIGN" : sign.name();
        colours[row++] = NAME_COLOUR;
        String status = sign.status().isEmpty() ? "NO RIDE" : sign.status();
        lines[row] = status;
        colours[row++] = "OPEN".equals(status) ? OPEN_COLOUR
            : "TESTING".equals(status) ? TEST_COLOUR
            : "NO RIDE".equals(status) ? MUTED_COLOUR : CLOSED_COLOUR;
        if (sign.isRated()) {
            lines[row] = "Excitement  " + score(sign.excitement());
            colours[row++] = TEXT_COLOUR;
            lines[row] = "Intensity  " + score(sign.intensity());
            colours[row++] = TEXT_COLOUR;
            lines[row] = "Nausea  " + score(sign.nausea());
            colours[row++] = TEXT_COLOUR;
            lines[row] = "Top speed  " + String.format(Locale.ROOT, "%.0f", sign.topSpeed()) + " b/s";
            colours[row++] = MUTED_COLOUR;
            lines[row] = "Length  " + String.format(Locale.ROOT, "%.0f", sign.length()) + " blocks";
            colours[row++] = MUTED_COLOUR;
            lines[row] = "Inversions  " + sign.inversions();
            colours[row++] = MUTED_COLOUR;
            if (!sign.isSafe()) {
                lines[row] = "FAILS SAFETY CHECK";
                colours[row++] = CLOSED_COLOUR;
            }
        }
        String[] shown = new String[row];
        int[] shownColours = new int[row];
        System.arraycopy(lines, 0, shown, 0, row);
        System.arraycopy(colours, 0, shownColours, 0, row);
        SignPanels.drawJustifiedLines(getFontRenderer(), shown, shownColours, 2.07D, 0.0085F, 0.04D,
            HALF_WIDTH - 0.06D);

        GlStateManager.popMatrix();
    }

    private static String score(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
