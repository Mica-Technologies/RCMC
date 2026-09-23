package com.micatechnologies.minecraft.rcmc.track.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.ride.RideController;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideControllers;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RideCodecTest {

    private static RideControllers roundTrip(RideControllers rides) {
        NBTTagCompound tag = new NBTTagCompound();
        RideCodec.write(rides, tag);
        return RideCodec.read(tag);
    }

    @Test
    @DisplayName("a closed, manual ride comes back closed and manual")
    void settingsRoundTrip() {
        RideControllers rides = new RideControllers();
        RideController ride = rides.getOrCreate(4);
        ride.setState(RideController.State.CLOSED);
        ride.setDispatchMode(RideController.DispatchMode.MANUAL);
        ride.setCarsPerTrain(8);

        RideController back = roundTrip(rides).get(4);
        assertEquals(8, back.carsPerTrain());
        assertEquals(RideController.State.CLOSED, back.state());
        assertEquals(RideController.DispatchMode.MANUAL, back.dispatchMode());
        assertFalse(back.isEmergencyStopped());
    }

    @Test
    @DisplayName("a ride saved before train types keeps its car, as the matching built-in type")
    void oldCarModelLoadsAsItsType() {
        net.minecraft.nbt.NBTTagCompound root = new net.minecraft.nbt.NBTTagCompound();
        net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
        tag.setInteger("Section", 3);
        tag.setString("CarModel", "SHOULDER");
        list.appendTag(tag);
        root.setTag("Rides", list);
        assertEquals("shoulder", RideCodec.read(root).get(3).carType());

        RideControllers rides = new RideControllers();
        rides.getOrCreate(3).setCarType("family");
        assertEquals("family", roundTrip(rides).get(3).carType(), "a custom type id is kept as written");
    }

    @Test
    @DisplayName("an emergency stop survives a restart")
    void emergencyStopSurvives() {
        RideControllers rides = new RideControllers();
        rides.getOrCreate(2).emergencyStop();
        RideController back = roundTrip(rides).get(2);
        assertTrue(back.isEmergencyStopped(), "a stopped ride must not restart itself on a reload");
        assertEquals(RideController.State.CLOSED, back.state());
    }

    @Test
    @DisplayName("a pending DISPATCH press is not saved")
    void dispatchPressIsNotSaved() {
        RideControllers rides = new RideControllers();
        RideController ride = rides.getOrCreate(1);
        ride.setDispatchMode(RideController.DispatchMode.MANUAL);
        assertTrue(ride.requestDispatch());
        assertFalse(roundTrip(rides).get(1).isDispatchRequested(),
            "a press is for now; one outliving a restart would send a train nobody asked for");
    }

    @Test
    @DisplayName("a save with no rides, or from before rides existed, loads as none")
    void missingRidesLoadEmpty() {
        assertTrue(RideCodec.read(new NBTTagCompound()).isEmpty());
        assertNull(roundTrip(new RideControllers()).get(1));
    }
}
