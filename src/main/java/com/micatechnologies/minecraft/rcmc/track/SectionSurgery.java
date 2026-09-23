package com.micatechnologies.minecraft.rcmc.track;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork.End;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork.SectionEnd;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Edits that change which sections exist: splitting one in two, merging two into one, closing an
 * open run into a circuit, and reversing a section's direction of travel.
 *
 * <p>{@link SectionRemap} maps distances along a section whose nodes changed. These edits also move
 * track <em>between</em> sections, and can turn it round, so each one maps every old span to the new
 * span it becomes: which section it is on now, where along it, and which way it runs. The caller
 * carries hardware, stations and trains through that mapping, and uses the end mapping to keep the
 * joins and switches at each section's ends.</p>
 *
 * <p>As in {@code SectionRemap}, a point keeps its place between the same two nodes.</p>
 */
public final class SectionSurgery {

    /** How close two ends must be to be merged, blocks. They become one node, at their midpoint. */
    public static final double MERGE_REACH = 3.0D;

    private SectionSurgery() {
    }

    /** One old span and the new span it becomes. {@code newFrom > newTo} where it now runs backwards. */
    private static final class SpanMove {
        final int oldId;
        final double oldFrom;
        final double oldTo;
        final int newId;
        final double newFrom;
        final double newTo;

        SpanMove(int oldId, double oldFrom, double oldTo, int newId, double newFrom, double newTo) {
            this.oldId = oldId;
            this.oldFrom = oldFrom;
            this.oldTo = oldTo;
            this.newId = newId;
            this.newFrom = newFrom;
            this.newTo = newTo;
        }

        TrackRef at(double distance) {
            double span = oldTo - oldFrom;
            double f = span <= 1.0e-9D ? 0.0D : Math.max(0.0D, Math.min(1.0D, (distance - oldFrom) / span));
            return new TrackRef(newId, newFrom + f * (newTo - newFrom));
        }
    }

    /** What an edit produced, and where everything on the old track goes. */
    public static final class Result {
        private final List<TrackSection> sections;
        private final Set<Integer> removedIds;
        private final List<SpanMove> moves;
        private final Map<SectionEnd, SectionEnd> ends;
        private final List<SectionEnd[]> joins;

        private Result(List<TrackSection> sections, Set<Integer> removedIds, List<SpanMove> moves,
                       Map<SectionEnd, SectionEnd> ends, List<SectionEnd[]> joins) {
            this.sections = Collections.unmodifiableList(sections);
            this.removedIds = Collections.unmodifiableSet(removedIds);
            this.moves = moves;
            this.ends = ends;
            this.joins = Collections.unmodifiableList(joins);
        }

        /** The sections to put in the network, replacing any with the same id. */
        public List<TrackSection> sections() {
            return sections;
        }

        /** Old section ids that no longer exist afterwards. */
        public Set<Integer> removedIds() {
            return removedIds;
        }

        /** Whether section {@code oldId} was part of the edit. */
        public boolean touches(int oldId) {
            for (SpanMove m : moves) {
                if (m.oldId == oldId) {
                    return true;
                }
            }
            return false;
        }

