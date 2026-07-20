package com.micatechnologies.minecraft.rcmc.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class TrainTest {

    private static final double GRAVITY = 9.81D;
    private static final double TICK = 1.0D / 20.0D;

    private static TrackNode node(double x, double y, double z) {
        return new TrackNode(new Vec3(x, y, z));
    }

    private static PhysicsIntegrator frictionless() {
        return new PhysicsIntegrator(GRAVITY, 0.0D, 0.0D, 1000.0D);
    }

    private static PhysicsIntegrator realistic() {
        return new PhysicsIntegrator(GRAVITY, 0.01D, 0.0015D, 60.0D);
    }

    /** Flat track along +X, 200 blocks. */
    private static TrackNetwork flatNetwork() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(100, 64, 0), node(200, 64, 0)), false, null));
        return n;
    }

    /** A drop: 40 blocks down over 100 of run, then 100 flat. */
    private static TrackNetwork dropNetwork() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, Arrays.asList(
            node(0, 104, 0), node(30, 100, 0), node(70, 76, 0),
            node(100, 64, 0), node(150, 64, 0), node(200, 64, 0)), false, null));
        return n;
    }

    private static TrackNetwork ringNetwork(double radius) {
        List<TrackNode> ring = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double a = 2.0D * Math.PI * i / 12;
            ring.add(node(Math.cos(a) * radius, 64.0D, Math.sin(a) * radius));
        }
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, ring, true, null));
        return n;
    }

    @Test
    @DisplayName("a train gains speed down a drop and matches sqrt(2gh)")
    void trainAcceleratesDownDrop() {
        TrackNetwork n = dropNetwork();
        Train train = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 0.0D), 0.5D);

        double startHeight = n.frameAt(train.reference()).position.y;
        double peakSpeed = 0.0D;
        double heightAtPeak = startHeight;
        for (int tick = 0; tick < 400 && train.isRunning(); tick++) {
            train.tick(n, 0.0D, 4, TICK);
            if (train.speed() > peakSpeed) {
                peakSpeed = train.speed();
                heightAtPeak = n.frameAt(train.reference()).position.y;
            }
        }

        double expected = Math.sqrt(2.0D * GRAVITY * (startHeight - heightAtPeak));
        assertEquals(expected, peakSpeed, expected * 0.05D,
            "peak speed " + peakSpeed + " should match sqrt(2gh) = " + expected);
    }

    @Test
    @DisplayName("a long train crests a hill differently from a short one")
    void trainLengthChangesHillBehaviour() {
        // The headline reason gravity is averaged over the whole train rather than sampled at the
        // lead car. If this test ever starts passing with equal results, the averaging is gone.
        TrackNetwork n = dropNetwork();

        Train shortTrain = new Train(new TrainSpec(1, 3.0D, 0.5D, 4),
            frictionless(), new TrackRef(1, 20.0D), 2.0D);
        Train longTrain = new Train(new TrainSpec(8, 3.0D, 0.5D, 4),
            frictionless(), new TrackRef(1, 20.0D), 2.0D);

        for (int tick = 0; tick < 40; tick++) {
            shortTrain.tick(n, 0.0D, 4, TICK);
            longTrain.tick(n, 0.0D, 4, TICK);
        }

        assertNotEquals(shortTrain.velocity(), longTrain.velocity(), 1e-6,
            "train length had no effect — whole-train gravity averaging is not working");
    }

    @Test
    @DisplayName("every car sits behind the one in front by the spec's pitch")
    void carsAreSpacedAlongTheTrack() {
        TrackNetwork n = flatNetwork();
        TrainSpec spec = new TrainSpec(5, 3.0D, 0.5D, 4);
        Train train = new Train(spec, frictionless(), new TrackRef(1, 100.0D), 0.0D);

        for (int i = 1; i < spec.carCount(); i++) {
            double ahead = train.refOfCar(n, i - 1).distance();
            double behind = train.refOfCar(n, i).distance();
            assertEquals(spec.carPitch(), ahead - behind, 1e-6, "spacing between cars " + (i - 1) + " and " + i);
        }
    }

    @Test
    @DisplayName("cars stay spaced correctly when the train straddles a section join")
    void carsSpanSectionJoins() {
        // A long train genuinely straddles boundaries; if refOfCar() didn't traverse the network,
        // trailing cars would pile up at the start of the section instead.
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(25, 64, 0), node(50, 64, 0)), false, null));
        n.addSection(new TrackSection(2, Arrays.asList(
            node(50, 64, 0), node(75, 64, 0), node(100, 64, 0)), false, null));
        n.connect(new TrackNetwork.SectionEnd(1, TrackNetwork.End.END),
                  new TrackNetwork.SectionEnd(2, TrackNetwork.End.START));

        TrainSpec spec = new TrainSpec(6, 3.0D, 0.5D, 4);
        // Reference just past the join, so the rear of the train is still on section 1.
        Train train = new Train(spec, frictionless(), new TrackRef(2, 5.0D), 0.0D);

        assertEquals(2, train.refOfCar(n, 0).sectionId());
        assertEquals(1, train.refOfCar(n, spec.carCount() - 1).sectionId(),
            "the rear car should still be on the first section");

        // Positions in world space must remain evenly spaced across the join.
        for (int i = 1; i < spec.carCount(); i++) {
            double gap = train.frameOfCar(n, i - 1).position.distanceTo(train.frameOfCar(n, i).position);
            assertEquals(spec.carPitch(), gap, 0.05D, "world-space gap between cars " + (i - 1) + " and " + i);
        }
    }

    @Test
    @DisplayName("a train laps a closed circuit indefinitely")
    void trainLapsCircuit() {
        TrackNetwork n = ringNetwork(50.0D);
        Train train = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 0.0D), 20.0D);

        for (int tick = 0; tick < 600; tick++) {
            train.tick(n, 0.0D, 4, TICK);
        }
        assertTrue(train.isRunning(), "train should still be running, got " + train.status());
        assertEquals(20.0D, train.speed(), 0.5D, "a flat frictionless circuit should hold speed");
    }

    @Test
    @DisplayName("running off unconnected track reports a dead end rather than stopping silently")
    void deadEndIsReported() {
        TrackNetwork n = flatNetwork();
        Train train = new Train(TrainSpec.singleCar(), frictionless(),
            new TrackRef(1, n.section(1).totalLength() - 5.0D), 20.0D);

        for (int tick = 0; tick < 40 && train.isRunning(); tick++) {
            train.tick(n, 0.0D, 4, TICK);
        }
        assertEquals(Train.Status.DEAD_END, train.status());
        assertEquals(0.0D, train.velocity(), 1e-9);
    }

    @Test
    @DisplayName("a train that runs out of speed on flat track is flagged as valleyed")
    void valleyingIsDetected() {
        // A stuck train must be visible as a fault. Presenting as a hang is the failure mode.
        //
        // Uses a closed circuit deliberately: coasting from 3 blocks/s under realistic drag takes
        // roughly 300 blocks to stop, so on a 200-block open section the train reaches the far end
        // and reports DEAD_END first — a different fault, and not the one under test.
        TrackNetwork n = ringNetwork(50.0D);
        Train train = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 10.0D), 3.0D);

        for (int tick = 0; tick < 20_000 && train.isRunning(); tick++) {
            train.tick(n, 0.0D, 4, TICK);
        }
        assertEquals(Train.Status.VALLEYED, train.status(),
            "expected the train to be flagged valleyed, got " + train.status()
                + " at v=" + train.velocity());
    }

    @Test
    @DisplayName("a chain lift pulls a train up a grade it could not climb alone")
    void chainLiftClimbs() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(20, 74, 0), node(40, 88, 0), node(60, 104, 0)), false, null));
        Train train = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), 0.0D);

        for (int tick = 0; tick < 400 && train.isRunning(); tick++) {
            train.tick(n, GRAVITY, 4, TICK);
        }
        assertTrue(n.frameAt(train.reference()).position.y > 90.0D,
            "lift failed to climb; train at y=" + n.frameAt(train.reference()).position.y);
    }

    @Test
    @DisplayName("simulation is deterministic — identical inputs give identical results")
    void simulationIsDeterministic() {
        // Client-side prediction depends on this exactly. Any randomness, world lookup or
        // wall-clock reference introduced into the physics path breaks it, and the symptom is
        // rubber-banding that looks like a network fault rather than a physics one.
        TrackNetwork n = dropNetwork();

        Train a = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), 1.0D);
        Train b = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 0.0D), 1.0D);
        for (int tick = 0; tick < 300; tick++) {
            a.tick(n, 0.0D, 4, TICK);
            b.tick(n, 0.0D, 4, TICK);
        }

        assertEquals(a.reference().distance(), b.reference().distance(), 0.0D,
            "bit-identical distance required");
        assertEquals(a.velocity(), b.velocity(), 0.0D, "bit-identical velocity required");
    }

    @Test
    @DisplayName("sub-step count changes accuracy but not gross behaviour")
    void subSteppingConverges() {
        TrackNetwork n = dropNetwork();
        Train coarse = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 0.0D), 1.0D);
        Train fine = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 0.0D), 1.0D);

        for (int tick = 0; tick < 100; tick++) {
            coarse.tick(n, 0.0D, 1, TICK);
            fine.tick(n, 0.0D, 16, TICK);
        }
        assertEquals(fine.velocity(), coarse.velocity(), fine.velocity() * 0.05D,
            "1 and 16 sub-steps should broadly agree on a smooth drop");
    }

    @Test
    @DisplayName("state() carries only the numbers the wire protocol needs")
    void stateIsTheWirePayload() {
        TrackNetwork n = flatNetwork();
        Train train = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 42.0D), 7.5D);
        TrainState state = train.state();
        assertEquals(42.0D, state.distance, 1e-9);
        assertEquals(7.5D, state.velocity, 1e-9);
    }
}
