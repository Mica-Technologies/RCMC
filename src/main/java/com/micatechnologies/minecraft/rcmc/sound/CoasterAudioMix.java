package com.micatechnologies.minecraft.rcmc.sound;

/**
 * How loud, and at what pitch, each of a coaster train's sounds should be right now.
 *
 * <p>Pure arithmetic over the train's speed, its acceleration, the ride hardware under it and
 * whether the listener is riding it — no Minecraft types — so the whole mix is unit-tested on a bare
 * JVM, and the client code that plays it only has to apply the numbers. The loops it drives are
 * synthesised by {@code tools/audio/synth_coaster_sounds.py}; each is voiced at a representative
 * speed, and the pitch here is relative to that voicing ({@code 1.0} = as recorded).</p>
 *
 * <p>Minecraft clamps pitch to [0.5, 2.0] and a positional sound's gain to [0, 1]; every level
 * returned is inside those ranges, so what the tests assert is what is heard.</p>
 */
public final class CoasterAudioMix {

    /** The looping channels a coaster train carries. The launch is a one-shot, not a channel. */
    public enum Channel {
        /** Wheels on rail: always, scaled by speed. */
        ROLL,
        /** The chain and its anti-rollback dogs: only on a lift, only while climbing. */
        CHAIN,
        /** Air rushing past: riders only, and only once the train is really moving. */
        WIND,
        /** Brakes biting: only on a brake run, only while they are actually slowing the train. */
        BRAKE,
        /** Drive tyres pushing: only on a tyre span. */
        TYRES
    }

    /** One channel's level: gain in [0, 1] and pitch in [0.5, 2.0]. */
    public static final class Level {
        public static final Level SILENT = new Level(0.0F, 1.0F);

        public final float volume;
        public final float pitch;

        Level(float volume, float pitch) {
            this.volume = clamp(volume, 0.0F, 1.0F);
            this.pitch = clamp(pitch, MIN_PITCH, MAX_PITCH);
        }

        public boolean isSilent() {
            return volume <= 0.0F;
        }

        @Override
        public String toString() {
            return String.format("Level{volume=%.2f, pitch=%.2f}", volume, pitch);
        }
    }

    /** Speed at which the rolling loop plays at its recorded pitch, blocks/s. */
    static final double ROLL_VOICED_SPEED = 15.0D;
    /** Lift speed the chain loop was voiced for (four dogs a second), blocks/s. */
    static final double CHAIN_VOICED_SPEED = 5.0D;
    /** Speed the wind loop was voiced for, blocks/s. */
    static final double WIND_VOICED_SPEED = 20.0D;
    /** Speed the tyre loop was voiced for, blocks/s. */
    static final double TYRES_VOICED_SPEED = 6.0D;

    /** Below this the train is standing, and nothing rolls, clanks or whirs. */
    static final double MOVING = 0.3D;
    /** Wind starts to be heard above this. A lift's crawl is quiet. */
    static final double WIND_ONSET = 5.0D;
    /** Deceleration at which the brakes are at full voice, blocks/s². */
    static final double BRAKE_FULL = 5.0D;
    /** Acceleration that marks a launch as having fired, blocks/s². */
    static final double LAUNCH_ACCELERATION = 2.0D;

    private static final float MIN_PITCH = 0.5F;
    private static final float MAX_PITCH = 2.0F;

    /** Span type names, as {@code ElementCodec.typeOf} writes them and the client receives them. */
    public static final String LIFT = "chain_lift";
    public static final String LAUNCH = "launch";
    public static final String BRAKES = "brake";
    public static final String TYRE_SPAN = "drive_tyres";
    public static final String STATION = "station";

    private CoasterAudioMix() {
    }

