package com.micatechnologies.minecraft.rcmc.physics.block;

import com.micatechnologies.minecraft.rcmc.physics.element.BrakeRun;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Divides a coaster circuit into blocks at its hardware, the way a real ride is signalled.
 *
 * <p>A block ends where a train can be held safely and on purpose: the end of a block brake, the
 * end of the station, the top of the lift. Dividing the circuit into equal lengths instead — the
 * original {@code /rcmc block N} — puts boundaries wherever the arithmetic lands, so a train could
 * be stopped mid-drop or halfway through an inversion by a brake that is not there.</p>
 *
 * <p>The blocks run boundary to boundary round the circuit. The one that contains the seam wraps
 * through it ({@link BlockSection#wrapping}), and is listed last, which is where
 * {@link BlockSystem} expects the ring to close.</p>
 */
public final class BlockLayout {

    /** Boundaries closer than this are one boundary: two pieces of hardware back to back. */
    static final double MERGE_DISTANCE = 1.0D;

    private BlockLayout() {
    }

    /** Where blocks end on {@code sectionId}, from its hardware, in ascending distance. */
    public static List<Double> boundaries(int sectionId, List<RideElement> elements) {
        TreeSet<Double> ends = new TreeSet<>();
        for (RideElement element : elements) {
            if (element.sectionId() != sectionId) {
                continue;
            }
            boolean holdsTrains = element instanceof StationPlatform || element instanceof ChainLift
                || (element instanceof BrakeRun && ((BrakeRun) element).mode() == BrakeRun.Mode.BLOCK);
            if (holdsTrains) {
                ends.add(element.endDistance());
            }
        }
        List<Double> merged = new ArrayList<>();
        for (double end : ends) {
            if (merged.isEmpty() || end - merged.get(merged.size() - 1) >= MERGE_DISTANCE) {
                merged.add(end);
            }
        }
        return merged;
    }

    /**
     * The blocks of a closed circuit {@code length} long, one per boundary; empty if there are
     * fewer than two boundaries, which is not enough to separate two trains.
     */
    public static List<BlockSection> forCircuit(int sectionId, double length, List<Double> boundaries) {
        List<BlockSection> blocks = new ArrayList<>();
        int count = boundaries.size();
        if (count < 2) {
            return blocks;
        }
        for (int i = 0; i + 1 < count; i++) {
            blocks.add(new BlockSection("b" + (i + 1), sectionId, boundaries.get(i),
                boundaries.get(i + 1)));
        }
        double last = boundaries.get(count - 1);
        double first = boundaries.get(0);
        if (last >= length - 1.0e-9D) {
            // The last boundary is the seam itself: the closing block needs no wrap.
            blocks.add(new BlockSection("b" + count, sectionId, 0.0D, first));
        }
        else {
            blocks.add(BlockSection.wrapping("b" + count, sectionId, last, first));
        }
        return blocks;
    }
}
