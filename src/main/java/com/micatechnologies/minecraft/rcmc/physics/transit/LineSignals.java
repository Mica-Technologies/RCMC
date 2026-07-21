package com.micatechnologies.minecraft.rcmc.physics.transit;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.block.BlockSection;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackWalk;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Metro signalling as <b>movement authority</b>: how far each train is permitted to travel
 * before it would reach a block occupied by another train.
 *
 * <p>This is the same one-train-per-{@link BlockSection} safety idea as the coaster
 * {@code BlockSystem}, delivered the way modern ATO/ATP delivers it. The coaster system is
 * itself the brake — an {@code ExternalAcceleration} that slams a block brake on whatever
 * approaches a red boundary. A metro already has a driver with a stopping curve, so signalling
 * only needs to tell it <em>where its permission ends</em>: the authority feeds
 * {@code TransitStopController.acceleration} as one more distance limit, the driver brakes
 * against whichever limit is nearer, and a train held at a red signal is just a train berthed at
 * a boundary with its doors shut, proceeding the moment the authority extends. One braking law,
 * not two fighting each other.</p>
 *
 * <p><b>Direction-free by construction.</b> Rather than assuming blocks are traversed in list
 * order (the coaster system's model — right for a one-way circuit, wrong for a metro that runs
 * both ways over the same track), the authority for a train is computed by walking the track in
 * the train's own facing and measuring to the nearer boundary of <em>every</em> block occupied
 * by another train ({@link TrackWalk}); the nearest such boundary, minus a margin, is the
 * authority. A block behind the train is simply unreachable within the horizon and drops out
 * naturally — or, on a loop, is correctly found the long way around. This also makes
 * bidirectional single track safe by default: an occupied block ahead limits the authority no
 * matter which way its occupant is pointing.</p>
 *
 * <p><b>Same two-phase contract as {@code BlockSystem}, for the same reason:</b> call
 * {@link #updateOccupancy} once per tick before any {@link #authorityFor} query, so every
 * train's answer is a function of the tick's starting state rather than of iteration order.
 * Occupancy is by lead-car reference, with the same block-sizing caveat documented there: blocks
 * must be comfortably longer than the longest train.</p>
 *
 * <p>Pure Java, deterministic, per-line. Signal aspects for M7 signage fall out of
 * {@link #isBlockOccupied}/{@link #occupantOf}.</p>
 */
public final class LineSignals {

    /** How far short of an occupied block's boundary the authority ends, blocks. */
    public static final double DEFAULT_MARGIN = 0.5D;

    private final List<BlockSection> blocks;
    private final double margin;
    private final double horizon;

    /** trainId -> index of the block its lead reference is literally inside; fresh each update. */
    private final Map<Integer, Integer> occupancy = new LinkedHashMap<>();

    /**
     * @param blocks  the line's block sections, in any order — see the class javadoc for why
     *                order deliberately does not matter here
     * @param margin  how far short of an occupied block's boundary the authority ends — must be
     *                {@code >= 0}
     * @param horizon how far ahead, in blocks, occupancy is looked for; beyond it the authority
     *                is unlimited. Bounds the per-train cost and should comfortably exceed the
     *                longest braking distance on the line.
     */
    public LineSignals(List<BlockSection> blocks, double margin, double horizon) {
        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalArgumentException("a signalled line needs at least one block");
        }
        if (margin < 0.0D) {
            throw new IllegalArgumentException("margin must be >= 0, got " + margin);
        }
        if (horizon <= 0.0D) {
            throw new IllegalArgumentException("horizon must be positive, got " + horizon);
        }
        this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
        this.margin = margin;
        this.horizon = horizon;
    }

    /**
     * Recomputes, from scratch, which block each train's lead reference is inside. Once per tick,
     * before any {@link #authorityFor} call — see the class javadoc.
     */
    public void updateOccupancy(TrainManager trains) {
        occupancy.clear();
        for (Map.Entry<Integer, Train> entry : trains.asMap().entrySet()) {
            TrackRef ref = entry.getValue().reference();
            for (int i = 0; i < blocks.size(); i++) {
                if (blocks.get(i).contains(ref)) {
                    occupancy.put(entry.getKey(), i);
                    break;
                }
            }
        }
    }

    /**
     * Movement authority for {@code trainId}: distance it may travel along {@code facing} before
     * the boundary of the nearest block occupied by another train, minus the margin — or
     * {@link Double#POSITIVE_INFINITY} when nothing within the horizon limits it. Never negative:
     * a train that finds itself at (or somehow past) a red boundary gets zero, i.e. "stop now",
     * not a demand to reverse.
     */
    public double authorityFor(int trainId, Train train, TrackNetwork network, double facing) {
        double nearest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < blocks.size(); i++) {
            if (!isOccupiedByOther(i, trainId)) {
                continue;
            }
            BlockSection block = blocks.get(i);
            double toStart = TrackWalk.distanceTo(network, train.reference(), facing,
                new TrackRef(block.sectionId(), block.startDistance()), horizon);
            double toEnd = TrackWalk.distanceTo(network, train.reference(), facing,
                new TrackRef(block.sectionId(), block.endDistance()), horizon);
            nearest = Math.min(nearest, Math.min(toStart, toEnd));
        }
        if (Double.isInfinite(nearest)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0D, nearest - margin);
    }

    private boolean isOccupiedByOther(int blockIndex, int excludingTrainId) {
        for (Map.Entry<Integer, Integer> entry : occupancy.entrySet()) {
            if (entry.getKey() != excludingTrainId && entry.getValue() == blockIndex) {
                return true;
            }
        }
        return false;
    }

    /** Whether any train's lead reference is currently inside block {@code blockIndex}. */
    public boolean isBlockOccupied(int blockIndex) {
        return occupancy.containsValue(blockIndex);
    }

    /** The train occupying block {@code blockIndex}, or {@code -1} — an M7 board's raw data. */
    public int occupantOf(int blockIndex) {
        for (Map.Entry<Integer, Integer> entry : occupancy.entrySet()) {
            if (entry.getValue() == blockIndex) {
                return entry.getKey();
            }
        }
        return -1;
    }

    /** The block {@code trainId}'s lead reference is inside, or {@code -1}. */
    public int blockIndexOf(int trainId) {
        Integer index = occupancy.get(trainId);
        return index == null ? -1 : index;
    }

    public List<BlockSection> blocks() {
        return blocks;
    }

    public double margin() {
        return margin;
    }

    public double horizon() {
        return horizon;
    }

    @Override
    public String toString() {
        return "LineSignals{" + blocks.size() + " blocks, occupied=" + occupancy.size() + '}';
    }
}
