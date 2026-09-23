package com.micatechnologies.minecraft.rcmc.physics.transit;

import com.micatechnologies.minecraft.rcmc.physics.block.BlockSection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Editing a line's signal blocks one boundary at a time — what placing a signal with the transit
 * tool does.
 *
 * <p>A line's blocks on one track section are held as the boundaries between them: the section's
 * two ends, plus a signal wherever one was placed. Adding a signal splits the block it lands in;
 * removing one joins the two blocks it separated; removing the last leaves the section unsignalled.
 * Blocks on other sections are passed through untouched.</p>
 *
 * <p>{@code /rcmc line signals <name> <count>} could only divide a section into equal blocks, which
 * puts boundaries wherever the arithmetic lands — mid-platform, or just past one, where a held train
 * blocks the station behind it. A real layout puts them where a train can sensibly wait: on the
 * approach to a platform, clear of the one before.</p>
 */
public final class SignalLayout {

    /**
     * The shortest block a signal may leave, in blocks. Occupancy is read off a train's lead car,
     * so a block much shorter than a train is one it can be in and out of at once; this only stops
     * the obvious mistakes (a double click, a signal on top of another) — see
     * {@link #shortestBlock}, which the tool warns with against the trains actually running.
     */
    public static final double MIN_BLOCK = 8.0D;

    private SignalLayout() {
    }

    /**
     * The blocks with a signal added at {@code at} on {@code sectionId}, or {@code null} if it
     * would leave a block shorter than {@link #MIN_BLOCK} (including a signal where one already is).
     *
     * @param blocks        the line's blocks now, possibly empty
     * @param sectionLength that section's length, for its first block
     */
    public static List<BlockSection> withSignal(List<BlockSection> blocks, int sectionId,
                                                double sectionLength, double at) {
        List<Double> cuts = boundaries(blocks, sectionId, sectionLength);
        for (double cut : cuts) {
            if (Math.abs(cut - at) < MIN_BLOCK) {
                return null;
            }
        }
        cuts.add(at);
        Collections.sort(cuts);
        return rebuilt(blocks, sectionId, cuts);
    }

    /**
     * The blocks with the signal nearest {@code at} on {@code sectionId} taken out, or {@code null}
     * if there is none within {@code radius}. The section's two ends are not signals and are never
     * removed; taking the last signal out leaves the section with no blocks at all.
     */
    public static List<BlockSection> withoutSignal(List<BlockSection> blocks, int sectionId,
                                                   double at, double radius) {
        double length = sectionEnd(blocks, sectionId);
        List<Double> cuts = boundaries(blocks, sectionId, length);
        int nearest = -1;
        for (int i = 1; i < cuts.size() - 1; i++) {
            if (Math.abs(cuts.get(i) - at) <= radius
                && (nearest < 0 || Math.abs(cuts.get(i) - at) < Math.abs(cuts.get(nearest) - at))) {
                nearest = i;
            }
        }
        if (nearest < 0) {
            return null;
        }
        cuts.remove(nearest);
        return rebuilt(blocks, sectionId, cuts);
    }

    /** Where the signals on {@code sectionId} stand: the inner boundaries, not the section's ends. */
    public static List<Double> signals(List<BlockSection> blocks, int sectionId) {
        List<Double> cuts = boundaries(blocks, sectionId, sectionEnd(blocks, sectionId));
        return cuts.size() <= 2 ? new ArrayList<>() : new ArrayList<>(cuts.subList(1, cuts.size() - 1));
    }

    /** The shortest block on {@code sectionId}, or infinity if it has none. */
    public static double shortestBlock(List<BlockSection> blocks, int sectionId) {
        double shortest = Double.POSITIVE_INFINITY;
        for (BlockSection block : blocks) {
            if (block.sectionId() == sectionId) {
                shortest = Math.min(shortest, block.length());
            }
        }
        return shortest;
    }

    /**
     * The sorted boundaries of the blocks on {@code sectionId}, ends included. A section with no
     * blocks yet is one block from 0 to {@code sectionLength}, so its boundaries are just those.
     */
    private static List<Double> boundaries(List<BlockSection> blocks, int sectionId, double sectionLength) {
        Map<Long, Double> cuts = new TreeMap<>();
        boolean any = false;
        for (BlockSection block : blocks) {
            if (block.sectionId() != sectionId) {
                continue;
            }
            any = true;
            cuts.put(key(block.startDistance()), block.startDistance());
            cuts.put(key(block.endDistance()), block.endDistance());
        }
        if (!any) {
            cuts.put(key(0.0D), 0.0D);
            cuts.put(key(sectionLength), sectionLength);
        }
        return new ArrayList<>(cuts.values());
    }

    private static double sectionEnd(List<BlockSection> blocks, int sectionId) {
        double end = 0.0D;
        for (BlockSection block : blocks) {
            if (block.sectionId() == sectionId) {
                end = Math.max(end, block.endDistance());
            }
        }
        return end;
    }

    /** Boundaries a hair apart are the same boundary: one block's end is the next one's start. */
    private static long key(double distance) {
        return Math.round(distance * 1000.0D);
    }

    /** The other sections' blocks as they were, and this section's rebuilt from {@code cuts}. */
    private static List<BlockSection> rebuilt(List<BlockSection> blocks, int sectionId, List<Double> cuts) {
        List<BlockSection> out = new ArrayList<>();
        for (BlockSection block : blocks) {
            if (block.sectionId() != sectionId) {
                out.add(block);
            }
        }
        if (cuts.size() > 2) {
            for (int i = 0; i + 1 < cuts.size(); i++) {
                out.add(new BlockSection("s" + sectionId + "-b" + (i + 1), sectionId, cuts.get(i), cuts.get(i + 1)));
            }
        }
        return out;
    }
}
