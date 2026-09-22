package com.micatechnologies.minecraft.rcmc.client.sound;

import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * One looping sound following one train — its rolling, its chain, its wind. The
 * {@link CoasterSoundDirector} sets a target volume, pitch and position every tick; this glides
 * toward them, so a train speeding up is a rising note rather than a staircase.
 *
 * <p><b>Range versus loudness.</b> Minecraft fixes a positional sound's audible range once, when it
 * starts, at 16 blocks times its volume if that is above 1 — and reads the same volume again, in
 * the same call, for the starting gain ({@code SoundManager.playSound}: range from the first
 * {@code getVolume()}, gain from the second). A coaster should carry across a park without every
 * loop starting at full blast, so the first read answers with the range and every later read with
 * the real level. Riders' loops have no position and so no range; for them it is never used.</p>
 */
@SideOnly(Side.CLIENT)
final class TrainLoopSound extends MovingSound {

    /** Largest volume change per tick. A full fade in or out takes about half a second. */
    private static final float VOLUME_SLEW = 0.1F;
    /** Largest pitch change per tick; smooths the steps between client prediction corrections. */
    private static final float PITCH_SLEW = 0.06F;
    /** A loop that has been silent this long is released; the director starts a new one if needed. */
    private static final int SILENT_TICKS_BEFORE_RELEASE = 40;

    private final float startingRange;
    private boolean rangeReported;

    private float targetVolume;
    private float targetPitch;
    private int silentTicks;
    private boolean released;
    private int age;

    /**
     * @param onBoard       true for a rider's own loop: no position, no distance falloff
     * @param range         audible-range factor for a bystander's loop (16 blocks per unit, min 1)
     * @param volume        starting level, above zero — Minecraft skips a sound that starts at zero
     */
    TrainLoopSound(SoundEvent event, boolean onBoard, float range, float volume, float pitch,
                   double x, double y, double z) {
        super(event, SoundCategory.BLOCKS);
        this.repeat = true;
        this.repeatDelay = 0;
        this.attenuationType = onBoard ? ISound.AttenuationType.NONE : ISound.AttenuationType.LINEAR;
        this.startingRange = onBoard ? 1.0F : Math.max(1.0F, range);
        this.volume = volume;
        this.pitch = pitch;
        this.targetVolume = volume;
        this.targetPitch = pitch;
        moveTo(x, y, z);
    }

    /** What the director wants this tick. */
    void target(float volume, float pitch, double x, double y, double z) {
        this.age++;
        this.targetVolume = volume;
        this.targetPitch = pitch;
        moveTo(x, y, z);
    }

    /** Fades out and ends. The director calls this when the train is gone or out of earshot. */
    void release() {
        this.released = true;
        this.targetVolume = 0.0F;
    }

    boolean isReleased() {
        return released || donePlaying;
    }

    /**
     * Ticks the director has been driving this loop. A loop only reads as playing once the sound
     * library has actually started it, which happens a moment after {@code playSound} — so a loop
     * younger than a second is never taken for a stopped one, or a fresh copy would be started
     * every tick until it caught up.
     */
    int age() {
        return age;
    }

    @Override
    public float getVolume() {
        if (!rangeReported) {
            rangeReported = true;
            return startingRange;
        }
        return super.getVolume();
    }

    @Override
    public void update() {
        volume += clamp(targetVolume - volume, -VOLUME_SLEW, VOLUME_SLEW);
        pitch += clamp(targetPitch - pitch, -PITCH_SLEW, PITCH_SLEW);
        if (volume <= 0.001F) {
            volume = 0.0F;
            silentTicks++;
            if (released || silentTicks >= SILENT_TICKS_BEFORE_RELEASE) {
                donePlaying = true;
            }
        }
        else {
            silentTicks = 0;
        }
    }

    private void moveTo(double x, double y, double z) {
        this.xPosF = (float) x;
        this.yPosF = (float) y;
        this.zPosF = (float) z;
    }

    private static float clamp(float value, float low, float high) {
        return value < low ? low : Math.min(high, value);
    }
}
