package com.micatechnologies.minecraft.rcmc.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.micatechnologies.minecraft.rcmc.physics.ride.RideControlLayer;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideController;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideControllers;
import com.micatechnologies.minecraft.rcmc.physics.ride.Rollbacks;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Anti-rollback: a train falling back down a lift is caught, held, and the ride stops.
 *
 * <p>Without it the chain caught the train itself, carried it up and over the top again, and it
 * failed the same hill again, round and round for as long as anyone let it.</p>
 */
class LiftRollbackTest {

    private static final double TICK = RcmcConstants.SECONDS_PER_TICK;

    private static final class Ride {
        final DemoCoaster.Result demo = DemoCoaster.build(1, new Vec3(0, 64, 0), 1.0D, 34.0D);
        final TrackNetwork network = new TrackNetwork();
        final RideElementSet elements = new RideElementSet();
        final RideControllers rides = new RideControllers();
        final TrainManager trains = new TrainManager();

        Ride() {
            network.addSection(demo.section);
            elements.add(new StationPlatform(1, demo.stationStart, demo.stationEnd, demo.stationStop,
                6.0D, 60, 4.0D, 6.0D, TICK));
            elements.add(new ChainLift(1, demo.liftStart, demo.liftEnd, 5.0D, 12.0D, TICK));
            elements.add(new BrakeRun(1, demo.brakeStart, demo.brakeEnd, 6.0D, 6.0D,
                BrakeRun.Mode.TRIM, TICK));
        }

        /** One tick as the world runs it: e-stop layer over the hardware, then the rollback check. */
        void tick() {
            trains.tick(network, new RideControlLayer(elements, rides, network, TICK), 4, TICK);
            for (int section : Rollbacks.sectionsRollingBack(trains.asMap(), elements)) {
                RideController ride = rides.getOrCreate(section);
                if (!ride.isEmergencyStopped()) {
                    ride.emergencyStop(RideController.StopCause.ROLLBACK);
                }
            }
        }
    }

    @Test
    @DisplayName("a train falling back over the crest is caught on the lift, held, and the ride stops")
    void rollbackIsCaught() {
        Ride ride = new Ride();
        // Just over the top, falling back the way it came.
        ride.trains.add(1, new Train(new TrainSpec(5, 3.0D, 0.5D, 4),
            new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, ride.demo.liftEnd + 6.0D), -3.0D));
        for (int t = 0; t < 20 * 30; t++) {
            ride.tick();
        }
        RideController controller = ride.rides.get(1);
        assertTrue(controller != null && controller.isEmergencyStopped(), "the ride stopped");
        assertEquals(RideController.StopCause.ROLLBACK, controller.stopCause());
        Train train = ride.trains.train(1);
        double caught = train.reference().distance();
        assertTrue(caught > ride.demo.liftStart && caught <= ride.demo.liftEnd + 6.0D,
            "caught on the lift, not at the bottom of it: s=" + caught);
        for (int t = 0; t < 20 * 30; t++) {
            ride.tick();
        }
        assertEquals(caught, train.reference().distance(), 0.05D, "and held there");
    }

    @Test
    @DisplayName("an ordinary lap never trips it")
    void normalLapIsNotARollback() {
        Ride ride = new Ride();
        ride.trains.add(1, new Train(new TrainSpec(5, 3.0D, 0.5D, 4),
            new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, ride.demo.stationStop), 0.0D));
        for (int t = 0; t < 20 * 300; t++) {
            ride.tick();
            assertTrue(ride.rides.get(1) == null || !ride.rides.get(1).isEmergencyStopped(),
                "stopped for rollback at s=" + ride.trains.train(1).reference().distance());
        }
    }
}
