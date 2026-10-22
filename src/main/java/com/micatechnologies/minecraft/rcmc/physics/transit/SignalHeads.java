package com.micatechnologies.minecraft.rcmc.physics.transit;

import com.micatechnologies.minecraft.rcmc.physics.block.BlockSection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * The logic behind a physical signal head linked to a line's block signalling. Pure Java, so the
 * aspect rules are unit-testable on a bare JVM; the tile entity only feeds it live state.
 *
 * <p><b>A head stands at a block boundary and faces one direction of travel.</b> A
 * {@link LineSignals} line is direction-free, so every boundary is really two signals back to
 * back: the one a train running with the track (increasing distance) sees as it enters a block at
 * its start, and the one a train running against it sees as it enters at its end. A head is bound
 * to exactly one of them by position: the section, the distance along it and the direction of
 * travel. Binding to a position rather than a block index keeps a head honest when the line is
 * re-signalled with a different block count: it finds no block starting at its position any more
 * and shows red until it is relinked, instead of silently protecting some other block.</p>
 *
 * <p>The aspect is three-aspect, as the classic rule goes: red when the block the head protects is
 * occupied, yellow when that block is clear but the one beyond it is occupied, green otherwise.</p>
 */
public final class SignalHeads {

    /** How close two distances must be to count as the same boundary, in blocks. */
    static final double TOLERANCE = 1.0E-3D;

    private SignalHeads() {
    }

    /** One end of a block, seen by trains travelling in {@link #direction} (+1 or -1). */
    public static final class Boundary {
        public final int sectionId;
        public final double distance;
        public final int direction;
        /** Index, in the line's block list, of the block a train passing here enters. */
        public final int blockIndex;

        public Boundary(int sectionId, double distance, int direction, int blockIndex) {
            this.sectionId = sectionId;
            this.distance = distance;
            this.direction = direction >= 0 ? 1 : -1;
            this.blockIndex = blockIndex;
        }
    }

    /**
     * Every signal position on a line: each block's start, seen by trains running with the track,
     * and its end, seen by trains running against it.
     */
    public static List<Boundary> boundaries(List<BlockSection> blocks) {
        List<Boundary> out = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            BlockSection block = blocks.get(i);
            out.add(new Boundary(block.sectionId(), block.startDistance(), 1, i));
            out.add(new Boundary(block.sectionId(), block.endDistance(), -1, i));
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * The block a head at {@code (sectionId, distance)} facing trains travelling in
     * {@code direction} protects (the block such a train enters there), or {@code -1} when no
     * block of the line begins at that point in that direction.
     */
    public static int protectedBlock(List<BlockSection> blocks, int sectionId, double distance,
                                     int direction) {
        for (int i = 0; i < blocks.size(); i++) {
            BlockSection block = blocks.get(i);
            if (block.sectionId() != sectionId) {
                continue;
            }
            double entry = direction >= 0 ? block.startDistance() : block.endDistance();
            if (Math.abs(entry - distance) <= TOLERANCE) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The block a train travelling in {@code direction} enters after leaving block {@code index},
     * or {@code -1} if none of the line's blocks follows it on the same section.
     *
     * @param sectionLength length of the block's section, used only when {@code closed}
     * @param closed        whether the section is a ring, so that its end joins its start
     */
    public static int nextBlock(List<BlockSection> blocks, int index, int direction,
                                double sectionLength, boolean closed) {
        if (index < 0 || index >= blocks.size()) {
            return -1;
        }
        BlockSection from = blocks.get(index);
        double exit = direction >= 0 ? from.endDistance() : from.startDistance();
        int next = protectedBlock(blocks, from.sectionId(), exit, direction);
        if (next < 0 && closed) {
            // On a ring the seam is one point with two names: leaving at the end is entering at 0.
            if (direction >= 0 && Math.abs(exit - sectionLength) <= TOLERANCE) {
                next = protectedBlock(blocks, from.sectionId(), 0.0D, direction);
            } else if (direction < 0 && Math.abs(exit) <= TOLERANCE) {
                next = protectedBlock(blocks, from.sectionId(), sectionLength, direction);
            }
        }
        return next == index ? -1 : next;
    }

    /**
     * The aspect of a head protecting block {@code index} for trains travelling in
     * {@code direction}. An index of {@code -1} (a head whose boundary no longer exists) is red:
     * a signal that does not know what it protects must not show clear.
     *
     * @param occupied whether a block index currently holds a train
     */
    public static SignalAspect aspect(List<BlockSection> blocks, IntPredicate occupied, int index,
                                      int direction, double sectionLength, boolean closed) {
        if (index < 0 || index >= blocks.size() || occupied.test(index)) {
            return SignalAspect.RED;
        }
        int next = nextBlock(blocks, index, direction, sectionLength, closed);
        if (next >= 0 && occupied.test(next)) {
            return SignalAspect.YELLOW;
        }
        return SignalAspect.GREEN;
    }
}
