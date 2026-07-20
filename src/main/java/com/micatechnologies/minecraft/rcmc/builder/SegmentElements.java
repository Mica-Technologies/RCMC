package com.micatechnologies.minecraft.rcmc.builder;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.physics.element.BrakeRun;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a builder's per-node segment types into ride elements.
 *
 * <p>Pulled out of {@code ItemTrackTool} so it can be tested. It was originally inline there, which
 * meant the only way to check it was to build a coaster by hand and ride it — and it shipped
 * broken: track marked as chain lift behaved as plain track, because a run of nodes all sharing
 * one type produced a span the element never actually covered.</p>
 *
 * <p>Free of Minecraft types, like the rest of the geometry and physics path, so the mapping from
 * "what the builder selected" to "what the ride does" is verifiable without a game instance.</p>
 */
public final class SegmentElements {

    private SegmentElements() {
        throw new AssertionError("No instances.");
    }

    /**
     * Builds elements for contiguous runs of the same non-plain type.
     *
     * <p><b>A type recorded at node {@code i} describes the span ARRIVING at it</b> — the track
     * from node {@code i-1} to node {@code i}. That is what the builder just drew: they select a
     * type, then click, and the track that appears between their last node and the new one is the
     * track they meant to type. So a run of tagged nodes {@code [i..j]} becomes one element
     * spanning {@code nodeDistance(i-1)} to {@code nodeDistance(j)}.</p>
     *
     * <p>Getting this backwards is what made segment types appear not to work. Treating the type as
     * describing the span <em>leaving</em> node {@code i} puts every element exactly one span later
     * than it was drawn — so the stretch the builder marked as lift came out plain, and the plain
     * stretch after it came out as lift. The HUD was right the whole time; the track disagreed with
     * it by one span.</p>
     *
     * <p>A run starting at node 0 has no preceding node to span from, so it starts at the section's
     * beginning. The first click places a node without drawing any track, so there is nothing
     * earlier for it to describe.</p>
     *
     * @param types segment type recorded at each node, parallel to the section's nodes
     */
    public static List<RideElement> build(TrackSection section,
                                          List<TrackBuildSession.SegmentType> types) {
        List<RideElement> elements = new ArrayList<>();
        if (section == null || types == null || types.isEmpty()) {
            return elements;
        }
        int nodeCount = Math.min(types.size(), section.nodes().size());
        double tick = RcmcConstants.SECONDS_PER_TICK;
        int id = section.id();

        int i = 0;
        while (i < nodeCount) {
            TrackBuildSession.SegmentType type = types.get(i);
            int runEnd = i;
            while (runEnd + 1 < nodeCount && types.get(runEnd + 1) == type) {
                runEnd++;
            }

            if (type != TrackBuildSession.SegmentType.PLAIN) {
                // Start at the node BEFORE the run: the first tagged node's type describes the
                // track arriving at it, which begins at its predecessor.
                double from = section.nodeDistance(Math.max(0, i - 1));
                // End at the last tagged node — that is where the tagged track stops. On a closed
                // circuit a run reaching the final node continues to the seam.
                double to = runEnd >= nodeCount - 1 && section.isClosed()
                    ? section.totalLength()
                    : section.nodeDistance(runEnd);
                if (to > from) {
                    RideElement element = create(type, id, from, to, tick);
                    if (element != null) {
                        elements.add(element);
                    }
                }
            }
            i = runEnd + 1;
        }
        return elements;
    }

    /**
     * Conservative defaults per type. Tuning a specific lift's speed or a brake's target belongs in
     * the ride-controller UI, not in a placement gesture — a builder tagging track is saying what
     * it <em>is</em>, not how it is configured.
     */
    private static RideElement create(TrackBuildSession.SegmentType type, int sectionId,
                                      double from, double to, double tick) {
        switch (type) {
            case LIFT:
                return new ChainLift(sectionId, from, to, 5.0D, 12.0D, tick);
            case BRAKE:
                return new BrakeRun(sectionId, from, to, 6.0D, 6.0D, BrakeRun.Mode.TRIM, tick);
            case STATION:
                // Stop shortly before the far end, leaving room to accelerate away from the
                // platform before whatever follows takes over.
                return new StationPlatform(sectionId, from, to,
                    Math.max(from, to - 3.0D), 6.0D, 60, 4.0D, 6.0D, tick);
            default:
                return null;
        }
    }
}
