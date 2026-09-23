package com.micatechnologies.minecraft.rcmc.physics.ride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Found in review: train #1 ran straight through train #2 standing at s=462 on the demo. */
class TrainCollisionsTest {

    /** A 5-car coaster train is 5 x 3.0 + 4 x 0.5 = 17 blocks long. */
    private static final TrainSpec FIVE_CARS = new TrainSpec(5, 3.0D, 0.5D, 4);

    private static TrackNetwork ring() {
        List<TrackNode> nodes = new ArrayList<>();
        for (double[] p : new double[][] {{0, 0}, {100, 0}, {100, 100}, {0, 100}}) {
            nodes.add(new TrackNode(new Vec3(p[0], 64, p[1])));
        }
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, nodes, true, null));
        return network;
    }

    private static Train at(double distance, TrainSpec spec) {
        return new Train(spec, new PhysicsIntegrator(9.81D, 0.0D, 0.0D, 60.0D),
            new TrackRef(1, distance), 0.0D);
    }

    private static Map<Integer, Train> trains(Train... all) {
        Map<Integer, Train> map = new LinkedHashMap<>();
        for (int i = 0; i < all.length; i++) {
            map.put(i + 1, all[i]);
        }
        return map;
    }

    @Test
    @DisplayName("two trains on top of each other collide")
    void overlappingTrainsCollide() {
        assertEquals(Collections.singleton(1), TrainCollisions.sectionsWithCollisions(
            trains(at(200.0D, FIVE_CARS), at(190.0D, FIVE_CARS)), ring()));
    }

    @Test
    @DisplayName("trains a clear stretch apart do not")
    void separatedTrainsDoNot() {
        assertTrue(TrainCollisions.sectionsWithCollisions(
            trains(at(200.0D, FIVE_CARS), at(150.0D, FIVE_CARS)), ring()).isEmpty());
    }

    @Test
    @DisplayName("a train straddling a circuit's seam collides with one just past it")
    void collisionAcrossTheSeam() {
        TrackNetwork network = ring();
        // The first train's tail wraps back past zero to the end of the circuit.
        double length = network.section(1).totalLength();
        assertEquals(Collections.singleton(1), TrainCollisions.sectionsWithCollisions(
            trains(at(5.0D, FIVE_CARS), at(length - 2.0D, FIVE_CARS)), network));
    }

    @Test
    @DisplayName("metro trains are left to their own signalling")
    void metroTrainsAreNotChecked() {
        assertTrue(TrainCollisions.sectionsWithCollisions(
            trains(at(200.0D, TrainSpec.metroTrain(3)), at(195.0D, TrainSpec.metroTrain(3))),
            ring()).isEmpty());
    }
}
