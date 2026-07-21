package com.micatechnologies.minecraft.rcmc.sound;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;

/**
 * The mod's sound events.
 *
 * <p><b>Every one of these is synthesised from scratch</b> by {@code tools/audio/synth_metro_sounds.py},
 * which generates the waveforms mathematically and encodes them to Ogg Vorbis. No third-party
 * recording is sampled, filtered, or otherwise present — see the asset rule in {@code CLAUDE.md}.
 * Reference recordings supplied by the project owner were measured for <em>facts</em> only (a
 * 612 Hz chime fundamental with odd harmonics, two dings 0.72 s apart, door and air-release
 * envelopes), and those numbers are reproduced by the generator. Keep it that way: the script is
 * checked in precisely so the provenance of every sound file is inspectable.</p>
 *
 * <p>Sounds must be <b>mono</b> to be positional in 1.12.2 — a stereo Ogg plays flat at constant
 * volume wherever the player stands, which for a train is exactly wrong.</p>
 */
public final class RcmcSounds {

    private static final List<SoundEvent> ALL = new ArrayList<>();

    /** Two-tone chime warning that the doors are about to close. */
    public static final SoundEvent METRO_DOOR_CHIME = create("metro_door_chime");

    /** Door mechanism running, ending in the latch as the leaves meet. */
    public static final SoundEvent METRO_DOOR_CLOSE = create("metro_door_close");

    /** Air dumping from the brake pipe as a train releases and pulls away. */
    public static final SoundEvent METRO_BRAKE_RELEASE = create("metro_brake_release");

    private RcmcSounds() {
        throw new AssertionError("No instances.");
    }

    private static SoundEvent create(String name) {
        ResourceLocation id = new ResourceLocation(RcmcConstants.MOD_NAMESPACE, name);
        // The registry name and the event's own id must match, or the client resolves the entry in
        // sounds.json and then fails to find the event it belongs to.
        SoundEvent event = new SoundEvent(id).setRegistryName(id);
        ALL.add(event);
        return event;
    }

    /** Everything to hand to {@code RegistryEvent.Register<SoundEvent>}. */
    public static List<SoundEvent> all() {
        return ALL;
    }
}
