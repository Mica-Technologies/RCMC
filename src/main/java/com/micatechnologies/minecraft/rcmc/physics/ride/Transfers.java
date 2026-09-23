package com.micatechnologies.minecraft.rcmc.physics.ride;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.element.TransferTrack;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.Map;

/**
 * The transfer table's rules: when a train may slide across to storage, when one may come back,
 * and the move itself.
 *
 * <p>A train moves only whole and at rest. Going out, all of it has to be on the table; coming back,
 * the table and a train's length either side of it have to be clear of every other train — a train
 * slid back into the circuit lands where it lands, and nothing can stop in time for it.</p>
 */
public final class Transfers {

    /** Speed under which a train counts as at rest for a transfer, blocks/s. */
    static final double AT_REST = 0.05D;

    private Transfers() {
    }

    /** The train standing wholly on {@code transfer} and at rest, ready to store; {@code null} if none. */
    public static Integer readyToStore(TransferTrack transfer, Map<Integer, Train> trains) {
        for (Map.Entry<Integer, Train> entry : trains.entrySet()) {
            Train train = entry.getValue();
            TrackRef lead = train.reference();
            if (lead == null || lead.sectionId() != transfer.sectionId()) {
                continue;
            }
            double tail = lead.distance() - train.spec().totalLength();
            if (tail >= transfer.startDistance() && lead.distance() <= transfer.endDistance()
                && Math.abs(train.velocity()) <= AT_REST) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** The train in {@code transfer}'s storage; {@code null} if it is empty. */
    public static Integer stored(TransferTrack transfer, Map<Integer, Train> trains) {
        if (!transfer.isLinked()) {
            return null;
        }
        double from = transfer.storageOffset();
        double to = from + (transfer.endDistance() - transfer.startDistance());
        for (Map.Entry<Integer, Train> entry : trains.entrySet()) {
            TrackRef lead = entry.getValue().reference();
            if (lead != null && lead.sectionId() == transfer.storageSectionId()
                && lead.distance() >= from - 1.0D && lead.distance() <= to + 1.0D) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Whether the table, and a train's length either side, has no train on it. */
    public static boolean tableClear(TransferTrack transfer, Map<Integer, Train> trains, double margin) {
        double from = transfer.startDistance() - margin;
        double to = transfer.endDistance() + margin;
        for (Train train : trains.values()) {
            TrackRef lead = train.reference();
            if (lead == null || lead.sectionId() != transfer.sectionId()) {
                continue;
            }
            double tail = lead.distance() - train.spec().totalLength();
            if (lead.distance() >= from && tail <= to) {
                return false;
            }
        }
        return true;
    }

    /**
     * The distance along {@code storage} nearest the point {@code at} — where a storage track lines
     * up with the start of a transfer table beside it.
     */
    public static double nearestOn(TrackSection storage, Vec3 at) {
        double best = 0.0D;
        double bestSq = Double.MAX_VALUE;
        for (double d = 0.0D; d <= storage.totalLength(); d += 0.25D) {
            double sq = storage.positionAtDistance(d).subtract(at).lengthSquared();
            if (sq < bestSq) {
                bestSq = sq;
                best = d;
            }
        }
        return best;
    }

    /** Slides {@code train} across to storage, keeping its place along the table. */
    public static void store(Train train, TransferTrack transfer) {
        double along = train.reference().distance() - transfer.startDistance();
        train.setState(new TrackRef(transfer.storageSectionId(), transfer.storageOffset() + along), 0.0D);
    }

    /** Slides {@code train} back from storage onto the table, keeping its place along it. */
    public static void retrieve(Train train, TransferTrack transfer) {
        double along = train.reference().distance() - transfer.storageOffset();
        train.setState(new TrackRef(transfer.sectionId(), transfer.startDistance() + along), 0.0D);
    }
}
