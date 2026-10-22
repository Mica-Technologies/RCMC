package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.track.SectionRemap;
import com.micatechnologies.minecraft.rcmc.track.SectionSurgery;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork.End;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork.SectionEnd;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.World;

/**
 * Splitting a section at any point along it, and putting a switch there — for building by command
 * what the track editor builds by hand.
 *
 * <p>The editor splits at a node. A plan names a point by its distance along the line, which rarely
 * lands on one, so a node is first inserted on the curve at that point (or the nearest node is used,
 * if one is within a block) and the editor's own split does the rest. Both steps go through the same
 * paths the editor uses — {@link TrackEdits#apply} and {@link SectionSurgeries#apply} — so ride
 * hardware, stations, berths, trains, joins and switches all follow the track.</p>
 *
 * <p><b>A switch on a circuit.</b> Switches are built from section ends, and a circuit has none. So a
 * switch at a point on a circuit first splits it there, which opens it into one section whose end
 * meets its own start at that point, and then replaces that join with the switch. A train lined
 * straight through runs on round the loop exactly as before.</p>
 */
public final class TrackSplits {

    /** A node this close to the requested point is used rather than another inserted beside it. */
    static final double SNAP_TO_NODE = 1.0D;

    /**
     * How close an existing node must be for a switch to cut there instead of at the exact point.
     * Small, because the diverging track's end is checked against the exact point: snapping a
     * whole block away left a siding start 0.5 from the joint and the join was refused.
     */
    private static final double SWITCH_SNAP = 0.05D;

    private TrackSplits() {
        throw new AssertionError("No instances.");
    }

    /** A refusal, worded for the builder. */
    public static final class Refused extends Exception {
        public Refused(String message) {
            super(message);
        }
    }

    /** The two ends that now meet where the section was split, and what to tell the builder. */
    public static final class Split {
        /** The end the track arrives at the point on, running in the direction distances increase. */
        public final SectionEnd arriving;
        /** The end it carries on from. */
        public final SectionEnd departing;
        public final String message;

        Split(SectionEnd arriving, SectionEnd departing, String message) {
            this.arriving = arriving;
            this.departing = departing;
            this.message = message;
        }
    }

    /** Splits section {@code sectionId} at {@code distance} along it. */
    public static Split splitAt(World world, RcmcWorldState state, int sectionId, double distance)
        throws Refused {
        return splitAt(world, state, sectionId, distance, SNAP_TO_NODE);
    }

    /**
     * Splits section {@code sectionId} at {@code distance}, reusing an existing node only within
     * {@code snap} of it — a switch wants its joint exactly where the diverging track starts, not at
     * a node up to a block away from it.
     */
    static Split splitAt(World world, RcmcWorldState state, int sectionId, double distance,
                         double snap) throws Refused {
        TrackNetwork network = state.network();
        TrackSection section = network.section(sectionId);
        if (section == null) {
            throw new Refused("No section with id " + sectionId);
        }
        double total = section.totalLength();
        if (section.isClosed()) {
            distance = ((distance % total) + total) % total;
        }
        else if (distance < SNAP_TO_NODE || distance > total - SNAP_TO_NODE) {
            throw new Refused(String.format("Section #%d runs 0 to %.1f; %.1f is at an end already, "
                + "which needs no split", sectionId, total, distance));
        }

        int node = nodeAt(section, distance, snap);
        if (node < 0) {
            node = insertionIndex(section, distance);
            TrackNode inserted = new TrackNode(section.positionAtDistance(distance),
                Math.toDegrees(section.bankRadiansAt(distance)), null);
            TrackSection edited = section.withNodeInserted(node, inserted);
            TrackEdits.apply(world, state, edited, SectionRemap.nodeInserted(section, edited, node));
            section = network.section(sectionId);
        }

        // Peeked, not allocated, as the editor does: a refused split must not use an id up.
        int newId = network.nextSectionId();
        while (network.hasSection(newId)) {
            newId++;
        }
        boolean closed = section.isClosed();
        SectionSurgeries.Outcome outcome = SectionSurgeries.apply(world, state,
            SectionSurgery.split(section, node, newId), closed
                ? String.format("Circuit #%d opened at %.1f; its ends meet there, joined.", sectionId, distance)
                : String.format("Split #%d at %.1f into #%d and #%d, joined there.", sectionId, distance,
                    sectionId, newId));
        if (!outcome.applied) {
            throw new Refused(outcome.message);
        }
        return closed
            ? new Split(new SectionEnd(sectionId, End.END), new SectionEnd(sectionId, End.START), outcome.message)
            : new Split(new SectionEnd(sectionId, End.END), new SectionEnd(newId, End.START), outcome.message);
    }

