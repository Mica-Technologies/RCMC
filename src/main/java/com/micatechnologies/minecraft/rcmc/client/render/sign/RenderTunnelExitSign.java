package com.micatechnologies.minecraft.rcmc.client.render.sign;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.block.sign.BlockTunnelExitSign;
import com.micatechnologies.minecraft.rcmc.block.sign.TileTunnelExitSign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Draws a tunnel exit sign: the printed plate from one texture, three rows of it (both ways,
 * left, right), with the typed distances written under the arrows. Lit full-bright, as an
 * illuminated safety sign is: it has to read in an unlit tunnel.
 *
 * <p>Local frame: origin at the block's centre, turned so {@code +z} points out from the wall; a
 * reader faces {@code -z}, so their right is {@code +x}.</p>
 */
public class RenderTunnelExitSign extends TileEntitySpecialRenderer<TileTunnelExitSign> {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(RcmcConstants.MOD_NAMESPACE, "textures/blocks/tunnel_exit_sign.png");

    /** Plate size, blocks: the texture row's 1024 x 160 at 4.5 blocks long. */
    private static final double LENGTH = 4.5D;
    private static final double HEIGHT = LENGTH * 160.0D / 1024.0D;
    private static final double CENTRE_Y = 0.52D;
    /** Plate face, out from the wall. */
    private static final double FACE_Z = -0.5D + 0.03D;

    /** Row height in the texture, as a fraction of its 512-pixel height. */
    private static final double ROW = 160.0D / 512.0D;

    /** Where the distance text goes in each row, from the texture generator: x in pixels of 1024. */
    private static final int[][] TEXT_X = {{167, 857}, {277, -1}, {-1, 747}};
    /** Text baseline height in a row, as a fraction down from its top. */
    private static final double TEXT_V = 112.0D / 160.0D;
    private static final double TEXT_SCALE = 0.0135D;
    private static final int CREAM = 0xF4F1E4;

    @Override
    public void render(TileTunnelExitSign sign, double x, double y, double z, float partialTicks,
                       int destroyStage, float alpha) {
        IBlockState state = sign.getWorld().getBlockState(sign.getPos());
        if (!(state.getBlock() instanceof BlockTunnelExitSign)) {
            return;
        }
        BlockTunnelExitSign.Variant variant = state.getValue(BlockTunnelExitSign.VARIANT);
        float angle = state.getValue(BlockTunnelExitSign.FACING).getHorizontalAngle();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y + CENTRE_Y, z + 0.5D);
        GlStateManager.rotate(-angle, 0.0F, 1.0F, 0.0F);
        GlStateManager.disableLighting();
        float lastX = OpenGlHelper.lastBrightnessX;
        float lastY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        double half = LENGTH / 2.0D;
        double top = HEIGHT / 2.0D;
        double v0 = variant.ordinal() * ROW;
        double v1 = v0 + ROW;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        bindTexture(TEXTURE);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        // Counter-clockwise seen from +z, the reader's side.
        buffer.pos(-half, -top, FACE_Z).tex(0.0D, v1).endVertex();
        buffer.pos(half, -top, FACE_Z).tex(1.0D, v1).endVertex();
        buffer.pos(half, top, FACE_Z).tex(1.0D, v0).endVertex();
        buffer.pos(-half, top, FACE_Z).tex(0.0D, v0).endVertex();
        tessellator.draw();

        // The plate's edges, so it has a thickness seen from along the wall.
        GlStateManager.disableTexture2D();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        edge(buffer, -half, -top, half, top);
        tessellator.draw();
        GlStateManager.enableTexture2D();

        FontRenderer font = getFontRenderer();
        int[] xs = TEXT_X[variant.ordinal()];
        String[] texts = {sign.left(), sign.right()};
        for (int i = 0; i < 2; i++) {
            if (xs[i] < 0 || texts[i].isEmpty()) {
                continue;
            }
            String text = withUnit(texts[i]);
            double tx = -half + LENGTH * xs[i] / 1024.0D;
            double ty = top - HEIGHT * TEXT_V;
            GlStateManager.pushMatrix();
            GlStateManager.translate(tx, ty, FACE_Z + 0.002D);
            GlStateManager.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
            font.drawString(text, -font.getStringWidth(text) / 2, -font.FONT_HEIGHT / 2, CREAM);
            GlStateManager.popMatrix();
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /** "240" reads "240 blocks"; anything already worded is left as typed. */
    static String withUnit(String typed) {
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            if (!Character.isDigit(c) && c != '.' && c != ',') {
                return typed;
            }
        }
        return typed + " blocks";
    }

    private static void edge(BufferBuilder buffer, double x1, double y1, double x2, double y2) {
        double back = -0.5D;
        float c = 0.55F;
        quad(buffer, x1, y2, back, x2, y2, FACE_Z, c);
        quad(buffer, x1, y1, back, x2, y1, FACE_Z, c * 0.7F);
        buffer.pos(x1, y1, back).color(c, c, c, 1.0F).endVertex();
        buffer.pos(x1, y1, FACE_Z).color(c, c, c, 1.0F).endVertex();
        buffer.pos(x1, y2, FACE_Z).color(c, c, c, 1.0F).endVertex();
        buffer.pos(x1, y2, back).color(c, c, c, 1.0F).endVertex();
        buffer.pos(x2, y1, FACE_Z).color(c, c, c, 1.0F).endVertex();
        buffer.pos(x2, y1, back).color(c, c, c, 1.0F).endVertex();
        buffer.pos(x2, y2, back).color(c, c, c, 1.0F).endVertex();
        buffer.pos(x2, y2, FACE_Z).color(c, c, c, 1.0F).endVertex();
    }

    /** A horizontal strip at height y from x1 to x2, between depths z1 and z2. */
    private static void quad(BufferBuilder buffer, double x1, double y, double z1, double x2, double y2,
                             double z2, float c) {
        buffer.pos(x1, y, z1).color(c, c, c, 1.0F).endVertex();
        buffer.pos(x1, y, z2).color(c, c, c, 1.0F).endVertex();
        buffer.pos(x2, y, z2).color(c, c, c, 1.0F).endVertex();
        buffer.pos(x2, y, z1).color(c, c, c, 1.0F).endVertex();
    }

    @Override
    public boolean isGlobalRenderer(TileTunnelExitSign sign) {
        return false;
    }
}
