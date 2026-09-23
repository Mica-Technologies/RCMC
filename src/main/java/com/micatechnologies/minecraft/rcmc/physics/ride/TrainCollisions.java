package com.micatechnologies.minecraft.rcmc.physics.ride;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Finds coaster trains occupying the same stretch of track.
 *
 * <p>Before this, two trains on one circuit without block signalling simply passed through each
 * other, and even with it a collision was only recorded and shown in {@code /rcmc info}. A ride
 * where two trains meet has to stop — the world tick e-stops any ride this reports.</p>
 *
 * <p>A train occupies the stretch from its lead car back along its own length. On a closed circuit
 * that stretch can wrap past the seam, so both overlaps are checked modulo the circuit's length.
 * Metro trains are left to their line signalling and are not checked here.</p>
 */
public final class TrainCollisions {

    /** How much two trains must overlap, in blocks, to count — clearing couplers are not a crash. */
    static final double MIN_OVERLAP = 0.5D;

    private TrainCollisions() {
    }

    /** The sections on which two or more coaster trains overlap, in ascending order. */
    public static Set<Integer> sectionsWithCollisions(Map<Integer, Train> trains, TrackNetwork network) {
        Set<Integer> hit = new TreeSet<>();
        List<Map.Entry<Integer, Train>> coasters = new ArrayList<>();
        for (Map.Entry<Integer, Train> entry : trains.entrySet()) {
            Train train = entry.getValue();
            if (train.isRunning() && train.spec().carStyle() == TrainSpec.CarStyle.COASTER
                && train.reference() != null) {
                coasters.add(entry);
            }
        }
        for (int i = 0; i < coasters.size(); i++) {
            for (int j = i + 1; j < coasters.size(); j++) {
                Train a = coasters.get(i).getValue();
                Train b = coasters.get(j).getValue();
                int section = a.reference().sectionId();
                if (section != b.reference().sectionId()) {
                    continue;
                }
                TrackSection track = network.section(section);
                if (track != null && overlap(a, b, track) >= MIN_OVERLAP) {
                    hit.add(section);
                }
            }
        }
        return hit;
    }

    /** Blocks of track the two trains share, on {@code track}. */
    static double overlap(Train a, Train b, TrackSection track) {
        double aHead = a.reference().distance();
        double bHead = b.reference().distance();
        double aTail = aHead - length(a.spec());
        double bTail = bHead - length(b.spec());
        double direct = shared(aTail, aHead, bTail, bHead);
        if (!track.isClosed()) {
            return direct;
        }
        // A train straddling the seam occupies both ends of the circuit; shifting one by a lap
        // either way catches the overlap wherever it wraps.
        double lap = track.totalLength();
        return Math.max(direct, Math.max(shared(aTail + lap, aHead + lap, bTail, bHead),
            shared(aTail - lap, aHead - lap, bTail, bHead)));
    }

    private static double shared(double aFrom, double aTo, double bFrom, double bTo) {
        return Math.max(0.0D, Math.min(aTo, bTo) - Math.max(aFrom, bFrom));
    }

    private static double length(TrainSpec spec) {
        return spec.carCount() * spec.carLength() + Math.max(0, spec.carCount() - 1) * spec.couplingGap();
    }
}
