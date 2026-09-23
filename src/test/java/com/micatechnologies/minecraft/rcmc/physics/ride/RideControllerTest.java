package com.micatechnologies.minecraft.rcmc.physics.ride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.debug.DemoCoaster;
import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.element.BrakeRun;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideController.DispatchMode;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideController.State;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ride controller as an operator uses it: open and close the ride, dispatch by hand, stop it in
 * an emergency. Run against the demo coaster, the same way {@code DemoCoasterRunTest} does, so the
 * controller is tested through the station and the physics rather than on its own.
 */
class RideControllerTest {

    private static final double TICK = RcmcConstants.SECONDS_PER_TICK;
    private static final int SECTION = 1;

    private static final class Ride {
        final DemoCoaster.Result demo;
        final TrackNetwork network = new TrackNetwork();
        final RideElementSet elements = new RideElementSet();
        final RideControllers rides = new RideControllers();
        final RideController controller;
        final TrainManager trains = new TrainManager();
        final Train train;

        Ride() {
            demo = DemoCoaster.build(SECTION, new Vec3(0, 64, 0), 1.0D, 34.0D);
            network.addSection(demo.section);
            elements.add(new StationPlatform(SECTION, demo.stationStart, demo.stationEnd,
                demo.stationStop, 6.0D, 60, 4.0D, 6.0D, TICK));
            elements.add(new ChainLift(SECTION, demo.liftStart, demo.liftEnd, 5.0D, 12.0D, TICK));
            elements.add(new BrakeRun(SECTION, demo.brakeStart, demo.brakeEnd, 6.0D, 6.0D,
                BrakeRun.Mode.TRIM, TICK));
            elements.setDispatchGates(rides::gateFor);
            controller = rides.getOrCreate(SECTION);
            train = new Train(new TrainSpec(5, 3.0D, 0.5D, 4),
                new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
                new TrackRef(SECTION, demo.stationStop), 0.0D);
            trains.add(1, train);
        }

        void run(int ticks) {
            RideControlLayer control = new RideControlLayer(elements, rides, network, TICK);
            for (int i = 0; i < ticks; i++) {
                trains.tick(network, control, 4, TICK);
                // As the world tick does: the gates' hold and a reopened ride's loading run down.
                for (RideController ride : rides.all()) {
                    ride.tickGates();
                }
            }
        }

        boolean inStation() {
            double s = train.reference().distance();
            return s >= demo.stationStart && s <= demo.stationEnd;
        }
    }

    @Test
    @DisplayName("an untouched ride is open and dispatches by itself, exactly as before controllers")
    void defaultsAreTodaysBehaviour() {
        RideController fresh = new RideController(7);
        assertEquals(State.OPEN, fresh.state());
        assertEquals(DispatchMode.AUTOMATIC, fresh.dispatchMode());
        assertTrue(fresh.mayDispatch());
        assertTrue(fresh.ridersMayBoard());

        Ride ride = new Ride();
        ride.run(200);
        assertFalse(ride.inStation(), "the train should have left the station on its own");
    }

    @Test
    @DisplayName("a closed ride keeps its train in the station")
    void closedRideHoldsTheTrain() {
        Ride ride = new Ride();
        ride.controller.setState(State.CLOSED);
        ride.run(20 * 30);
        assertTrue(ride.inStation(), "half a minute on, a closed ride's train is still in the station");
        assertTrue(ride.train.isRunning(), "held, not stalled: " + ride.train.status());
        assertFalse(ride.controller.ridersMayBoard());

        ride.controller.setState(State.OPEN);
        ride.run(200);
        assertFalse(ride.inStation(), "opening it lets the train go");
    }

    @Test
    @DisplayName("in manual mode the train waits for DISPATCH, and one press sends one train")
    void manualDispatchWaitsForThePress() {
        Ride ride = new Ride();
        ride.controller.setDispatchMode(DispatchMode.MANUAL);
        ride.run(20 * 20);
        assertTrue(ride.inStation(), "no press, no dispatch");
        assertTrue(ride.train.isRunning(), "waiting, not stalled: " + ride.train.status());

        assertTrue(ride.controller.requestDispatch());
        ride.run(200);
        assertFalse(ride.inStation(), "the press sent it");
        assertFalse(ride.controller.isDispatchRequested(), "and was used up by that dispatch");
    }

