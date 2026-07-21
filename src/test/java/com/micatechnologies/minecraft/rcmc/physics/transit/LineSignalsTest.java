package com.micatechnologies.minecraft.rcmc.physics.transit;

import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.TICK;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.flatNetwork;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.metroDriver;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.realistic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.block.BlockSection;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LineSignalsTest {

    private static final int LEADER = 1;
    private static final int FOLLOWER = 2;

    private static LineSignals signals() {
        return new LineSignals(Arrays.asList(
            new BlockSection("block-1", 1, 300.0D, 400.0D),
            new BlockSection("block-2", 1, 500.0D, 600.0D)), 1.0D, 2000.0D);
    }

    private static LineService serviceTo(double stationDistance) {
        TransitLine line = new TransitLine("Signalled Line", Arrays.asList(
            new TransitStation("Target", new TrackRef(1, stationDistance)),
            new TransitStation("Beyond", new TrackRef(1, stationDistance + 150.0D))), false);
        TransitStopController controller = new TransitStopController(
            metroDriver(), TransitTestSupport.LINE_SPEED, 0.5D, 5, 10, 5);
        return new LineService(line, controller, 0, 1, 1.0D);
    }

    /** One signalled tick for the follower, exactly as the ride-control wiring will run it. */
    private static void tickSignalled(TrainManager trains, TrackNetwork network,
                                      LineSignals signals, LineService service) {
        Train follower = trains.train(FOLLOWER);
        signals.updateOccupancy(trains);
        double authority = signals.authorityFor(FOLLOWER, follower, network, service.facing());
        double acceleration = service.tick(follower, network, authority);
        follower.setHeld(service.isHolding(follower));
        follower.tick(network, acceleration, 4, TICK);
    }

    @Test
    @DisplayName("a follower is held short of a block occupied by the train ahead")
    void followerHeldShortOfOccupiedBlock() {
        TrackNetwork flat = flatNetwork(1000.0D);
        TrainManager trains = new TrainManager();
        // Leader parked inside block-2; never ticked — it is scenery for this test.
        trains.add(LEADER, new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 550.0D), 0.0D));
        trains.add(FOLLOWER, new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), 0.0D));
        LineSignals signals = signals();
        LineService service = serviceTo(800.0D);

        for (int i = 0; i < 2000; i++) {
            tickSignalled(trains, flat, signals, service);
        }
        Train follower = trains.train(FOLLOWER);
        assertTrue(follower.speed() <= 0.06D, "expected the follower held at rest at the red signal");
        assertTrue(follower.reference().distance() < 500.0D,
            "the follower must never enter the occupied block, but reached "
                + follower.reference().distance());
        assertTrue(follower.reference().distance() > 480.0D,
            "expected the follower to have closed up to the authority boundary");
        assertEquals(TransitStopController.Phase.APPROACHING, service.controller().phase(),
            "a train held at a signal must not open its doors in the tunnel");
        assertTrue(follower.isRunning(), "a signal hold is held intent, never a valleyed fault");
    }

    @Test
    @DisplayName("when the block ahead clears, the authority extends and the follower proceeds to its station")
    void followerProceedsWhenBlockClears() {
        TrackNetwork flat = flatNetwork(1000.0D);
        TrainManager trains = new TrainManager();
        trains.add(LEADER, new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 550.0D), 0.0D));
        trains.add(FOLLOWER, new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), 0.0D));
        LineSignals signals = signals();
        LineService service = serviceTo(800.0D);

        for (int i = 0; i < 2000; i++) {
            tickSignalled(trains, flat, signals, service);
        }
        assertTrue(trains.train(FOLLOWER).speed() <= 0.06D, "precondition: follower held at the red");

        // The leader departs beyond the blocks (and beyond the follower's station).
        trains.train(LEADER).setState(new TrackRef(1, 960.0D), 0.0D);

        int guard = 0;
        while (service.controller().stopsServed() == 0 && guard++ < 4000) {
            tickSignalled(trains, flat, signals, service);
        }
        assertTrue(guard < 4000, "expected the follower to proceed once the block cleared");
        assertEquals(800.0D, trains.train(FOLLOWER).reference().distance(), 1.0D,
            "expected the follower to run through the cleared block and berth at its station");
    }

    @Test
    @DisplayName("with no other trains anywhere ahead, the authority is unlimited")
    void authorityUnlimitedWhenAlone() {
        TrackNetwork flat = flatNetwork(1000.0D);
        TrainManager trains = new TrainManager();
        Train alone = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), 0.0D);
        trains.add(FOLLOWER, alone);
        LineSignals signals = signals();

        signals.updateOccupancy(trains);
        assertTrue(Double.isInfinite(signals.authorityFor(FOLLOWER, alone, flat, 1.0D)));
    }

    @Test
    @DisplayName("a train inside its own block is not limited by itself")
    void ownBlockDoesNotLimit() {
        TrackNetwork flat = flatNetwork(1000.0D);
        TrainManager trains = new TrainManager();
        Train inBlock = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 350.0D), 0.0D);
        trains.add(FOLLOWER, inBlock);
        LineSignals signals = signals();

        signals.updateOccupancy(trains);
        assertTrue(Double.isInfinite(signals.authorityFor(FOLLOWER, inBlock, flat, 1.0D)));
        assertEquals(0, signals.blockIndexOf(FOLLOWER));
    }
}
