package com.micatechnologies.minecraft.rcmc.track;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Finding a free end of existing track to continue from, and continuing it.
 *
 * <p><b>Why this extends a section rather than joining a new one to it.</b> The obvious reading of
 * "snap to the existing track" is: build a second section, then {@code connect()} the two ends.
 * That produces a join, and a join is where continuity has to be <em>enforced</em> — matching
 * position, tangent, and bank across a boundary the geometry knows nothing about.
 * {@link TrackNetwork#connect} deliberately permits a kink, so nothing would have stopped the new
 * track leaving at a visibly different angle from the way the old track arrived.</p>
 *
 * <p>Extending the section instead makes that problem disappear rather than solving it. One section
 * is one Catmull-Rom spline through all its nodes, and a spline is C¹ everywhere by construction —
 * so the tangent at the seam matches because there is no seam. Tangent matching is not implemented
 * here; it is a property of the representation, and this class's job is only to keep the track in
 * one section so that property applies.</p>
 *
 * <p>Pure, like the rest of {@code track}: no Minecraft types, so attaching is testable without a
 * game instance.</p>
 */
public final class TrackAttachment {

    private TrackAttachment() {
        throw new AssertionError("No instances.");
    }

    /** A free end of an existing section that new track can continue from. */
    public static final class Target {

        public final int sectionId;
        public final TrackNetwork.End end;

        /** Where the end sits, so a snapped node can be placed exactly on it. */
        public final Vec3 position;

        /**
         * The direction the existing track points at this end, leading <em>away</em> from the
         * section — the direction new track should set off in.
         */
        public final Vec3 outward;

        public final double distanceFromQuery;

        public Target(int sectionId, TrackNetwork.End end, Vec3 position, Vec3 outward,
                      double distanceFromQuery) {
            this.sectionId = sectionId;
            this.end = end;
            this.position = position;
            this.outward = outward;
            this.distanceFromQuery = distanceFromQuery;
        }

        /** True when continuing here appends to the section, leaving existing distances untouched. */
        public boolean appends() {
            return end == TrackNetwork.End.END;
        }

        @Override
        public String toString() {
            return "Target{section " + sectionId + " " + end + " at " + position + '}';
        }
    }

    /**
     * The nearest free end within {@code radius} of {@code query}, or {@code null}.
     *
     * <p>Closed circuits are skipped entirely: a circuit has no free end, and its start and end are
     * the same point, so offering to continue from it would mean cutting it open. Ends already
     * joined to another section are skipped for the same reason — they are not free.</p>
     */
    public static Target find(TrackNetwork network, Vec3 query, double radius) {
        if (network == null || query == null) {
            return null;
        }
        Target best = null;
        for (TrackSection section : network.sections()) {
            if (section.isClosed()) {
                continue;
            }
            for (TrackNetwork.End end : TrackNetwork.End.values()) {
                if (network.joinedTo(new TrackNetwork.SectionEnd(section.id(), end)) != null) {
                    continue;
                }
                Vec3 at = section.endpointAt(end);
                double distance = at.distanceTo(query);
                if (distance > radius) {
                    continue;
                }
                if (best == null || distance < best.distanceFromQuery) {
                    best = new Target(section.id(), end, at, section.exitDirectionAt(end), distance);
                }
            }
        }
        return best;
    }

    /**
     * A copy of {@code section} continued by {@code added}, which must have been built starting at
     * the attachment point and running away from the section.
     *
     * <p><b>What this does to distances already anchored on the section</b>, which is the part that
     * decides which end is safe to build from:</p>
     *
     * <p>Appending at {@link TrackNetwork.End#END} leaves earlier distances alone. Distance is
     * measured cumulatively from the start, so extending the far end cannot move anything before it.
     * The one exception is the final span itself: the old last node used to be an endpoint, where
     * the spline extrapolates a reflected phantom point, and now it has a real successor — so the
     * shape of that last span changes slightly. That is the intended effect (the track curves onward
     * instead of stopping) and it only moves distances inside the span being continued.</p>
     *
     * <p>Prepending at {@link TrackNetwork.End#START} moves <em>everything</em>, and — importantly —
     * <b>not by a constant</b>. It is tempting to assume every distance simply shifts by the inserted
     * length, but the old first node also stops being an endpoint, so the spline near it changes
     * shape and the section's own arc length is redistributed. There is no offset a caller could
     * apply to a lift hill's span to keep it over the same track. Callers must therefore refuse to
     * prepend onto a section that has ride elements or trains anchored to it, rather than shifting
     * them and hoping.</p>
     *
     * <p>The first added node is dropped when it is within {@code MAX_JOIN_GAP} of the existing
     * endpoint: snapping puts it exactly on top of the end node, and two coincident nodes give the
     * spline a zero-length span to turn through — the one thing guaranteed to look wrong.</p>
     */
    public static TrackSection extend(TrackSection section, TrackNetwork.End end,
                                      List<TrackNode> added) {
        return extend(section, end, added, false);
    }

    /**
     * As {@link #extend(TrackSection, TrackNetwork.End, List)}, but closing the result into a
     * circuit when {@code closed} is set — the case where a builder continues from one free end and
     * carries on all the way round to the other.
     *
     * <p>The last added node is dropped when it lands on the node the circuit will wrap to, for the
     * same reason the first one is: a closed spline already joins its last node to its first, so
     * keeping both leaves a zero-length span at the seam.</p>
     */
    public static TrackSection extend(TrackSection section, TrackNetwork.End end,
                                      List<TrackNode> added, boolean closed) {
        if (section == null) {
            throw new IllegalArgumentException("section must not be null");
        }
        if (added == null || added.isEmpty()) {
            throw new IllegalArgumentException("added must not be empty");
        }
        if (section.isClosed()) {
            throw new IllegalArgumentException(
                "cannot extend a closed circuit — section " + section.id());
        }

        List<TrackNode> extra = new ArrayList<>(added);
        Vec3 endpoint = section.endpointAt(end);
        if (extra.get(0).position().distanceTo(endpoint) <= TrackNetwork.MAX_JOIN_GAP) {
            extra.remove(0);
        }
        if (extra.isEmpty()) {
            throw new IllegalArgumentException(
                "nothing to add: every node coincided with the existing endpoint");
        }

        List<TrackNode> combined = new ArrayList<>(section.nodes().size() + extra.size());
        if (end == TrackNetwork.End.END) {
            combined.addAll(section.nodes());
            combined.addAll(extra);
        }
        else {
            // Built outward from the head, so the new nodes run away from the section and have to
            // be reversed to read as a run arriving at it.
            Collections.reverse(extra);
            combined.addAll(extra);
            combined.addAll(section.nodes());
        }
        if (closed && combined.size() > 2
            && combined.get(combined.size() - 1).position()
                .distanceTo(combined.get(0).position()) <= TrackNetwork.MAX_JOIN_GAP) {
            combined.remove(combined.size() - 1);
        }
        return new TrackSection(section.id(), combined, closed, section.styleId(), section.palette());
    }
}
