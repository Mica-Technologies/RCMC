package com.micatechnologies.minecraft.rcmc.builder;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.physics.element.BrakeRun;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.DriveTyres;
import com.micatechnologies.minecraft.rcmc.physics.element.LaunchTrack;
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
     * The segment type that would produce {@code element}, or {@code null} for an element with no
     * authoring path.
     *
     * <p>The inverse of {@link #create}, and it exists so the track editor can cycle a span's type
     * without keeping its own table of "what follows what". It previously did keep one, keyed on
     * {@code ElementCodec}'s type strings, and its own comment said the two could drift apart —
     * which they duly did the moment a type was added here.</p>
     *
     * <p>A {@code null} return is the signal this class is meant to give: an element the builder
     * cannot express. That is a real state — {@code LaunchTrack} sat in exactly it, complete and
     * persisted but unreachable, because nothing in {@link #create} produced one.</p>
     */
    public static TrackBuildSession.SegmentType segmentTypeOf(RideElement element) {
        if (element instanceof ChainLift) {
            return TrackBuildSession.SegmentType.LIFT;
        }
        if (element instanceof LaunchTrack) {
            return ((LaunchTrack) element).targetSpeed() < 0.0D
                ? TrackBuildSession.SegmentType.LAUNCH_BACKWARD : TrackBuildSession.SegmentType.LAUNCH;
        }
        if (element instanceof BrakeRun) {
            return ((BrakeRun) element).mode() == BrakeRun.Mode.BLOCK
                ? TrackBuildSession.SegmentType.BLOCK_BRAKE : TrackBuildSession.SegmentType.BRAKE;
        }
        if (element instanceof DriveTyres) {
            return TrackBuildSession.SegmentType.TYRES;
        }
        if (element instanceof com.micatechnologies.minecraft.rcmc.physics.element.TransferTrack) {
            return TrackBuildSession.SegmentType.TRANSFER;
        }
        if (element instanceof StationPlatform) {
            return TrackBuildSession.SegmentType.STATION;
        }
        return null;
    }

    /**
     * The element {@code type} makes for exactly {@code from}..{@code to} on {@code sectionId}, or
     * {@code null} for plain track — for editing one span of committed track, where the span is
     * known and {@link #build}'s node tags would have to be arranged to say it.
     */
    public static RideElement forSpan(TrackBuildSession.SegmentType type, int sectionId,
                                      double from, double to) {
        return type == TrackBuildSession.SegmentType.PLAIN || to <= from
            ? null : create(type, sectionId, from, to, RcmcConstants.SECONDS_PER_TICK);
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
            case LAUNCH:
                // A launch is a force, not a speed constraint, so the span the builder tagged is
                // what decides the exit speed: 8 blocks/s^2 needs about 30 blocks to reach 22, and
                // a shorter run simply leaves slower (sqrt(2*a*length)). That is the honest
                // behaviour of a real launch and it is why the default is stated as a target the
                // motors aim for rather than a speed they guarantee.
                //
                // Positive target = the direction of increasing distance, which is the direction
                // the builder was laying track in when they tagged the span.
                return new LaunchTrack(sectionId, from, to, LAUNCH_TARGET_SPEED, LAUNCH_ACCELERATION);
            case LAUNCH_BACKWARD:
                // The same motors fired the other way: toward decreasing distance, against the
                // direction the track was laid. What a shuttle coaster uses to send a train back
                // up its rear spike.
                return new LaunchTrack(sectionId, from, to, -LAUNCH_TARGET_SPEED, LAUNCH_ACCELERATION);
            case BRAKE:
                return new BrakeRun(sectionId, from, to, 6.0D, 6.0D, BrakeRun.Mode.TRIM, tick);
            case BLOCK_BRAKE:
                // Trims to a crawl on every pass, so a train is always slow enough to be stopped at
                // its end; the stop itself is the block system's, when the block ahead is occupied.
                // /rcmc block <id> auto puts a block boundary at the end of each one.
                return new BrakeRun(sectionId, from, to, BLOCK_BRAKE_PASS_SPEED, 6.0D,
                    BrakeRun.Mode.BLOCK, tick);
            case TYRES:
                // Walking pace. Drive tyres position a train within a station; anything faster
                // reads as a launch, which is the element next to this one in the palette.
                return new DriveTyres(sectionId, from, to, 2.0D, 3.0D, tick);
            case TRANSFER:
                // Tyres, at the same walking pace, until /rcmc transfer links a storage track.
                return new com.micatechnologies.minecraft.rcmc.physics.element.TransferTrack(
                    sectionId, from, to, 2.0D, 3.0D, tick,
                    com.micatechnologies.minecraft.rcmc.physics.element.TransferTrack.UNLINKED, 0.0D);
            case STATION:
                // Stop shortly before the far end, leaving room to accelerate away from the
                // platform before whatever follows takes over.
                return new StationPlatform(sectionId, from, to,
                    Math.max(from, to - 3.0D), 6.0D, 60, 4.0D, 6.0D, tick);
            default:
                return null;
        }
    }

    /** Speed a block brake lets a train through at when the block ahead is clear, blocks/s. */
    static final double BLOCK_BRAKE_PASS_SPEED = 4.0D;

    /** Launch speed the motors aim for, blocks/s — reached only if the tagged run is long enough. */
    private static final double LAUNCH_TARGET_SPEED = 22.0D;

    /** Launch acceleration, blocks/s². Roughly 0.8 g, which is a firm but not brutal LSM launch. */
    private static final double LAUNCH_ACCELERATION = 8.0D;
}
