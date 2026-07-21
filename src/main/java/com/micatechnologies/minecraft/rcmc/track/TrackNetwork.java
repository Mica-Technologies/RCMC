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
     * A switch (turnout): one section end — the <b>throat</b> — leading to a choice of two or
     * more branch ends, with a selection saying which branch is currently lined.
     *
     * <p>Traversal through the throat follows the selected branch. Traversal arriving <em>at</em>
     * the switch from the selected branch passes through to the throat; arriving from a
     * non-selected branch — a trailing move against the points — is treated as a dead end. Real
     * turnouts either derail such a move or are damaged by it; stopping the train and surfacing
     * the fault (exactly like running off unconnected track) is the honest, safe rendering of
     * that, and it is what forces a metro layout's signalling to actually line switches rather
     * than trains teleporting through them. Run-through behaviour, if ever wanted, is a policy
     * change confined to {@link #effectiveLink}.</p>
     *
     * <p>Selection is part of network state, synced and saved with it — a client predicting a
     * train through a switch the server lined differently would rubber-band, the same reason the
     * geometry itself is synced.</p>
     */
    public static final class TrackSwitch {

        private final SectionEnd throat;
        private final java.util.List<SectionEnd> branches;
        private int selected;

        TrackSwitch(SectionEnd throat, java.util.List<SectionEnd> branches) {
            this.throat = throat;
            this.branches = Collections.unmodifiableList(new java.util.ArrayList<>(branches));
        }

        public SectionEnd throat() {
            return throat;
        }

        public java.util.List<SectionEnd> branches() {
            return branches;
        }

        public int selectedIndex() {
            return selected;
        }

        public SectionEnd selectedBranch() {
            return branches.get(selected);
        }

        @Override
        public String toString() {
            return "TrackSwitch{" + throat + " -> " + branches + ", lined=" + selectedBranch() + '}';
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

    /** Switches keyed by throat end. Iteration order is insertion order, so saves are stable. */
    private final Map<SectionEnd, TrackSwitch> switches = new LinkedHashMap<>();

    /** Reverse index: every branch end of every switch -> its throat. Kept in lockstep with {@link #switches}. */
    private final Map<SectionEnd, SectionEnd> branchToThroat = new HashMap<>();

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

    /** Removes a section and every join and switch touching it. */
    public TrackSection removeSection(int sectionId) {
        TrackSection removed = sections.remove(sectionId);
        if (removed != null) {
            disconnect(new SectionEnd(sectionId, End.START));
            disconnect(new SectionEnd(sectionId, End.END));
            // A switch missing any of its ends is no longer a meaningful choice; drop it whole
            // rather than leave a branch list pointing at nothing.
            java.util.List<SectionEnd> throatsToDrop = new java.util.ArrayList<>();
            for (TrackSwitch sw : switches.values()) {
                boolean touches = sw.throat().sectionId == sectionId;
                for (SectionEnd branch : sw.branches()) {
                    touches |= branch.sectionId == sectionId;
                }
                if (touches) {
                    throatsToDrop.add(sw.throat());
                }
            }
            for (SectionEnd throat : throatsToDrop) {
                removeSwitch(throat);
            }
        }
        return removed;
    }

    public TrackSection section(int sectionId) {
        return sections.get(sectionId);
    }

    /**
     * Whether a section exists, without throwing.
     *
     * <p>Callers on the client need this: the client's network and its trains arrive in separate
     * packets and are updated independently, so there are legitimately ticks where a train
     * references track the client has not received yet, or track that has just been removed under
     * it. That is a normal transient, not an error, and it must not reach {@link #advance} — which
     * correctly throws for an unknown section, since on the server that genuinely is a bug.</p>
     */
    public boolean hasSection(int sectionId) {
        return sections.containsKey(sectionId);
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
        if (isSwitched(a) || isSwitched(b)) {
            throw new IllegalArgumentException(
                "cannot join an end that belongs to a switch — remove the switch first");
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
     * Adds a switch: {@code throat} leading to a choice of {@code branches}, initially lined to
     * the first. Every involved end must be free — not plainly joined and not already part of
     * another switch — and every branch must meet the throat within {@link #MAX_JOIN_GAP},
     * exactly as {@link #connect} demands of a join, and for the same teleport reason.
     */
    public void addSwitch(SectionEnd throat, java.util.List<SectionEnd> branches) {
        if (throat == null || branches == null || branches.size() < 2) {
            throw new IllegalArgumentException("a switch needs a throat and at least two branches");
        }
        java.util.Set<SectionEnd> distinct = new java.util.HashSet<>(branches);
        if (distinct.size() != branches.size() || distinct.contains(throat)) {
            throw new IllegalArgumentException("switch ends must be distinct, and no branch may be the throat");
        }
        java.util.List<SectionEnd> all = new java.util.ArrayList<>(branches);
        all.add(throat);
        for (SectionEnd end : all) {
            TrackSection section = sections.get(end.sectionId);
            if (section == null) {
                throw new IllegalArgumentException("section " + end.sectionId + " must exist before switching");
            }
            if (section.isClosed()) {
                throw new IllegalArgumentException("a closed circuit has no ends to switch: " + end);
            }
            if (joins.containsKey(end) || isSwitched(end)) {
                throw new IllegalArgumentException(end + " is already joined or switched — an end can only lead one place");
            }
        }
        for (SectionEnd branch : branches) {
            JoinAlignment alignment = alignmentOf(throat, branch);
            if (alignment.positionGap > MAX_JOIN_GAP) {
                throw new IllegalArgumentException(
                    "cannot switch " + throat + " to " + branch + ": endpoints are "
                        + String.format("%.2f", alignment.positionGap) + " blocks apart (limit "
                        + MAX_JOIN_GAP + ")");
            }
        }
        TrackSwitch added = new TrackSwitch(throat, branches);
        switches.put(throat, added);
        for (SectionEnd branch : added.branches()) {
            branchToThroat.put(branch, throat);
        }
    }

    /** The switch whose throat is {@code throat}, or {@code null}. */
    public TrackSwitch switchAt(SectionEnd throat) {
        return switches.get(throat);
    }

    /** The switch that {@code end} participates in — as throat or as a branch — or {@code null}. */
    public TrackSwitch switchInvolving(SectionEnd end) {
        TrackSwitch asThroat = switches.get(end);
        if (asThroat != null) {
            return asThroat;
        }
        SectionEnd throat = branchToThroat.get(end);
        return throat == null ? null : switches.get(throat);
    }

    public Collection<TrackSwitch> switches() {
        return Collections.unmodifiableCollection(switches.values());
    }

    /** Lines the switch at {@code throat} to branch {@code branchIndex}. */
    public void setSwitchSelection(SectionEnd throat, int branchIndex) {
        TrackSwitch sw = switches.get(throat);
        if (sw == null) {
            throw new IllegalArgumentException("no switch at " + throat);
        }
        if (branchIndex < 0 || branchIndex >= sw.branches().size()) {
            throw new IllegalArgumentException(
                "branch index " + branchIndex + " out of range for " + sw);
        }
        sw.selected = branchIndex;
    }

    /** Removes the switch at {@code throat}, if any. */
    public void removeSwitch(SectionEnd throat) {
        TrackSwitch removed = switches.remove(throat);
        if (removed != null) {
            for (SectionEnd branch : removed.branches()) {
                branchToThroat.remove(branch);
            }
        }
    }

    private boolean isSwitched(SectionEnd end) {
        return switches.containsKey(end) || branchToThroat.containsKey(end);
    }

    /**
     * Where travel leaving through {@code end} arrives, honouring both plain joins and switches
     * — the traversal-facing generalisation of {@link #joinedTo}. {@code null} means travel out
     * of that end dead-ends: nothing attached, or a trailing move against a switch's points (see
     * {@link TrackSwitch}).
     */
    public SectionEnd linkedTo(SectionEnd end) {
        SectionEnd joined = joins.get(end);
        if (joined != null) {
            return joined;
        }
        TrackSwitch asThroat = switches.get(end);
        if (asThroat != null) {
            return asThroat.selectedBranch();
        }
        SectionEnd throat = branchToThroat.get(end);
        if (throat != null && switches.get(throat).selectedBranch().equals(end)) {
            return throat;
        }
        return null;
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
            SectionEnd arriving = linkedTo(leaving);

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
        switches.clear();
        branchToThroat.clear();
        nextSectionId = 1;
    }

    @Override
    public String toString() {
        return "TrackNetwork{sections=" + sections.size() + ", joins=" + (joins.size() / 2)
            + ", switches=" + switches.size() + '}';
    }
}
