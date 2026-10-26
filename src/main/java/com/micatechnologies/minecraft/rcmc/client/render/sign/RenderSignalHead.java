package com.micatechnologies.minecraft.rcmc.client.render.sign;

import com.micatechnologies.minecraft.rcmc.block.signal.SignalHeadMount;
import com.micatechnologies.minecraft.rcmc.block.signal.SignalHeadStyle;
import com.micatechnologies.minecraft.rcmc.block.signal.TileSignalHead;
import com.micatechnologies.minecraft.rcmc.item.RcmcItems;
import com.micatechnologies.minecraft.rcmc.physics.transit.SignalAspect;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

/**
 * Draws a signal head: its mount, housing, lenses or arm, as untextured boxes.
 *
 * <p>Two passes per head. Everything but the lit lamps takes the block's own light, which the
 * dispatcher has already set. The lit lamps are then drawn with the lightmap forced full-bright,
 * as {@code RenderCoasterCar} does for its lamps, so a signal reads in a pitch-dark tunnel and its
 * housing stays as dark as the tunnel around it. The distance cull is the tile's
 * {@code getMaxRenderDistanceSquared}; past it the dispatcher never calls this at all.</p>
 *
 * <p>Local frame: origin at the block's bottom centre, the face toward {@code -z}, turned by the
 * tile's facing. A driver looks at the face along {@code +z}, so the driver's right is {@code -x}.
 * Mount hardware that reaches the ground — masts, gantry legs — runs down {@code reach} blocks to
 * the top of the first solid block, which the server measures.</p>
 *
 * <p>Also draws items: {@link #renderItem} feeds a stand-in head through the same code, so every
 * style looks in the hand the way it does in the world.</p>
 */
public class RenderSignalHead extends TileEntitySpecialRenderer<TileSignalHead> {

    static final float[] RED = {1.0F, 0.12F, 0.08F};
    static final float[] YELLOW = {1.0F, 0.72F, 0.08F};
    static final float[] GREEN = {0.15F, 1.0F, 0.4F};
    static final float[] LUNAR = {0.82F, 0.88F, 1.0F};
    static final float[] AMBER = {1.0F, 0.84F, 0.45F};
    static final float[] WHITE = {1.0F, 1.0F, 1.0F};

    private static final float[] BODY = {0.08F, 0.08F, 0.09F};
    private static final float[] HOOD = {0.05F, 0.05F, 0.05F};
    private static final float[] METAL = {0.36F, 0.37F, 0.39F};
    private static final float[] GALV = {0.55F, 0.57F, 0.58F};
    private static final float[] PLATE_WHITE = {0.92F, 0.92F, 0.9F};
    private static final float[] SEMAPHORE_RED = {0.75F, 0.08F, 0.06F};
    private static final float[] SEMAPHORE_YELLOW = {0.95F, 0.75F, 0.05F};
    private static final float[] TRIP_YELLOW = {0.9F, 0.75F, 0.1F};

    /** How bright an unlit lens is, as a fraction of its colour: dark glass, still identifiable. */
    private static final float DARK = 0.16F;

    /** How fast a semaphore arm or trip cock swings, degrees a second. */
    private static final float ARM_SPEED = 90.0F;

    @Override
    public void render(TileSignalHead head, double x, double y, double z, float partialTicks,
                       int destroyStage, float alpha) {
        if (head.getWorld() != null && isHoldingTransitTool()) {
            drawBinding(head, x, y, z);
        }
        draw(head, x, y, z, true);
    }

    /** Draws an item: a stand-in head of the item's style, scaled to fit an item's cube. */
    public void renderItem(SignalHeadStyle style) {
        TileSignalHead head = TileSignalHead.forItem(style);
        GlStateManager.pushMatrix();
        double height = mountTop(head) + headHeight(style);
        double scale = Math.min(0.9D, 1.0D / Math.max(1.0D, height));
        GlStateManager.translate(0.5D, 0.0D, 0.5D);
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(-0.5D, 0.0D, -0.5D);
        draw(head, 0.0D, 0.0D, 0.0D, false);
        GlStateManager.popMatrix();
    }