        /** Whether section {@code oldId}'s track now runs the other way. */
        public boolean reverses(int oldId) {
            for (SpanMove m : moves) {
                if (m.oldId == oldId && m.newFrom > m.newTo) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Distances along {@code oldId} where the track now goes to a different section, or jumps
         * along the same one: anything laid across one of these has to be cut there first.
         */
        public List<Double> cutsOn(int oldId) {
            List<SpanMove> spans = spansOf(oldId);
            List<Double> cuts = new ArrayList<>();
            for (int i = 1; i < spans.size(); i++) {
                SpanMove before = spans.get(i - 1);
                SpanMove after = spans.get(i);
                if (before.newId != after.newId || Math.abs(before.newTo - after.newFrom) > 1.0e-6D) {
                    cuts.add(after.oldFrom);
                }
            }
            return cuts;
        }

        /**
         * Where distance {@code d} of old section {@code oldId} is now. A node belongs to the span it
         * starts, as in {@code SectionRemap}. {@code null} when the section was not part of the edit.
         */
        public TrackRef map(int oldId, double d) {
            SpanMove last = null;
            for (SpanMove m : moves) {
                if (m.oldId != oldId) {
                    continue;
                }
                if (d >= m.oldFrom - 1.0e-9D && d < m.oldTo - 1.0e-9D) {
                    return m.at(d);
                }
                last = m;
            }
            return last == null ? null : last.at(Math.min(d, last.oldTo));
        }

        /**
         * As {@link #map}, but a node belongs to the span it ends: for the far end of something laid
         * along the track, which must stay with the piece it ends rather than jump to the next one.
         */
        public TrackRef mapEnd(int oldId, double d) {
            SpanMove first = null;
            for (SpanMove m : moves) {
                if (m.oldId != oldId) {
                    continue;
                }
                if (first == null) {
                    first = m;
                }
                if (d > m.oldFrom + 1.0e-9D && d <= m.oldTo + 1.0e-9D) {
                    return m.at(d);
                }
            }
            return first == null ? null : first.at(first.oldFrom);
        }

        /** Where end {@code old} of a section in the edit is now; {@code null} if the edit used it up. */
        public SectionEnd endOf(SectionEnd old) {
            return ends.get(old);
        }

        /** Whether end {@code old} of a section in the edit was used up — merged into a join. */
        public boolean consumes(SectionEnd old) {
            return touches(old.sectionId) && !ends.containsKey(old);
        }

        /** Joins the edit makes, between the new sections' ends. */
        public List<SectionEnd[]> joins() {
            return joins;
        }

        private List<SpanMove> spansOf(int oldId) {
            List<SpanMove> spans = new ArrayList<>();
            for (SpanMove m : moves) {
                if (m.oldId == oldId) {
                    spans.add(m);
                }
            }
            return spans;
        }
    }

    /**
     * Splits {@code section} at node {@code node} into two sections that meet there and stay joined,
     * so trains run straight through. The first half keeps the id; the second takes {@code newId}.
     *
     * <p>A circuit has no ends to split into two pieces, so it is opened instead: one open run that
     * starts and ends at the node, joined end to start. Either way, what was laid across the node is
     * cut there, since the node is now the end of a section.</p>
     */
    public static Result split(TrackSection section, int node, int newId) {
        int n = section.nodes().size();
        int id = section.id();
        List<SpanMove> moves = new ArrayList<>();
        Map<SectionEnd, SectionEnd> ends = new HashMap<>();
        List<SectionEnd[]> joins = new ArrayList<>();
        if (section.isClosed()) {
            if (n < 3 || node < 0 || node >= n) {
                throw new IllegalArgumentException("a circuit of " + n + " nodes cannot be opened at node " + node);
            }
            List<TrackNode> rotated = new ArrayList<>(n + 1);
            for (int k = 0; k <= n; k++) {
                rotated.add(section.nodes().get((node + k) % n));
            }
            TrackSection open = new TrackSection(id, rotated, false, section.styleId(), section.palette());
            for (int j = 0; j < n; j++) {
                int k = Math.floorMod(j - node, n);
                moves.add(new SpanMove(id, start(section, j), end(section, j), id, start(open, k), end(open, k)));
            }
            joins.add(new SectionEnd[] {new SectionEnd(id, End.END), new SectionEnd(id, End.START)});
            return new Result(listOf(open), new LinkedHashSet<>(), moves, ends, joins);
        }
        if (node <= 0 || node >= n - 1) {
            throw new IllegalArgumentException("open track splits at an inner node, not node " + node + " of " + n);
        }
        TrackSection first = new TrackSection(id, new ArrayList<>(section.nodes().subList(0, node + 1)), false,
            section.styleId(), section.palette());
        TrackSection second = new TrackSection(newId, new ArrayList<>(section.nodes().subList(node, n)), false,
            section.styleId(), section.palette());
        for (int j = 0; j < n - 1; j++) {
            if (j < node) {
                moves.add(new SpanMove(id, start(section, j), end(section, j), id, start(first, j), end(first, j)));
            }
            else {
                int k = j - node;
                moves.add(new SpanMove(id, start(section, j), end(section, j), newId, start(second, k), end(second, k)));
            }
        }
        ends.put(new SectionEnd(id, End.START), new SectionEnd(id, End.START));
        ends.put(new SectionEnd(id, End.END), new SectionEnd(newId, End.END));
        joins.add(new SectionEnd[] {new SectionEnd(id, End.END), new SectionEnd(newId, End.START)});
        List<TrackSection> out = new ArrayList<>();
        out.add(first);
        out.add(second);
        return new Result(out, new LinkedHashSet<>(), moves, ends, joins);
    }

    /**
     * Merges end {@code aEnd} of {@code a} with end {@code bEnd} of {@code b} into one section, one
     * continuous spline with no seam. The two end nodes become one, at their midpoint. The merged
     * section keeps {@code a}'s id, style, colours and direction; {@code b} is turned round to match
     * when the ends meet head to head or tail to tail.
     */
    public static Result merge(TrackSection a, End aEnd, TrackSection b, End bEnd) {
        if (a.id() == b.id()) {
            throw new IllegalArgumentException("merging a section with itself closes it; use close()");
        }
        if (a.isClosed() || b.isClosed()) {
            throw new IllegalArgumentException("a circuit has no ends to merge");
        }
        // Normalise to first.END meeting second.START, turning b round where needed, never a.
        TrackSection first;
        TrackSection second;
        boolean firstReversed;
        boolean secondReversed;
        if (aEnd == End.END) {
            first = a;
            firstReversed = false;
            secondReversed = bEnd == End.END;
            second = secondReversed ? reversedNodes(b) : b;
        }
        else {
            secondReversed = false;
            second = a;
            firstReversed = bEnd == End.START;
            first = firstReversed ? reversedNodes(b) : b;
        }
        List<TrackNode> nodes = new ArrayList<>(first.nodes().subList(0, first.nodes().size() - 1));
        nodes.add(junction(first.nodes().get(first.nodes().size() - 1), second.nodes().get(0)));
        nodes.addAll(second.nodes().subList(1, second.nodes().size()));
        TrackSection merged = new TrackSection(a.id(), nodes, false, a.styleId(), a.palette());

        List<SpanMove> moves = new ArrayList<>();
        int firstSpans = first.nodes().size() - 1;
        int secondSpans = second.nodes().size() - 1;
        TrackSection firstOld = first == a ? a : b;
        TrackSection secondOld = second == a ? a : b;
        addMoves(moves, firstOld, firstReversed, merged, 0, firstSpans);
        addMoves(moves, secondOld, secondReversed, merged, firstSpans, secondSpans);

        Map<SectionEnd, SectionEnd> ends = new HashMap<>();
        ends.put(new SectionEnd(firstOld.id(), firstReversed ? End.END : End.START), new SectionEnd(a.id(), End.START));
        ends.put(new SectionEnd(secondOld.id(), secondReversed ? End.START : End.END), new SectionEnd(a.id(), End.END));
        Set<Integer> removed = new LinkedHashSet<>();
        removed.add(b.id());
        return new Result(listOf(merged), removed, moves, ends, new ArrayList<>());
    }

    /**
     * Closes open {@code section} into a circuit, its two end nodes becoming one at their midpoint.
     * A circuit needs three nodes, so the open run needs four.
     */
    public static Result close(TrackSection section) {
        int n = section.nodes().size();
        if (section.isClosed() || n < 4) {
            throw new IllegalArgumentException("only open track of at least four nodes closes into a circuit");
        }
        List<TrackNode> nodes = new ArrayList<>(n - 1);
        nodes.add(junction(section.nodes().get(n - 1), section.nodes().get(0)));
        nodes.addAll(section.nodes().subList(1, n - 1));
        TrackSection closed = new TrackSection(section.id(), nodes, true, section.styleId(), section.palette());
        List<SpanMove> moves = new ArrayList<>();
        addMoves(moves, section, false, closed, 0, n - 1);
        return new Result(listOf(closed), new LinkedHashSet<>(), moves, new HashMap<>(), new ArrayList<>());
    }

    /** Turns {@code section} round: trains run the other way along the same track. */
    public static Result reverse(TrackSection section) {
        TrackSection turned = reversedNodes(section);
        int id = section.id();
        int n = section.nodes().size();
        List<SpanMove> moves = new ArrayList<>();
        if (section.isClosed()) {
            // Node k of the turned circuit is old node n-1-k, so old span j (node j to j+1) runs
            // between new nodes n-2-j and n-1-j, and the old wrap span is still the wrap span.
            for (int j = 0; j < n; j++) {
                int k = j == n - 1 ? n - 1 : n - 2 - j;
                moves.add(new SpanMove(id, start(section, j), end(section, j), id, end(turned, k), start(turned, k)));
            }
        }
        else {
            addMoves(moves, section, true, turned, 0, n - 1);
        }
        Map<SectionEnd, SectionEnd> ends = new HashMap<>();
        if (!section.isClosed()) {
            ends.put(new SectionEnd(id, End.START), new SectionEnd(id, End.END));
            ends.put(new SectionEnd(id, End.END), new SectionEnd(id, End.START));
        }
        return new Result(listOf(turned), new LinkedHashSet<>(), moves, ends, new ArrayList<>());
    }

    /** Whether the two ends are close enough to {@link #merge} or {@link #close}. */
    public static boolean inReach(TrackSection a, End aEnd, TrackSection b, End bEnd) {
        return a.endpointAt(aEnd).distanceTo(b.endpointAt(bEnd)) <= MERGE_REACH;
    }

    /**
     * Old spans {@code 0..count-1} of {@code old} onto new spans starting at {@code offset} of
     * {@code into}; when {@code reversed}, old span j is new span {@code offset + count-1-j}, run
     * backwards.
     */
    private static void addMoves(List<SpanMove> moves, TrackSection old, boolean reversed, TrackSection into,
                                 int offset, int count) {
        for (int j = 0; j < count; j++) {
            if (reversed) {
                int k = offset + count - 1 - j;
                moves.add(new SpanMove(old.id(), start(old, j), end(old, j), into.id(), end(into, k), start(into, k)));
            }
            else {
                int k = offset + j;
                moves.add(new SpanMove(old.id(), start(old, j), end(old, j), into.id(), start(into, k), end(into, k)));
            }
        }
    }

    /** {@code section} with its nodes in reverse order, banks negated since right becomes left. */
    private static TrackSection reversedNodes(TrackSection section) {
        return section.reversed();
    }

    private static TrackNode junction(TrackNode a, TrackNode b) {
        Vec3 mid = a.position().add(b.position()).scale(0.5D);
        return new TrackNode(mid, (a.bankDegrees() + b.bankDegrees()) * 0.5D, a.styleId());
    }

    private static List<TrackSection> listOf(TrackSection section) {
        List<TrackSection> list = new ArrayList<>(1);
        list.add(section);
        return list;
    }

    private static double start(TrackSection section, int span) {
        return section.nodeDistance(span);
    }

    private static double end(TrackSection section, int span) {
        return span + 1 < section.nodes().size() ? section.nodeDistance(span + 1) : section.totalLength();
    }
}
