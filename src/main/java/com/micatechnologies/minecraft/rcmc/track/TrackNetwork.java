package com.micatechnologies.minecraft.rcmc.track;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The graph of {@link TrackSection}s and the joins between their ends.
 *
 * <p>Owns section identity (ids are allocated here) and answers the one question the simulation
 * asks every tick: "I am here and I moved this far — where am I now?" ({@link #advance}). That
 * question is the network's whole reason to exist; a single section could answer it only within
 * itself.</p>
 *
 * <p>Mutable, and deliberately not thread-safe: it lives in server-side saved data and is touched
 * from the server tick thread. The client holds its own copy, synced from the server.</p>
 */
public final class TrackNetwork {

    /** Which end of a section a join attaches to. */
    public enum End {
        START,
        END
    }

    /** One end of one section — the unit a join connects. */
    public static final class SectionEnd {

        public final int sectionId;
        public final End end;

        public SectionEnd(int sectionId, End end) {
            this.sectionId = sectionId;
            this.end = end;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SectionEnd)) {
                return false;
            }
            SectionEnd o = (SectionEnd) obj;
            return sectionId == o.sectionId && end == o.end;
        }

        @Override
        public int hashCode() {
            return 31 * sectionId + end.hashCode();
        }

        @Override
        public String toString() {
            return "Section" + sectionId + ":" + end;
        }
    }

    /**
     * Result of moving along the network.
     *
     * <p>{@link #reversed} matters and is easy to overlook: when two sections were built toward
     * each other and joined END-to-END, crossing that join means the direction of increasing
     * distance flips. A train's velocity sign must flip with it, or it will immediately turn
     * around at the join. Callers that ignore this field have a bug waiting.</p>
     */
    public static final class Traversal {

        public final TrackRef ref;
        public final boolean reversed;
        public final boolean hitDeadEnd;

        public Traversal(TrackRef ref, boolean reversed, boolean hitDeadEnd) {
            this.ref = ref;
            this.reversed = reversed;
            this.hitDeadEnd = hitDeadEnd;
        }

        @Override
        public String toString() {
            return "Traversal{" + ref + (reversed ? ", REVERSED" : "")
                + (hitDeadEnd ? ", DEAD END" : "") + '}';
        }
    }

    /**
     * Bound on joins crossed in a single {@link #advance} call. A train moving a fraction of a
     * block per tick crosses at most one; anything approaching this bound means either a
     * pathologically short section or a cycle, and looping forever inside the tick loop would
     * hang the server rather than produce a visible bug.
     */
    private static final int MAX_JOINS_PER_ADVANCE = 64;

    private final Map<Integer, TrackSection> sections = new LinkedHashMap<>();
    private final Map<SectionEnd, SectionEnd> joins = new HashMap<>();

    private int nextSectionId = 1;

    /** Allocates an id no existing section uses. */
    public int allocateSectionId() {
        while (sections.containsKey(nextSectionId)) {
            nextSectionId++;
        }
        return nextSectionId++;
    }

    public void addSection(TrackSection section) {
        if (section == null) {
            throw new IllegalArgumentException("section must not be null");
        }
        sections.put(section.id(), section);
        if (section.id() >= nextSectionId) {
            nextSectionId = section.id() + 1;
        }
    }

    /** Replaces a section in place, keeping its joins. Used after an edit rebuilds geometry. */
    public void replaceSection(TrackSection section) {
        if (!sections.containsKey(section.id())) {
            throw new IllegalArgumentException("No section with id " + section.id());
        }
        sections.put(section.id(), section);
    }

    /** Removes a section and every join touching it. */
    public TrackSection removeSection(int sectionId) {
        TrackSection removed = sections.remove(sectionId);
        if (removed != null) {
            disconnect(new SectionEnd(sectionId, End.START));
            disconnect(new SectionEnd(sectionId, End.END));
        }
        return removed;
    }

    public TrackSection section(int sectionId) {
        return sections.get(sectionId);
    }

    public Collection<TrackSection> sections() {
        return Collections.unmodifiableCollection(sections.values());
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }

    public int sectionCount() {
        return sections.size();
    }

    /**
     * Joins two section ends. Stored symmetrically so traversal works in both directions.
     *
     * <p>Any join already present on either end is dropped first — an end can only lead one place.
     * Branching (a switch) is a distinct concept: one end leading to a <em>choice</em> of others,
     * with a state saying which is currently selected. That is scheduled for Phase 7.1 and
     * deliberately not conflated with a plain join here.</p>
     */
    public void connect(SectionEnd a, SectionEnd b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("both ends must be non-null");
        }
        if (a.equals(b)) {
            throw new IllegalArgumentException("cannot join an end to itself: " + a);
        }
        if (!sections.containsKey(a.sectionId) || !sections.containsKey(b.sectionId)) {
            throw new IllegalArgumentException("both sections must exist before joining");
        }
        disconnect(a);
        disconnect(b);
        joins.put(a, b);
        joins.put(b, a);
    }

    /** Removes the join on {@code end}, if any, from both sides. */
    public void disconnect(SectionEnd end) {
        SectionEnd other = joins.remove(end);
        if (other != null) {
            joins.remove(other);
        }
    }

    /** What {@code end} is joined to, or {@code null}. */
    public SectionEnd joinedTo(SectionEnd end) {
        return joins.get(end);
    }

    public Map<SectionEnd, SectionEnd> joins() {
        return Collections.unmodifiableMap(joins);
    }

    /**
     * Moves {@code delta} blocks along the network from {@code from}, crossing joins as needed.
     *
     * <p>A closed circuit never leaves its section — it wraps. An open section that runs out of
     * track with nothing joined stops at the boundary and reports {@code hitDeadEnd}, which the
     * ride-control layer should surface as a fault rather than silently absorb: a train reaching
     * the end of unconnected track is a real operational failure, and the RCT-honest response is
     * to tell the operator, not to quietly stop the train.</p>
     */
    public Traversal advance(TrackRef from, double delta) {
        TrackSection section = sections.get(from.sectionId());
        if (section == null) {
            throw new IllegalArgumentException("No section with id " + from.sectionId());
        }

        double remaining = delta;
        int currentId = from.sectionId();
        double position = from.distance();
        boolean reversed = false;

        for (int crossings = 0; crossings <= MAX_JOINS_PER_ADVANCE; crossings++) {
            section = sections.get(currentId);
            double length = section.totalLength();

            if (section.isClosed()) {
                return new Traversal(
                    new TrackRef(currentId, section.clampDistance(position + remaining)),
                    reversed, false);
            }

            double target = position + remaining;
            if (target >= 0.0D && target <= length) {
                return new Traversal(new TrackRef(currentId, target), reversed, false);
            }

            // Ran off one end. Work out which, how far past, and where that end leads.
            boolean offTheEnd = target > length;
            double overshoot = offTheEnd ? target - length : -target;
            SectionEnd leaving = new SectionEnd(currentId, offTheEnd ? End.END : End.START);
            SectionEnd arriving = joins.get(leaving);

            if (arriving == null) {
                return new Traversal(
                    new TrackRef(currentId, offTheEnd ? length : 0.0D), reversed, true);
            }

            TrackSection next = sections.get(arriving.sectionId);
            currentId = arriving.sectionId;

            if (arriving.end == End.START) {
                // Entering at the start: distance still increases in our direction of travel.
                position = 0.0D;
                remaining = offTheEnd ? overshoot : -overshoot;
            }
            else {
                // Entering at the far end: the section's distance axis runs opposite to us, so
                // our direction of travel flips relative to it.
                position = next.totalLength();
                remaining = offTheEnd ? -overshoot : overshoot;
                reversed = !reversed;
            }
        }

        throw new IllegalStateException(
            "advance() crossed more than " + MAX_JOINS_PER_ADVANCE + " joins moving " + delta
                + " from " + from + " — cyclic joins or degenerate sections");
    }

    /** Convenience: the full frame at a network address. */
    public com.micatechnologies.minecraft.rcmc.track.math.TrackFrame frameAt(TrackRef ref) {
        TrackSection section = sections.get(ref.sectionId());
        if (section == null) {
            throw new IllegalArgumentException("No section with id " + ref.sectionId());
        }
        return section.frameAtDistance(ref.distance());
    }

    public void clear() {
        sections.clear();
        joins.clear();
        nextSectionId = 1;
    }

    @Override
    public String toString() {
        return "TrackNetwork{sections=" + sections.size() + ", joins=" + (joins.size() / 2) + '}';
    }
}
