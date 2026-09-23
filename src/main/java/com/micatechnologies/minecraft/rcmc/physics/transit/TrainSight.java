package com.micatechnologies.minecraft.rcmc.physics.transit;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackWalk;
import java.util.Map;

/**
 * Line-of-sight driving: a train in service never runs into another train, signals or no signals.
 *
 * <p>Before this, an unsignalled line gave its trains unlimited movement authority, and they drove
 * straight through each other — found in game with two Subway services a couple of blocks apart
 * on the same rail, and both running through a parked train left on the loop. Signals are an
 * operator's tool for keeping trains a block apart at speed; not colliding is not something a
 * builder should have to opt in to.</p>
 *
 * <p>The authority is the distance from this train's leading end to the nearest end of any other
 * train ahead of it along its facing, less {@link #MARGIN}: the driver brakes to stand there, the
 * way it brakes for a red signal. Every train on the network counts, in service or not.</p>
 */
public final class TrainSight {

    /** How far short of the train ahead a train stops, in blocks. */
    public static final double MARGIN = 5.0D;

    /**
     * How far ahead a driver looks. Well past a full service stop from line speed
     * ({@code 22² / (2 · 1.2)} ≈ 200 blocks), so authority never appears closer than the train
     * can stop for.
     */
    public static final double HORIZON = 320.0D;

    private TrainSight() {
    }

    /** What a driver sees ahead: how far it may run, and which train it is stopping for. */
    public static final class View {
        public final double authority;
        /** The train that limits the authority, or {@code -1} when nothing is in sight. */
        public final int ahead;

        View(double authority, int ahead) {
            this.authority = authority;
            this.ahead = ahead;
        }
    }

    /**
     * Movement authority for {@code trainId} moving along {@code facing}: blocks it may run before
     * it must stand, or {@link Double#POSITIVE_INFINITY} when nothing is within sight. Never negative.
     */
    public static double authority(int trainId, Train train, TrainManager trains, TrackNetwork network,
                                   double facing) {
        return look(trainId, train, trains, network, facing).authority;
    }

    /** As {@link #authority}, naming the train in the way. */
    public static View look(int trainId, Train train, TrainManager trains, TrackNetwork network, double facing) {
        if (train == null || train.reference() == null || network == null) {
            return new View(Double.POSITIVE_INFINITY, -1);
        }
        TrainSpec spec = train.spec();
        // The distance is walked from the lead car's reference point; the train reaches past it by
        // half a car forwards, and by its whole length when it runs tail first.
        double ownReach = facing >= 0.0D
            ? spec.carLength() * 0.5D
            : spec.offsetOfCar(spec.carCount() - 1) + spec.carLength() * 0.5D;
        double nearest = Double.POSITIVE_INFINITY;
        int ahead = -1;
        for (Map.Entry<Integer, Train> entry : trains.asMap().entrySet()) {
            if (entry.getKey() == trainId) {
                continue;
            }
            Train other = entry.getValue();
            if (other.reference() == null || network.section(other.reference().sectionId()) == null) {
                continue;
            }
            TrainSpec otherSpec = other.spec();
            TrackRef front = network.advance(other.reference(), otherSpec.carLength() * 0.5D).ref;
            TrackRef back = network.advance(other.reference(),
                -(otherSpec.offsetOfCar(otherSpec.carCount() - 1) + otherSpec.carLength() * 0.5D)).ref;
            double to = Math.min(TrackWalk.distanceTo(network, train.reference(), facing, front, HORIZON),
                TrackWalk.distanceTo(network, train.reference(), facing, back, HORIZON));
            if (to < nearest) {
                nearest = to;
                ahead = entry.getKey();
            }
        }
        if (Double.isInfinite(nearest)) {
            return new View(Double.POSITIVE_INFINITY, -1);
        }
        return new View(Math.max(0.0D, nearest - ownReach - MARGIN), ahead);
    }

    /**
     * Whether two trains are running at each other on the same rail — which no amount of waiting
     * resolves: each stops short of the other, for good.
     *
     * <p>Walks from {@code one}, along its facing, to wherever {@code two} stands, and compares the
     * direction it arrives in with the way {@code two} is facing. The same way is a train being
     * followed — round a circuit, that includes one that is half a lap off, or on the far side of a
     * turnback loop. The opposite way is head-on.</p>
     */
    public static boolean headOn(TrackNetwork network, Train one, double oneFacing, Train two, double twoFacing) {
        if (one.reference() == null || two.reference() == null) {
            return false;
        }
        double there = TrackWalk.distanceTo(network, one.reference(), oneFacing, two.reference(), 10_000.0D);
        if (Double.isInfinite(there)) {
            return false;
        }
        TrackNetwork.Traversal arrival = network.advance(one.reference(), (oneFacing >= 0.0D ? 1.0D : -1.0D) * there);
        double arriving = (oneFacing >= 0.0D ? 1.0D : -1.0D) * (arrival.reversed ? -1.0D : 1.0D);
        return arriving * twoFacing < 0.0D;
    }
}
