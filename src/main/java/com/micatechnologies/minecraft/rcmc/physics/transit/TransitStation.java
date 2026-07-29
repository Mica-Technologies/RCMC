package com.micatechnologies.minecraft.rcmc.physics.transit;

import com.micatechnologies.minecraft.rcmc.track.TrackRef;

/**
 * One named stop on a {@link TransitLine}: where trains berth, and what the signage calls it.
 *
 * <p>The stop point is a single {@link TrackRef} — the position the lead car's reference should
 * come to rest at, exactly the convention {@code StationPlatform} uses for coasters. Platform
 * geometry (span, decking, edging) belongs to the world-facing blocks; what lives here is only what
 * the route logic, the signage and the doors need.</p>
 *
 * <p><b>Which side the doors open</b> is one of those: it is a property of the station rather than
 * of the train, it has to survive a save, and it has to reach a client to be announced and
 * animated. Stored against the track's own axis — see {@link DoorSide} for why that is the only
 * frame in which the question has a stable answer.</p>
 *
 * <p>Immutable, pure Java.</p>
 */
public final class TransitStation {

    private final String name;
    private final TrackRef stopPoint;
    private final DoorSide doorSide;

    public TransitStation(String name, TrackRef stopPoint) {
        this(name, stopPoint, DoorSide.BOTH);
    }

    public TransitStation(String name, TrackRef stopPoint, DoorSide doorSide) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("a station needs a name — the signage renders it");
        }
        if (stopPoint == null) {
            throw new IllegalArgumentException("stopPoint is required");
        }
        this.name = name;
        this.stopPoint = stopPoint;
        // Defaulted rather than rejected: every station that existed before doors had sides opens
        // both, and a station read from an older save arrives here with nothing to say about it.
        this.doorSide = doorSide == null ? DoorSide.BOTH : doorSide;
    }

    public String name() {
        return name;
    }

    public TrackRef stopPoint() {
        return stopPoint;
    }

    /**
     * Which side of the <em>track</em> this station's platform is on. Never null.
     *
     * <p>Convert with {@link DoorSide#asSeenFrom} before showing it to anybody: a train serving
     * this platform in the other direction has it on the other hand.</p>
     */
    public DoorSide doorSide() {
        return doorSide;
    }

    /** A copy opening on {@code side}. Immutable, so authoring a side makes a new station. */
    public TransitStation withDoorSide(DoorSide side) {
        return new TransitStation(name, stopPoint, side);
    }

    @Override
    public String toString() {
        return "TransitStation{" + name + " @ " + stopPoint + ", doors " + doorSide + '}';
    }
}
