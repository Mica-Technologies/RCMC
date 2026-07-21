package com.micatechnologies.minecraft.rcmc.physics.block;

import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shared network/integrator builders and the two-phase tick helper for the block tests, mirroring
 * the style of {@code ElementTestSupport} in the sibling {@code physics.element} test package.
 */
final class BlockTestSupport {

    static final double GRAVITY = 9.81D;
    static final double TICK = 1.0D / 20.0D;
    static final int SUB_STEPS = 4;
    static final int SECTION_ID = 1;

    private BlockTestSupport() {
    }

    static TrackNode node(double x, double y, double z) {
        return new TrackNode(new Vec3(x, y, z));
    }

    static PhysicsIntegrator frictionless() {
        return new PhysicsIntegrator(GRAVITY, 0.0D, 0.0D, 1000.0D);
    }

    /** Flat track along +X, {@code length} blocks, open (not a circuit). */
    static TrackNetwork flatNetwork(double length) {
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(SECTION_ID, Arrays.asList(
            node(0, 64, 0), node(length / 2.0D, 64, 0), node(length, 64, 0)), false, null));
        return n;
    }

    /** A flat, closed ring of the given radius — the same shape {@code TrainTest} rings use. */
    static TrackNetwork ringNetwork(double radius) {
        List<TrackNode> ring = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            double a = 2.0D * Math.PI * i / 24;
            ring.add(node(Math.cos(a) * radius, 64.0D, Math.sin(a) * radius));
        }
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(SECTION_ID, ring, true, null));
        return n;
    }

    /**
     * Advances every train in {@code trains} by one tick under {@code blocks}, in the two-phase
     * order {@link BlockSystem}'s javadoc requires: a full occupancy snapshot first, then the tick
     * itself, so every train's hold decision this tick is based on the same simultaneous state.
     */
    static void tick(TrainManager trains, TrackNetwork network, BlockSystem blocks) {
        blocks.updateOccupancy(trains, network);
        trains.tick(network, blocks, SUB_STEPS, TICK);
    }
}
