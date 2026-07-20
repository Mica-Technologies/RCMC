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

    /**
     * Largest endpoint separation, in blocks, that {@link #connect} will accept.
     *
     * <p>Half a block is generous enough to absorb the rounding a builder's snapping introduces,
     * and tight enough that a train crossing the join moves less than it does in a single physics
     * sub-step — so the discontinuity is invisible rather than a visible jump.</p>
     */
    public static final double MAX_JOIN_GAP = 0.5D;

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
        TrackSection sectionA = sections.get(a.sectionId);
        TrackSection sectionB = sections.get(b.sectionId);
        if (sectionA == null || sectionB == null) {
            throw new IllegalArgumentException("both sections must exist before joining");
        }
        if (sectionA.isClosed() || sectionB.isClosed()) {
            throw new IllegalArgumentException(
                "a closed circuit has no ends to join; close it or join it, not both");
        }

        JoinAlignment alignment = alignmentOf(a, b);
        if (alignment.positionGap > MAX_JOIN_GAP) {
            throw new IllegalArgumentException(
                "cannot join " + a + " to " + b + ": endpoints are "
                    + String.format("%.2f", alignment.positionGap) + " blocks apart (limit "
                    + MAX_JOIN_GAP + "). A train would teleport across this join. Move the "
                    + "endpoints together first.");
        }

        disconnect(a);
        disconnect(b);
        joins.put(a, b);
        joins.put(b, a);
    }

    /**
     * Measures how well two ends meet, without joining them. Safe to call on ends that are already
     * joined, or on ones that never will be — the editor uses it to preview a candidate join.
     *
     * <p>The incoming direction is the <em>negated</em> exit direction of the arriving end: two
     * sections that meet properly point their exits <em>at each other</em>, so their exit vectors
     * are antiparallel when the join is smooth.</p>
     */
    public JoinAlignment alignmentOf(SectionEnd a, SectionEnd b) {
        TrackSection sectionA = sections.get(a.sectionId);
        TrackSection sectionB = sections.get(b.sectionId);
        if (sectionA == null || sectionB == null) {
            throw new IllegalArgumentException("both sections must exist");
        }

        double gap = sectionA.endpointAt(a.end).distanceTo(sectionB.endpointAt(b.end));

        com.micatechnologies.minecraft.rcmc.track.math.Vec3 leaving = sectionA.exitDirectionAt(a.end);
        com.micatechnologies.minecraft.rcmc.track.math.Vec3 entering =
            sectionB.exitDirectionAt(b.end).scale(-1.0D);
        double cos = Math.max(-1.0D, Math.min(1.0D, leaving.dot(entering)));
        return new JoinAlignment(gap, Math.toDegrees(Math.acos(cos)));
    }

    /** Alignment of the join on {@code end}, or {@code null} if nothing is joined there. */
    public JoinAlignment alignmentOf(SectionEnd end) {
        SectionEnd other = joins.get(end);
        return other == null ? null : alignmentOf(end, other);
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

            // Which way we then travel through the new section depends only on WHICH END we
            // arrive at, never on which end we left. Arriving at a START means heading into the
            // section, so toward increasing distance; arriving at an END means heading into it
            // from the far side, so toward decreasing distance. (An earlier version made this
            // conditional on the departing end too, which was correct only for forward motion and
            // sent trailing cars of a train back the way they came.)
            boolean movingForwardInNext = arriving.end == End.START;
            position = movingForwardInNext ? 0.0D : next.totalLength();
            remaining = movingForwardInNext ? overshoot : -overshoot;

            // The travel direction has flipped relative to the track's distance axis if we were
            // moving one way in the old section's terms and the other way in the new section's.
            if (offTheEnd != movingForwardInNext) {
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
