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

    private final String name;
    private final List<TransitStation> stations;
    private final boolean loop;
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

    @Override
    public String toString() {
        return "TransitLine{" + name + ", " + stations.size() + " stations"
            + (loop ? ", loop" : "") + '}';
    }
}