    /**
     * Puts a switch where section {@code throughId} passes {@code distance}, leading off onto whichever
     * end of {@code divergeId} sits there. Which side is the throat follows from the way the diverging
     * track leaves: a turnout leading on in the direction distances increase is entered from behind,
     * one leading back the other way from ahead.
     *
     * @return what to tell the builder
     */
    public static String switchAt(World world, RcmcWorldState state, int throughId, double distance,
                                  int divergeId) throws Refused {
        TrackNetwork network = state.network();
        TrackSection through = network.section(throughId);
        TrackSection diverge = network.section(divergeId);
        if (through == null || diverge == null) {
            throw new Refused("No section with id " + (through == null ? throughId : divergeId));
        }
        if (throughId == divergeId) {
            throw new Refused("A section cannot diverge from itself");
        }
        if (diverge.isClosed()) {
            throw new Refused("Section #" + divergeId + " is a circuit; the diverging track needs an end");
        }
        double total = through.totalLength();
        double at = through.isClosed() ? ((distance % total) + total) % total : distance;
        if (at < 0.0D || at > total) {
            throw new Refused(String.format("Section #%d runs 0 to %.1f", throughId, total));
        }
        Vec3 point = through.positionAtDistance(at);

        // Which end of the diverging track is at the points, checked before cutting anything.
        End divergeEnd = diverge.endpointAt(End.START).distanceTo(point)
            <= diverge.endpointAt(End.END).distanceTo(point) ? End.START : End.END;
        SectionEnd branch = new SectionEnd(divergeId, divergeEnd);
        double gap = diverge.endpointAt(divergeEnd).distanceTo(point);
        if (gap > TrackNetwork.MAX_JOIN_GAP) {
            throw new Refused(String.format("Neither end of #%d is at the points: the nearest is %.2f blocks "
                + "from #%d at %.1f (limit %.2f). Lay the diverging track to start there.",
                divergeId, gap, throughId, at, TrackNetwork.MAX_JOIN_GAP));
        }
        if (network.joinedTo(branch) != null || network.switchInvolving(branch) != null) {
            throw new Refused(branch + " already leads somewhere; an end can only lead one place");
        }
        // Into the diverging track, from the points.
        Vec3 leaving = diverge.exitDirectionAt(divergeEnd).scale(-1.0D);
        boolean forward = leaving.dot(through.tangentAtDistance(at)) >= 0.0D;

        Split split;
        boolean atStart = !through.isClosed() && at < SNAP_TO_NODE;
        boolean atEnd = !through.isClosed() && at > total - SNAP_TO_NODE;
        if (atStart || atEnd) {
            // At a joint already: no split needed, the switch goes between this end and whatever it
            // was joined to. Refusing here used to mean removing and re-laying a section to put
            // points where two sections already met.
            SectionEnd own = new SectionEnd(throughId, atStart ? End.START : End.END);
            SectionEnd partner = network.joinedTo(own);
            if (partner == null) {
                throw new Refused(String.format("Section #%d ends at %.1f and is joined to nothing there; "
                    + "use /rcmc switch create <throat> <start|end> <branch> <start|end> ... instead",
                    throughId, at));
            }
            split = atStart
                ? new Split(partner, own, "Using the joint between " + partner + " and " + own + ".")
                : new Split(own, partner, "Using the joint between " + own + " and " + partner + ".");
        }
        else {
            split = splitAt(world, state, throughId, at, SWITCH_SNAP);
        }
        network.disconnect(split.arriving);
        SectionEnd throat = forward ? split.arriving : split.departing;
        SectionEnd straight = forward ? split.departing : split.arriving;
        List<SectionEnd> branches = new ArrayList<>();
        branches.add(straight);
        branches.add(branch);
        try {
            network.addSwitch(throat, branches);
        }
        catch (IllegalArgumentException e) {
            network.connect(split.arriving, split.departing);
            state.markTrackDirty(world);
            broadcast(world, state);
            throw new Refused("Split, but the switch could not be made: " + e.getMessage());
        }
        state.markTrackDirty(world);
        broadcast(world, state);
        return split.message + " Switch at " + throat + ": straight on to " + straight + ", diverging to "
            + branch + ". Lined straight.";
    }

    private static void broadcast(World world, RcmcWorldState state) {
        int dimension = world.provider.getDimension();
        com.micatechnologies.minecraft.rcmc.net.PacketTrackSync.broadcast(state.network(), dimension);
    }

    /** A node within {@code snap} of {@code distance} that the section can be split at, or -1. */
    private static int nodeAt(TrackSection section, double distance, double snap) {
        int n = section.nodes().size();
        int best = -1;
        double bestGap = snap;
        for (int i = 0; i < n; i++) {
            if (!section.isClosed() && (i == 0 || i == n - 1)) {
                continue;
            }
            double gap = Math.abs(section.nodeDistance(i) - distance);
            if (section.isClosed()) {
                gap = Math.min(gap, section.totalLength() - gap);
            }
            if (gap <= bestGap) {
                bestGap = gap;
                best = i;
            }
        }
        return best;
    }

    /** Where a node at {@code distance} goes: after every node before it. */
    private static int insertionIndex(TrackSection section, double distance) {
        int index = 0;
        while (index < section.nodes().size() && section.nodeDistance(index) < distance) {
            index++;
        }
        return index;
    }
}
