package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.block.TileOperatorPanel;
import com.micatechnologies.minecraft.rcmc.net.PacketRideAction.Action;
import com.micatechnologies.minecraft.rcmc.net.PacketRideView;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.net.RideView;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.block.BlockSystem;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideController;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideTuning;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * What the ride operator panel does, server side: opens a ride's controls for a player, and applies
 * what they press.
 *
 * <p><b>Who may press.</b> A player can act on a ride only while standing at a panel linked to it —
 * the one they opened, within reach. The client says which ride and which button; everything else is
 * checked here, because a packet can claim anything. A player who walks away, or whose panel has been
 * broken or relinked, is simply ignored.</p>
 */
public final class RideOperations {

    /** How far from their panel a player may stand and still operate the ride. */
    private static final double REACH = 10.0D;

    private static final class Session {
        final int dimension;
        final BlockPos panel;
        final int sectionId;

        Session(int dimension, BlockPos panel, int sectionId) {
            this.dimension = dimension;
            this.panel = panel;
            this.sectionId = sectionId;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private RideOperations() {
    }

    /** A player used a panel linked to {@code sectionId}: remember it, and show them the ride. */
    public static void open(EntityPlayerMP player, BlockPos panel, int sectionId) {
        SESSIONS.put(player.getUniqueID(),
            new Session(player.dimension, panel.toImmutable(), sectionId));
        send(player, sectionId, "");
    }

    /** Applies one press from the panel, then answers with the ride as it now stands. */
    public static void handle(EntityPlayerMP player, int sectionId, Action action, int first,
                              int second, double value) {
        Session session = SESSIONS.get(player.getUniqueID());
        if (session == null || session.sectionId != sectionId || session.dimension != player.dimension
            || player.getDistanceSq(session.panel) > REACH * REACH || !stillLinked(player.world, session)) {
            return;
        }
        RcmcWorldState state = RcmcWorldState.of(player.world);
        if (state == null || state.network().section(sectionId) == null) {
            return;
        }
        String message = apply(player.world, state, sectionId, action, first, second, value);
        send(player, sectionId, message);
    }

    private static boolean stillLinked(World world, Session session) {
        TileEntity tile = world.getTileEntity(session.panel);
        return tile instanceof TileOperatorPanel
            && ((TileOperatorPanel) tile).linkedSection() == session.sectionId;
    }

    private static String apply(World world, RcmcWorldState state, int sectionId, Action action,
                                int first, int second, double value) {
        RideController ride = state.rides().getOrCreate(sectionId);
        switch (action) {
            case REFRESH:
                return "";
            case OPEN:
                if (ride.isEmergencyStopped()) {
                    return "Reset the emergency stop before opening the ride.";
                }
                ride.setState(RideController.State.OPEN);
                return changed(world, state, "Ride open.");
            case TEST:
                if (ride.isEmergencyStopped()) {
                    return "Reset the emergency stop first.";
                }
                ride.setState(RideController.State.TESTING);
                return changed(world, state, "Testing: trains run, nobody boards.");
            case CLOSE:
                ride.setState(RideController.State.CLOSED);
                return changed(world, state, "Closed: trains return to the station and wait.");
            case EMERGENCY_STOP:
                ride.emergencyStop();
                return changed(world, state, "EMERGENCY STOP.");
            case RESET_EMERGENCY:
                ride.resetEmergency();
                return changed(world, state, "Emergency stop reset. The ride is closed.");
            case MODE_AUTOMATIC:
                ride.setDispatchMode(RideController.DispatchMode.AUTOMATIC);
                return changed(world, state, "Trains dispatch themselves after the dwell.");
            case MODE_MANUAL:
                ride.setDispatchMode(RideController.DispatchMode.MANUAL);
                return changed(world, state, "Trains wait for DISPATCH.");
            case DISPATCH:
                return ride.requestDispatch() ? "Dispatching the next train." : "Nothing to dispatch.";
            case SET_CARS:
                ride.setCarsPerTrain(first);
                return changed(world, state, "New trains will have " + ride.carsPerTrain() + " cars.");
            case ADD_TRAIN:
                return addTrain(world, state, sectionId, ride);
            case REMOVE_TRAIN:
                return removeTrain(world, state, sectionId, first);
            case TUNE:
                return tune(world, state, sectionId, first, second, value);
            default:
                return "";
        }
    }

    /** Operator state changed: make sure the save picks it up. */
    private static String changed(World world, RcmcWorldState state, String message) {
        state.markTrainsDirty(world);
        return message;
    }

    private static String addTrain(World world, RcmcWorldState state, int sectionId,
                                   RideController ride) {
        StationPlatform station = stationOf(state, sectionId);
        if (station == null) {
            return "This ride has no station to put a train in.";
        }
        int running = trainsOn(state, sectionId).size();
        int max = RideController.maxTrains(blockCount(state, sectionId));
        if (running >= max) {
            return max == 1
                ? "One train is the most this ride can run without block sections."
                : "This ride's " + blockCount(state, sectionId) + " blocks allow " + max + " trains.";
        }
        if (stationOccupied(state, station)) {
            return "Wait for the station to clear first.";
        }
        int id = TrainSpawner.spawn(world, state, sectionId,
            new TrainSpec(ride.carsPerTrain(), 3.0D, 0.5D, 4), station.stopDistance(), 0.0D);
        return "Added train #" + id + " (" + ride.carsPerTrain() + " cars).";
    }

    private static String removeTrain(World world, RcmcWorldState state, int sectionId,
                                      int trainId) {
        Train train = state.trains().train(trainId);
        if (train == null || train.reference().sectionId() != sectionId) {
            return "That train is not on this ride.";
        }
        TrainSpawner.remove(world, state, trainId);
        return "Removed train #" + trainId + ".";
    }

    private static String tune(World world, RcmcWorldState state, int sectionId, int elementIndex,
                               int parameterOrdinal, double value) {
        RideTuning.Parameter[] all = RideTuning.Parameter.values();
        if (parameterOrdinal < 0 || parameterOrdinal >= all.length) {
            return "";
        }
        RideTuning.Parameter parameter = all[parameterOrdinal];
        double applied = RideTuning.apply(state.elements(), sectionId, elementIndex, parameter,
            value, RcmcConstants.SECONDS_PER_TICK);
        if (Double.isNaN(applied)) {
            return "That setting has changed — try again.";
        }
        // Authored hardware: saved, and undoable, like any other edit to the ride.
        state.markTrackDirty(world);
        return parameter.label + " set to " + format(applied) + " " + parameter.unit + ".";
    }

    private static void send(EntityPlayerMP player, int sectionId, String message) {
        RcmcWorldState state = RcmcWorldState.of(player.world);
        if (state == null) {
            return;
        }
        RcmcNetwork.sendTo(new PacketRideView(viewOf(state, sectionId, message)), player);
    }

    /** The ride as the panel shows it. */
    static RideView viewOf(RcmcWorldState state, int sectionId, String message) {
        RideController ride = state.rides().get(sectionId);
        if (ride == null) {
            ride = new RideController(sectionId);   // Untouched: shown with the defaults it runs on.
        }
        List<RideView.TrainRow> trains = new ArrayList<>();
        for (Map.Entry<Integer, Train> entry : trainsOn(state, sectionId).entrySet()) {
            Train train = entry.getValue();
            trains.add(new RideView.TrainRow(entry.getKey(), train.spec().carCount(),
                train.speed(), train.status().name(), train.reference().distance()));
        }
        List<RideView.SettingRow> settings = new ArrayList<>();
        for (RideTuning.Setting setting : RideTuning.settingsFor(state.elements(), sectionId,
            RcmcConstants.SECONDS_PER_TICK)) {
            settings.add(new RideView.SettingRow(setting.elementIndex, setting.parameter.ordinal(),
                setting.value));
        }
        int blocks = blockCount(state, sectionId);
        return new RideView(sectionId, "Coaster #" + sectionId, ride.state().ordinal(),
            ride.dispatchMode().ordinal(), ride.isEmergencyStopped(),
            ride.stopCause() == null ? "" : ride.stopCause().name(), ride.carsPerTrain(),
            RideController.maxTrains(blocks), blocks, message, trains, settings);
    }

    static StationPlatform stationOf(RcmcWorldState state, int sectionId) {
        for (RideElement element : state.elements().elements()) {
            if (element.sectionId() == sectionId && element instanceof StationPlatform) {
                return (StationPlatform) element;
            }
        }
        return null;
    }

    private static Map<Integer, Train> trainsOn(RcmcWorldState state, int sectionId) {
        Map<Integer, Train> out = new java.util.TreeMap<>();
        for (Map.Entry<Integer, Train> entry : state.trains().asMap().entrySet()) {
            if (entry.getValue().reference().sectionId() == sectionId) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    private static int blockCount(RcmcWorldState state, int sectionId) {
        BlockSystem blocks = state.blocks().get(sectionId);
        return blocks == null ? 0 : blocks.blockCount();
    }

    /** Whether any car of any train is standing in the station. */
    private static boolean stationOccupied(RcmcWorldState state, StationPlatform station) {
        for (Train train : state.trains().trains()) {
            for (int i = 0; i < train.spec().carCount(); i++) {
                TrackRef car = train.refOfCar(state.network(), i);
                if (station.contains(car)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String format(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    /** Forgets a player's panel, when they log out. */
    public static void forget(UUID player) {
        SESSIONS.remove(player);
    }
}
