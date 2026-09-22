package com.micatechnologies.minecraft.rcmc.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.sound.CoasterAudioMix.Channel;
import com.micatechnologies.minecraft.rcmc.sound.CoasterAudioMix.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a listener should hear from a coaster train, stated as behaviour: a standing train is silent,
 * a faster one is louder and higher, the chain clanks only on a lift, the brakes only while braking.
 */
class CoasterAudioMixTest {

    private static Level at(Channel channel, double speed, double accel, String span, boolean riding) {
        return CoasterAudioMix.level(channel, speed, accel, span, riding);
    }

    @Test
    @DisplayName("a standing train makes no sound on any channel")
    void standingTrainIsSilent() {
        for (Channel channel : Channel.values()) {
            for (String span : new String[] {null, "chain_lift", "brake", "drive_tyres", "launch"}) {
                assertTrue(at(channel, 0.0D, 0.0D, span, true).isSilent(),
                    channel + " on " + span + " should be silent at rest");
            }
        }
    }

    @Test
    @DisplayName("rolling gets louder and higher as the train speeds up")
    void rollRisesWithSpeed() {
        Level slow = at(Channel.ROLL, 3.0D, 0.0D, null, false);
        Level fast = at(Channel.ROLL, 25.0D, 0.0D, null, false);
        assertTrue(fast.volume > slow.volume, slow + " vs " + fast);
        assertTrue(fast.pitch > slow.pitch, slow + " vs " + fast);
        assertEquals(1.0F, at(Channel.ROLL, 15.0D, 0.0D, null, false).pitch, 1e-6F,
            "the loop plays as recorded at the speed it was voiced for");
    }

    @Test
    @DisplayName("travelling backwards sounds the same as forwards")
    void directionDoesNotMatter() {
        assertEquals(at(Channel.ROLL, 12.0D, 0.0D, null, false).volume,
            at(Channel.ROLL, -12.0D, 0.0D, null, false).volume, 1e-6F);
    }

    @Test
    @DisplayName("the chain only clanks on a lift, in step with the lift's speed")
    void chainOnlyOnALift() {
        assertTrue(at(Channel.CHAIN, 5.0D, 0.0D, null, false).isSilent());
        assertTrue(at(Channel.CHAIN, 5.0D, 0.0D, "brake", false).isSilent());
        Level lift = at(Channel.CHAIN, 5.0D, 0.0D, "chain_lift", false);
        assertFalse(lift.isSilent());
        assertEquals(1.0F, lift.pitch, 1e-6F, "four clacks a second at the voiced lift speed");
        assertTrue(at(Channel.CHAIN, 8.0D, 0.0D, "chain_lift", false).pitch > lift.pitch,
            "a faster chain clacks faster");
    }

    @Test
    @DisplayName("only riders hear the wind, and only once the train is really moving")
    void windIsForRiders() {
        assertTrue(at(Channel.WIND, 25.0D, 0.0D, null, false).isSilent(), "not for bystanders");
        assertTrue(at(Channel.WIND, 4.0D, 0.0D, "chain_lift", true).isSilent(),
            "a lift's crawl is not windy");
        assertTrue(at(Channel.WIND, 30.0D, 0.0D, null, true).volume
            > at(Channel.WIND, 10.0D, 0.0D, null, true).volume);
    }

    @Test
    @DisplayName("brakes are heard only while they are slowing the train")
    void brakesOnlyWhileBraking() {
        assertTrue(at(Channel.BRAKE, 12.0D, -4.0D, null, false).isSilent(), "not off a brake run");
        assertTrue(at(Channel.BRAKE, 6.0D, 0.0D, "brake", false).isSilent(),
            "rolling through an open trim brake at its target speed");
        assertTrue(at(Channel.BRAKE, 0.0D, -4.0D, "brake", false).isSilent(), "stopped");
        assertTrue(at(Channel.BRAKE, 12.0D, -6.0D, "brake", false).volume
            > at(Channel.BRAKE, 12.0D, -1.0D, "brake", false).volume, "harder braking is louder");
    }

    @Test
    @DisplayName("drive tyres whir only on a tyre span")
    void tyresOnlyOnTyres() {
        assertTrue(at(Channel.TYRES, 6.0D, 0.0D, null, false).isSilent());
        assertEquals(1.0F, at(Channel.TYRES, 6.0D, 0.0D, "drive_tyres", false).pitch, 1e-6F);
    }

    @Test
    @DisplayName("levels stay inside what Minecraft will actually play")
    void levelsAreClamped() {
        for (Channel channel : Channel.values()) {
            for (double speed : new double[] {0.5D, 5.0D, 40.0D, 120.0D}) {
                Level level = at(channel, speed, -50.0D, "brake", true);
                assertTrue(level.volume >= 0.0F && level.volume <= 1.0F, channel + " " + level);
                assertTrue(level.pitch >= 0.5F && level.pitch <= 2.0F, channel + " " + level);
            }
        }
    }

    @Test
    @DisplayName("a launch fires once, on the tick the motors start driving")
    void launchFiresOnTheEdge() {
        assertTrue(CoasterAudioMix.launchFires(null, 0.0D, "launch", 8.0D), "entering a launch");
        assertTrue(CoasterAudioMix.launchFires("launch", 0.0D, "launch", 8.0D),
            "a train held on the launch until the motors fire");
        assertFalse(CoasterAudioMix.launchFires("launch", 8.0D, "launch", 8.0D),
            "still launching: no second surge");
        assertFalse(CoasterAudioMix.launchFires(null, 0.0D, "launch", 0.5D),
            "rolling over an idle launch");
        assertFalse(CoasterAudioMix.launchFires("launch", 8.0D, null, 8.0D), "off the launch");
    }

    @Test
    @DisplayName("a fast train carries further than a slow one")
    void rangeGrowsWithSpeed() {
        assertTrue(CoasterAudioMix.bystanderRange(25.0D) > CoasterAudioMix.bystanderRange(2.0D));
        assertTrue(CoasterAudioMix.bystanderRange(0.0D) >= 1.0F, "never under the 16-block default");
    }
}