    private void draw(TileSignalHead head, double x, double y, double z, boolean inWorld) {
        SignalHeadStyle style = head.style();
        SignalHeadMount mount = head.mount();
        Painter p = new Painter();
        List<Text> texts = new ArrayList<>();

        double handX = -head.hand();
        double height = headHeight(style);
        double bottom;
        switch (mount) {
            case CEILING:
            case GANTRY:
            case CANTILEVER:
                bottom = 0.88D - height;
                break;
            case WALL:
                bottom = 0.5D - height / 2.0D;
                break;
            default:
                bottom = mountTop(head);
                break;
        }
        drawMount(p, head, mount, bottom, height, handX);
        drawHousing(p, texts, head, style, bottom, height, handX);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y, z + 0.5D);
        GlStateManager.rotate(-head.facingDegrees(), 0.0F, 1.0F, 0.0F);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        p.emit(buffer, false);
        tessellator.draw();

        float lastX = OpenGlHelper.lastBrightnessX;
        float lastY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        p.emit(buffer, true);
        tessellator.draw();
        GlStateManager.enableTexture2D();
        drawTexts(texts);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);

        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    // --- Sizes. --------------------------------------------------------------------------------

    /** Height of a style's housing, blocks. */
    static double headHeight(SignalHeadStyle style) {
        switch (style) {
            case LED:
                return 0.78D;
            case LED_TWO:
                return 0.54D;
            case LED_LUNAR:
                return 0.98D;
            case STARTER:
                return 0.8D;
            case SEARCHLIGHT:
                return 0.44D;
            case DWARF:
                return 0.42D;
            case COLOUR_LIGHT:
                return 0.92D;
            case COLOUR_LIGHT_FOUR:
                return 1.2D;
            case POSITION_LIGHT:
            case COLOUR_POSITION_LIGHT:
                return 0.72D;
            case ROUTE_FEATHER:
                return 0.7D;
            case THEATRE:
                return 0.42D;
            case CALL_ON:
                return 0.56D;
            case TRIP_COCK:
                return 0.2D;
            default:
                // Semaphores: the arm and spectacle at the top of the post.
                return 0.5D;
        }
    }

    /** Where the housing's bottom sits for a mount that holds it from below. */
    static double mountTop(TileSignalHead head) {
        switch (head.mount()) {
            case GROUND:
                return 0.06D;
            case PLATFORM:
                return 0.7D;
            case MAST:
                return head.style().isSemaphore() ? 2.6D : 1.8D;
            default:
                return head.style() == SignalHeadStyle.DWARF || head.style() == SignalHeadStyle.TRIP_COCK
                    ? 0.06D : 1.2D;
        }
    }

    // --- Mounts. -------------------------------------------------------------------------------

    private static void drawMount(Painter p, TileSignalHead head, SignalHeadMount mount, double bottom,
                                  double height, double handX) {
        double top = bottom + height;
        double ground = -head.reach();
        switch (mount) {
            case CEILING:
                p.box(-0.04, top, -0.04, 0.04, 1.0, 0.04, METAL);
                p.box(-0.12, 0.97, -0.12, 0.12, 1.0, 0.12, METAL);
                break;
            case WALL: {
                // In block space: the bracket must still meet its wall whichever way the head turns.
                p.push();
                p.rotateY(head.facingDegrees());
                wallArm(p, head.wallSide(), top - 0.14D);
                wallArm(p, head.wallSide(), bottom + 0.06D);
                p.pop();
                break;
            }
            case GROUND:
                p.box(-0.22, 0.0, -0.18, 0.22, 0.06, 0.18, GALV);
                break;
            case PLATFORM:
            case POST:
                if (bottom > 0.1D) {
                    p.box(-0.05, 0.0, -0.05, 0.05, bottom, 0.05, METAL);
                    p.box(-0.12, 0.0, -0.12, 0.12, 0.04, 0.12, METAL);
                }
                break;
            case MAST:
                mast(p, ground, bottom, 0.0D);
                // A platform for the maintainer behind the head, with a guard rail round its back
                // and sides: nothing in front of the lamps, where it would hide them.
                p.box(-0.3, bottom - 0.08, -0.12, 0.3, bottom - 0.04, 0.5, GALV);
                p.box(-0.3, bottom - 0.04, 0.48, 0.3, bottom + 0.3, 0.5, GALV);
                for (double side : new double[] {-0.3, 0.28}) {
                    p.box(side, bottom - 0.04, 0.0, side + 0.02, bottom + 0.3, 0.5, GALV);
                }
                // Finial.
                if (!head.style().isSemaphore()) {
                    p.box(-0.04, top, -0.04, 0.04, top + 0.12, 0.04, METAL);
                }
                break;
            case GANTRY: {
                double half = head.span() / 2.0D;
                truss(p, -half, half, 0.9D, 1.1D);
                for (double side : new double[] {-half, half}) {
                    p.box(side - 0.08, ground, -0.08, side + 0.08, 1.1, 0.08, GALV);
                    p.box(side - 0.2, ground, -0.2, side + 0.2, ground + 0.08, 0.2, METAL);
                }
                p.box(-0.04, top, -0.04, 0.04, 0.9, 0.04, METAL);
                break;
            }
            case CANTILEVER: {
                double mastX = handX * head.span();
                double from = Math.min(mastX, 0.0D) - 0.2D;
                double to = Math.max(mastX, 0.0D) + 0.2D;
                truss(p, from, to, 0.9D, 1.1D);
                p.box(mastX - 0.1, ground, -0.1, mastX + 0.1, 1.15, 0.1, GALV);
                p.box(mastX - 0.22, ground, -0.22, mastX + 0.22, ground + 0.08, 0.22, METAL);
                // A strut from the mast up to the arm.
                p.push();
                p.translate(mastX, 0.55, 0.0);
                p.rotateZ(handX > 0 ? 45.0D : -45.0D);
                p.box(-0.03, -0.03, -0.03, 0.03, 0.55, 0.03, GALV);
                p.pop();
                p.box(-0.04, top, -0.04, 0.04, 0.9, 0.04, METAL);
                break;
            }
            default:
                break;
        }
    }

    /** A pole with a ladder up its back, from {@code from} to {@code to}, at {@code x}. */
    private static void mast(Painter p, double from, double to, double x) {
        p.box(x - 0.07, from, -0.07, x + 0.07, to, 0.07, GALV);
        p.box(x - 0.16, from, 0.12, x - 0.13, to - 0.05, 0.15, GALV);
        p.box(x + 0.13, from, 0.12, x + 0.16, to - 0.05, 0.15, GALV);
        for (double rung = from + 0.25; rung < to - 0.1; rung += 0.3) {
            p.box(x - 0.13, rung, 0.125, x + 0.13, rung + 0.025, 0.145, GALV);
        }
        p.box(x - 0.03, from, 0.07, x + 0.03, to - 0.05, 0.12, GALV);
    }

    /** A lattice beam along x between {@code from} and {@code to}, {@code low} to {@code high}. */
    private static void truss(Painter p, double from, double to, double low, double high) {
        p.box(from, low, -0.1, to, low + 0.05, 0.1, GALV);
        p.box(from, high - 0.05, -0.1, to, high, 0.1, GALV);
        for (double at = Math.ceil(from * 2.0D) / 2.0D; at <= to; at += 0.5D) {
            p.box(at - 0.02, low, -0.09, at + 0.02, high, 0.09, GALV);
        }
        // A walkway along the back.
        p.box(from, low - 0.02, 0.1, to, low + 0.02, 0.45, METAL);
    }

    /** A horizontal arm from the block centre to the wall on {@code side} (horizontal index). */
    private static void wallArm(Painter p, int side, double y) {
        double t = 0.035D;
        switch (side) {
            case 0: // south, +z
                p.box(-t, y, 0.0, t, y + 2 * t, 0.5, METAL);
                p.box(-0.12, y - 0.06, 0.47, 0.12, y + 0.14, 0.5, METAL);
                break;
            case 1: // west, -x
                p.box(-0.5, y, -t, 0.0, y + 2 * t, t, METAL);
                p.box(-0.5, y - 0.06, -0.12, -0.47, y + 0.14, 0.12, METAL);
                break;
            case 2: // north, -z
                p.box(-t, y, -0.5, t, y + 2 * t, 0.0, METAL);
                p.box(-0.12, y - 0.06, -0.5, 0.12, y + 0.14, -0.47, METAL);
                break;
            case 3: // east, +x
                p.box(0.0, y, -t, 0.5, y + 2 * t, t, METAL);
                p.box(0.47, y - 0.06, -0.12, 0.5, y + 0.14, 0.12, METAL);
                break;
            default:
                break;
        }
    }

    // --- Housings. -----------------------------------------------------------------------------

    private void drawHousing(Painter p, List<Text> texts, TileSignalHead head, SignalHeadStyle style,
                             double bottom, double height, double handX) {
        SignalAspect aspect = head.aspect();
        boolean lit = head.lampsLit();
        double top = bottom + height;
        switch (style) {
            case LED:
            case LED_TWO:
            case LED_LUNAR:
            case STARTER: {
                int mains = style == SignalHeadStyle.LED || style == SignalHeadStyle.LED_LUNAR ? 3 : 2;
                double pitch = 0.26D;
                double mainBottom = top - mains * pitch;
                p.box(-0.17, mainBottom, -0.1, 0.17, top, 0.1, BODY);
                p.box(-0.25, mainBottom - 0.04, -0.02, 0.25, top + 0.04, 0.02, HOOD);
                float[][] colours = mains == 3
                    ? new float[][] {RED, YELLOW, GREEN} : new float[][] {RED, GREEN};
                SignalAspect[] shows = mains == 3
                    ? new SignalAspect[] {SignalAspect.RED, SignalAspect.YELLOW, SignalAspect.GREEN}
                    : new SignalAspect[] {SignalAspect.RED, SignalAspect.GREEN};
                for (int i = 0; i < mains; i++) {
                    double cy = top - pitch * (i + 0.5D);
                    lens(p, 0.0, cy, 0.08, colours[i], lit && shows[i] == aspect, 0.08);
                }
                if (style == SignalHeadStyle.LED_LUNAR) {
                    double cy = mainBottom - 0.12;
                    p.box(-0.12, bottom, -0.1, 0.12, mainBottom, 0.1, BODY);
                    lens(p, 0.0, cy, 0.06, LUNAR, lit && head.subsidiaryLit(), 0.06);
                }
                if (style == SignalHeadStyle.STARTER) {
                    // The departure indicator: a box that lights "RA", right away.
                    p.box(-0.2, bottom, -0.1, 0.2, mainBottom - 0.02, 0.1, BODY);
                    if (lit && head.subsidiaryLit()) {
                        texts.add(new Text("RA", 0.0, (bottom + mainBottom) / 2.0, -0.106, 0.016, WHITE));
                    }
                    else {
                        p.box(-0.14, bottom + 0.04, -0.104, 0.14, mainBottom - 0.06, -0.1, HOOD);
                    }
                }
                break;
            }
            case SEARCHLIGHT: {
                double cy = (bottom + top) / 2.0D;
                p.box(-0.16, bottom, -0.05, 0.16, top, 0.22, BODY);
                disc(p, 0.0, cy, 0.3, -0.06, -0.04, HOOD, false);
                lens(p, 0.0, cy, 0.1, colourOf(aspect), lit, 0.16);
                break;
            }
            case DWARF: {
                p.box(-0.2, bottom, -0.12, 0.2, top, 0.12, BODY);
                double step = height / 4.0D;
                lens(p, 0.0, top - step, 0.07, RED, lit && aspect == SignalAspect.RED, 0.05);
                lens(p, 0.0, bottom + step, 0.07, aspect == SignalAspect.YELLOW ? YELLOW : GREEN,
                    lit && aspect != SignalAspect.RED, 0.05);
                break;
            }
            case COLOUR_LIGHT:
            case COLOUR_LIGHT_FOUR: {
                boolean four = style == SignalHeadStyle.COLOUR_LIGHT_FOUR;
                // Top to bottom, the British way, red at the driver's eye level at the bottom.
                float[][] colours = four
                    ? new float[][] {YELLOW, GREEN, YELLOW, RED} : new float[][] {GREEN, YELLOW, RED};
                boolean[] on = four
                    ? new boolean[] {aspect == SignalAspect.DOUBLE_YELLOW, aspect == SignalAspect.GREEN,
                        aspect == SignalAspect.YELLOW || aspect == SignalAspect.DOUBLE_YELLOW,
                        aspect == SignalAspect.RED}
                    : new boolean[] {aspect == SignalAspect.GREEN, aspect == SignalAspect.YELLOW,
                        aspect == SignalAspect.RED};
                double pitch = height / colours.length;
                p.box(-0.18, bottom, -0.08, 0.18, top, 0.12, BODY);
                // The backplate: black with a white border, round-cornered.
                p.box(-0.34, bottom - 0.05, 0.12, 0.34, top + 0.05, 0.14, PLATE_WHITE);
                p.box(-0.31, bottom - 0.02, 0.105, 0.31, top + 0.02, 0.12, HOOD);
                for (int i = 0; i < colours.length; i++) {
                    double cy = top - pitch * (i + 0.5D);
                    lens(p, 0.0, cy, 0.1, colours[i], lit && on[i], 0.2);
                }
                break;
            }
            case SEMAPHORE_UPPER_HOME:
            case SEMAPHORE_LOWER_HOME:
            case SEMAPHORE_UPPER_DISTANT:
            case SEMAPHORE_LOWER_DISTANT:
                semaphore(p, head, style, bottom, handX);
                break;
            case POSITION_LIGHT:
            case COLOUR_POSITION_LIGHT:
                positionLight(p, style, aspect, lit, (bottom + top) / 2.0D);
                break;
            case ROUTE_FEATHER: {
                boolean on = lit && !head.routeShown().isEmpty();
                p.box(-0.1, bottom, -0.08, 0.1, bottom + 0.2, 0.08, BODY);
                p.push();
                p.translate(0.0, bottom + 0.1, 0.0);
                p.rotateZ(handX > 0 ? -45.0D : 45.0D);
                p.box(-0.08, 0.0, -0.08, 0.08, 0.66, 0.06, BODY);
                for (int i = 0; i < 5; i++) {
                    lens(p, 0.0, 0.14 + i * 0.12, 0.045, LUNAR, on, 0.03);
                }
                p.pop();
                break;
            }
            case THEATRE: {
                p.box(-0.28, bottom, -0.1, 0.28, top, 0.12, BODY);
                p.box(-0.24, bottom + 0.04, -0.104, 0.24, top - 0.04, -0.1, HOOD);
                if (lit && !head.routeShown().isEmpty()) {
                    texts.add(new Text(head.routeShown(), 0.0, (bottom + top) / 2.0, -0.108, 0.03, LUNAR));
                }
                break;
            }
            case CALL_ON: {
                // A small position light: two lunar lamps at 45 degrees; and under it the plate.
                double plateTop = bottom + 0.24;
                p.box(-0.17, plateTop + 0.02, -0.08, 0.17, top, 0.08, BODY);
                boolean on = lit && head.subsidiaryLit();
                lens(p, -0.08, plateTop + 0.1, 0.045, LUNAR, on, 0.03);
                lens(p, 0.08, top - 0.08, 0.045, LUNAR, on, 0.03);
                p.box(-0.2, bottom, -0.04, 0.2, plateTop, 0.0, PLATE_WHITE);
                texts.add(new Text("S&P", 0.0, bottom + 0.12, -0.044, 0.0085, BODY));
                break;
            }
            case TRIP_COCK: {
                p.box(-0.14, bottom, -0.25, 0.14, bottom + 0.12, 0.25, METAL);
                float target = aspect == SignalAspect.RED ? 0.0F : -80.0F;
                p.push();
                p.translate(0.0, bottom + 0.12, -0.2);
                p.rotateX(armAngle(head, target));
                p.box(-0.03, 0.0, -0.03, 0.03, 0.36, 0.03, TRIP_YELLOW);
                p.box(-0.05, 0.3, -0.05, 0.05, 0.38, 0.05, TRIP_YELLOW);
                p.pop();
                break;
            }
            default:
                break;
        }
    }

    /** A semaphore arm, its spectacle and lamp, and the post top it pivots on. */
    private void semaphore(Painter p, TileSignalHead head, SignalHeadStyle style, double bottom,
                           double handX) {
        boolean distant = style.isDistant();
        boolean upper = style == SignalHeadStyle.SEMAPHORE_UPPER_HOME
            || style == SignalHeadStyle.SEMAPHORE_UPPER_DISTANT;
        boolean on = head.aspect() != SignalAspect.GREEN;
        double pivotY = bottom + 0.3D;
        // The post runs up past the pivot to a finial.
        p.box(-0.06, bottom, -0.06, 0.06, pivotY + 0.25, 0.06, GALV);
        p.box(-0.04, pivotY + 0.25, -0.04, 0.04, pivotY + 0.4, 0.04, METAL);
        p.box(-0.07, pivotY + 0.38, -0.07, 0.07, pivotY + 0.42, 0.07, METAL);

        float target = on ? 0.0F : (upper ? 45.0F : -45.0F);
        float angle = armAngle(head, target);
        p.push();
        p.translate(0.0, pivotY, -0.1);
        p.rotateZ(handX > 0 ? angle : -angle);
        float[] blade = distant ? SEMAPHORE_YELLOW : SEMAPHORE_RED;
        double s = handX > 0 ? 1.0D : -1.0D;
        double end = 0.95D;
        p.boxX(s, 0.12, end - (distant ? 0.1 : 0.0), -0.07, 0.07, -0.02, 0.0, blade);
        // The stripe: white bar on a home arm, black chevron on a distant.
        if (distant) {
            p.boxX(s, end - 0.32, end - 0.27, -0.07, 0.07, -0.024, -0.02, HOOD);
            // The fishtail: two prongs either side of a notch.
            p.boxX(s, end - 0.1, end, 0.03, 0.07, -0.02, 0.0, blade);
            p.boxX(s, end - 0.1, end, -0.07, -0.03, -0.02, 0.0, blade);
        }
        else {
            p.boxX(s, end - 0.24, end - 0.16, -0.07, 0.07, -0.024, -0.02, PLATE_WHITE);
        }
        // The spectacle: two coloured glasses on the arm's inner end, behind the pivot.
        float[] onGlass = distant ? YELLOW : RED;
        p.boxX(-s, 0.02, 0.36, -0.12, 0.12, -0.02, 0.0, HOOD);
        p.boxX(-s, 0.08, 0.18, -0.06, 0.04, -0.03, -0.01, scaled(onGlass, 0.5F));
        p.boxX(-s, 0.24, 0.34, -0.06, 0.04, -0.03, -0.01, scaled(GREEN, 0.5F));
        p.pop();
        // The lamp sits still behind the spectacle; whichever glass is in front of it colours it.
        double lampX = -s * 0.13D;
        p.box(lampX - 0.07, pivotY - 0.09, 0.0, lampX + 0.07, pivotY + 0.06, 0.12, BODY);
        lens(p, lampX, pivotY - 0.01, 0.045, on ? onGlass : GREEN, head.lampsLit(), 0.0);
    }

    /** A position light or colour-position light: lamps around a black disc. */
    private static void positionLight(Painter p, SignalHeadStyle style, SignalAspect aspect, boolean lit,
                                      double cy) {
        boolean colour = style == SignalHeadStyle.COLOUR_POSITION_LIGHT;
        double r = 0.25D;
        disc(p, 0.0, cy, 0.34, -0.04, 0.04, HOOD, false);
        p.box(-0.08, cy - 0.08, 0.04, 0.08, cy + 0.08, 0.14, BODY);
        // Stop: horizontal. Caution: diagonal, high on the left as a driver sees it (+x).
        // Clear: vertical.
        double d = r * 0.7071D;
        double[][] stop = {{-r, 0}, {r, 0}};
        double[][] caution = {{d, d}, {-d, -d}};
        double[][] clear = {{0, r}, {0, -r}};
        double[][][] rows = {stop, caution, clear};
        float[][] colours = colour ? new float[][] {RED, YELLOW, GREEN} : new float[][] {AMBER, AMBER, AMBER};
        int shown = aspect == SignalAspect.RED ? 0 : aspect == SignalAspect.GREEN ? 2 : 1;
        for (int row = 0; row < rows.length; row++) {
            for (double[] at : rows[row]) {
                lens(p, at[0], cy + at[1], 0.055, colours[row], lit && row == shown, 0.03);
            }
        }
        if (!colour) {
            // The position light's centre lamp belongs to every row.
            lens(p, 0.0, cy, 0.055, AMBER, lit, 0.03);
        }
    }

    /** The arm's drawn angle, swung toward {@code target} at {@link #ARM_SPEED}. */
    private static float armAngle(TileSignalHead head, float target) {
        long now = System.nanoTime();
        if (Float.isNaN(head.armDegrees) || head.getWorld() == null) {
            head.armDegrees = target;
        }
        else {
            float dt = Math.min(0.25F, (now - head.armNanos) / 1.0E9F);
            float step = ARM_SPEED * dt;
            float diff = target - head.armDegrees;
            head.armDegrees += Math.abs(diff) <= step ? diff : Math.signum(diff) * step;
        }
        head.armNanos = now;
        return head.armDegrees;
    }

    private static float[] colourOf(SignalAspect aspect) {
        switch (aspect) {
            case RED:
                return RED;
            case GREEN:
                return GREEN;
            default:
                return YELLOW;
        }
    }

    private static float[] scaled(float[] colour, float by) {
        return new float[] {colour[0] * by, colour[1] * by, colour[2] * by};
    }

    /**
     * A lens on the face: an octagonal disc of glass, lit (full-bright) or dark, under a hood
     * {@code hoodDepth} deep, all at {@code z = -0.1}, the face plane.
     */
    private static void lens(Painter p, double cx, double cy, double r, float[] colour, boolean lit,
                             double hoodDepth) {
        disc(p, cx, cy, r, -0.13, -0.1, lit ? colour : scaled(colour, DARK), lit);
        if (hoodDepth > 0.0D) {
            double y = cy + r + 0.01D;
            p.box(cx - r - 0.02, y, -0.1 - hoodDepth - 0.03, cx + r + 0.02, y + 0.02, -0.1, HOOD);
            p.box(cx - r - 0.03, cy - r * 0.2, -0.1 - hoodDepth * 0.6, cx - r - 0.01, y + 0.02, -0.1, HOOD);
            p.box(cx + r + 0.01, cy - r * 0.2, -0.1 - hoodDepth * 0.6, cx + r + 0.03, y + 0.02, -0.1, HOOD);
        }
    }

    /** An octagon-ish disc as three overlapping boxes. */
    private static void disc(Painter p, double cx, double cy, double r, double z1, double z2,
                             float[] colour, boolean glow) {
        double k = r * 0.42D;
        p.box(cx - r, cy - k, z1, cx + r, cy + k, z2, colour, glow);
        p.box(cx - k, cy - r, z1, cx + k, cy + r, z2, colour, glow);
        double m = r * 0.75D;
        p.box(cx - m, cy - m, z1 + 0.001, cx + m, cy + m, z2 - 0.001, colour, glow);
    }

    // --- Text. ---------------------------------------------------------------------------------

    /** Text on a face: what it says, centre, scale, colour; drawn full-bright. */
    private static final class Text {
        final String text;
        final double x;
        final double y;
        final double z;
        final double scale;
        final float[] colour;

        Text(String text, double x, double y, double z, double scale, float[] colour) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.z = z;
            this.scale = scale;
            this.colour = colour;
        }
    }

    private static void drawTexts(List<Text> texts) {
        if (texts.isEmpty()) {
            return;
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        for (Text text : texts) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(text.x, text.y, text.z);
            // Face toward -z, reading left to right for a viewer looking along +z.
            GlStateManager.scale(-text.scale, -text.scale, text.scale);
            int colour = ((int) (text.colour[0] * 255) << 16) | ((int) (text.colour[1] * 255) << 8)
                | (int) (text.colour[2] * 255);
            font.drawString(text.text, -font.getStringWidth(text.text) / 2, -font.FONT_HEIGHT / 2 + 1, colour);
            GlStateManager.popMatrix();
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // --- The binding overlay. ------------------------------------------------------------------

    private static boolean isHoldingTransitTool() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        return player != null && player.getHeldItemMainhand().getItem() == RcmcItems.transitTool;
    }

    /**
     * A line from the head to the signal or switch it is linked to, while the player holds the
     * transit tool: the ride-check view of which logical signal a head repeats.
     */
    private static void drawBinding(TileSignalHead head, double x, double y, double z) {
        RcmcWorldState state = RcmcWorldState.of(head.getWorld());
        if (state == null) {
            return;
        }
        TrackNetwork network = state.network();
        List<Vec3> targets = new ArrayList<>();
        List<float[]> colours = new ArrayList<>();
        if (head.isLinked() && network.hasSection(head.sectionId())) {
            targets.add(network.positionAt(new TrackRef(head.sectionId(), head.distance())));
            colours.add(colourOf(head.aspect()));
        }
        TrackNetwork.SectionEnd sw = head.linkedSwitch();
        if (sw != null && network.hasSection(sw.sectionId)) {
            TrackSection section = network.section(sw.sectionId);
            targets.add(section.endpointAt(sw.end));
            colours.add(LUNAR);
        }
        if (targets.isEmpty()) {
            return;
        }
        double ox = head.getPos().getX();
        double oy = head.getPos().getY();
        double oz = head.getPos().getZ();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x - ox, y - oy, z - oz);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.glLineWidth(3.0F);
        // Full-bright, or at night the lines take the dark of the world around them and vanish.
        float lastX = OpenGlHelper.lastBrightnessX;
        float lastY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < targets.size(); i++) {
            Vec3 at = targets.get(i);
            float[] c = colours.get(i);
            buffer.pos(ox + 0.5D, oy + 0.5D, oz + 0.5D).color(c[0], c[1], c[2], 1.0F).endVertex();
            buffer.pos(at.x, at.y + 0.3D, at.z).color(c[0], c[1], c[2], 1.0F).endVertex();
            // A short post at the boundary itself.
            buffer.pos(at.x, at.y, at.z).color(c[0], c[1], c[2], 1.0F).endVertex();
            buffer.pos(at.x, at.y + 1.5D, at.z).color(c[0], c[1], c[2], 1.0F).endVertex();
        }
        tessellator.draw();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    // --- Painter. ------------------------------------------------------------------------------

    /**
     * Collects boxes in the head's local frame, through a small stack of rotations and
     * translations, into two lists: what the world lights, and what glows.
     */
    static final class Painter {
        private final List<double[]> lit = new ArrayList<>();
        private final List<double[]> glow = new ArrayList<>();
        /** Row-major 3x4 affine transform. */
        private double[] m = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};
        private final java.util.ArrayDeque<double[]> stack = new java.util.ArrayDeque<>();

        void push() {
            stack.push(m.clone());
        }

        void pop() {
            m = stack.pop();
        }

        void translate(double x, double y, double z) {
            m[3] += m[0] * x + m[1] * y + m[2] * z;
            m[7] += m[4] * x + m[5] * y + m[6] * z;
            m[11] += m[8] * x + m[9] * y + m[10] * z;
        }

        /** Rotates about y the way {@code GlStateManager.rotate(degrees, 0, 1, 0)} does. */
        void rotateY(double degrees) {
            double c = Math.cos(Math.toRadians(degrees));
            double s = Math.sin(Math.toRadians(degrees));
            multiply(new double[] {c, 0, s, 0, 1, 0, -s, 0, c});
        }

        /** Rotates about z, counter-clockwise looking along -z: +x toward +y. */
        void rotateZ(double degrees) {
            double c = Math.cos(Math.toRadians(degrees));
            double s = Math.sin(Math.toRadians(degrees));
            multiply(new double[] {c, -s, 0, s, c, 0, 0, 0, 1});
        }

        /** Rotates about x: +y toward -z. */
        void rotateX(double degrees) {
            double c = Math.cos(Math.toRadians(degrees));
            double s = Math.sin(Math.toRadians(degrees));
            multiply(new double[] {1, 0, 0, 0, c, s, 0, -s, c});
        }

        private void multiply(double[] r) {
            double[] n = m.clone();
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    n[row * 4 + col] = m[row * 4] * r[col] + m[row * 4 + 1] * r[3 + col]
                        + m[row * 4 + 2] * r[6 + col];
                }
            }
            m = n;
        }

        void box(double x1, double y1, double z1, double x2, double y2, double z2, float[] colour) {
            box(x1, y1, z1, x2, y2, z2, colour, false);
        }

        /** A box whose x extent runs from {@code a} to {@code b} on the {@code side} (+1 or -1) of 0. */
        void boxX(double side, double a, double b, double y1, double y2, double z1, double z2,
                  float[] colour) {
            box(side > 0 ? a : -b, y1, z1, side > 0 ? b : -a, y2, z2, colour, false);
        }

        void box(double x1, double y1, double z1, double x2, double y2, double z2, float[] colour,
                 boolean glows) {
            double[][] corners = new double[8][];
            int i = 0;
            for (double x : new double[] {x1, x2}) {
                for (double y : new double[] {y1, y2}) {
                    for (double z : new double[] {z1, z2}) {
                        corners[i++] = new double[] {
                            m[0] * x + m[1] * y + m[2] * z + m[3],
                            m[4] * x + m[5] * y + m[6] * z + m[7],
                            m[8] * x + m[9] * y + m[10] * z + m[11]};
                    }
                }
            }
            List<double[]> into = glows ? glow : lit;
            // Corner index bits: x<<2 | y<<1 | z. Faces with their shade.
            face(into, corners, 0, 2, 6, 4, colour, 0.85F, glows); // z1 (front)
            face(into, corners, 1, 3, 7, 5, colour, 0.85F, glows); // z2 (back)
            face(into, corners, 0, 2, 3, 1, colour, 0.7F, glows);  // x1
            face(into, corners, 4, 6, 7, 5, colour, 0.7F, glows);  // x2
            face(into, corners, 2, 3, 7, 6, colour, 1.0F, glows);  // y2 (top)
            face(into, corners, 0, 1, 5, 4, colour, 0.55F, glows); // y1 (bottom)
        }

        private static void face(List<double[]> into, double[][] c, int a, int b, int d, int e,
                                 float[] colour, float shade, boolean glows) {
            float s = glows ? 1.0F : shade;
            into.add(new double[] {
                c[a][0], c[a][1], c[a][2], c[b][0], c[b][1], c[b][2],
                c[d][0], c[d][1], c[d][2], c[e][0], c[e][1], c[e][2],
                Math.min(1.0F, colour[0] * s), Math.min(1.0F, colour[1] * s), Math.min(1.0F, colour[2] * s)});
        }

        void emit(BufferBuilder buffer, boolean glows) {
            for (double[] q : glows ? glow : lit) {
                float r = (float) q[12];
                float g = (float) q[13];
                float b = (float) q[14];
                for (int v = 0; v < 4; v++) {
                    buffer.pos(q[v * 3], q[v * 3 + 1], q[v * 3 + 2]).color(r, g, b, 1.0F).endVertex();
                }
            }
        }
    }
}
