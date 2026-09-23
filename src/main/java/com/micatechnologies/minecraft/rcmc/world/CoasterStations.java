package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideController;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideControllers;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.Set;
import net.minecraft.util.math.BlockPos;

/**
 * A coaster's station, as the blocks around it and the riders in it see it: which station is which
 * ride's, which is nearest a block, and when a train there may be boarded, left, or have its gates
 * open.
 *
 * <p>Boarding used to be allowed anywhere on the circuit while the ride was open — a guest could
 * climb into a train stopped on the lift. A train is boarded, and left, in its station, stopped:
 * where its station is dwelling with it. Track with no station at all is a sandbox run and boards
 * anywhere; and riders of a train stopped on a closed or emergency-stopped ride may always get
 * off, since that is an evacuation.</p>
 */
public final class CoasterStations {

    /** Ticks before the end of an automatic dwell that the air gates start to close. */
    public static final int GATES_CLOSE_LEAD = 30;

    /** Ticks a gate must have been shut before the train may leave: a gate still swinging is not
     *  a closed gate. */
    public static final int GATES_SHUT_FOR = 10;

    /**
     * What an open gate holds dispatch for each tick: {@link #GATES_SHUT_FOR} and two. The hold is
     * renewed on the last tick a gate is open and counts down on it, and again on the tick the
     * train would move — so with only {@code GATES_SHUT_FOR} the gates had been shut for two ticks
     * fewer than that when it went.
     */
    public static final int GATE_HOLD = GATES_SHUT_FOR + 2;

    /** How far from the centreline a coaster platform's edge stands, in blocks. The car body is
     *  0.62 either side of it; the block grid puts the edge's block anywhere up to a block further
     *  in than this, so this keeps even the nearest clear of the car. A rider stepping off reaches
     *  out to the platform (see {@code EntityCoasterCar}). */
    public static final double PLATFORM_OFFSET = 1.7D;

    /** A coaster platform's deck meets the track: the cars sit on it, and riders step off level. */
    public static final double PLATFORM_FLOOR = 0.0D;

    private CoasterStations() {
    }

    /**
     * Lays a platform along {@code station}, both sides or one, with air gates on its edge where
     * the ride's train stops.
     */
    public static PlatformBuilder.Result layPlatform(net.minecraft.world.World world, RcmcWorldState state,
                                                     StationPlatform station, boolean left, boolean right,
                                                     int width) {
        com.micatechnologies.minecraft.rcmc.track.TrackSection section =
            state.network().section(station.sectionId());
        if (section == null) {
            return new PlatformBuilder.Result(0, 0);
        }
        RideController ride = state.rides().get(station.sectionId());
        double train = com.micatechnologies.minecraft.rcmc.physics.TrainTypes
            .coasterOrDefault(ride == null ? null : ride.carType())
            .spec(ride == null ? RideController.DEFAULT_CARS : ride.carsPerTrain()).totalLength();
        // The lead car stops at the stop point and the train stands behind it, so that is where
        // riders get on and off.
        double stop = station.stopDistance();
        return PlatformBuilder.lay(world, section, station.startDistance(), station.endDistance(),
            PLATFORM_FLOOR, PLATFORM_OFFSET, width, left, right, stop - train - 0.5D, stop + 1.5D,
            station.sectionId());
    }

    /** The station of the ride {@code sectionId} belongs to, or {@code null} if it has none. */
    public static StationPlatform stationOf(RcmcWorldState state, int sectionId) {
        return stationOf(state.rides(), state.elements(), sectionId);
    }

    /** As above, from the parts. */
    public static StationPlatform stationOf(RideControllers rides, RideElementSet elements, int sectionId) {
        Set<Integer> members = rides.members(sectionId);
        for (RideElement element : elements.elements()) {
            if (element instanceof StationPlatform && members.contains(element.sectionId())) {
                return (StationPlatform) element;
            }
        }
        return null;
    }