    @Test
    @DisplayName("a ride runs one train fewer than it has blocks, and one with no blocks at all")
    void trainCapacityFollowsTheBlocks() {
        assertEquals(1, RideController.maxTrains(0), "nothing would keep a second train off the first");
        assertEquals(1, RideController.maxTrains(1));
        assertEquals(1, RideController.maxTrains(2));
        assertEquals(3, RideController.maxTrains(4), "four trains on four blocks deadlock");
    }

    @Test
    @DisplayName("cars per train stay within what a train can be")
    void carsPerTrainAreBounded() {
        RideController c = new RideController(1);
        assertEquals(RideController.DEFAULT_CARS, c.carsPerTrain());
        c.setCarsPerTrain(0);
        assertEquals(RideController.MIN_CARS, c.carsPerTrain());
        c.setCarsPerTrain(99);
        assertEquals(RideController.MAX_CARS, c.carsPerTrain());
    }

    @Test
    @DisplayName("DISPATCH does nothing on an automatic, closed or stopped ride")
    void dispatchPressIsRefusedWhenMeaningless() {
        RideController c = new RideController(1);
        assertFalse(c.requestDispatch(), "automatic rides dispatch themselves");
        c.setDispatchMode(DispatchMode.MANUAL);
        c.setState(State.CLOSED);
        assertFalse(c.requestDispatch(), "a closed ride dispatches nothing");
        c.setState(State.OPEN);
        c.emergencyStop();
        assertFalse(c.requestDispatch(), "nor does a stopped one");
    }

    @Test
    @DisplayName("an emergency stop brings a running train to rest and holds it there")
    void emergencyStopHaltsARunningTrain() {
        Ride ride = new Ride();
        // Out of the station and well up the lift before the stop.
        ride.run(20 * 10);
        double before = ride.train.reference().distance();
        assertFalse(ride.inStation());

        ride.controller.emergencyStop();
        ride.run(20 * 10);
        assertEquals(0.0D, ride.train.velocity(), 0.05D, "stopped");
        assertTrue(ride.train.isRunning(), "held by the stop, not latched as a stall: "
            + ride.train.status());
        double held = ride.train.reference().distance();
        ride.run(20 * 10);
        assertEquals(held, ride.train.reference().distance(), 0.1D, "and it stays put");
        assertTrue(held >= before - 1.0D, "stopped where it was, not rolled back down the lift");
        assertEquals(State.CLOSED, ride.controller.state(), "an e-stop closes the ride");
    }

    @Test
    @DisplayName("after an emergency stop the ride needs a reset and an open before it runs again")
    void recoveringFromAnEmergencyStop() {
        Ride ride = new Ride();
        ride.run(20 * 10);
        ride.controller.emergencyStop();
        ride.run(20 * 5);
        double stopped = ride.train.reference().distance();

        ride.controller.resetEmergency();
        ride.run(20 * 2);
        assertFalse(ride.controller.ridersMayBoard(), "still closed after the reset");

        ride.controller.setState(State.OPEN);
        ride.run(20 * 10);
        assertTrue(ride.train.reference().distance() > stopped + 5.0D,
            "released, the lift carries the train on again");
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("open air gates hold dispatch until they have been shut a while, and a DISPATCH press waits for them")
    void airGatesHoldDispatch() {
        RideController ride = new RideController(1);
        ride.holdForGates(3);
        org.junit.jupiter.api.Assertions.assertFalse(ride.mayDispatch(), "gates open: nothing leaves");
        ride.tickGates();
        ride.tickGates();
        org.junit.jupiter.api.Assertions.assertFalse(ride.mayDispatch(), "just shut: still waiting");
        ride.tickGates();
        org.junit.jupiter.api.Assertions.assertTrue(ride.mayDispatch(), "shut long enough: it may go");

        ride.setDispatchMode(RideController.DispatchMode.MANUAL);
        ride.requestDispatch();
        ride.holdForGates(1);
        org.junit.jupiter.api.Assertions.assertFalse(ride.mayDispatch());
        ride.tickGates();
        org.junit.jupiter.api.Assertions.assertTrue(ride.mayDispatch(),
            "the press made while the gates were open still sends the train once they shut");
    }
}
