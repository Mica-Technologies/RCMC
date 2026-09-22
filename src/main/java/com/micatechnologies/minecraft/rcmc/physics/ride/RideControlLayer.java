package com.micatechnologies.minecraft.rcmc.physics.ride;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.element.VelocityServo;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;

/**
 * The operator's layer over everything else that drives a coaster: an emergency stop overrides the
 * ride hardware and the block signals, brakes every train on the ride to a halt and holds it.
 *
 * <p>Wraps whatever control the world tick already composed (elements, or elements under block
 * signalling). A ride that is not e-stopped passes straight through, so this costs one map lookup
 * per train per tick.</p>
 *
 * <p><b>A stopped train stays stopped, wherever it is.</b> The stop cancels gravity along the track
 * as well as braking — the anti-rollback dogs on a lift, the parking brakes everywhere else. Without
 * that, a train stopped on a lift slid back down it at a fifth of a block a second (found in the
 * tests). Real coasters can only stop where they have brakes; a sandbox where a stopped ride stays
 * exactly where it was is the more useful, and the safer, choice.</p>
 */
public final class RideControlLayer implements TrainManager.ExternalAcceleration {

    /**
     * How hard an emergency stop brakes, blocks/s². Firmer than a service stop — it is an
     * emergency — but well short of anything that would injure a rider: about 0.5 g.
     */
    public static final double EMERGENCY_DECELERATION = 5.0D;

    private final TrainManager.ExternalAcceleration inner;
    private final RideControllers rides;
    private final TrackNetwork network;
    private final double tickSeconds;

    public RideControlLayer(TrainManager.ExternalAcceleration inner, RideControllers rides,
                            TrackNetwork network, double tickSeconds) {
        if (rides == null || network == null) {
            throw new IllegalArgumentException("rides and network are required");
        }
        this.inner = inner;
        this.rides = rides;
        this.network = network;
        this.tickSeconds = tickSeconds;
    }

    @Override
    public double forTrain(int trainId, Train train) {
        if (rides.isEmergencyStopped(train.reference().sectionId())) {
            return VelocityServo.accelerationToHold(train.velocity(), 0.0D, EMERGENCY_DECELERATION,
                tickSeconds) - train.averageGravityAlongTrack(network);
        }
        return inner == null ? 0.0D : inner.forTrain(trainId, train);
    }

    /**
     * An e-stopped train is held, deliberately — so stopping on a level stretch mid-circuit is not
     * read as the train having stalled there.
     */
    @Override
    public boolean isHolding(int trainId, Train train) {
        if (rides.isEmergencyStopped(train.reference().sectionId())) {
            return true;
        }
        return inner != null && inner.isHolding(trainId, train);
    }
}
