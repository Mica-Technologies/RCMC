package com.micatechnologies.minecraft.rcmc.track.element;

/** Which way a {@link Curve} or {@link Helix} turns, as seen from above looking along the direction of
 * travel (i.e. from the rider's perspective, not a fixed compass sense — it follows the track's own
 * entry orientation, so "right" is always the side {@code TrackFrame.right} points to). */
public enum TurnDirection {
    LEFT,
    RIGHT
}
