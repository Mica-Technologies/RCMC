package com.micatechnologies.minecraft.rcmc.physics.transit;

import com.micatechnologies.minecraft.rcmc.track.TrackRef;

/**
 * One named stop on a {@link TransitLine}: where trains berth, and what the signage calls it.
 *
 * <p>The stop point is a single {@link TrackRef} — the position the lead car's reference should
 * come to rest at, exactly the convention {@code StationPlatform} uses for coasters. Platform
 * geometry (span, doors, which side opens) belongs to the world-facing station block in M6;
 * this is only what the route logic and the signage need.</p>
 *
 * <p>Immutable, pure Java.</p>
 */
public final class TransitStation {

    private final String name;
    private final TrackRef stopPoint;

    public TransitStation(String name, TrackRef stopPoint) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("a station needs a name — the signage renders it");
        }
        if (stopPoint == null) {
            throw new IllegalArgumentException("stopPoint is required");
        }
        this.name = name;
        this.stopPoint = stopPoint;
    }

    public String name() {
        return name;
    }

    public TrackRef stopPoint() {
        return stopPoint;
    }

    @Override
    public String toString() {
        return "TransitStation{" + name + " @ " + stopPoint + '}';
    }
}
