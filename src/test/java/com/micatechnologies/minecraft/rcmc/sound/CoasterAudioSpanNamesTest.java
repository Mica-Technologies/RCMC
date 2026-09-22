package com.micatechnologies.minecraft.rcmc.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.micatechnologies.minecraft.rcmc.physics.element.BrakeRun;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.DriveTyres;
import com.micatechnologies.minecraft.rcmc.physics.element.LaunchTrack;
import com.micatechnologies.minecraft.rcmc.track.storage.ElementCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The client learns what hardware is under a train from span type names written by
 * {@link ElementCodec#typeOf}; the audio mix listens for its own copies of those names. Two lists of
 * strings drift, and the symptom would be silence — a chain lift that never clanks — with nothing
 * failing anywhere. This ties them together.
 */
class CoasterAudioSpanNamesTest {

    private static final double TICK = 1.0D / 20.0D;

    @Test
    @DisplayName("the mix listens for exactly the span names the client is sent")
    void spanNamesMatchTheCodec() {
        assertEquals(CoasterAudioMix.LIFT,
            ElementCodec.typeOf(new ChainLift(1, 0.0D, 50.0D, 5.0D, 12.0D, TICK)));
        assertEquals(CoasterAudioMix.LAUNCH,
            ElementCodec.typeOf(new LaunchTrack(1, 0.0D, 50.0D, 22.0D, 8.0D)));
        assertEquals(CoasterAudioMix.BRAKES,
            ElementCodec.typeOf(new BrakeRun(1, 0.0D, 50.0D, 6.0D, 6.0D, BrakeRun.Mode.TRIM, TICK)));
        assertEquals(CoasterAudioMix.TYRE_SPAN,
            ElementCodec.typeOf(new DriveTyres(1, 0.0D, 50.0D, 6.0D, 2.0D, TICK)));
    }
}
