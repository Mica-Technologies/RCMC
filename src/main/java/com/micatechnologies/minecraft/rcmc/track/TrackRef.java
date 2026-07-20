package com.micatechnologies.minecraft.rcmc.track;

/**
 * A stable address on the track network: which section, and how far along it.
 *
 * <p>Everything downstream — trains, ride elements, block sections, the editor — addresses track
 * through this, never through a raw spline parameter. Spline parameters are an implementation
 * detail that shifts the moment a node is inserted; distance along a section is the quantity the
 * physics actually integrates and the one a player would recognise ("the brake run starts 240
 * blocks in").</p>
 *
 * <p>Immutable.</p>
 */
public final class TrackRef {

    private final int sectionId;
    private final double distance;

    public TrackRef(int sectionId, double distance) {
        this.sectionId = sectionId;
        this.distance = distance;
    }

    public int sectionId() {
        return sectionId;
    }

    /** Distance in blocks from the start of the section. */
    public double distance() {
        return distance;
    }

    public TrackRef withDistance(double newDistance) {
        return new TrackRef(sectionId, newDistance);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TrackRef)) {
            return false;
        }
        TrackRef o = (TrackRef) obj;
        return sectionId == o.sectionId && Double.compare(distance, o.distance) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * sectionId + Double.hashCode(distance);
    }

    @Override
    public String toString() {
        return "TrackRef{section=" + sectionId + ", s=" + String.format("%.3f", distance) + '}';
    }
}