    /** The coaster station whose stop point is nearest {@code pos}, within {@code range} blocks. */
    public static StationPlatform nearest(RcmcWorldState state, BlockPos pos, double range) {
        double best = range * range;
        StationPlatform nearest = null;
        for (RideElement element : state.elements().elements()) {
            if (!(element instanceof StationPlatform) || state.network().section(element.sectionId()) == null) {
                continue;
            }
            StationPlatform station = (StationPlatform) element;
            Vec3 at;
            try {
                at = state.network().frameAt(new TrackRef(station.sectionId(), station.stopDistance())).position;
            }
            catch (RuntimeException e) {
                continue;
            }
            double dx = at.x - (pos.getX() + 0.5D);
            double dy = at.y - (pos.getY() + 0.5D);
            double dz = at.z - (pos.getZ() + 0.5D);
            double d = dx * dx + dy * dy + dz * dz;
            if (d < best) {
                best = d;
                nearest = station;
            }
        }
        return nearest;
    }

    /** Whether {@code station} has a train stopped at it, dwelling. */
    public static boolean isLoading(StationPlatform station) {
        return station != null && station.servingTrain() != StationPlatform.NO_TRAIN
            && station.phase() == StationPlatform.Phase.DWELLING;
    }

    /** Whether a guest may get on train {@code trainId} where it is now. */
    public static boolean mayBoard(RcmcWorldState state, int trainId) {
        return mayBoard(state.rides(), state.elements(), state.trains(), trainId);
    }

    /** As above, from the parts. */
    public static boolean mayBoard(RideControllers rides, RideElementSet elements, TrainManager trains,
                                   int trainId) {
        Train train = trains.train(trainId);
        if (train == null || train.reference() == null) {
            return true;
        }
        StationPlatform station = stationOf(rides, elements, train.reference().sectionId());
        if (station == null) {
            return true;
        }
        return isLoading(station) && station.servingTrain() == trainId;
    }

    /** Whether a rider may get off train {@code trainId} where it is now. */
    public static boolean mayLeave(RcmcWorldState state, int trainId) {
        return mayLeave(state.rides(), state.elements(), state.trains(), trainId);
    }

    /** As above, from the parts. */
    public static boolean mayLeave(RideControllers rides, RideElementSet elements, TrainManager trains,
                                   int trainId) {
        if (mayBoard(rides, elements, trains, trainId)) {
            return true;
        }
        Train train = trains.train(trainId);
        if (train == null || Math.abs(train.velocity()) > 0.1D) {
            return false;
        }
        RideController ride = rides.get(train.reference().sectionId());
        // No controller yet is a ride nobody has operated, which runs as an open one does.
        return ride != null && (ride.isEmergencyStopped() || ride.state() == RideController.State.CLOSED);
    }

    /**
     * Whether {@code station}'s air gates should stand open: a train is stopped there, and is not
     * about to leave — the gates start to close {@link #GATES_CLOSE_LEAD} ticks before an automatic
     * dwell ends, or as soon as the operator presses DISPATCH. A closed or emergency-stopped ride's
     * train is going nowhere, so its gates stay open for its riders to get off.
     */
    public static boolean gatesOpen(RcmcWorldState state, StationPlatform station) {
        return gatesOpen(state.rides(), station);
    }

    /** As above, from the parts. */
    public static boolean gatesOpen(RideControllers rides, StationPlatform station) {
        if (!isLoading(station)) {
            return false;
        }
        RideController ride = rides.get(station.sectionId());
        if (ride == null) {
            return station.dwellRemaining() > GATES_CLOSE_LEAD;
        }
        if (ride.isEmergencyStopped() || ride.state() == RideController.State.CLOSED) {
            // Held in the station, going nowhere: the gates open so its riders can get off. Nobody
            // new gets on — the ride turns them away — and the train cannot leave anyway.
            return true;
        }
        if (ride.dispatchMode() == RideController.DispatchMode.MANUAL) {
            return !ride.isDispatchRequested();
        }
        // A ride just opened gives the train already waiting a fresh loading period, the gates
        // open for it as for a dwell.
        return Math.max(station.dwellRemaining(), ride.loadingRemaining()) > GATES_CLOSE_LEAD;
    }
}
