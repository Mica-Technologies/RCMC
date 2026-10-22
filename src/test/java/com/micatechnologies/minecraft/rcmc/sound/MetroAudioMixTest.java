package com.micatechnologies.minecraft.rcmc.sound;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.sound.CoasterAudioMix.Level;
import com.micatechnologies.minecraft.rcmc.sound.MetroAudioMix.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a listener should hear from a moving metro train, stated as behaviour. */
class MetroAudioMixTest {

    private static Level at(Channel channel, double speed, double accel, boolean underground) {
        return MetroAudioMix.level(channel, speed, accel, false, underground);
    }

    @Test
    @DisplayName("A standing train makes no running sound")
    void standingIsSilent() {
        for (Channel c : Channel.values()) {
            assertTrue(at(c, 0.0D, 0.0D, false).isSilent(), c.name());
        }
    }

    @Test
    @DisplayName("A moving train is audible on both channels")
    void movingIsAudible() {
        for (Channel c : Channel.values()) {
            assertTrue(at(c, 8.0D, 0.0D, false).volume > 0.0F, c.name());
        }
    }

    @Test
    @DisplayName("The motor rises in pitch with speed")
    void motorPitchFollowsSpeed() {
        assertTrue(at(Channel.MOTOR, 16.0D, 0.0D, false).pitch > at(Channel.MOTOR, 4.0D, 0.0D, false).pitch);
    }

    @Test
    @DisplayName("The motor is louder accelerating or braking than coasting")
    void motorLouderUnderEffort() {
        float coasting = at(Channel.MOTOR, 8.0D, 0.0D, false).volume;
        assertTrue(at(Channel.MOTOR, 8.0D, 1.0D, false).volume > coasting);
        assertTrue(at(Channel.MOTOR, 8.0D, -1.0D, false).volume > coasting);
    }

    @Test
    @DisplayName("Rolling noise grows with speed")
    void rollingFollowsSpeed() {
        assertTrue(at(Channel.ROLLING, 10.0D, 0.0D, false).volume > at(Channel.ROLLING, 2.0D, 0.0D, false).volume);
    }

    @Test
    @DisplayName("A train underground is louder")
    void undergroundIsLouder() {
        assertTrue(at(Channel.ROLLING, 6.0D, 0.0D, true).volume > at(Channel.ROLLING, 6.0D, 0.0D, false).volume);
    }

    @Test
    @DisplayName("Levels stay in Minecraft's pitch and gain ranges at any speed")
    void staysInRange() {
        for (Channel c : Channel.values()) {
            for (double v = 0.0D; v <= 80.0D; v += 2.5D) {
                Level l = MetroAudioMix.level(c, v, 5.0D, true, true);
                assertTrue(l.volume >= 0.0F && l.volume <= 1.0F);
                assertTrue(l.pitch >= 0.5F && l.pitch <= 2.0F);
            }
        }
    }
}
