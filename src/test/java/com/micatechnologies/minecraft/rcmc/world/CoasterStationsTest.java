package com.micatechnologies.minecraft.rcmc.world;

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
import com.micatechnologies.minecraft.rcmc.physics.ride.RideController;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideControllers;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A coaster is boarded and left in its station, and its air gates keep everyone clear as it goes.
 *
 * <p>Boarding used to be allowed anywhere on the circuit while the ride was open: a guest could
 * climb into a train on the lift hill. And nothing stood between the platform and a train leaving
 * it.</p>
 */
class CoasterStationsTest {

    private static final double TICK = RcmcConstants.SECONDS_PER_TICK;

    private final DemoCoaster.Result demo = DemoCoaster.build(1, new Vec3(0, 64, 0));
    private final TrackNetwork network = new TrackNetwork();
    private final RideElementSet elements = new RideElementSet();
    private final RideControllers rides = new RideControllers();
    private final TrainManager trains = new TrainManager();
    private final StationPlatform station;
    private final RideController ride;

    CoasterStationsTest() {
        network.addSection(demo.section);
        station = new StationPlatform(1, demo.stationStart, demo.stationEnd, demo.stationStop, 6.0D, 60, 4.0D,
            6.0D, TICK);
        elements.add(station);
        elements.add(new ChainLift(1, demo.liftStart, demo.liftEnd, 5.0D, 12.0D, TICK));
        elements.add(new BrakeRun(1, demo.brakeStart, demo.brakeEnd, 6.0D, 6.0D, BrakeRun.Mode.TRIM, TICK));
        ride = rides.getOrCreate(1);
        elements.setDispatchGates(rides::gateFor);
    }

