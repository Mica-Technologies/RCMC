package com.micatechnologies.minecraft.rcmc.physics.element;

import com.micatechnologies.minecraft.rcmc.track.TrackRef;

/**
 * Common bookkeeping shared by every {@link RideElement}: which section, what span of it, and the
 * "is this ref inside me" test.
 *
 * <p>Pulled out because every element needs exactly the same answer to that question, and getting
 * the boundary condition (inclusive at both ends, exact section match, null-safe) subtly wrong in
 * five different places is a bug waiting to happen. Package-private: it is an implementation
 * detail of this package, not part of the published shape — callers program against
 * {@link RideElement}.</p>
 */
abstract class RideElementSpan implements RideElement {

    final int sectionId;
    final double startDistance;
    final double endDistance;

    RideElementSpan(int sectionId, double startDistance, double endDistance) {
        if (endDistance < startDistance) {
            throw new IllegalArgumentException(
                "endDistance (" + endDistance + ") must be >= startDistance (" + startDistance + ")");
        }
        this.sectionId = sectionId;
        this.startDistance = startDistance;
        this.endDistance = endDistance;
    }

    @Override
    public final int sectionId() {
        return sectionId;
    }

    @Override
    public final double startDistance() {
        return startDistance;
    }

    @Override
    public final double endDistance() {
        return endDistance;
    }

    @Override
    public final boolean contains(TrackRef ref) {
        return ref != null && ref.sectionId() == sectionId
            && ref.distance() >= startDistance && ref.distance() <= endDistance;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{section=" + sectionId
            + ", [" + startDistance + ", " + endDistance + "]}";
    }
}
