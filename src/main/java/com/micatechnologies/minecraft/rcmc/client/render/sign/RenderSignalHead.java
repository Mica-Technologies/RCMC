package com.micatechnologies.minecraft.rcmc.client.render.sign;

import com.micatechnologies.minecraft.rcmc.block.signal.BlockSignalHead;
import com.micatechnologies.minecraft.rcmc.block.signal.SignalHeadMount;
import com.micatechnologies.minecraft.rcmc.block.signal.SignalHeadStyle;
import com.micatechnologies.minecraft.rcmc.block.signal.TileSignalHead;
import com.micatechnologies.minecraft.rcmc.physics.transit.SignalAspect;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Draws a signal head: housing, mount and lenses, as a few untextured boxes.
 *
 * <p>Two passes per head. The housing and the dark lenses take the block's own light, which the
 * dispatcher has already set. The lit lens is then drawn with the lightmap forced full-bright,
 * as {@code RenderCoasterCar} does for its lamps, so a signal reads in a pitch-dark tunnel. The
 * distance cull is the tile's {@code getMaxRenderDistanceSquared}; past it the dispatcher never
 * calls this at all.</p>
 *
 * <p>Local frame: origin at the block's bottom centre, the face toward {@code -z}, turned by the
 * tile's facing. Lenses run red at the top to green at the bottom.</p>
 */
public class RenderSignalHead extends TileEntitySpecialRenderer<TileSignalHead> {

    private static final float[] RED = {1.0F, 0.12F, 0.08F};
    private static final float[] YELLOW = {1.0F, 0.78F, 0.1F};
    private static final float[] GREEN = {0.15F, 1.0F, 0.35F};

    /** How bright an unlit lens is, as a fraction of its colour: dark glass, still identifiable. */
    private static final float DARK = 0.16F;

    private static final float BODY = 0.09F;
    private static final float POLE = 0.32F;

    @Override
    public void render(TileSignalHead head, double x, double y, double z, float partialTicks,
                       int destroyStage, float alpha) {
        IBlockState state = head.getWorld().getBlockState(head.getPos());
        if (!(state.getBlock() instanceof BlockSignalHead)) {
            return;
        }
        SignalHeadStyle style = state.getValue(BlockSignalHead.STYLE);
        SignalHeadMount mount = state.getValue(BlockSignalHead.MOUNT);
        SignalAspect aspect = head.aspect();

        // Housing size and where its bottom sits, per style and mount.
        double halfWidth = style == SignalHeadStyle.DWARF ? 0.2D : 0.17D;
        double height = style == SignalHeadStyle.LED ? 0.78D
            : style == SignalHeadStyle.SEARCHLIGHT ? 0.36D : 0.42D;
        double bottom;
        switch (mount) {
            case CEILING:
                bottom = 0.92D - height;
                break;
            case WALL:
                bottom = 0.5D - height / 2.0D;
                break;
            default:
                bottom = style == SignalHeadStyle.DWARF ? 0.0D : 1.2D;
                break;
        }
        double top = bottom + height;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y, z + 0.5D);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        // Mount hardware, in block space (not turned): a bracket must still meet its wall.
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        switch (mount) {
            case CEILING:
                box(buffer, -0.04D, top, -0.04D, 0.04D, 1.0D, 0.04D, POLE, POLE, POLE);
                break;
            case WALL:
                wallArm(buffer, head.wallSide(), top - 0.12D);
                break;
            default:
                if (style != SignalHeadStyle.DWARF) {
                    box(buffer, -0.06D, 0.0D, -0.06D, 0.06D, bottom, 0.06D, POLE, POLE, POLE);
                }
                break;
        }
        tessellator.draw();

        GlStateManager.rotate(-head.facingDegrees(), 0.0F, 1.0F, 0.0F);

        // Housing, backplate and unlit lenses, in the block's own light.
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        box(buffer, -halfWidth, bottom, -0.1D, halfWidth, top, 0.1D, BODY, BODY, BODY);
        if (style == SignalHeadStyle.LED) {
            box(buffer, -halfWidth - 0.08D, bottom - 0.04D, -0.02D, halfWidth + 0.08D,
                top + 0.04D, 0.02D, 0.05F, 0.05F, 0.05F);
        }
        Lens[] lenses = lenses(style, aspect, bottom, top);
        for (Lens lens : lenses) {
            hood(buffer, lens);
            if (!lens.lit) {
                lensBox(buffer, lens, DARK);
            }
        }
        tessellator.draw();