    /**
     * @param channel      which loop
     * @param speed        the train's speed, blocks/s; sign ignored
     * @param acceleration change in speed, blocks/s² — positive gaining speed, negative losing it
     * @param spanType     the ride hardware under the train, or {@code null} for plain track
     * @param riding       whether the listener is riding this train
     */
    public static Level level(Channel channel, double speed, double acceleration, String spanType,
                              boolean riding) {
        double v = Math.abs(speed);
        switch (channel) {
            case ROLL:
                return roll(v, riding);
            case CHAIN:
                return LIFT.equals(spanType) && v > MOVING
                    ? new Level(riding ? 0.85F : 0.9F, (float) (v / CHAIN_VOICED_SPEED))
                    : Level.SILENT;
            case WIND:
                if (!riding || v <= WIND_ONSET) {
                    return Level.SILENT;
                }
                return new Level((float) Math.min(1.0D, (v - WIND_ONSET) / 20.0D),
                    (float) (0.75D + 0.35D * v / WIND_VOICED_SPEED));
            case BRAKE:
                // Only while the fins are genuinely taking speed off. A train rolling through an
                // open (trim) brake at its target speed makes no brake noise, and neither does one
                // standing in a block brake.
                // A station's brakes too, stopping an arriving train: they are brakes all the same.
                if (!(BRAKES.equals(spanType) || STATION.equals(spanType)) || v <= MOVING
                    || acceleration >= -0.5D) {
                    return Level.SILENT;
                }
                return new Level((float) Math.min(1.0D, -acceleration / BRAKE_FULL),
                    (float) (0.85D + 0.25D * Math.min(1.0D, v / ROLL_VOICED_SPEED)));
            case TYRES:
                return TYRE_SPAN.equals(spanType) && v > MOVING
                    ? new Level(0.8F, (float) (v / TYRES_VOICED_SPEED))
                    : Level.SILENT;
            default:
                return Level.SILENT;
        }
    }

    /**
     * Wheels on rail. Silent at rest, rising steeply at first (the first few blocks/s are what make
     * a train sound like it is moving) and levelling off, so a fast train is loud but not deafening.
     * Riders hear it a little quieter: the car and the wind sit between them and the wheels.
     */
    private static Level roll(double v, boolean riding) {
        if (v <= MOVING) {
            return Level.SILENT;
        }
        double loudness = Math.pow(Math.min(1.0D, v / ROLL_VOICED_SPEED), 0.6D);
        float volume = (float) (loudness * (riding ? 0.75D : 1.0D));
        float pitch = (float) (0.6D + 0.4D * v / ROLL_VOICED_SPEED);
        return new Level(volume, pitch);
    }

    /**
     * Whether the launch sound should fire this tick: the train is on a launch span, it was not on
     * one last tick or had not yet started accelerating, and it is now being driven hard. Firing on
     * the edge rather than every tick is what makes it a single surge rather than a stutter.
     */
    public static boolean launchFires(String previousSpanType, double previousAcceleration,
                                      String spanType, double acceleration) {
        if (!LAUNCH.equals(spanType) || acceleration < LAUNCH_ACCELERATION) {
            return false;
        }
        return !LAUNCH.equals(previousSpanType) || previousAcceleration < LAUNCH_ACCELERATION;
    }

    /**
     * Whether the dispatch sound should fire this tick: the train is in its station and has just
     * started to move — the platform's brakes letting go. On the edge, so it is heard once.
     */
    public static boolean dispatchFires(double previousSpeed, double speed, String spanType) {
        return STATION.equals(spanType) && Math.abs(previousSpeed) <= MOVING && Math.abs(speed) > MOVING;
    }

    /**
     * How far away a bystander hears the train. Minecraft sets a positional sound's range once, when
     * it starts playing, at 16 blocks times its volume if that volume is above 1 — so a coaster that
     * should carry across a park starts its rolling loop with this as its volume and is then turned
     * down to its real level.
     */
    public static float bystanderRange(double speed) {
        double v = Math.abs(speed);
        return (float) (1.0D + 1.5D * Math.min(1.0D, v / ROLL_VOICED_SPEED));
    }

    private static float clamp(float value, float low, float high) {
        return value < low ? low : Math.min(high, value);
    }
}
