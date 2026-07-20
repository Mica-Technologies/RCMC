package com.micatechnologies.minecraft.rcmc.physics;

/**
 * The complete dynamic state of a train, in one dimension.
 *
 * <p>A train constrained to a track has exactly one degree of freedom: how far along it is.
 * Everything else — world position, orientation, which way is down relative to the car — is
 * a pure function of that distance and the track geometry. Simulating in 1D rather than
 * simulating 3D bodies and constraining them afterwards is the single most important design
 * decision in the physics layer: it makes the simulation exactly conserve energy, makes it
 * impossible for a car to leave the rails through numerical error, and makes the whole thing
 * cheap enough to run for every train in a park every tick.</p>
 *
 * <p>Immutable: the integrator returns a new state rather than mutating. That keeps
 * rollback (for client prediction reconciliation) and multi-substep integration trivially
 * correct.</p>
 */
public final class TrainState {

    /** Distance of the train's reference point along the track, in blocks. */
    public final double distance;

    /** Speed along the track, in blocks/second. Signed: negative means running backwards. */
    public final double velocity;

    public TrainState(double distance, double velocity) {
        this.distance = distance;
        this.velocity = velocity;
    }

    public TrainState with(double newDistance, double newVelocity) {
        return new TrainState(newDistance, newVelocity);
    }

    @Override
    public String toString() {
        return "TrainState{s=" + distance + ", v=" + velocity + '}';
    }
}
