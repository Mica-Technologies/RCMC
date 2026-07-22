package com.micatechnologies.minecraft.rcmc.client.render;

import com.micatechnologies.minecraft.rcmc.client.render.sign.SignPanels;
import com.micatechnologies.minecraft.rcmc.physics.transit.ServiceSnapshot;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSignText;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.world.World;

/**
 * The amber destination sign on the <em>outside</em> of a metro car — one per side — showing where
 * the train is bound, the way an MBTA Orange Line car reads "FOREST HILLS" to the platform.
 *
 * <p>Content is the destination terminus from the same synced {@link ServiceSnapshot} the interior
 * sign and platform boards use, routed through {@link TransitSignText} so every display in the mod
 * says the same thing. A car not in service shows nothing.</p>
 *
 * <p>The sign sits in the body's upper band, between the window heads and the roll of the roof, and
 * faces outward on each side. It is drawn inside {@code RenderCoasterCar}'s car-space transform,
 * which loads the car frame as a left-handed basis — so, exactly like {@link MetroInteriorSign}, the
 * text is un-mirrored before drawing or the glyphs come out backwards. Both faces are drawn, so the
 * sign reads from either approach along the platform.</p>
 */
public final class MetroExteriorSign {

    /** Side wall x, matching {@code MetroCarModel.BODY_HALF_WIDTH}; proud so the sign stands off it. */
    private static final double SIDE_X = 1.90D;
    private static final double PROUD = 0.04D;

    /** Sign band, within the body's window-head-to-roof-base gap (5.15 → 5.75). */
    private static final double BAND_BOTTOM = 5.20D;
    private static final double BAND_TOP = 5.66D;

    private static final double PANEL_HALF_WIDTH = 1.5D;
    private static final double PANEL_HALF_THICKNESS = 0.03D;

    /** World units per font pixel — a bold, platform-legible dot-matrix line. */
    private static final float TEXT_SCALE = 0.02F;

    /** MBTA amber. */
    private static final int AMBER = 0xFFB300;

    private MetroExteriorSign() {
        throw new AssertionError("No instances.");
    }

    /**
     * Draws both side destination signs for a car, if its train is in service.
     *
     * @param bodyLength the visible body length; the sign centres along it
     */
    public static void draw(World world, int trainId, double bodyLength, float partialTicks) {
        RcmcWorldState state = RcmcWorldState.of(world);
        ServiceSnapshot snapshot = snapshotFor(state, trainId);
        if (snapshot == null) {
            return;
        }
        TransitLine line = state.transit().line(snapshot.lineName());
        if (line == null) {
            return;
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        if (font == null) {
            return;
        }
        String destination = TransitSignText.exteriorDestination(line, snapshot.serviceDirection());
        int maxPixels = (int) (PANEL_HALF_WIDTH * 2.0D / TEXT_SCALE) - 8;
        String shown = MetroInteriorSign.marquee(font, destination, maxPixels,
            world.getTotalWorldTime());
        String[] lines = {shown};
        int[] colours = {AMBER};

        // Both sides. sx = +1 is the +x wall, -1 the -x wall; each panel is rotated to face outward.
        for (int sx = 1; sx >= -1; sx -= 2) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(sx * (SIDE_X + PROUD), 0.0D, 0.0D);
            // Turn the natively ±z-facing panel to face ±x (outward on this side).
            GlStateManager.rotate(90.0F * sx, 0.0F, 1.0F, 0.0F);
            SignPanels.drawPanel(PANEL_HALF_WIDTH, BAND_BOTTOM, BAND_TOP, PANEL_HALF_THICKNESS,
                0.04F, 0.04F, 0.05F);
            // Undo the car basis's reflection so glyphs read forwards — see the class javadoc and
            // MetroInteriorSign, which does the same for the interior panels.
            GlStateManager.scale(-1.0F, 1.0F, 1.0F);
            SignPanels.drawLines(font, lines, colours, BAND_TOP - 0.05D, TEXT_SCALE,
                PANEL_HALF_THICKNESS + 0.005D);
            GlStateManager.popMatrix();
        }
    }

    private static ServiceSnapshot snapshotFor(RcmcWorldState state, int trainId) {
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
}
