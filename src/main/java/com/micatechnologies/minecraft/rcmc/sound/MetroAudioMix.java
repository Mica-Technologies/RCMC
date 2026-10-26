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
    /** Highest the motor loop is pitched, however fast the train. */
    static final double MAX_MOTOR_PITCH = 1.25D;
    /** Highest the rolling loop is pitched. */
    static final double MAX_ROLLING_PITCH = 1.2D;
    /** Below this speed wheels cross a joint too gently to be heard, blocks/s. */
    static final double JOINT_AUDIBLE_SPEED = 1.5D;

    /**
     * The clack of one wheel crossing a rail joint: louder and a touch sharper the faster it is
     * taken, silent at a crawl.
     */
    public static CoasterAudioMix.Level jointClick(double speed, boolean onBoard, boolean underground) {
        double v = Math.abs(speed);
        if (v < JOINT_AUDIBLE_SPEED) {
            return CoasterAudioMix.Level.SILENT;
        }
        double ratio = v / VOICED_SPEED;
        float volume = (float) Math.min(1.0D, 0.25D + 0.6D * ratio);
        float pitch = (float) Math.min(1.15D, 0.85D + 0.15D * ratio);
        if (onBoard) {
            volume *= ON_BOARD_FACTOR;
        }
        if (underground) {
            volume = Math.min(1.0F, volume * UNDERGROUND_BOOST);
        }
        return new CoasterAudioMix.Level(volume, pitch);
    }

    /** Half the distance between a bogie's two axles, blocks. */
    static final double AXLE_HALF_SPACING = 0.9D;

    /**
     * Every axle of a metro train, as a distance behind its lead reference, front to back: two per
     * bogie, two bogies per car. {@code carOffsets} are each car's centre behind the lead and
     * {@code bogieSpacing} the distance between a car's bogie centres, as {@code TrainSpec} gives
     * them.
     */
    public static double[] axleOffsets(double[] carOffsets, double bogieSpacing) {
        double[] axles = new double[carOffsets.length * 4];
        double half = bogieSpacing / 2.0D;
        int i = 0;
        for (double centre : carOffsets) {
            axles[i++] = centre - half - AXLE_HALF_SPACING;
            axles[i++] = centre - half + AXLE_HALF_SPACING;
            axles[i++] = centre + half - AXLE_HALF_SPACING;
            axles[i++] = centre + half + AXLE_HALF_SPACING;
        }
        return axles;
    }

    /**
     * Whether a wheel that moved from {@code before} to {@code after} along the track crossed a
     * rail joint, with joints every {@code spacing} blocks from distance 0. Either way along the
     * track; a spacing of 0 or less is welded rail, which has none.
     */
    public static boolean crossesJoint(double before, double after, double spacing) {
        if (spacing <= 0.0D || before == after) {
            return false;
        }
        return Math.floor(before / spacing) != Math.floor(after / spacing);
    }

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
                // Rises with speed but levels off: a straight line put a train at line speed half
                // an octave above how it was voiced, and a fast one higher still — the whine people
                // heard. A real drive's note climbs and then settles.
                pitch = (float) Math.min(MAX_MOTOR_PITCH, 0.6D + 0.4D * ratio);
                break;
            }
            case ROLLING:
            default:
                volume = (float) Math.min(1.0D, 0.15D + 0.85D * ratio);
                pitch = (float) Math.min(MAX_ROLLING_PITCH, 0.8D + 0.2D * ratio);
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
