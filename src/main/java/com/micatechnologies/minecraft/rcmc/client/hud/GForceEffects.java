package com.micatechnologies.minecraft.rcmc.client.hud;

import com.micatechnologies.minecraft.rcmc.RcmcConfig;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Screen effects driven by sustained G-load: grey-out under heavy positive vertical G, red-out
 * under sustained negative vertical G, and an FOV kick proportional to longitudinal acceleration.
 *
 * <p><b>These are motion-comfort and accessibility settings, not decoration.</b> Every effect here
 * is independently disableable via {@code RcmcConfig} ({@link RcmcConfig#enableGForceTint},
 * {@link RcmcConfig#enableGForceFovKick}), and every default is intensity-modest rather than
 * cinematic — a player who leaves them on should barely notice them outside genuinely intense
 * moments of a ride.</p>
 *
 * <p><b>Sustained, not instantaneous.</b> Both effects read {@link RideMonitor#smoothedVerticalG()}
 * / {@link RideMonitor#smoothedLongitudinalG()} — values already run through a multi-second
 * {@link GForceSmoother} — rather than the instantaneous reading {@link RideHud} displays. See
 * that class's javadoc for the filter itself; the point here is simply that a single-tick physics
 * spike (a sub-step catching a sharp transition, a curvature sample landing on a section boundary)
 * cannot reach these effects at meaningful intensity, only load that is actually held for a
 * while.</p>
 *
 * <p><b>Real-world calibration.</b> Sustained +4.5 g vertical is roughly where real pilots and
 * coaster riders start greying out, which is why {@link RcmcConfig#grayOutThresholdG} defaults
 * there. Red-out (blood pooling toward the head under negative Gz) sets in at a lower magnitude in
 * practice, so {@link RcmcConfig#redOutThresholdG} defaults to a smaller-magnitude negative value.
 * Both are config, not constants, precisely because "how much G a given player can take before it
 * should show" is a comfort preference, not a fact.</p>
 */
@SideOnly(Side.CLIENT)
public final class GForceEffects {

    private static final int GRAY_RGB = 0x808080;
    private static final int RED_RGB = 0xB00000;

    private final RideMonitor monitor;

    public GForceEffects(RideMonitor monitor) {
        if (monitor == null) {
            throw new IllegalArgumentException("monitor must not be null");
        }
        this.monitor = monitor;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (!RcmcConfig.enableGForceTint || event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            // ALL fires once per frame after every other overlay element, which is exactly where a
            // full-screen tint needs to draw to sit on top of everything else.
            return;
        }
        if (!monitor.isRiding()) {
            return;
        }
        drawTint(monitor.smoothedVerticalG(), event.getResolution());
    }

    private void drawTint(double sustainedVerticalG, ScaledResolution resolution) {
        double alpha;
        int rgb;
        if (sustainedVerticalG >= RcmcConfig.grayOutThresholdG) {
            alpha = ramp(sustainedVerticalG, RcmcConfig.grayOutThresholdG, RcmcConfig.grayOutRangeG);
            rgb = GRAY_RGB;
        }
        else if (sustainedVerticalG <= RcmcConfig.redOutThresholdG) {
            // Ramp direction flips: red-out gets stronger the further BELOW the threshold, so the
            // ramp input is measured the other way round from the grey-out case above.
            alpha = ramp(-sustainedVerticalG, -RcmcConfig.redOutThresholdG, RcmcConfig.redOutRangeG);
            rgb = RED_RGB;
        }
        else {
            return;
        }

        alpha *= RcmcConfig.gForceTintMaxAlpha;
        if (alpha < 0.004D) {
            // Below one 8-bit alpha step — drawing it would be a wasted GL call with no visible
            // effect.
            return;
        }

        int argb = (clampAlphaByte(alpha) << 24) | (rgb & 0xFFFFFF);
        Gui.drawRect(0, 0, resolution.getScaledWidth(), resolution.getScaledHeight(), argb);
    }

    /** 0 at {@code threshold}, ramping linearly to 1 over the next {@code range}, clamped to [0, 1]. */
    private static double ramp(double value, double threshold, double range) {
        double safeRange = Math.max(range, 0.01D);
        return clamp01((value - threshold) / safeRange);
    }

    private static double clamp01(double v) {
        return v < 0.0D ? 0.0D : (v > 1.0D ? 1.0D : v);
    }

    private static int clampAlphaByte(double alpha) {
        return (int) (clamp01(alpha) * 255.0D);
    }

    @SubscribeEvent
    public void onFovModifier(EntityViewRenderEvent.FOVModifier event) {
        if (!RcmcConfig.enableGForceFovKick || !monitor.isRiding()) {
            return;
        }
        double longitudinalG = monitor.smoothedLongitudinalG();
        double kick = longitudinalG * RcmcConfig.fovKickDegreesPerG;
        double clampedKick = Math.max(-RcmcConfig.fovKickMaxDegrees,
            Math.min(RcmcConfig.fovKickMaxDegrees, kick));
        // Positive longitudinal G (accelerating, e.g. a launch) widens the FOV for a speed-sensation
        // kick; negative (braking) narrows it toward a tunnel-like deceleration feel. Matches the
        // sign convention GForces documents: positive longitudinal is "under acceleration".
        event.setFOV((float) (event.getFOV() + clampedKick));
    }
}
