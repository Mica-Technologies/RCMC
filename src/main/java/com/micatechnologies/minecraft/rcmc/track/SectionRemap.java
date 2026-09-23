package com.micatechnologies.minecraft.rcmc.track;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Where a distance along a section ends up after the section's nodes are edited.
 *
 * <p>Everything on a section is placed by distance along it — ride hardware, block sections, a
 * station's stop point, every train. Move a node and every one of those distances past it is now
 * somewhere else on the track, or off the end of it: a lift hill edited a block higher would leave
 * its chain half a span short of the crest. So each edit maps the old distances to new ones, and
 * the caller carries every anchored thing across.</p>
 *
 * <p>The mapping keeps a point's place <em>between the same two nodes</em>: a train a third of the
 * way along a span is a third of the way along it afterwards, however long the span has become.
 * That is what a builder means by "the same place" — hardware tagged on a span stays on that span.
 * An inserted node splits a span in two and its old stretch is shared across both; a removed node
 * merges two spans, each keeping its share of the merged one; removing an open section's end node
 * trims the track, and what stood on the trimmed span is left at the new end.</p>
 */
public final class SectionRemap implements DoubleUnaryOperator {

    /** A run of old track and the run of new track it becomes, as distance intervals. */
    private static final class Group {
        final double oldFrom;
        final double oldTo;
        final double newFrom;
        final double newTo;

        Group(double oldFrom, double oldTo, double newFrom, double newTo) {
            this.oldFrom = oldFrom;
            this.oldTo = oldTo;
            this.newFrom = newFrom;
            this.newTo = newTo;
        }
    }

    private final List<Group> groups;
    private final double newLength;

    private SectionRemap(List<Group> groups, double newLength) {
        this.groups = groups;
        this.newLength = newLength;
    }

    @Override
    public double applyAsDouble(double distance) {
        // Half-open, so a node — the end of one span and the start of the next — belongs to the span
        // it starts. On a circuit the end of the wrap span is the same point as the start of the
        // first, and taken from the wrong side it came out as the lap length rather than zero.
        for (Group g : groups) {
            if (distance >= g.oldFrom - 1.0e-9D && distance < g.oldTo - 1.0e-9D) {
                double span = g.oldTo - g.oldFrom;
                double f = span <= 1.0e-9D ? 0.0D : (distance - g.oldFrom) / span;
                return g.newFrom + Math.max(0.0D, Math.min(1.0D, f)) * (g.newTo - g.newFrom);
            }
        }
        // At or past the far end of the old section, or before its start: onto the matching end of
        // the new one. A zero-length group — a trimmed span — is caught here too, by its end.
        for (Group g : groups) {
            if (Math.abs(distance - g.oldTo) <= 1.0e-9D && g.oldTo - g.oldFrom <= 1.0e-9D) {
                return g.newTo;
            }
        }
        return distance <= 0.0D ? 0.0D : newLength;
    }

    /** A node moved or re-banked: the same spans, each mapped onto its new self. */
    public static SectionRemap nodeChanged(TrackSection before, TrackSection after) {
        List<Group> groups = new ArrayList<>();
        int spans = spanCount(before);
        for (int i = 0; i < spans; i++) {
            groups.add(new Group(start(before, i), end(before, i), start(after, i), end(after, i)));
        }
        return new SectionRemap(groups, after.totalLength());
    }

    /**
     * A node inserted at {@code index}, between what were nodes {@code index - 1} and
     * {@code index}. On an open section, {@code index} equal to the old node count appends a node
     * past the end, which extends the track and moves nothing.
     */
    public static SectionRemap nodeInserted(TrackSection before, TrackSection after, int index) {
        List<Group> groups = new ArrayList<>();
        int spans = spanCount(before);
        for (int i = 0; i < spans; i++) {
            if (i < index - 1) {
                groups.add(new Group(start(before, i), end(before, i), start(after, i), end(after, i)));
            }
            else if (i == index - 1) {
                // The split span: its old stretch now covers both new spans either side of the node.
                groups.add(new Group(start(before, i), end(before, i), start(after, i), end(after, i + 1)));
            }
            else {
                groups.add(new Group(start(before, i), end(before, i), start(after, i + 1), end(after, i + 1)));
            }
        }
        return new SectionRemap(groups, after.totalLength());
    }

    /** Node {@code index} removed. */
    public static SectionRemap nodeRemoved(TrackSection before, TrackSection after, int index) {
        List<Group> groups = new ArrayList<>();
        int oldSpans = spanCount(before);
        int nodes = before.nodes().size();
        if (!before.isClosed() && (index == 0 || index == nodes - 1)) {
            // An end trimmed off: the span it ended is gone, and what was on it is left at the end.
            boolean first = index == 0;
            double at = first ? 0.0D : after.totalLength();
            for (int i = 0; i < oldSpans; i++) {
                if ((first && i == 0) || (!first && i == oldSpans - 1)) {
                    groups.add(new Group(start(before, i), end(before, i), at, at));
                }
                else {
                    int j = first ? i - 1 : i;
                    groups.add(new Group(start(before, i), end(before, i), start(after, j), end(after, j)));
                }
            }
            return new SectionRemap(groups, after.totalLength());
        }
        // An inner node (or any node of a circuit): the spans either side of it merge, each keeping
        // its share of the merged span in proportion to how long it was.
        int left = Math.floorMod(index - 1, oldSpans);
        int right = index % oldSpans;
        int merged = Math.floorMod(index - 1, spanCount(after));
        double leftLength = end(before, left) - start(before, left);
        double rightLength = end(before, right) - start(before, right);
        double share = leftLength + rightLength <= 1.0e-9D ? 0.5D : leftLength / (leftLength + rightLength);
        double mergedFrom = start(after, merged);
        double mergedTo = end(after, merged);
        double split = mergedFrom + share * (mergedTo - mergedFrom);
        for (int i = 0; i < oldSpans; i++) {
            if (i == left) {
                groups.add(new Group(start(before, i), end(before, i), mergedFrom, split));
            }
            else if (i == right) {
                groups.add(new Group(start(before, i), end(before, i), split, mergedTo));
            }
            else {
                // Spans after the removed node shift down one; on a circuit whose removed node was
                // the first, the wrap span is the merged one and nothing else moves.
                int j = right == 0 || i > right ? i - 1 : i;
                groups.add(new Group(start(before, i), end(before, i), start(after, j), end(after, j)));
            }
        }
        return new SectionRemap(groups, after.totalLength());
    }

    private static int spanCount(TrackSection section) {
        int nodes = section.nodes().size();
        return section.isClosed() ? nodes : nodes - 1;
    }

    private static double start(TrackSection section, int span) {
        return section.nodeDistance(span);
    }

    private static double end(TrackSection section, int span) {
        return span + 1 < section.nodes().size() ? section.nodeDistance(span + 1) : section.totalLength();
    }
}
