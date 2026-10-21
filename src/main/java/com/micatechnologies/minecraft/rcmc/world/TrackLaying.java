package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.builder.SegmentElements;
import com.micatechnologies.minecraft.rcmc.builder.TrackBuildSession.SegmentType;
import com.micatechnologies.minecraft.rcmc.net.PacketElementSync;
import com.micatechnologies.minecraft.rcmc.net.PacketTrackSync;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.track.TrackAttachment;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.World;

/**
 * Commits a run of nodes to the network as a section: the one path the track tool and
 * {@code /rcmc track lay} share.
 *
 * <p>It was inline in the track tool. A command that built sections its own way would drift from
 * it — hardware made differently, a station without its platform, a client never told — and a
 * section laid by a script would behave unlike one laid by hand in ways nobody could see until a
 * train ran on it. One path, so laying by command is laying by clicking without the clicks.</p>
 */
public final class TrackLaying {

    /** Blocks of platform either side of a station segment, the edge course included. */
    private static final int STATION_PLATFORM_WIDTH = 3;

    private TrackLaying() {
        throw new AssertionError("No instances.");
    }

    /** A refusal, worded for the builder. Nothing was changed. */
    public static final class Refused extends Exception {
        public Refused(String message) {
            super(message);
        }
    }

    /** What was built. */
    public static final class Result {
        public final TrackSection section;
        /** {@code true} when an existing section was continued rather than a new one created. */
        public final boolean extended;
        public final List<RideElement> created;
        /** One line per station platform laid, for the builder. */
        public final List<String> platforms;

        Result(TrackSection section, boolean extended, List<RideElement> created, List<String> platforms) {
            this.section = section;
            this.extended = extended;
            this.created = Collections.unmodifiableList(created);
            this.platforms = Collections.unmodifiableList(platforms);
        }
    }

    /**
     * Builds {@code nodes} into the network, adds the hardware {@code types} describe, lays station
     * platforms, records the edit and tells every client.
     *
     * @param types      the segment type recorded at each node, parallel to {@code nodes}
     * @param closing    close the result into a circuit
     * @param attachment the free end to continue, or {@code null} for a new section
     * @param styleId    a new section's style ({@code null} for coaster); an extension keeps the
     *                   style of the section it continues
     */
    public static Result lay(World world, RcmcWorldState state, List<TrackNode> nodes, List<SegmentType> types,
                             boolean closing, TrackAttachment.Target attachment, String styleId)
        throws Refused {
        int minimum = closing ? 3 : 2;
        if (nodes.size() < minimum) {
            throw new Refused("Need at least " + minimum + " nodes to build "
                + (closing ? "a circuit" : "a section") + "; you have " + nodes.size() + ".");
        }

        boolean extending = attachment != null && state.network().hasSection(attachment.sectionId);
        TrackSection section;
        List<SegmentType> sectionTypes;
        try {
            if (extending) {
                TrackSection existing = state.network().section(attachment.sectionId);
                if (!attachment.appends() && !canPrependSafely(state, existing)) {
                    // Prepending redistributes every distance on the section, and not by a constant
                    // (see TrackAttachment.extend). Anything anchored by distance would end up over
                    // different track, so refuse rather than move a lift hill silently.
                    throw new Refused("Section #" + existing.id()
                        + " has ride hardware or a train on it, so it can only be continued from its"
                        + " far end — building onto its start would move everything already placed.");
                }
                section = TrackAttachment.extend(existing, attachment.end, nodes, closing);
                sectionTypes = TrackAttachment.alignTypes(existing, attachment.end, nodes, types,
                    SegmentType.PLAIN, section);
            }
            else {
                int id = state.network().allocateSectionId();
                section = new TrackSection(id, nodes, closing, styleId);
                sectionTypes = types;
            }
        }
        catch (IllegalArgumentException e) {
            // Degenerate node layout. Report it; the caller keeps whatever it was building from.
            throw new Refused("Could not build that section: " + e.getMessage());
        }

        if (extending) {
            state.network().replaceSection(section);
        }
        else {
            state.network().addSection(section);
        }
        List<RideElement> created = SegmentElements.build(section, sectionTypes);
        for (RideElement element : created) {
            state.elements().add(element);
        }
        // A station gets its platform: decking both sides at the cars' floor, and air gates where
        // the train stops — see CoasterStations.layPlatform. Only into air; nothing is replaced.
        List<String> platforms = new ArrayList<>();
        for (RideElement element : created) {
            if (element instanceof StationPlatform) {
                PlatformBuilder.Result laid = CoasterStations.layPlatform(world, state,
                    (StationPlatform) element, true, true, STATION_PLATFORM_WIDTH);
                platforms.add(laid.blocks + " platform blocks and " + laid.gates + " air gates");
            }
        }
        state.markTrackDirty(world);
        // BOTH, always. Sending only the track leaves clients with a lift hill that renders as
        // plain rail — the element exists and works, but nothing draws a chain on it.
        int dimension = world.provider.getDimension();
        RcmcNetwork.sendToAllIn(new PacketTrackSync(state.network()), dimension);
        RcmcNetwork.sendToAllIn(new PacketElementSync(state.elements()), dimension);
        return new Result(section, extending, created, platforms);
    }

    /**
     * Whether {@code section} can have track prepended without stranding anything anchored to it.
     *
     * <p>Only a bare section qualifies. See {@link TrackAttachment#extend} for why a caller cannot
     * simply shift the anchors along instead.</p>
     */
    public static boolean canPrependSafely(RcmcWorldState state, TrackSection section) {
        for (RideElement element : state.elements().elements()) {
            if (element.sectionId() == section.id()) {
                return false;
            }
        }
        for (com.micatechnologies.minecraft.rcmc.physics.Train train : state.trains().asMap().values()) {
            if (train.reference() != null && train.reference().sectionId() == section.id()) {
                return false;
            }
        }
        // Stations and berths are stored by distance too.
        for (com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation station
            : state.transit().stations()) {
            for (com.micatechnologies.minecraft.rcmc.physics.transit.TransitPlatform berth : station.platforms()) {
                if (berth.stopPoint().sectionId() == section.id()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The free end of {@code section} a polyline appended to it continues: always the far end. */
    public static TrackAttachment.Target endOf(TrackSection section) {
        return new TrackAttachment.Target(section.id(), TrackNetwork.End.END,
            section.endpointAt(TrackNetwork.End.END), section.exitDirectionAt(TrackNetwork.End.END), 0.0D);
    }
}
