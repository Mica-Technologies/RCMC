package com.micatechnologies.minecraft.rcmc.physics.block;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;

/**
 * Composes a {@link RideElementSet} (lifts, launches, brakes, stations) with a {@link BlockSystem}
 * (multi-train safety) into the single {@link TrainManager.ExternalAcceleration} a real park needs
 * — most parks have both, and {@link TrainManager#tick} only accepts one.
 *
 * <p><b>The block system wins, but only on the ticks it actually has something to say.</b>
 * {@link BlockSystem#forTrain} returns exactly {@code 0.0} whenever the train does not currently
 * need braking to hold at a boundary — including the entire time it is simply cruising through a
 * block whose next block happens to be occupied but is still far away; see
 * {@code BlockSystem#brakeTowardBoundary}. That is what makes "prefer the block system's answer
 * whenever it is nonzero" safe rather than a bug: a chain lift or launch under a train keeps
 * driving it normally right up until the block system actually needs to intervene, at which point
 * the brake — correctly — overrides the lift, exactly as a real block brake overrides a chain dog
 * or launch motor. An earlier, simpler design that deferred to the block system whenever
 * {@link BlockSystem#isHolding} was true (rather than whenever its acceleration was nonzero) would
 * have silently killed lift and launch power for the entire approach to every occupied block, since
 * {@code isHolding} is deliberately eager — see its javadoc — long before any braking is actually
 * needed.</p>
 */
public final class BlockSignaledElementSet implements TrainManager.ExternalAcceleration {

    private final RideElementSet elements;

    /**
     * Typed as the interface rather than {@link BlockSystem} so this composes equally with a single
     * circuit's signalling and with a whole park's ({@link BlockSystems}). The composition rule
     * below is the same either way.
     */
    private final TrainManager.ExternalAcceleration blocks;

    public BlockSignaledElementSet(RideElementSet elements, BlockSystem blocks) {
        this(elements, (TrainManager.ExternalAcceleration) blocks);
    }

    public BlockSignaledElementSet(RideElementSet elements, BlockSystems blocks) {
        this(elements, (TrainManager.ExternalAcceleration) blocks);
    }

    private BlockSignaledElementSet(RideElementSet elements,
                                    TrainManager.ExternalAcceleration blocks) {
        if (elements == null || blocks == null) {
            throw new IllegalArgumentException("elements and blocks are both required");
        }
        this.elements = elements;
        this.blocks = blocks;
    }

    @Override
    public double forTrain(int trainId, Train train) {
        double blockAcceleration = blocks.forTrain(trainId, train);
        if (blockAcceleration != 0.0D) {
            return blockAcceleration;
        }
        return elements.forTrain(trainId, train);
    }

    /**
     * {@inheritDoc}
     *
     * <p>True if either side would hold the train — a station dwell and a block hold are not
     * mutually exclusive (a train can be sitting in a station whose exit block happens to also be
     * occupied), and {@code Train}'s valleying check only needs to know that <em>something</em> is
     * deliberately in control, not which.</p>
     */
    @Override
    public boolean isHolding(int trainId, Train train) {
        return blocks.isHolding(trainId, train) || elements.isHolding(trainId, train);
    }

    public RideElementSet elements() {
        return elements;
    }

    /** The signalling layer, whether one circuit's or a whole park's. */
    public TrainManager.ExternalAcceleration signalling() {
        return blocks;
    }

    @Override
    public String toString() {
        return "BlockSignaledElementSet{" + elements + ", " + blocks + '}';
    }
}
