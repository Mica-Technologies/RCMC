package com.micatechnologies.minecraft.rcmc.track.element;

/**
 * Which way a {@link Corkscrew} rolls. Named by sign rather than "clockwise"/"counterclockwise" to avoid
 * committing to a viewing convention (from behind the car? from in front, looking back at it?) that is
 * easy to get backwards — {@link #POSITIVE} simply means bank sweeps upward from
 * {@code entryBankDegrees} toward {@code entryBankDegrees + 360}, using exactly the sign convention
 * {@code TrackNode#bankDegrees} already documents (positive banks the frame's {@code up} toward
 * {@code right}).
 */
public enum RollDirection {
    POSITIVE,
    NEGATIVE
}
