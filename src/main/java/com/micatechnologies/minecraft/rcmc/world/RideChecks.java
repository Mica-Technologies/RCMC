package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.RcmcConfig;
import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideController;
import com.micatechnologies.minecraft.rcmc.rating.RideCheck;
import com.micatechnologies.minecraft.rcmc.rating.RideRater;
import com.micatechnologies.minecraft.rcmc.rating.RideWarning;
import com.micatechnologies.minecraft.rcmc.rating.SafetyLimits;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Runs {@link RideCheck} for a ride in the world: its own train, from its own station, through a copy
 * of its hardware, with this server's physics.
 */
public final class RideChecks {

    private RideChecks() {
    }

    /**
     * The warnings for the ride {@code sectionId} is part of; empty for track that is not a ride — no
     * station anywhere on it, so no lap to run.
     */
    public static List<RideWarning> forRide(RcmcWorldState state, int sectionId) {
        Set<Integer> members = state.rides().members(sectionId);
        StationPlatform station = null;
        for (RideElement element : state.elements().elements()) {
            if (element instanceof StationPlatform && members.contains(element.sectionId())) {
                station = (StationPlatform) element;
                break;
            }
        }
        if (station == null || !state.network().hasSection(station.sectionId())) {
            return Collections.emptyList();
        }
        return RideCheck.check(rater(), state.network(), state.elements(), station.sectionId(),
            trainFor(state, members), SafetyLimits.DEFAULT);
    }

    /** The ride's own train if one is running on it, or the one its operator would add. */
    static TrainSpec trainFor(RcmcWorldState state, Set<Integer> members) {
        for (Train train : state.trains().trains()) {
            if (train.reference() != null && members.contains(train.reference().sectionId())) {
                return train.spec();
            }
        }
        RideController ride = state.rides().get(members.iterator().next());
        int cars = ride == null ? 5 : ride.carsPerTrain();
        return new TrainSpec(cars, 3.0D, 0.5D, 4);
    }

    private static RideRater rater() {
        return RideRater.standard(new PhysicsIntegrator(RcmcConfig.gravity, RcmcConfig.rollingResistance,
            RcmcConfig.airDrag, RcmcConfig.maxSpeed), RcmcConfig.gravity);
    }

    /** "between nodes 9 and 10" — where a distance along {@code section} is, as a builder sees it. */
    public static String whereOn(TrackSection section, double distance) {
        int nodes = section.nodes().size();
        for (int i = 0; i < nodes; i++) {
            double start = section.nodeDistance(i);
            double end = i + 1 < nodes ? section.nodeDistance(i + 1) : section.totalLength();
            if (distance >= start - 1.0e-6D && distance <= end + 1.0e-6D) {
                if (Math.abs(distance - start) < 1.0D) {
                    return "at node " + (i + 1);
                }
                return "between nodes " + (i + 1) + " and " + (i + 2 > nodes ? 1 : i + 2);
            }
        }
        return "at " + Math.round(distance) + " blocks";
    }
}
