package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.micatechnologies.minecraft.rcmc.track.storage.TransitCodec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A line's dwell and headway are what its trains actually do.
 *
 * <p>Before these existed, dwell was fixed at ten seconds for every line, and nothing stopped two
 * trains leaving a platform nose to tail — once a line bunched it stayed bunched.</p>
 */
class LineOperationsTest {

    private static final double TICK = 1.0D / 20.0D;

    private static TrackNetwork ring() {
        List<TrackNode> nodes = new ArrayList<>();
        for (double[] p : new double[][] {{0, 0}, {200, 0}, {200, 200}, {0, 200}}) {
            nodes.add(new TrackNode(new Vec3(p[0], 64.0D, p[1])));
        }
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, nodes, true, null));
        return network;
    }

    private static TransitSystem line(TrackNetwork network) {
        double length = network.section(1).totalLength();
        TransitSystem transit = new TransitSystem();
        TransitStation a = new TransitStation("A", new TrackRef(1, 100.0D));
        TransitStation b = new TransitStation("B", new TrackRef(1, length * 0.6D));
        transit.addStation(a);
        transit.addStation(b);
        transit.addLine(new TransitLine("Ring", Arrays.asList(a, b), true));
        return transit;
    }

    private static Train trainAt(double distance) {
        return new Train(TrainSpec.metroTrain(1), new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, distance), 0.0D);
    }

    /** Ticks at which each train left station A (boarding over, doors starting to close). */
    private static long[] departuresFromA(LineOperations operations) {
        TrackNetwork network = ring();
        TransitSystem transit = line(network);
        transit.setOperations("Ring", operations);
        TrainManager trains = new TrainManager();
        trains.add(1, trainAt(85.0D));
        trains.add(2, trainAt(60.0D));
        LineService first = transit.enterService(1, trains.train(1), network, "Ring",
            TransitDrives.metro(10.0D, TICK));
        LineService second = transit.enterService(2, trains.train(2), network, "Ring",
            TransitDrives.metro(10.0D, TICK));
        long[] departed = {-1, -1};
        LineService[] services = {first, second};
        TransitStopController.Phase[] previous = {first.controller().phase(), second.controller().phase()};
        for (long t = 0; t < 20 * 180 && (departed[0] < 0 || departed[1] < 0); t++) {
            transit.beginTick(trains, network);
            trains.tick(network, transit.composedWith(null), 4, TICK);
            for (int i = 0; i < 2; i++) {
                TransitStopController.Phase phase = services[i].controller().phase();
                if (departed[i] < 0 && phase == TransitStopController.Phase.DOORS_CLOSING
                    && previous[i] == TransitStopController.Phase.BOARDING
                    && services[i].currentStopIndex() == 0) {
                    departed[i] = t;
                }
                previous[i] = phase;
            }
        }
        assertTrue(departed[0] >= 0 && departed[1] >= 0, "both trains left A: "
            + Arrays.toString(departed));
        return departed;
    }

    @Test
    @DisplayName("a train that has caught up is held until the headway has passed")
    void headwayHoldsTheSecondTrain() {
        int headway = 20 * 45;
        long[] unregulated = departuresFromA(new LineOperations(TransitDrives.METRO_DWELL_TICKS, 0));
        assertTrue(Math.abs(unregulated[1] - unregulated[0]) < headway,
            "the scenario must bunch without regulation, or it proves nothing: "
                + Arrays.toString(unregulated));

        long[] regulated = departuresFromA(new LineOperations(TransitDrives.METRO_DWELL_TICKS, headway));
        assertTrue(Math.abs(regulated[1] - regulated[0]) >= headway,
            "second departure only " + Math.abs(regulated[1] - regulated[0]) + " ticks after the first");
    }

    @Test
    @DisplayName("the doors stay open for the line's dwell")
    void dwellIsTheLines() {
        TrackNetwork network = ring();
        TransitSystem transit = line(network);
        transit.setOperations("Ring", new LineOperations(40, 0));
        TrainManager trains = new TrainManager();
        trains.add(1, trainAt(85.0D));
        LineService service = transit.enterService(1, trains.train(1), network, "Ring",
            TransitDrives.metro(10.0D, TICK));
        int boarding = 0;
        for (int t = 0; t < 20 * 60; t++) {
            transit.beginTick(trains, network);
            trains.tick(network, transit.composedWith(null), 4, TICK);
            if (service.controller().phase() == TransitStopController.Phase.BOARDING) {
                boarding++;
            }
            else if (boarding > 0) {
                break;
            }
        }
        assertEquals(40, boarding, "ticks with the doors open for boarding");
    }

    @Test
    @DisplayName("dwell and headway are saved with the line")
    void savedWithTheLine() {
        TransitSystem transit = line(ring());
        LineOperations set = new LineOperations(120, 600);
        transit.setOperations("ring", set);
        NBTTagCompound root = new NBTTagCompound();
        TransitCodec.write(transit, root);
        assertEquals(set, TransitCodec.read(root).operationsFor("Ring"));
    }
}
