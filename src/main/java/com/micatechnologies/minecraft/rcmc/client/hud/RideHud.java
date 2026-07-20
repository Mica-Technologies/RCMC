package com.micatechnologies.minecraft.rcmc.client.hud;

import com.micatechnologies.minecraft.rcmc.RcmcConfig;
import com.micatechnologies.minecraft.rcmc.physics.GForces;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Draws a rider's live speed, G-forces, height and ride time while they are on a coaster car.
 *
 * <p><b>Units.</b> Speed is shown as blocks/s <em>and</em> km/h. Blocks/s is the unit the
 * simulation actually runs in — a block is a metre by convention (see {@code CLAUDE.md}), so it
 * is exactly metres/second — but very few players have an intuition for coaster speed in m/s.
 * km/h is how the real coaster hobby usually reports it, so it is added purely for a rider to
 * recognise "that felt like a real 100 km/h drop"; mph would serve the same purpose equally well
 * and was not chosen for any deeper reason than the mod having no other unit precedent to match.</p>
 *
 * <p><b>Layout.</b> Bottom-left corner, growing upward, sized to its own content rather than a
 * hardcoded height. The hotbar and its neighbours (health/food/XP bar) are all horizontally
 * centred, so anchoring to the far left keeps this clear of them without needing to know their
 * exact pixel geometry.</p>
 *
 * <p>Gated behind {@code RcmcConfig.enableRideHud} — purely a convenience toggle for players who
 * find any on-screen readout distracting; unlike {@link GForceEffects} this HUD has no motion-
 * comfort implications of its own.</p>
 */
@SideOnly(Side.CLIENT)
public final class RideHud {

    private static final double BLOCKS_PER_SECOND_TO_KMH = 3.6D;

    private static final int COLOR_NORMAL = 0xFFFFFFFF;
    /** Distinct from the rest of the readout so airtime — the single most rider-relevant line — cannot be skimmed past. */
    private static final int COLOR_AIRTIME = 0xFF55FFFF;
    private static final int COLOR_LABEL = 0xFFAAAAAA;

    private static final int MARGIN = 6;

    private final RideMonitor monitor;

    public RideHud(RideMonitor monitor) {
        if (monitor == null) {
            throw new IllegalArgumentException("monitor must not be null");
        }
        this.monitor = monitor;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (!RcmcConfig.enableRideHud || event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            // ElementType.ALL fires exactly once per frame, after every other overlay element —
            // filtering to it avoids drawing five separate times as Post fires for HOTBAR, HEALTH,
            // FOOD, etc. in turn.
            return;
        }
        RideTelemetry.Reading reading = monitor.latestReading();
        if (!monitor.isRiding() || reading == null) {
            return;
        }
        draw(reading, event.getResolution());
    }

    private void draw(RideTelemetry.Reading reading, ScaledResolution resolution) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        GForces g = reading.gForces;
        int lineHeight = font.FONT_HEIGHT + 2;
        int lineCount = 5;

        int x = MARGIN;
        int y = resolution.getScaledHeight() - MARGIN - lineCount * lineHeight;

        double kmh = reading.speedBlocksPerSecond * BLOCKS_PER_SECOND_TO_KMH;
        font.drawStringWithShadow(
            String.format("%.1f blocks/s (%.0f km/h)", reading.speedBlocksPerSecond, kmh),
            x, y, COLOR_NORMAL);
        y += lineHeight;

        boolean airtime = g.isAirtime();
        font.drawStringWithShadow(formatAxis("Vert", g.vertical, airtime),
            x, y, airtime ? COLOR_AIRTIME : COLOR_NORMAL);
        y += lineHeight;

        font.drawStringWithShadow(formatAxis("Lat", g.lateral, false), x, y, COLOR_NORMAL);
        y += lineHeight;

        font.drawStringWithShadow(formatAxis("Long", g.longitudinal, false), x, y, COLOR_NORMAL);
        y += lineHeight;

        font.drawStringWithShadow(
            String.format("+%.1f blocks  |  %s", monitor.heightAboveLowestPoint(),
                formatTime(monitor.elapsedSeconds())),
            x, y, COLOR_LABEL);
    }

    /**
     * Airtime is called out with both a colour change (above) and this text tag, rather than
     * colour alone — a colour-only signal is invisible to a colourblind rider, and airtime is
     * exactly the reading a coaster fan most wants to not miss.
     */
    private static String formatAxis(String label, double valueG, boolean airtime) {
        return label + " " + String.format("%+.2fg", valueG) + (airtime ? " AIRTIME" : "");
    }

    private static String formatTime(double seconds) {
        int total = (int) seconds;
        return String.format("%d:%02d", total / 60, total % 60);
    }
}
