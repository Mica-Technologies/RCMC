package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What the line control desk does to a line: hold a train at a platform, and find room for a new one. */
class LineOperatorControlsTest {

    private static final double TICK = 1.0D / 20.0D;

    private final TrackNetwork network = new TrackNetwork();
    private final TransitSystem transit = new TransitSystem();
    private final TrainManager trains = new TrainManager();
    private final TransitLine line;

    LineOperatorControlsTest() {
        network.addSection(new TrackSection(1, Arrays.asList(new TrackNode(new Vec3(0, 64, 0)),
            new TrackNode(new Vec3(300, 64, 0)), new TrackNode(new Vec3(600, 64, 0))), false, null));
        TransitStation a = new TransitStation("A", new TrackRef(1, 100.0D));
        TransitStation b = new TransitStation("B", new TrackRef(1, 300.0D));
        TransitStation c = new TransitStation("C", new TrackRef(1, 500.0D));
        transit.addStation(a);
        transit.addStation(b);
        transit.addStation(c);
        line = new TransitLine("Line", Arrays.asList(a, b, c), false, "IN", "OUT");
        transit.addLine(line);
    }

    private Train put(int id, double at) {
        Train train = new Train(TrainSpec.metroTrain(3), new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, at), 0.0D);
        trains.add(id, train);
        return train;
    }

    private void run(int ticks) {
        for (int t = 0; t < ticks; t++) {
            transit.beginTick(trains, network);
            trains.tick(network, transit.composedWith(null), 4, TICK);
        }
    }

    @Test
    @DisplayName("a held train boards at its platform and stays there, doors open, until released")
    void holdAndRelease() {
        Train train = put(1, 200.0D);
        transit.enterService(1, train, network, "Line", TransitDrives.metro(15.0D, TICK));
        assertTrue(transit.setHeld(1, true));
        run(20 * 60);
        LineService service = transit.serviceFor(1);
        assertEquals(TransitStopController.Phase.BOARDING, service.controller().phase(), "waiting at a platform");
        assertTrue(service.controller().doorsOpen(), "with its doors open");
        double at = train.reference().distance();
        run(20 * 30);
        assertEquals(at, train.reference().distance(), 0.01D, "and it has not moved");

        transit.setHeld(1, false);
        run(20 * 20);
        assertTrue(Math.abs(train.reference().distance() - at) > 10.0D, "released, it goes on");
        assertFalse(transit.setHeld(99, true), "only a train in service can be held");
    }

    @Test
    @DisplayName("a new train goes to a platform with clear rails, never on top of another train")
    void freeBerth() {
        double length = TrainSpec.metroTrain(3).totalLength();
        TransitPlatform first = LineDepot.freeBerth(transit, line, network, trains, length);
        assertNotNull(first);
        assertEquals(100.0D, first.stopPoint().distance(), 0.001D, "the first platform, when all are free");

        put(1, 100.0D);
        TransitPlatform second = LineDepot.freeBerth(transit, line, network, trains, length);
        assertEquals(300.0D, second.stopPoint().distance(), 0.001D, "the next one along, while A is taken");

        // A train standing a little way along from B's stop point still has cars on its platform.
        put(2, 300.0D + LineDepot.CLEARANCE * 0.5D + TrainSpec.metroTrain(3).carLength());
        put(3, 500.0D);
        assertNull(LineDepot.freeBerth(transit, line, network, trains, length), "every platform taken");
    }

    @Test
    @DisplayName("a train standing at a sloping platform holds its brakes: it does not roll away with its doors open")
    void holdsOnASlope() {
        TrackNetwork slope = new TrackNetwork();
        java.util.List<TrackNode> nodes = new java.util.ArrayList<>();
        for (int x = 0; x <= 600; x += 50) {
            nodes.add(new TrackNode(new Vec3(x, 64 + 0.04D * x, 0)));
        }
        slope.addSection(new TrackSection(1, nodes, false, null));
        TransitSystem system = new TransitSystem();
        TransitStation a = new TransitStation("Low", new TrackRef(1, 150.0D));
        TransitStation b = new TransitStation("High", new TrackRef(1, 450.0D));
        system.addStation(a);
        system.addStation(b);
        system.addLine(new TransitLine("Hill", Arrays.asList(a, b), false, "DOWN", "UP"));
        TrainManager fleet = new TrainManager();
        Train train = new Train(TrainSpec.metroTrain(3), new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, 300.0D), 0.0D);
        fleet.add(1, train);
        system.enterService(1, train, slope, "Hill", TransitDrives.metro(15.0D, TICK));
        system.setHeld(1, true);
        for (int t = 0; t < 20 * 60; t++) {
            system.beginTick(fleet, slope);
            fleet.tick(slope, system.composedWith(null), 4, TICK);
        }
        assertEquals(TransitStopController.Phase.BOARDING, system.serviceFor(1).controller().phase());
        double at = train.reference().distance();
        assertEquals(com.micatechnologies.minecraft.rcmc.physics.Train.Status.RUNNING, train.status(),
            "still on the line, not rolled off the end of it");
        assertTrue(Math.abs(at - 150.0D) < 3.0D || Math.abs(at - 450.0D) < 3.0D,
            "standing at a platform, not at s=" + at);
        for (int t = 0; t < 20 * 30; t++) {
            system.beginTick(fleet, slope);
            fleet.tick(slope, system.composedWith(null), 4, TICK);
        }
        assertEquals(at, train.reference().distance(), 0.05D, "held on a 4% grade for 30 s");
    }
}
