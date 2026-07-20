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
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Runs the demo coaster the way the game does, and asserts it actually completes a circuit.
 *
 * <p>Written after a real failure: the demo dispatched a train from the station, failed to get it
 * up the lift hill, and rolled it back. The cause was that the chain lift element was anchored to
 * the first <em>climbing</em> node rather than to the platform exit — the first quarter of the hill
 * had no chain on it, so the train had to climb on dispatch momentum alone. Every unit test still
 * passed, because each part was individually correct; nothing tested them <em>together</em>.</p>
 *
 * <p>That is what this file is for. It builds the demo exactly as {@code /rcmc demo} does, places
 * the same hardware at the same spans, and simulates a train from a standing start in the station.
 * A layout that cannot complete a lap is a broken layout regardless of how well its pieces test.</p>
 */
class DemoCoasterRunTest {

    private static final double TICK = RcmcConstants.SECONDS_PER_TICK;

    /** Mirrors CommandRcmc.buildDemo — if that changes, this must change with it. */
    private static Harness buildDemoAsCommandDoes() {
        DemoCoaster.Result demo = DemoCoaster.build(1, new Vec3(0, 64, 0), 1.0D, 34.0D);
        TrackNetwork network = new TrackNetwork();
        network.addSection(demo.section);

        RideElementSet elements = new RideElementSet();
        elements.add(new StationPlatform(1, demo.stationStart, demo.stationEnd,
            demo.stationStop, 6.0D, 60, 4.0D, 6.0D, TICK));
        elements.add(new ChainLift(1, demo.liftStart, demo.liftEnd, 5.0D, 12.0D, TICK));
        elements.add(new BrakeRun(1, demo.brakeStart, demo.brakeEnd,
            6.0D, 6.0D, BrakeRun.Mode.TRIM, TICK));

        Train train = new Train(new TrainSpec(5, 3.0D, 0.5D, 4),
            new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, demo.stationStop), 0.0D);

        TrainManager manager = new TrainManager();
        manager.add(1, train);
        return new Harness(demo, network, elements, manager, train);
    }

    private static final class Harness {
        final DemoCoaster.Result demo;
        final TrackNetwork network;
        final RideElementSet elements;
        final TrainManager manager;
        final Train train;

        Harness(DemoCoaster.Result demo, TrackNetwork network, RideElementSet elements,
                TrainManager manager, Train train) {
            this.demo = demo;
            this.network = network;
            this.elements = elements;
            this.manager = manager;
            this.train = train;
        }

        void tick() {
            manager.tick(network, elements, 4, TICK);
        }
    }

    @Test
    @DisplayName("the demo dispatches from the station and crests the lift hill")
    void trainCrestsTheLift() {
        // The exact failure that was reported: the train left the station, could not climb, and
        // rolled back.
        Harness h = buildDemoAsCommandDoes();
        double crestHeight = h.network.frameAt(new TrackRef(1, h.demo.liftEnd)).position.y;

        double highest = Double.NEGATIVE_INFINITY;
        for (int tick = 0; tick < 2000; tick++) {
            h.tick();
            highest = Math.max(highest, h.network.frameAt(h.train.reference()).position.y);
        }

        assertTrue(highest >= crestHeight - 1.0D,
            "train never reached the lift crest at y=" + crestHeight + "; got only y=" + highest
                + " (status " + h.train.status() + ")");
    }

    @Test
    @DisplayName("the chain covers the track from the platform exit onward — no unpowered gap")
    void liftStartsAtThePlatformExit() {
        // Guards the specific mistake directly, not just its symptom: any gap between where the
        // station's authority ends and the chain's begins is a stretch the train must climb on
        // momentum, and on a real lift grade it cannot.
        DemoCoaster.Result demo = DemoCoaster.build(1, new Vec3(0, 64, 0), 1.0D, 34.0D);
        assertEquals(demo.stationEnd, demo.liftStart, 1e-9,
            "the lift must begin exactly where the platform ends");
        assertTrue(demo.liftEnd > demo.liftStart, "lift span must be non-empty");
    }

    @Test
    @DisplayName("a train parked at the stop point dwells and then dispatches")
    void stationDispatchesAStandingTrain() {
        // A station brakes an arriving train but never pushes one, so a train spawned even
        // slightly short of the stop point would sit forever. Spawning exactly on it is what makes
        // the demo self-starting.
        Harness h = buildDemoAsCommandDoes();
        assertEquals(0.0D, h.train.velocity(), 1e-9, "should start at rest");

        for (int tick = 0; tick < 200; tick++) {
            h.tick();
        }
        assertTrue(h.train.speed() > 1.0D,
            "station never dispatched the train; still at " + h.train.speed() + " blocks/s");
    }

    @Test
    @DisplayName("the demo completes a full lap and returns to the station")
    void trainCompletesALap() {
        Harness h = buildDemoAsCommandDoes();
        double length = h.demo.section.totalLength();

        // Track cumulative travel rather than raw distance, which wraps on a closed circuit.
        double travelled = 0.0D;
        double previous = h.train.reference().distance();
        for (int tick = 0; tick < 6000 && travelled < length; tick++) {
            h.tick();
            double now = h.train.reference().distance();
            double step = now - previous;
            if (step < -length * 0.5D) {
                step += length;   // wrapped past the start/finish line
            }
            travelled += step;
            previous = now;
        }

        assertTrue(travelled >= length,
            "train covered only " + String.format("%.1f", travelled) + " of "
                + String.format("%.1f", length) + " blocks (status " + h.train.status() + ")");
        assertTrue(h.train.isRunning(), "train faulted during the lap: " + h.train.status());
    }

    @Test
    @DisplayName("the first drop produces a respectable top speed")
    void firstDropIsExciting() {
        // A demo that technically completes a lap but crawls is not demonstrating anything. This
        // also catches a lift or brake mistuned so aggressively that it flattens the ride.
        Harness h = buildDemoAsCommandDoes();
        double peak = 0.0D;
        for (int tick = 0; tick < 3000; tick++) {
            h.tick();
            peak = Math.max(peak, h.train.speed());
        }
        assertTrue(peak > 15.0D,
            "peak speed was only " + String.format("%.1f", peak) + " blocks/s on a 34-block drop");
    }
}
