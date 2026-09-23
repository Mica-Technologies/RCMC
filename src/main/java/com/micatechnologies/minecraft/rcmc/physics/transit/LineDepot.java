package com.micatechnologies.minecraft.rcmc.physics.transit;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import java.util.Map;

/**
 * Where a new train can join a line: at one of its platforms, with the rails there clear.
 *
 * <p>The control desk's "add train" puts one in service without the operator saying where, which
 * the {@code /rcmc train} command leaves to them. A platform is the natural place — passengers can
 * board the moment it opens its doors — so every berth of the line is tried in turn, and the first
 * with no other train within {@link #CLEARANCE} of the stretch the new one would stand on is used.</p>
 */
public final class LineDepot {

    /** Rails kept clear either side of a new train, blocks: room for it and a neighbour to stop. */
    public static final double CLEARANCE = 20.0D;

    private LineDepot() {
    }

    /**
     * A berth of {@code line} where a train {@code length} blocks long fits with nothing near it,
     * or {@code null} if every one is taken. The train would stand with its lead car at the stop
     * point and the rest behind it, toward -s, as every train stands.
     */
    public static TransitPlatform freeBerth(TransitSystem transit, TransitLine line, TrackNetwork network,
                                            TrainManager trains, double length) {
        for (TransitStation stop : line.stations()) {
            TransitStation live = transit.station(stop.name());
            for (TransitPlatform berth : (live == null ? stop : live).platforms()) {
                TrackSection section = network.section(berth.stopPoint().sectionId());
                if (section == null) {
                    continue;
                }
                double from = berth.stopPoint().distance() - length;
                double to = berth.stopPoint().distance();
                if (!section.isClosed() && from < 0.0D) {
                    continue;  // it would hang off the end of the track
                }
                if (clear(section, from - CLEARANCE, to + CLEARANCE, trains)) {
                    return berth;
                }
            }
        }
        return null;
    }

    /** Whether no train has any car between {@code from} and {@code to} on this section. */
    private static boolean clear(TrackSection section, double from, double to, TrainManager trains) {
        double length = section.totalLength();
        for (Map.Entry<Integer, Train> entry : trains.asMap().entrySet()) {
            Train train = entry.getValue();
            if (train.reference() == null || train.reference().sectionId() != section.id()) {
                continue;
            }
            TrainSpec spec = train.spec();
            double head = train.reference().distance() + spec.carLength() * 0.5D;
            double tail = train.reference().distance() - spec.offsetOfCar(spec.carCount() - 1) - spec.carLength() * 0.5D;
            if (overlaps(tail, head, from, to, section.isClosed() ? length : 0.0D)) {
                return false;
            }
        }
        return true;
    }

    /** Whether two stretches meet, allowing for a closed section's seam when {@code lap} is its length. */
    static boolean overlaps(double a0, double a1, double b0, double b1, double lap) {
        if (lap <= 0.0D) {
            return a0 <= b1 && b0 <= a1;
        }
        for (int k = -1; k <= 1; k++) {
            if (a0 + k * lap <= b1 && b0 <= a1 + k * lap) {
                return true;
            }
        }
        return false;
    }
}
