package com.micatechnologies.minecraft.rcmc.physics.transit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A named metro line: an ordered list of {@link TransitStation}s and how service runs over them.
 *
 * <p>Two service shapes, chosen by {@code loop}:</p>
 * <ul>
 *   <li><b>Out-and-back</b> ({@code loop = false}) — trains serve the stations in order, reverse
 *       at each end, and serve them back. The two ends are termini; direction labels name the two
 *       senses of travel ("INBOUND"/"OUTBOUND" by default, or whatever the operator renames them
 *       to — real networks say "Northbound", "toward Airport", and the signage in M7 renders
 *       these verbatim).</li>
 *   <li><b>Loop</b> ({@code loop = true}) — after the last station comes the first again, no
 *       reversal. The "outbound" label reads as the direction of increasing station index.</li>
 * </ul>
 *
 * <p><b>How an out-and-back line turns round</b> is a separate question from its service pattern,
 * and {@link #turnsBackOnLoop()} is the answer. A stub terminus is a dead end: the train stops,
 * changes ends, and leaves the way it came, on the same track and the same platform. A
 * <b>turnback loop</b> is what most real metros actually have — inbound and outbound are separate
 * tracks joined by a turning loop at each terminus, so the train never reverses at all. It keeps
 * driving forward, round the loop, and comes back on the other track, which is what lets inbound
 * and outbound run at the same time instead of taking turns down one rail.</p>
 *
 * <p>The service pattern is identical either way — stations in order, then back in reverse — so
 * this changes exactly one thing: whether the train's <em>physical</em> direction of travel flips
 * with the service direction. See {@code LineService.advanceToNextStop}.</p>
 *
 * <p>This is deliberately only the <em>service</em> model — which stops exist, in what order,
 * under what names. Track topology (which sections, which switches) is the network's business;
 * the route between consecutive stations is discovered by walking the track
 * ({@code TrackWalk}), not stored here, so re-laying track between two stations never
 * invalidates a line.</p>
 *
 * <p>Immutable, pure Java. Station order is the identity of the line; signage (M7) renders it
 * directly.</p>
 */
public final class TransitLine {

    /**
     * The three shapes of line, by what a train does at the end of the route.
     *
     * <p>Named for the builder, who picks one: a {@code loop} never ends; a {@code shuttle} runs to
     * a stub terminus and changes ends; a {@code turnback} line runs out on one track and back on
     * another, turning round on a loop at each end.</p>
     */
    public enum Kind {
        LOOP, SHUTTLE, TURNBACK;

        /** The word the builder types and reads. */
        public String label() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public Kind next() {
            return values()[(ordinal() + 1) % values().length];
        }

        /** Parses {@link #label}, case-insensitively; {@code null} for anything else. */
        public static Kind byLabel(String label) {
            for (Kind kind : values()) {
                if (kind.label().equalsIgnoreCase(label)) {
                    return kind;
                }
            }
            return null;
        }
    }

    /** A line of this kind, with the default direction labels. */
    public static TransitLine of(String name, List<TransitStation> stations, Kind kind) {
        return new TransitLine(name, stations, kind == Kind.LOOP, kind == Kind.TURNBACK,
            "INBOUND", "OUTBOUND");
    }

    private final String name;
    private final List<TransitStation> stations;
    private final boolean loop;
    private final boolean turnbackLoop;
    private final String inboundLabel;
    private final String outboundLabel;

    public TransitLine(String name, List<TransitStation> stations, boolean loop) {
        this(name, stations, loop, "INBOUND", "OUTBOUND");
    }

    /**
     * @param name          the line's display name ("Red Line")
     * @param stations      route order; at least two
     * @param loop          see the class javadoc
     * @param inboundLabel  display label for travel toward lower station indices
     * @param outboundLabel display label for travel toward higher station indices
     */
    public TransitLine(String name, List<TransitStation> stations, boolean loop,
                       String inboundLabel, String outboundLabel) {
        this(name, stations, loop, false, inboundLabel, outboundLabel);
    }

    /**
     * @param turnbackLoop whether an out-and-back line's termini are turning loops rather than
     *                     stub ends — see {@link #turnsBackOnLoop()}. Meaningless, and forced
     *                     false, on a {@code loop} line, which never reaches a terminus at all
     */
    public TransitLine(String name, List<TransitStation> stations, boolean loop,
                       boolean turnbackLoop, String inboundLabel, String outboundLabel) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("a line needs a name");
        }
        if (stations == null || stations.size() < 2) {
            throw new IllegalArgumentException("a line needs at least two stations");
        }
        if (inboundLabel == null || inboundLabel.isEmpty()
            || outboundLabel == null || outboundLabel.isEmpty()) {
            throw new IllegalArgumentException("direction labels must be non-empty — signage renders them");
        }
        this.name = name;
        this.stations = Collections.unmodifiableList(new ArrayList<>(stations));
        this.loop = loop;
        this.turnbackLoop = !loop && turnbackLoop;
        this.inboundLabel = inboundLabel;
        this.outboundLabel = outboundLabel;
    }

    public String name() {
        return name;
    }

    public List<TransitStation> stations() {
        return stations;
    }

    public TransitStation station(int index) {
        return stations.get(index);
    }

    public int stationCount() {
        return stations.size();
    }

    /** Index of the station with this name (case-insensitive), or {@code -1}. Signage's lookup. */
    public int indexOfStation(String stationName) {
        if (stationName == null) {
            return -1;
        }
        for (int i = 0; i < stations.size(); i++) {
            if (stations.get(i).name().equalsIgnoreCase(stationName)) {
                return i;
            }
        }
        return -1;
    }

    public boolean isLoop() {
        return loop;
    }

    public Kind kind() {
        return loop ? Kind.LOOP : turnbackLoop ? Kind.TURNBACK : Kind.SHUTTLE;
    }

    /**
     * Whether this out-and-back line turns round on a loop rather than reversing in the platform.
     *
     * <p>True means the two directions run on separate tracks joined by a turning loop at each
     * terminus — so a train keeps driving forward through the terminus and comes back on the other
     * track, and the berth it reaches at every station is the one on that track. That is what makes
     * a two-platform station mean anything, and what lets inbound and outbound run at once.</p>
     *
     * <p>False is a stub terminus: one track, one platform, and the train changes ends.</p>
     */
    public boolean turnsBackOnLoop() {
        return turnbackLoop;
    }

    /** Display label for travel in service direction {@code -1} (toward lower indices). */
    public String inboundLabel() {
        return inboundLabel;
    }

    /** Display label for travel in service direction {@code +1} (toward higher indices). */
    public String outboundLabel() {
        return outboundLabel;
    }

    /** The label for a signed service direction — what an M7 arrival board prints. */
    public String labelFor(int serviceDirection) {
        return serviceDirection >= 0 ? outboundLabel : inboundLabel;
    }

    /**
     * The destination station a service running in {@code serviceDirection} is ultimately heading
     * for — its terminus — so signage can say "to Alewife" rather than only "OUTBOUND".
     *
     * <p>On an out-and-back line the terminus is the far end in the current sense of travel:
     * {@code +1} (toward higher indices) runs to the last station, {@code -1} to the first. A
     * <b>loop</b> has no terminus — service never reverses and every station is both ahead and
     * behind — so this returns {@code null}, and signage falls back to the bare direction label.</p>
     */
    public String terminusName(int serviceDirection) {
        if (loop) {
            return null;
        }
        return serviceDirection >= 0
            ? stations.get(stations.size() - 1).name()
            : stations.get(0).name();
    }

    @Override
    public String toString() {
        return "TransitLine{" + name + ", " + stations.size() + " stations"
            + (loop ? ", loop" : "") + '}';
    }
}
