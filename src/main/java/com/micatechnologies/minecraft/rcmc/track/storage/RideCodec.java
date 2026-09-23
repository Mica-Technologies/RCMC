package com.micatechnologies.minecraft.rcmc.track.storage;

import com.micatechnologies.minecraft.rcmc.physics.ride.RideController;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideControllers;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Saves each ride's operating state: open or closed, how it dispatches, whether it is e-stopped.
 *
 * <p>Operator state, not authored state — it is written beside the trains rather than into the undo
 * snapshot, because undoing a track edit should not reopen a ride someone has just closed. A pending
 * manual DISPATCH press is deliberately not saved: a press is an instruction for now, and one
 * surviving a restart would send a train nobody asked for.</p>
 *
 * <p>Enums are stored by name, so reordering them can never quietly turn a closed ride into an open
 * one; an unknown name loads as the default for that field.</p>
 */
public final class RideCodec {

    private static final String KEY_RIDES = "Rides";
    private static final String KEY_SECTION = "Section";
    private static final String KEY_STATE = "State";
    private static final String KEY_DISPATCH = "Dispatch";
    private static final String KEY_ESTOP = "EmergencyStop";
    private static final String KEY_CAUSE = "StopCause";
    private static final String KEY_TRANSFER = "TransferRequest";
    private static final String KEY_CAR_MODEL = "CarModel";
    private static final String KEY_CARS = "CarsPerTrain";
    private static final String KEY_HOMES = "RideHomes";
    private static final String KEY_MEMBER = "Member";
    private static final String KEY_HOME = "Home";

    /**
     * Writes which sections belong to which ride. Authored state, unlike the rest of this codec: it
     * says what the track IS — a split section is still one ride — so it goes into the undo snapshot
     * with the track, and an undone merge gives each section its own ride back.
     */
    public static void writeHomes(RideControllers rides, NBTTagCompound root) {
        NBTTagList list = new NBTTagList();
        if (rides != null) {
            for (Map.Entry<Integer, Integer> entry : rides.homes().entrySet()) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger(KEY_MEMBER, entry.getKey());
                tag.setInteger(KEY_HOME, entry.getValue());
                list.appendTag(tag);
            }
        }
        root.setTag(KEY_HOMES, list);
    }

    /** Reads what {@link #writeHomes} wrote; empty for a save from before rides could span sections. */
    public static Map<Integer, Integer> readHomes(NBTTagCompound root) {
        Map<Integer, Integer> homes = new java.util.LinkedHashMap<>();
        if (root == null || !root.hasKey(KEY_HOMES)) {
            return homes;
        }
        NBTTagList list = root.getTagList(KEY_HOMES, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            homes.put(tag.getInteger(KEY_MEMBER), tag.getInteger(KEY_HOME));
        }
        return homes;
    }

    private RideCodec() {
    }

    public static void write(RideControllers rides, NBTTagCompound root) {
        NBTTagList list = new NBTTagList();
        if (rides != null) {
            for (RideController ride : rides.all()) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger(KEY_SECTION, ride.sectionId());
                tag.setString(KEY_STATE, ride.state().name());
                tag.setString(KEY_DISPATCH, ride.dispatchMode().name());
                tag.setBoolean(KEY_ESTOP, ride.isEmergencyStopped());
                tag.setString(KEY_TRANSFER, ride.transferRequest().name());
                tag.setString(KEY_CAR_MODEL, ride.carModel().name());
                if (ride.stopCause() != null) {
                    tag.setString(KEY_CAUSE, ride.stopCause().name());
                }
                tag.setInteger(KEY_CARS, ride.carsPerTrain());
                list.appendTag(tag);
            }
        }
        root.setTag(KEY_RIDES, list);
    }

    public static RideControllers read(NBTTagCompound root) {
        RideControllers rides = new RideControllers();
        if (root == null || !root.hasKey(KEY_RIDES)) {
            return rides;
        }
        NBTTagList list = root.getTagList(KEY_RIDES, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            RideController ride = new RideController(tag.getInteger(KEY_SECTION));
            ride.setState(parse(RideController.State.class, tag.getString(KEY_STATE),
                RideController.State.OPEN));
            ride.setDispatchMode(parse(RideController.DispatchMode.class, tag.getString(KEY_DISPATCH),
                RideController.DispatchMode.AUTOMATIC));
            ride.setCarModel(parse(com.micatechnologies.minecraft.rcmc.physics.TrainSpec.CoasterModel.class,
                tag.getString(KEY_CAR_MODEL),
                com.micatechnologies.minecraft.rcmc.physics.TrainSpec.CoasterModel.SIT_DOWN));
            ride.setTransferRequest(parse(RideController.TransferRequest.class,
                tag.getString(KEY_TRANSFER), RideController.TransferRequest.NONE));
            if (tag.hasKey(KEY_CARS)) {
                ride.setCarsPerTrain(tag.getInteger(KEY_CARS));
            }
            if (tag.getBoolean(KEY_ESTOP)) {
                // Keeps the state read above: an e-stopped ride is also closed, but a save from a
                // ride stopped and then deliberately reopened must not come back closed.
                RideController.State state = ride.state();
                ride.emergencyStop(parse(RideController.StopCause.class, tag.getString(KEY_CAUSE),
                    RideController.StopCause.OPERATOR));
                ride.setState(state);
            }
            rides.put(ride);
        }
        return rides;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        }
        catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }
}
