package com.micatechnologies.minecraft.rcmc.physics;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;

/**
 * The ride hardware a client predicts trains with: what the server last reported, held until the
 * next correction.
 *
 * <p>Only the server runs ride elements, block signals and train drivers. Predicting with none of
 * them, a client lets a train coast up a lift hill it is really being hauled up, or roll on through
 * a brake run, and every correction then pulls the train back to where it really is. That shows as
 * the train, and the rider's view with it, juddering four times a second through exactly the parts
 * of a ride where the hardware is working hardest.</p>
 *
 * <p>Holding the last value is a good guess for a lift, a launch or a cruising metro, whose
 * acceleration changes slowly. It is a bad one for a brake bringing a train to a stop: the server
 * re-aims that every tick to land on zero, and held constant it would carry the train through
 * zero and backwards. So a value that opposes the motion is clamped to stop the train at rest, as
 * the server's own servos do, and never to reverse it.</p>
 *
 * <p>Pure Java, like the rest of {@code physics}: it reads only the train and the track.</p>
 */
public final class PredictedControl implements TrainManager.ExternalAcceleration {

    /**
     * Below this speed a train the server reports as held is pinned where it is. A held train can
     * still be braking in, and stopping that dead on the client would be a jump of its own; this is
     * the speed the metro stop controller itself treats as holding.
     */
    private static final double HELD_SPEED = 0.5D;

    private final TrackNetwork network;
    private final double tickSeconds;

    public PredictedControl(TrackNetwork network, double tickSeconds) {
        this.network = network;
        this.tickSeconds = tickSeconds;
    }

    @Override
    public double forTrain(int trainId, Train train) {
        double acceleration = train.syncedExternalAcceleration();
        double velocity = train.velocity();
        boolean opposing = velocity * acceleration < 0.0D;
        if (!opposing && !train.syncedHeld()) {
            return acceleration;
        }
        // What brings the train to rest in one tick, gravity included. Drag is left out: it too
        // opposes the motion, so it can only stop the train sooner, never push it through zero.
        double toRest = -velocity / tickSeconds - train.averageGravityAlongTrack(network);
        if (Math.abs(velocity) <= HELD_SPEED && train.syncedHeld()) {
            // A held train is one the server is keeping still, at a platform or a block boundary,
            // on whatever grade is there.
            return toRest;
        }
        if (!opposing) {
            // Held but still coming in: the server's brake is doing the stopping.
            return acceleration;
        }
        return velocity > 0.0D ? Math.max(acceleration, toRest) : Math.min(acceleration, toRest);
    }

    @Override
    public boolean isHolding(int trainId, Train train) {
        return train.syncedHeld();
    }
}
