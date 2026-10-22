package com.micatechnologies.minecraft.rcmc.sound;

/**
 * How loud, and at what pitch, a metro train's running loops should be right now.
 *
 * <p>Pure arithmetic over speed, acceleration, whether the listener is on board and whether the
 * train is underground — no Minecraft types — so it is unit-tested on a bare JVM. The loops are
 * synthesised by {@code tools/audio/synth_metro_running_sounds.py}, voiced at
 * {@link #VOICED_SPEED}; pitch here is relative to that ({@code 1.0} = as recorded).</p>
 */
public final class MetroAudioMix {

    /** The looping channels a metro train carries. */
    public enum Channel {
        /** VVVF traction motor: pitch follows speed, louder while accelerating or braking. */
        MOTOR,
        /** Wheels on rail: scaled by speed. */
        ROLLING
    }

    /** Speed both loops were voiced for, blocks/s. */
    static final double VOICED_SPEED = 12.0D;
    /** Below this speed a train is standing, and silent. */
    static final double STANDING_SPEED = 0.05D;
    /** Acceleration magnitude, blocks/s², at which the motor reaches its full working level. */
    static final double FULL_EFFORT_ACCELERATION = 1.2D;
    /** Volume factor for a train in a tunnel: sound has nowhere to go but back at the listener. */
    static final float UNDERGROUND_BOOST = 1.35F;
    /** Riders hear their own train a little quieter than a platform hears it pass. */
    static final float ON_BOARD_FACTOR = 0.7F;

    private MetroAudioMix() {
        throw new AssertionError("No instances.");
    }

    /**
     * @param speed        absolute speed along the track, blocks/s
     * @param acceleration change of speed, blocks/s² (sign ignored: braking is as loud as powering)
     * @param onBoard      the listener is riding this train
     * @param underground  the train is under cover (little sky light where it is)
     */
    public static CoasterAudioMix.Level level(Channel channel, double speed, double acceleration,
                                              boolean onBoard, boolean underground) {
        double v = Math.abs(speed);
        if (v < STANDING_SPEED) {
            return CoasterAudioMix.Level.SILENT;
        }
        double ratio = v / VOICED_SPEED;
        float volume;
        float pitch;
        switch (channel) {
            case MOTOR: {
                double effort = Math.min(1.0D, Math.abs(acceleration) / FULL_EFFORT_ACCELERATION);
                // Coasting still hums faintly; under power or the rheostatic brake it sings.
                double speedPart = Math.min(1.0D, 0.25D + 0.75D * ratio);
                volume = (float) (speedPart * (0.25D + 0.75D * effort));
                pitch = (float) (0.5D + 0.5D * ratio);
                break;
            }
            case ROLLING:
            default:
                volume = (float) Math.min(1.0D, 0.15D + 0.85D * ratio);
                pitch = (float) (0.7D + 0.3D * ratio);
                break;
        }
        if (onBoard) {
            volume *= ON_BOARD_FACTOR;
        }
        if (underground) {
            volume *= UNDERGROUND_BOOST;
        }
        return new CoasterAudioMix.Level(volume, pitch);
    }

    /** Audible-range factor (16 blocks a unit) for a bystander: a faster train carries further. */
    public static float bystanderRange(double speed) {
        return (float) Math.min(3.0D, 1.5D + Math.abs(speed) / VOICED_SPEED);
    }
}
