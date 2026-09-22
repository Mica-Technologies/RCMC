package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An undo restores the authored transit state — stations, lines, signals — as a fresh
 * {@link TransitSystem} read from a snapshot, and snapshots hold no services, because a service is
 * runtime state. Found in review: any {@code /rcmc undo}, even of an unrelated track edit, left every
 * train on the network out of service. The running services have to be carried across.
 */
class AdoptServicesTest {

    private static final double TICK = 1.0D / 20.0D;

    private static TrackNetwork straight() {
        List<TrackNode> nodes = new ArrayList<>();
        for (int x = 0; x <= 240; x += 60) {
            nodes.add(new TrackNode(new Vec3(x, 64.0D, 0.0D)));
        }
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, nodes, false, null));
        return network;
    }

    /** The authored state as a snapshot would restore it: stations and lines, no services. */
    private static TransitSystem authored(boolean withLine) {
        TransitSystem transit = new TransitSystem();
        TransitStation a = new TransitStation("Airfield", new TrackRef(1, 40.0D));
        TransitStation b = new TransitStation("Docklands", new TrackRef(1, 120.0D));
        TransitStation c = new TransitStation("Parkway", new TrackRef(1, 200.0D));
        transit.addStation(a);
        transit.addStation(b);
        transit.addStation(c);
        if (withLine) {
            transit.addLine(new TransitLine("Airport", Arrays.asList(a, b, c), false));
        }
        return transit;
    }

    private static Train movingTrain(double distance, double velocity) {
        return new Train(TrainSpec.metroTrain(3), new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, distance), velocity);
    }

    @Test
    @DisplayName("running services survive a restore of the authored state")
    void servicesSurviveARestore() {
        TrackNetwork network = straight();
        TransitSystem before = authored(true);
        TrainManager trains = new TrainManager();
        Train train = movingTrain(150.0D, -12.0D);
        trains.add(7, train);
        LineService running = before.enterService(7, train, network, "Airport",
            TransitDrives.metro(18.0D, TICK));

        TransitSystem after = authored(true);
        int adopted = after.adoptServices(before, trains, network, TICK);

        assertEquals(1, adopted);
        LineService carried = after.serviceFor(7);
        assertNotNull(carried, "the train must still be in service after the restore");
        assertEquals("Airport", carried.line().name());
        assertEquals(18.0D, carried.controller().cruiseSpeed(), 1e-9,
            "the operator's cruise speed is part of the service");
        assertEquals(running.facing(), carried.facing(),
            "the train keeps going the way it was going");
        assertEquals(running.currentStopIndex(), carried.currentStopIndex(),
            "and to the station it was running to");
    }

    @Test
    @DisplayName("a service whose line the restore removed is dropped, leaving the train parked")
    void serviceOnARemovedLineIsDropped() {
        TrackNetwork network = straight();
        TransitSystem before = authored(true);
        TrainManager trains = new TrainManager();
        Train train = movingTrain(90.0D, 0.0D);
        trains.add(3, train);
        before.enterService(3, train, network, "Airport", TransitDrives.metro(15.0D, TICK));

        TransitSystem after = authored(false);
        assertEquals(0, after.adoptServices(before, trains, network, TICK));
        assertNull(after.serviceFor(3), "there is no line left to serve");
    }

    @Test
    @DisplayName("a service whose train is gone is not carried across")
    void serviceWithoutItsTrainIsDropped() {
        TrackNetwork network = straight();
        TransitSystem before = authored(true);
        TrainManager trains = new TrainManager();
        Train train = movingTrain(90.0D, 0.0D);
        trains.add(3, train);
        before.enterService(3, train, network, "Airport", TransitDrives.metro(15.0D, TICK));
        trains.remove(3);

        TransitSystem after = authored(true);
        assertEquals(0, after.adoptServices(before, trains, network, TICK));
        assertNull(after.serviceFor(3));
    }
}
