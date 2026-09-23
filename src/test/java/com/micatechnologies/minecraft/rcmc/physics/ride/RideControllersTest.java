package com.micatechnologies.minecraft.rcmc.physics.ride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A ride split over several sections is still one ride, with one controller. */
class RideControllersTest {

    @Test
    @DisplayName("the new half of a split section runs under the ride it was cut from")
    void splitHalfJoinsTheRide() {
        RideControllers rides = new RideControllers();
        RideController ride = rides.getOrCreate(1);
        ride.setState(RideController.State.OPEN);
        rides.join(7, 1);

        assertSame(ride, rides.get(7), "a train on the new half would obey a different, closed ride");
        assertEquals(2, rides.members(1).size());
        ride.emergencyStop(RideController.StopCause.OPERATOR);
        assertTrue(rides.isEmergencyStopped(7), "an e-stop must hold trains on every part of the ride");
    }

    @Test
    @DisplayName("a merge keeps whichever ride was operated, and undoing it gives each its own back")
    void mergeKeepsTheOperatedRide() {
        RideControllers rides = new RideControllers();
        RideController operated = rides.getOrCreate(2);
        Map<Integer, Integer> before = new HashMap<>(rides.homes());

        rides.absorb(2, 1);
        assertSame(operated, rides.get(1), "the survivor lost the ride the removed section had");

        rides.setHomes(before);   // what an undo restores
        assertSame(operated, rides.get(2));
        assertNotNull(rides.getOrCreate(1));
        assertTrue(rides.get(1) != operated, "after the undo, section 1 is its own ride again");
    }

    @Test
    @DisplayName("deleting one section of a ride keeps the ride running on the rest")
    void deletingAMemberKeepsTheRide() {
        RideControllers rides = new RideControllers();
        RideController ride = rides.getOrCreate(1);
        rides.join(5, 1);
        rides.remove(1);
        assertSame(ride, rides.get(5), "section 5 still carries the ride");
        rides.remove(5);
        assertEquals(0, rides.all().size());
    }
}
