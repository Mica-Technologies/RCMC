package com.micatechnologies.minecraft.rcmc.track.storage;

import com.micatechnologies.minecraft.rcmc.physics.ride.RideController;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideControllers;
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
    private static final String KEY_CARS = "CarsPerTrain";

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
            if (tag.hasKey(KEY_CARS)) {
                ride.setCarsPerTrain(tag.getInteger(KEY_CARS));
            }
            if (tag.getBoolean(KEY_ESTOP)) {
                // Keeps the state read above: an e-stopped ride is also closed, but a save from a
                // ride stopped and then deliberately reopened must not come back closed.
                RideController.State state = ride.state();
                ride.emergencyStop();
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
