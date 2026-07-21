package com.micatechnologies.minecraft.rcmc.client.render.sign;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Shared drawing for the transit sign renderers: a flat-shaded panel box, and text laid onto
 * both of its faces.
 *
 * <p>Text is deliberately drawn on <em>both</em> faces. Real platform signage is double-sided,
 * and it also makes the renderer forgiving of facing conventions — a sign placed "backwards"
 * still reads. Each line is an optionally-coloured string, centred; the caller owns layout
 * (which lines, what colours), this owns the GL ceremony.</p>
 */
final class SignPanels {

    private SignPanels() {
    }

    /** A solid panel box centred on x=0, spanning the given extents, in the local frame. */
    static void drawPanel(double halfWidth, double yBottom, double yTop, double halfThickness,
                          float r, float g, float b) {
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        // Culling stays on here (unlike the car renderer) because this is drawn in an ordinary
        // right-handed local frame — but winding is not guaranteed, so just disable for the box.
        GlStateManager.disableCull();
        box(buffer, -halfWidth, yBottom, -halfThickness, halfWidth, yTop, halfThickness, r, g, b);
        tessellator.draw();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
    }

    /**
     * Draws {@code lines} top-to-bottom on both faces of a panel, centred horizontally.
     *
     * @param yTop     world-space height of the first line's top
     * @param scale    world units per font pixel (font lines are 10px tall)
     * @param zFace    face offset from the panel centre plane
     * @param colours  per-line ARGB-less RGB ints (0xRRGGBB), parallel to {@code lines}
     */
    static void drawLines(FontRenderer font, String[] lines, int[] colours,
                          double yTop, float scale, double zFace) {
        for (int face = 0; face < 2; face++) {
            GlStateManager.pushMatrix();
            if (face == 1) {
                GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
            }
            GlStateManager.translate(0.0D, yTop, zFace);
            // Negative x/y: the font draws y-down and would mirror horizontally otherwise.
            GlStateManager.scale(-scale, -scale, scale);
            GlStateManager.disableLighting();
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line == null || line.isEmpty()) {
                    continue;
                }
                font.drawString(line, -font.getStringWidth(line) / 2, i * 10,
                    0xFF000000 | colours[i]);
            }
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }
    }

    private static void box(BufferBuilder buffer, double x1, double y1, double z1,
                            double x2, double y2, double z2, float r, float g, float b) {
        quad(buffer, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, 0.85F);
        quad(buffer, x1, y1, z2, x1, y2, z2, x2, y2, z2, x2, y1, z2, r, g, b, 0.85F);
        quad(buffer, x1, y1, z1, x1, y2, z1, x1, y2, z2, x1, y1, z2, r, g, b, 0.7F);
        quad(buffer, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, 0.7F);
        quad(buffer, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, 1.0F);
        quad(buffer, x1, y1, z1, x1, y1, z2, x2, y1, z2, x2, y1, z1, r, g, b, 0.55F);
    }

    private static void quad(BufferBuilder buffer,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4,
                             float r, float g, float b, float shade) {
        buffer.pos(x1, y1, z1).color(r * shade, g * shade, b * shade, 1.0F).endVertex();
        buffer.pos(x2, y2, z2).color(r * shade, g * shade, b * shade, 1.0F).endVertex();
        buffer.pos(x3, y3, z3).color(r * shade, g * shade, b * shade, 1.0F).endVertex();
        buffer.pos(x4, y4, z4).color(r * shade, g * shade, b * shade, 1.0F).endVertex();
    }
}
