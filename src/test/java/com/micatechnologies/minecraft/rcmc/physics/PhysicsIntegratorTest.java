package com.micatechnologies.minecraft.rcmc.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.math.ArcLengthTable;
import com.micatechnologies.minecraft.rcmc.track.math.CatmullRomSpline;
import com.micatechnologies.minecraft.rcmc.track.math.ParallelTransportFrames;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhysicsIntegratorTest {

    private static final double GRAVITY = 9.81D;

    /** A first drop: 40 blocks down over 60 blocks of run, then flat. */
    private static ParallelTransportFrames dropTrack() {
        List<Vec3> pts = Arrays.asList(
            new Vec3(0, 40, 0),
            new Vec3(20, 38, 0),
            new Vec3(40, 20, 0),
            new Vec3(60, 2, 0),
            new Vec3(80, 0, 0),
            new Vec3(120, 0, 0));
        return new ParallelTransportFrames(
            new ArcLengthTable(CatmullRomSpline.withPhantomEndpoints(pts)), 512);
    }

    private static PhysicsIntegrator frictionless() {
        return new PhysicsIntegrator(GRAVITY, 0.0D, 0.0D, 1000.0D);
    }

    @Test
    @DisplayName("frictionless track conserves energy")
    void conservesEnergyWithoutFriction() {
        // This is the integrator's defining invariant, and the reason the scheme is
        // semi-implicit (symplectic) Euler rather than explicit Euler: explicit Euler would
        // gain energy every step and the train would accelerate out of nowhere.
        ParallelTransportFrames track = dropTrack();
        PhysicsIntegrator integrator = frictionless();

        TrainState state = new TrainState(0.0D, 0.5D);
        double initialEnergy = integrator.specificEnergy(state, track);

        double dt = 1.0D / 80.0D;
        for (int i = 0; i < 4000 && state.distance < track.totalLength() - 1.0D; i++) {
            state = integrator.step(state, track, 0.0D, dt);
        }

        double finalEnergy = integrator.specificEnergy(state, track);
        assertEquals(initialEnergy, finalEnergy, initialEnergy * 0.02D,
            "energy drifted from " + initialEnergy + " to " + finalEnergy);
    }

    @Test
    @DisplayName("speed at the bottom of a drop matches v = sqrt(2gh)")
    void dropSpeedMatchesTextbookValue() {
        ParallelTransportFrames track = dropTrack();
        PhysicsIntegrator integrator = frictionless();

        TrainState state = new TrainState(0.0D, 0.1D);
        double startHeight = track.frameAtDistance(0.0D).position.y;

        double dt = 1.0D / 80.0D;
        double bestSpeed = 0.0D;
        double heightAtBest = startHeight;
        for (int i = 0; i < 4000 && state.distance < track.totalLength() - 1.0D; i++) {
            state = integrator.step(state, track, 0.0D, dt);
            if (Math.abs(state.velocity) > bestSpeed) {
                bestSpeed = Math.abs(state.velocity);
                heightAtBest = track.frameAtDistance(state.distance).position.y;
            }
        }

        double expected = Math.sqrt(2.0D * GRAVITY * (startHeight - heightAtBest));
        assertEquals(expected, bestSpeed, expected * 0.03D,
            "peak speed " + bestSpeed + " does not match sqrt(2gh) = " + expected);
    }

    @Test
    @DisplayName("drag brings a train to rest on level track")
    void dragDecelerates() {
        List<Vec3> flat = Arrays.asList(
            new Vec3(0, 10, 0), new Vec3(50, 10, 0), new Vec3(100, 10, 0), new Vec3(150, 10, 0));
        ParallelTransportFrames track = new ParallelTransportFrames(
            new ArcLengthTable(CatmullRomSpline.withPhantomEndpoints(flat)), 128);
        PhysicsIntegrator integrator = new PhysicsIntegrator(GRAVITY, 0.05D, 0.01D, 100.0D);

        TrainState state = new TrainState(0.0D, 30.0D);
        for (int i = 0; i < 20_000; i++) {
            state = integrator.step(state, track, 0.0D, 1.0D / 80.0D);
        }
        assertTrue(state.velocity < 1.0D,
            "train should have slowed to a crawl, still doing " + state.velocity);
        assertTrue(state.velocity >= 0.0D, "drag reversed the train: " + state.velocity);
    }

    @Test
    @DisplayName("external acceleration models a chain lift pulling a train uphill")
    void chainLiftPullsUphill() {
        List<Vec3> hill = Arrays.asList(
            new Vec3(0, 0, 0), new Vec3(20, 10, 0), new Vec3(40, 25, 0), new Vec3(60, 40, 0));
        ParallelTransportFrames track = new ParallelTransportFrames(
            new ArcLengthTable(CatmullRomSpline.withPhantomEndpoints(hill)), 256);
        PhysicsIntegrator integrator = new PhysicsIntegrator(GRAVITY, 0.01D, 0.001D, 100.0D);

        TrainState state = new TrainState(0.0D, 0.0D);
        // A real chain lift holds a constant slow speed; here we just assert that enough
        // external acceleration to beat gravity does in fact move the train up the hill.
        for (int i = 0; i < 4000; i++) {
            state = integrator.step(state, track, GRAVITY, 1.0D / 80.0D);
        }
        assertTrue(track.frameAtDistance(state.distance).position.y > 20.0D,
            "chain lift failed to climb; train at y="
                + track.frameAtDistance(state.distance).position.y);
    }
}
