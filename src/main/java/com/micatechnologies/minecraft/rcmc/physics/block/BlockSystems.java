package com.micatechnologies.minecraft.rcmc.physics.block;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The block systems of a whole park, one per track section, presented as a single
 * {@link TrainManager.ExternalAcceleration}.
 *
 * <p>{@link BlockSystem} is deliberately scoped to one circuit: its blocks are an ordered ring,
 * "next block" is defined by that order, and its closed-circuit wrap correction resolves distances
 * against a single {@code TrackSection}. That is the right scope for the safety logic and the wrong
 * scope for a world, which has as many coasters in it as the builder cares to make. This class is
 * the join between the two.</p>
 *
 * <p>Routing is by the train's <em>current</em> section rather than the section it was signalled on
 * last tick, so a train that crosses onto a section with no blocks defined simply stops being
 * signalled — which is the correct behaviour for unsignalled track, and the same thing that happens
 * to a park with no blocks at all.</p>
 *
 * <p>Pure Java like the rest of {@code physics}: no Minecraft types, so a multi-coaster park's
 * signalling is testable without a game instance.</p>
 */
public final class BlockSystems implements TrainManager.ExternalAcceleration {

    /** Insertion-ordered so {@code /rcmc info} lists coasters in the order they were signalled. */
    private final Map<Integer, BlockSystem> bySection = new LinkedHashMap<>();

    /** Installs (or replaces) the block system governing one track section. */
    public void put(int sectionId, BlockSystem system) {
        if (system == null) {
            throw new IllegalArgumentException("system must not be null");
        }
        bySection.put(sectionId, system);
    }

    /** Removes a section's signalling, returning it to unsignalled operation. */
    public BlockSystem remove(int sectionId) {
        return bySection.remove(sectionId);
    }

    public BlockSystem get(int sectionId) {
        return bySection.get(sectionId);
    }

    public Collection<BlockSystem> all() {
        return bySection.values();
    }

    public java.util.Set<Integer> sectionIds() {
        return bySection.keySet();
    }

    public boolean isEmpty() {
        return bySection.isEmpty();
    }

    public void clear() {
        bySection.clear();
    }

    /**
     * Refreshes every section's occupancy. Must be called once per tick <em>before</em> the trains
     * are advanced, for the reason {@link BlockSystem#updateOccupancy} gives: every train's hold
     * decision in a tick has to read one consistent snapshot rather than depend on iteration order.
     */
    public void updateOccupancy(TrainManager trains, TrackNetwork network) {
        for (BlockSystem system : bySection.values()) {
            system.updateOccupancy(trains, network);
        }
    }

    /**
     * Routes to the system owning the section this train is on. A train on unsignalled track gets
     * zero, meaning "no block brake is asking for anything" — not "stop".
     */
    @Override
    public double forTrain(int trainId, Train train) {
        BlockSystem system = systemFor(train);
        return system == null ? 0.0D : system.forTrain(trainId, train);
    }

    @Override
    public boolean isHolding(int trainId, Train train) {
        BlockSystem system = systemFor(train);
        return system != null && system.isHolding(trainId, train);
    }

    private BlockSystem systemFor(Train train) {
        return train == null || train.reference() == null
            ? null
            : bySection.get(train.reference().sectionId());
    }

    /** True if any section is currently reporting a collision. */
    public boolean hasCollision() {
        for (BlockSystem system : bySection.values()) {
            if (system.hasCollision()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "BlockSystems" + bySection.keySet();
    }
}
