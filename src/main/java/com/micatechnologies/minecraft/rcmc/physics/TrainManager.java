package com.micatechnologies.minecraft.rcmc.physics;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every train in one world, and the per-tick loop that advances them.
 *
 * <p>Exists on <b>both</b> sides. The server's copy is authoritative; the client's is a mirror
 * that runs the identical simulation and is periodically corrected. That symmetry is the whole
 * netcode design — see {@link #tick} — and it is only possible because nothing in this package
 * touches Minecraft, so the same code genuinely runs in both places rather than two
 * implementations that are supposed to agree.</p>
 *
 * <p>Mutable and single-threaded. The server touches it from the tick thread; the client from the
 * client tick thread. They are different instances.</p>
 */
public final class TrainManager {

    private final Map<Integer, Train> trains = new LinkedHashMap<>();
    private int nextTrainId = 1;

    public int allocateTrainId() {
        while (trains.containsKey(nextTrainId)) {
            nextTrainId++;
        }
        return nextTrainId++;
    }

    public void add(int trainId, Train train) {
        trains.put(trainId, train);
        if (trainId >= nextTrainId) {
            nextTrainId = trainId + 1;
        }
    }

    public Train remove(int trainId) {
        return trains.remove(trainId);
    }

    public Train train(int trainId) {
        return trains.get(trainId);
    }

    public Collection<Train> trains() {
        return Collections.unmodifiableCollection(trains.values());
    }

    public Map<Integer, Train> asMap() {
        return Collections.unmodifiableMap(trains);
    }

    public int count() {
        return trains.size();
    }

    public boolean isEmpty() {
        return trains.isEmpty();
    }

    public void clear() {
        trains.clear();
        nextTrainId = 1;
    }

    /**
     * Advances every train by one tick.
     *
     * <p>{@code externalAcceleration} is supplied per-train by the caller, because the value comes
     * from whichever ride element the train is currently sitting on — a lift, a launch, a brake
     * run — and this package deliberately knows nothing about ride elements. Phase 7 will pass a
     * lookup here; until then callers pass a constant.</p>
     *
     * <p>Trains that have faulted ({@link Train.Status#DEAD_END}, {@link Train.Status#VALLEYED})
     * are skipped rather than removed: an operator needs to see the stuck train to deal with it,
     * and deleting it would turn a visible fault into a mystery.</p>
     */
    public void tick(TrackNetwork network, ExternalAcceleration external, int subSteps, double tickSeconds) {
        for (Map.Entry<Integer, Train> entry : trains.entrySet()) {
            Train train = entry.getValue();
            if (!train.isRunning()) {
                continue;
            }
            if (!network.hasSection(train.reference().sectionId())) {
                // Track the train sits on is not present. On a client this is a normal transient
                // while track and train state converge; on a server it means the section was
                // removed out from under a train. Either way, skipping is the only sane action —
                // advancing would throw, and an exception per train per tick is worse than a
                // stationary train.
                continue;
            }
            double accel = 0.0D;
            if (external != null) {
                accel = external.forTrain(entry.getKey(), train);
                // Tell the train whether a ride element is deliberately holding it, so valleying
                // detection does not misread a normal station dwell as a stalled train.
                train.setHeld(external.isHolding(entry.getKey(), train));
            }
            train.tick(network, accel, subSteps, tickSeconds);
        }
    }

    /** Supplies the along-track acceleration acting on a train from ride hardware. */
    public interface ExternalAcceleration {

        double forTrain(int trainId, Train train);

        /**
         * Whether a ride element is deliberately holding this train stationary, as opposed to it
         * having stalled.
         *
         * <p>The two are indistinguishable from inside the physics — a train stopped in a station
         * on level track has the same speed, grade and applied force as one stranded in a valley —
         * so the ride-control layer has to say which it is. Defaults to false, which is correct for
         * a park with no elements at all.</p>
         */
        default boolean isHolding(int trainId, Train train) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "TrainManager{" + trains.size() + " trains}";
    }
}