    private Train train(double at, double speed) {
        return new Train(new TrainSpec(5, 3.0D, 0.5D, 4), new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, at), speed);
    }

    /** One tick of the park, with the air gates doing what {@code TileAirGate} does. */
    private boolean tick() {
        boolean open = CoasterStations.gatesOpen(rides, station);
        if (open) {
            ride.holdForGates(CoasterStations.GATE_HOLD);
        }
        ride.tickGates();
        trains.tick(network, elements, 4, TICK);
        return open;
    }

    @Test
    @DisplayName("a train is boarded in its station while it dwells, and not on the lift")
    void boardOnlyInTheStation() {
        trains.add(1, train(demo.stationStop, 0.0D));
        boolean boardedDwelling = false;
        boolean boardedOnLift = false;
        for (int t = 0; t < 20 * 40; t++) {
            tick();
            double at = trains.train(1).reference().distance();
            boolean may = CoasterStations.mayBoard(rides, elements, trains, 1);
            if (station.phase() == StationPlatform.Phase.DWELLING && station.servingTrain() == 1) {
                boardedDwelling |= may;
            }
            if (at > demo.liftStart + 5.0D && at < demo.liftEnd) {
                boardedOnLift |= may;
                assertFalse(CoasterStations.mayLeave(rides, elements, trains, 1),
                    "nobody gets off a train climbing the lift, at " + at);
            }
        }
        assertTrue(boardedDwelling, "a dwelling train can be boarded");
        assertFalse(boardedOnLift, "a train on the lift must not be boarded");
    }

    @Test
    @DisplayName("the gates open for the dwell, shut before the end of it, and the train waits for them")
    void gatesKeepThePlatformClear() {
        trains.add(1, train(demo.stationStop, 0.0D));
        int opened = -1;
        int shut = -1;
        int left = -1;
        for (int t = 0; t < 20 * 20 && left < 0; t++) {
            boolean open = tick();
            if (open && opened < 0) {
                opened = t;
            }
            if (!open && opened >= 0 && shut < 0) {
                shut = t;
            }
            if (station.phase() == StationPlatform.Phase.DISPATCHING && left < 0) {
                left = t;
                assertFalse(open, "the gates are shut when the train moves off");
            }
        }
        assertTrue(opened >= 0, "the gates open for a train at the platform");
        assertTrue(shut > opened, "and shut again");
        assertTrue(left >= shut + CoasterStations.GATES_SHUT_FOR,
            "the train left at tick " + left + ", only " + (left - shut) + " ticks after the gates shut at " + shut);
    }

    @Test
    @DisplayName("an operator's DISPATCH shuts the gates first, and the train waits for them to be shut")
    void manualDispatchWaitsForTheGates() {
        ride.setDispatchMode(RideController.DispatchMode.MANUAL);
        trains.add(1, train(demo.stationStop, 0.0D));
        int pressed = -1;
        int shut = -1;
        int left = -1;
        for (int t = 0; t < 20 * 30 && left < 0; t++) {
            boolean open = tick();
            if (pressed < 0 && open && station.dwellRemaining() == 0) {
                // The dwell has run and the gates are still open, as they stay until DISPATCH.
                ride.requestDispatch();
                pressed = t;
            }
            if (pressed >= 0 && !open && shut < 0) {
                shut = t;
            }
            if (station.phase() == StationPlatform.Phase.DISPATCHING) {
                left = t;
            }
        }
        assertTrue(pressed >= 0, "the gates stay open, waiting for DISPATCH");
        assertTrue(left >= shut + CoasterStations.GATES_SHUT_FOR,
            "pressed at " + pressed + ", gates shut at " + shut + ", but the train left at " + left);
    }

    @Test
    @DisplayName("a train held in the station while the ride was closed loads again when it opens")
    void reopeningGivesTimeToBoard() {
        // Found in game: the held train had served its dwell long before, and left the moment the
        // ride opened — nobody could get on.
        ride.setState(RideController.State.CLOSED);
        trains.add(1, train(demo.stationStop, 0.0D));
        for (int t = 0; t < 200; t++) {
            tick();
        }
        assertTrue(station.phase() == StationPlatform.Phase.DWELLING, "held in the station while closed");
        assertTrue(CoasterStations.gatesOpen(rides, station),
            "a closed ride's gates open, so the riders of the train it holds can get off");
        ride.setState(RideController.State.OPEN);
        int boardable = 0;
        int opened = 0;
        for (int t = 0; t < 20 * 10 && station.phase() == StationPlatform.Phase.DWELLING; t++) {
            if (CoasterStations.mayBoard(rides, elements, trains, 1)) {
                boardable++;
            }
            if (tick()) {
                opened++;
            }
        }
        assertTrue(boardable >= RideController.REOPEN_LOADING_TICKS,
            "boardable for " + boardable + " ticks after opening");
        assertTrue(opened > 0, "the gates opened for the guests");
    }

    @Test
    @DisplayName("riders of a stopped train off the station get off only in an emergency or once it is closed")
    void evacuation() {
        trains.add(1, train(demo.liftEnd + 30.0D, 0.0D));
        assertFalse(CoasterStations.mayBoard(rides, elements, trains, 1), "not in the station");
        assertFalse(CoasterStations.mayLeave(rides, elements, trains, 1), "an open ride's train is not left mid-course");
        ride.emergencyStop();
        assertTrue(CoasterStations.mayLeave(rides, elements, trains, 1), "an emergency stop is an evacuation");
    }

    @Test
    @DisplayName("track with no station at all boards anywhere: a sandbox run")
    void noStationBoardsAnywhere() {
        TrackNetwork bare = new TrackNetwork();
        bare.addSection(new TrackSection(7, Arrays.asList(new TrackNode(new Vec3(0, 64, 0)),
            new TrackNode(new Vec3(40, 64, 0))), false, null));
        TrainManager sandbox = new TrainManager();
        sandbox.add(2, new Train(new TrainSpec(1, 3.0D, 0.5D, 4), new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(7, 10.0D), 0.0D));
        assertTrue(CoasterStations.mayBoard(new RideControllers(), new RideElementSet(), sandbox, 2));
    }
}
