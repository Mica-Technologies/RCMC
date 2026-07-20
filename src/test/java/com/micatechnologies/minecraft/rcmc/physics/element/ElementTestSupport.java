package com.micatechnologies.minecraft.rcmc.physics.element;

import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.Arrays;

/**
 * Shared network/integrator builders for the ride-element tests, mirroring the style of
 * {@code TrainTest} in the parent {@code physics} package.
 */
final class ElementTestSupport {

    static final double GRAVITY = 9.81D;
    static final double TICK = 1.0D / 20.0D;

    private ElementTestSupport() {
    }

    static TrackNode node(double x, double y, double z) {
        return new TrackNode(new Vec3(x, y, z));
    }

    static PhysicsIntegrator frictionless() {
        return new PhysicsIntegrator(GRAVITY, 0.0D, 0.0D, 1000.0D);
    }

    static PhysicsIntegrator realistic() {
        return new PhysicsIntegrator(GRAVITY, 0.01D, 0.0015D, 60.0D);
    }

    /** Flat track along +X, {@code length} blocks. */
    static TrackNetwork flatNetwork(double length) {
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(length / 2.0D, 64, 0), node(length, 64, 0)), false, null));
        return n;
    }

    /** A steep, steady 40-block climb over 60 blocks of run — the same grade {@code TrainTest} uses. */
    static TrackNetwork steepRampNetwork() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(20, 74, 0), node(40, 88, 0), node(60, 104, 0)), false, null));
        return n;
    }

    /** A gentle grade, shallow enough that a low-power drive can still hold it. */
    static TrackNetwork gentleRampNetwork() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(50, 66, 0), node(100, 68, 0), node(150, 70, 0)), false, null));
        return n;
    }

    /** A hill with a crest partway along, then a longer descent below the start height. */
    static TrackNetwork crestNetwork() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, Arrays.asList(
            node(0, 60, 0), node(50, 90, 0), node(100, 110, 0),
            node(150, 90, 0), node(200, 60, 0), node(250, 30, 0)), false, null));
        return n;
    }
}
