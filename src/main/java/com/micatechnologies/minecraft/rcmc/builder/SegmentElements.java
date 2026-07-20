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
     * <p>A run of nodes sharing a type becomes <b>one</b> element spanning from the first node of
     * the run to the node <em>after</em> the last one. That trailing node matters and was the bug:
     * a type recorded at node {@code i} describes the track <em>leaving</em> that node, so a run
     * covering nodes 0..2 must reach node 3 to include the track those tags describe. Ending at the
     * last tagged node left the final span unpowered — and for a run of a single node, produced an
     * element of zero length that did nothing at all.</p>
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
                double from = section.nodeDistance(i);
                // Extend to the node after the run, or to the end of the section on a closed
                // circuit where the run reaches the last node.
                double to;
                if (runEnd + 1 < nodeCount) {
                    to = section.nodeDistance(runEnd + 1);
                }
                else {
                    to = section.totalLength();
                }
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
