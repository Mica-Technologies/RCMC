package com.micatechnologies.minecraft.rcmc.physics.transit;

import com.micatechnologies.minecraft.rcmc.track.TrackRef;

/**
 * One track's berth at a {@link TransitStation}: where a train stops, and which side it opens.
 *
 * <p><b>Why a station is not just a stop point.</b> An island platform sits between two running
 * lines and serves both — one named place, two tracks. Modelled as a single stop point, only one of
 * those tracks can have a station at all: the other has nothing for a service to target, nothing
 * for a door side to be authored against, and nothing for an arrival board to resolve, which is
 * exactly why such a board could only ever fill one of its two direction rows.</p>
 *
 * <p><b>The door side lives here rather than on the station</b>, because with more than one track
 * it is meaningless at station level: each line sees the same island from its own hand. Still
 * stored against the track's own axis — see {@link DoorSide} for why that is the only frame in
 * which the question has a stable answer.</p>
 *
 * <p>The label is what signage calls this berth — {@code Inbound}, {@code Outbound}, {@code 1},
 * {@code 2}. Empty is allowed and normal for a station with a single platform, where there is
 * nothing to disambiguate.</p>
 *
 * <p>Immutable, pure Java.</p>
 */
public final class TransitPlatform {

    private final TrackRef stopPoint;
    private final DoorSide doorSide;
    private final String label;

    public TransitPlatform(TrackRef stopPoint) {
        this(stopPoint, DoorSide.BOTH, "");
    }

    public TransitPlatform(TrackRef stopPoint, DoorSide doorSide, String label) {
        if (stopPoint == null) {
            throw new IllegalArgumentException("stopPoint is required");
        }
        this.stopPoint = stopPoint;
        // Defaulted rather than rejected, exactly as the station's side always was: a platform read
        // from a save written before sides existed has nothing to say about it, and a door that
        // opens onto nothing is a cosmetic oddity where one that refuses to open strands a rider.
        this.doorSide = doorSide == null ? DoorSide.BOTH : doorSide;
        this.label = label == null ? "" : label;
    }

    /** Where the lead car's reference comes to rest. */
    public TrackRef stopPoint() {
        return stopPoint;
    }

    /** Which side of the <em>track</em> this berth's platform is on. Never null. */
    public DoorSide doorSide() {
        return doorSide;
    }

    /** What signage calls this berth, or empty when there is nothing to disambiguate. */
    public String label() {
        return label;
    }

    public boolean hasLabel() {
        return !label.isEmpty();
    }

    public TransitPlatform withDoorSide(DoorSide side) {
        return new TransitPlatform(stopPoint, side, label);
    }

    public TransitPlatform withLabel(String newLabel) {
        return new TransitPlatform(stopPoint, doorSide, newLabel);
    }

    @Override
    public String toString() {
        return "TransitPlatform{" + (hasLabel() ? label + " " : "") + stopPoint
            + ", doors " + doorSide + '}';
    }
}
