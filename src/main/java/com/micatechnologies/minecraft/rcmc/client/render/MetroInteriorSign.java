package com.micatechnologies.minecraft.rcmc.client.render;

import com.micatechnologies.minecraft.rcmc.client.render.sign.SignPanels;
import com.micatechnologies.minecraft.rcmc.physics.transit.ServiceSnapshot;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.world.World;

/**
 * The "Next stop" display inside a metro car — the thing that makes riding one feel like riding
 * one rather than sitting in a moving box.
 *
 * <p>Mounted under the ceiling at each end of the car and drawn on both faces, so it reads from
 * anywhere in the saloon. Content comes from the same synced {@link ServiceSnapshot} the platform
 * arrival boards use: the sign stores nothing, resolves the live service every frame, and a car not
 * in service simply shows nothing. That is why the snapshot carries a train id — a platform board
 * asks "which line is coming", but a car has to find <em>its own</em> service among several running
 * on the same line.</p>
 *
 * <p><b>The mirror.</b> {@code RenderCoasterCar} loads the car's frame as a basis, and
 * {@code TrackFrame} defines {@code right = forward × up}, which makes that basis left-handed — a
 * reflection, not a rotation (its own javadoc explains why the fix belongs at the source, not
 * here). Text drawn under a reflection comes out backwards, so this flips x back before drawing.
 * Geometry does not care and the panel is symmetric, but a mirrored destination is exactly the kind
 * of thing that reads as "the sign is broken".</p>
 */
public final class MetroInteriorSign {

    /** Height of the sign's underside above the car floor datum, in blocks. */
    private static final double PANEL_BOTTOM = 4.55D;

    /** Height of the sign's top — just under the roof interior at 5.25. */
    private static final double PANEL_TOP = 5.18D;

    private static final double PANEL_HALF_THICKNESS = 0.03D;

    /** How far in from each end of the body the sign hangs, in blocks. */
    private static final double END_INSET = 0.9D;

    /** World units per font pixel. Sized so a stop name fills the panel without crowding it. */
    private static final float TEXT_SCALE = 0.019F;

    /** Ticks each character-step of the marquee holds — slow enough to read at a glance. */
    private static final int SCROLL_TICKS_PER_STEP = 3;

    /** What separates the end of a scrolling message from its own beginning. */
    private static final String MARQUEE_GAP = "   •   ";

    private static final int COLOUR_LABEL = 0xFFC24A;
    private static final int COLOUR_STOP = 0xFFFFFF;

    private MetroInteriorSign() {
        throw new AssertionError("No instances.");
    }

    /**
     * Draws both interior signs for a car, if its train is in service.
     *
     * <p>Called from inside {@code RenderCoasterCar}'s car-space transform, with texturing and
     * lighting in whatever state that method left them; this restores what it changes.</p>
     *
     * @param bodyLength the visible body length, so the signs sit at the ends of the saloon
     */
    public static void draw(World world, int trainId, double bodyLength, float partialTicks) {
        ServiceSnapshot snapshot = snapshotFor(world, trainId);
        if (snapshot == null) {
            return;
        }
        RcmcWorldState state = RcmcWorldState.of(world);
        TransitLine line = state == null ? null : state.transit().line(snapshot.lineName());
        if (line == null || snapshot.nextStopIndex() >= line.stationCount()) {
            // The line was edited out from under a running service. Showing nothing beats showing
            // a stale destination — a wrong stop name is worse than no sign.
            return;
        }
        String stop = line.station(snapshot.nextStopIndex()).name();
        String label = snapshot.atPlatform() ? "Now at" : "Next stop";
        String destination = line.name() + " to " + line.labelFor(snapshot.serviceDirection());

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        if (font == null) {
            return;
        }
        long time = world.getTotalWorldTime();
        double halfWidth = 1.65D;
        int maxPixels = (int) (halfWidth * 2.0D / TEXT_SCALE) - 8;

        String[] lines = {
            marquee(font, label + ": " + stop, maxPixels, time),
            marquee(font, destination, maxPixels, time),
        };
        int[] colours = {COLOUR_STOP, COLOUR_LABEL};

        double at = Math.max(1.2D, bodyLength * 0.5D - END_INSET);
        for (double z : new double[] {at, -at}) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0.0D, 0.0D, z);
            SignPanels.drawPanel(halfWidth, PANEL_BOTTOM, PANEL_TOP, PANEL_HALF_THICKNESS,
                0.06F, 0.06F, 0.07F);
            // Undo the car basis's reflection so glyphs read forwards — see the class javadoc.
            GlStateManager.scale(-1.0F, 1.0F, 1.0F);
            SignPanels.drawLines(font, lines, colours, PANEL_TOP - 0.06D, TEXT_SCALE,
                PANEL_HALF_THICKNESS + 0.005D);
            GlStateManager.popMatrix();
        }
    }

    /** The running service for this train, or {@code null} if it is not in service. */
    private static ServiceSnapshot snapshotFor(World world, int trainId) {
        RcmcWorldState state = RcmcWorldState.of(world);
        if (state == null) {
            return null;
        }
        for (ServiceSnapshot snapshot : state.serviceSnapshots()) {
            if (snapshot.trainId() == trainId) {
                return snapshot;
            }
        }
        return null;
    }

    /**
     * The visible window of a line that may be too long for the panel, scrolled by time.
     *
     * <p>Scrolling by <em>character window</em> rather than by pixel offset is deliberate: a pixel
     * offset needs the text clipped to the panel, and clipping means a scissor rectangle, which is
     * in screen space — meaningless for a panel hanging at an arbitrary angle inside a moving,
     * banking train. Stepping whole characters keeps everything in the string domain, costs
     * nothing, and looks like the dot-matrix displays it is imitating anyway.</p>
     *
     * <p>Text that fits is returned untouched, so the common case never moves.</p>
     */
    static String marquee(FontRenderer font, String text, int maxPixels, long timeTicks) {
        if (text == null || text.isEmpty() || font.getStringWidth(text) <= maxPixels) {
            return text;
        }
        String full = text + MARQUEE_GAP;
        int start = (int) ((timeTicks / SCROLL_TICKS_PER_STEP) % full.length());
        StringBuilder window = new StringBuilder();
        for (int i = 0; i < full.length(); i++) {
            char c = full.charAt((start + i) % full.length());
            if (font.getStringWidth(window.toString() + c) > maxPixels) {
                break;
            }
            window.append(c);
        }
        return window.toString();
    }
}
