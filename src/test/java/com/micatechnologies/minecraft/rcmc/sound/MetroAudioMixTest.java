package com.micatechnologies.minecraft.rcmc.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    @DisplayName("The motor levels off instead of whining higher and higher with speed")
    void motorPitchLevelsOff() {
        float lineSpeed = at(Channel.MOTOR, 18.0D, 0.0D, false).pitch;
        float flatOut = at(Channel.MOTOR, 40.0D, 0.0D, false).pitch;
        assertTrue(lineSpeed <= 1.25F, "at line speed the motor is near its voiced pitch, got " + lineSpeed);
        assertTrue(flatOut <= 1.25F, "and never far above it, got " + flatOut);
        assertTrue(at(Channel.ROLLING, 40.0D, 0.0D, false).pitch <= 1.2F);
    }

    @Test
    @DisplayName("Joint clacks are silent at a crawl and grow with speed")
    void jointClicks() {
        assertTrue(MetroAudioMix.jointClick(0.5D, false, false).isSilent());
        float slow = MetroAudioMix.jointClick(4.0D, false, false).volume;
        float fast = MetroAudioMix.jointClick(16.0D, false, false).volume;
        assertTrue(slow > 0.0F && fast > slow);
        for (double v = 0.0D; v <= 80.0D; v += 2.5D) {
            Level l = MetroAudioMix.jointClick(v, false, true);
            assertTrue(l.volume >= 0.0F && l.volume <= 1.0F);
            assertTrue(l.pitch >= 0.5F && l.pitch <= 2.0F);
        }
    }

    @Test
    @DisplayName("Axles come in pairs: two close together per bogie, two bogies per car")
    void axleLayout() {
        // One car centred 7 blocks behind the lead, bogies 14 apart.
        double[] axles = MetroAudioMix.axleOffsets(new double[] {7.0D}, 14.0D);
        assertEquals(4, axles.length);
        assertEquals(1.8D, axles[1] - axles[0], 1e-9, "a bogie's two axles");
        assertEquals(1.8D, axles[3] - axles[2], 1e-9);
        assertEquals(14.0D - 1.8D, axles[2] - axles[1], 1e-9, "the gap to the next bogie");
        assertEquals(7.0D, (axles[0] + axles[3]) / 2.0D, 1e-9, "centred on the car");
    }

    @Test
    @DisplayName("A wheel crosses a joint whichever way it runs, and welded rail has none")
    void joints() {
        assertTrue(MetroAudioMix.crossesJoint(17.5D, 18.2D, 18.0D));
        assertTrue(MetroAudioMix.crossesJoint(18.2D, 17.5D, 18.0D), "running the other way");
        assertFalse(MetroAudioMix.crossesJoint(18.2D, 19.0D, 18.0D));
        assertTrue(MetroAudioMix.crossesJoint(-0.3D, 0.2D, 18.0D), "the joint at 0");
        assertFalse(MetroAudioMix.crossesJoint(17.5D, 18.2D, 0.0D), "welded rail");
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
