package com.micatechnologies.minecraft.rcmc.client;

import com.micatechnologies.minecraft.rcmc.RcmcConfig;
import com.micatechnologies.minecraft.rcmc.entity.EntityCoasterCar;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Rolls a rider's camera with the car they are on.
 *
 * <p>Vanilla 1.12.2 has no camera roll — the player's view is yaw and pitch only, which is why a
 * banked turn or an inversion would otherwise leave the world stubbornly upright while the car
 * around it rotates. Forge does provide the hook: {@code EntityViewRenderEvent.CameraSetup}
 * carries a roll field, and {@code EntityRenderer} applies it as a Z-axis rotation immediately
 * after firing the event. So this needs no mixin, which is a real win — a mixin on the camera path
 * is prime territory for conflicts with Optifine and shader mods.</p>
 *
 * <p><b>How the angle is derived.</b> Roll is the angle between the car's {@code up} and the
 * world-up direction projected into the plane perpendicular to where the camera is looking. It is
 * signed by which side of the car's {@code right} axis world-up falls on, so banking left and
 * banking right roll opposite ways rather than both reading as positive.</p>
 *
 * <p>Gated behind {@code RcmcConfig.enableCameraRoll}: camera roll is a genuine motion-sickness
 * trigger for some players, and an inversion that rotates the world 360° is exactly the case where
 * that matters. Riders who disable it still ride; they just stay upright.</p>
 */
@SideOnly(Side.CLIENT)
public final class RiderCamera {

    /**
     * Per-frame smoothing applied to the roll angle.
     *
     * <p>The car's own frame is already interpolated between ticks, so this is not covering a
     * stepping problem — it takes the edge off the instant a train enters a sharp transition,
     * where the geometrically-correct roll rate is high enough to feel like a snap even though it
     * is technically right. Low-pass filtering the camera and not the car is deliberate: the car
     * should look exactly where the physics says it is, while the rider's head lags very slightly,
     * which is also what a real neck does.</p>
     */
    private static final float SMOOTHING = 0.35F;

    private float smoothedRoll;
    private boolean wasRiding;

    @SubscribeEvent
    public void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        if (!RcmcConfig.enableCameraRoll) {
            return;
        }
        Entity viewer = Minecraft.getMinecraft().getRenderViewEntity();
        if (viewer == null || !(viewer.getRidingEntity() instanceof EntityCoasterCar)) {
            // Reset on dismount so the next ride does not start from the last ride's roll.
            smoothedRoll = 0.0F;
            wasRiding = false;
            return;
        }

        EntityCoasterCar car = (EntityCoasterCar) viewer.getRidingEntity();
        TrackFrame frame = car.frame();
        if (frame == null) {
            return;
        }

        float target = (float) frame.rollDegreesFromLevel();
        if (!wasRiding) {
            // Boarding mid-bank should not sweep the camera from level; start where the car is.
            smoothedRoll = target;
            wasRiding = true;
        }
        else {
            smoothedRoll += (target - smoothedRoll) * SMOOTHING;
        }

        event.setRoll(smoothedRoll);
    }

}