        // The lit lens, full-bright.
        float lastX = OpenGlHelper.lastBrightnessX;
        float lastY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (Lens lens : lenses) {
            if (lens.lit) {
                lensBox(buffer, lens, 1.0F);
            }
        }
        tessellator.draw();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);

        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    /** A lens: centre height, half size, colour and whether it is the one lit. */
    private static final class Lens {
        final double centreY;
        final double half;
        final float[] colour;
        final boolean lit;

        Lens(double centreY, double half, float[] colour, boolean lit) {
            this.centreY = centreY;
            this.half = half;
            this.colour = colour;
            this.lit = lit;
        }
    }

    private static Lens[] lenses(SignalHeadStyle style, SignalAspect aspect, double bottom,
                                 double top) {
        double middle = (bottom + top) / 2.0D;
        switch (style) {
            case SEARCHLIGHT:
                return new Lens[] {new Lens(middle, 0.1D, colourOf(aspect), true)};
            case DWARF:
                // Stop over proceed; the proceed lens shows yellow for caution, green for clear.
                double step = (top - bottom) / 4.0D;
                return new Lens[] {
                    new Lens(top - step, 0.07D, RED, aspect == SignalAspect.RED),
                    new Lens(bottom + step, 0.07D,
                        aspect == SignalAspect.YELLOW ? YELLOW : GREEN, aspect != SignalAspect.RED)
                };
            default:
                double third = (top - bottom) / 3.0D;
                return new Lens[] {
                    new Lens(top - third / 2.0D, 0.08D, RED, aspect == SignalAspect.RED),
                    new Lens(middle, 0.08D, YELLOW, aspect == SignalAspect.YELLOW),
                    new Lens(bottom + third / 2.0D, 0.08D, GREEN, aspect == SignalAspect.GREEN)
                };
        }
    }

    private static float[] colourOf(SignalAspect aspect) {
        return aspect == SignalAspect.RED ? RED : aspect == SignalAspect.YELLOW ? YELLOW : GREEN;
    }

    private static void lensBox(BufferBuilder buffer, Lens lens, float brightness) {
        float[] c = lens.colour;
        box(buffer, -lens.half, lens.centreY - lens.half, -0.13D, lens.half,
            lens.centreY + lens.half, -0.1D, c[0] * brightness, c[1] * brightness,
            c[2] * brightness);
    }

    private static void hood(BufferBuilder buffer, Lens lens) {
        double y = lens.centreY + lens.half + 0.01D;
        box(buffer, -lens.half - 0.02D, y, -0.2D, lens.half + 0.02D, y + 0.02D, -0.1D,
            0.06F, 0.06F, 0.06F);
    }

    /** A horizontal arm from the block centre to the wall on {@code side} (horizontal index). */
    private static void wallArm(BufferBuilder buffer, int side, double y) {
        double t = 0.04D;
        switch (side) {
            case 0: // south, +z
                box(buffer, -t, y, 0.0D, t, y + 2 * t, 0.5D, POLE, POLE, POLE);
                break;
            case 1: // west, -x
                box(buffer, -0.5D, y, -t, 0.0D, y + 2 * t, t, POLE, POLE, POLE);
                break;
            case 2: // north, -z
                box(buffer, -t, y, -0.5D, t, y + 2 * t, 0.0D, POLE, POLE, POLE);
                break;
            case 3: // east, +x
                box(buffer, 0.0D, y, -t, 0.5D, y + 2 * t, t, POLE, POLE, POLE);
                break;
            default:
                break;
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
        float sr = Math.min(1.0F, r * shade);
        float sg = Math.min(1.0F, g * shade);
        float sb = Math.min(1.0F, b * shade);
        buffer.pos(x1, y1, z1).color(sr, sg, sb, 1.0F).endVertex();
        buffer.pos(x2, y2, z2).color(sr, sg, sb, 1.0F).endVertex();
        buffer.pos(x3, y3, z3).color(sr, sg, sb, 1.0F).endVertex();
        buffer.pos(x4, y4, z4).color(sr, sg, sb, 1.0F).endVertex();
    }
}
