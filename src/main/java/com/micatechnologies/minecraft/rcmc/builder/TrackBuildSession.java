package com.micatechnologies.minecraft.rcmc.builder;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The nodes a player has placed but not yet committed to a section.
 *
 * <p>Track cannot be built one node at a time into a live {@code TrackSection}: a section needs at
 * least two nodes to have any geometry at all, and every edit rebuilds its splines and frames. So
 * placement accumulates here and is committed in one go, which also makes "undo the last node"
 * trivial and means a half-built curve never enters the network where a train could find it.</p>
 *
 * <p>Server-side only, and deliberately not persisted: an unfinished session is scratch work. If a
 * player logs out mid-build they lose the pending nodes, which is the right trade — the
 * alternative is a save format for a half-built object that has no meaning to anything else.</p>
 */
public final class TrackBuildSession {

    private static final Map<UUID, TrackBuildSession> SESSIONS = new HashMap<>();

    /**
     * Segment types a builder can lay down, in the order the tool cycles through them.
     *
     * <p>Recorded per node rather than per section: a real coaster changes character along its
     * length — station, then chain, then plain track — and forcing one type per section would make
     * a builder cut the layout into pieces to express that, which is exactly the busywork the tool
     * exists to remove.</p>
     */
    public enum SegmentType {
        PLAIN("Plain track"),
        LIFT("Chain lift"),
        BRAKE("Brake run"),
        STATION("Station");

        private final String label;

        SegmentType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public SegmentType next() {
            SegmentType[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    private final List<TrackNode> pending = new ArrayList<>();

    /** Segment type in force from each pending node onward; parallel to {@link #pending}. */
    private final List<SegmentType> pendingTypes = new ArrayList<>();

    private double bankDegrees;
    private boolean closing;
    private SegmentType currentType = SegmentType.PLAIN;

    /**
     * Vertical offset applied to the next placed node, in blocks.
     *
     * <p>Track rarely wants to sit exactly one block above whatever the cursor happens to be on —
     * a lift crest or an airtime hill is defined by being well clear of the ground. Without this
     * the only way to place elevated track is to build a scaffold to stand the cursor on first.</p>
     */
    private double heightOffset;

    /**
     * The free end of an existing section this chain is continuing from, or {@code null} for a
     * fresh section.
     *
     * <p>Recorded when the first node snaps rather than worked out at commit time: by then the
     * first node sits exactly on the old endpoint and is indistinguishable from a node a builder
     * happened to place there, and guessing would silently swallow a section a builder meant to
     * keep separate.</p>
     */
    private com.micatechnologies.minecraft.rcmc.track.TrackAttachment.Target attachment;

    public com.micatechnologies.minecraft.rcmc.track.TrackAttachment.Target attachment() {
        return attachment;
    }

    public void setAttachment(
        com.micatechnologies.minecraft.rcmc.track.TrackAttachment.Target target) {
        this.attachment = target;
    }

    public static TrackBuildSession of(UUID playerId) {
        return SESSIONS.computeIfAbsent(playerId, id -> new TrackBuildSession());
    }

    public static void clear(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    public List<TrackNode> pending() {
        return pending;
    }

    public int size() {
        return pending.size();
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }

    public void add(TrackNode node) {
        pending.add(node);
        pendingTypes.add(currentType);
    }

    public List<SegmentType> pendingTypes() {
        return pendingTypes;
    }

    public SegmentType currentType() {
        return currentType;
    }

    /** Advances to the next segment type and returns it, for the tool to report. */
    public SegmentType cycleType() {
        currentType = currentType.next();
        return currentType;
    }

    public double heightOffset() {
        return heightOffset;
    }

    /**
     * Adjusts the height offset, clamped to a range that keeps a node reachable.
     *
     * <p>The ceiling is generous — lift hills are tall — but not unbounded: an offset large enough
     * to put a node outside the world would fail at commit time with a confusing error rather than
     * at the moment the builder scrolled past the limit.</p>
     */
    public void adjustHeightOffset(double delta) {
        heightOffset = Math.max(-32.0D, Math.min(64.0D, heightOffset + delta));
    }

    /** Removes and returns the most recently placed node, or {@code null} if there is none. */
    public TrackNode undo() {
        if (pending.isEmpty()) {
            return null;
        }
        pendingTypes.remove(pendingTypes.size() - 1);
        return pending.remove(pending.size() - 1);
    }

    public void reset() {
        attachment = null;
        pending.clear();
        pendingTypes.clear();
        bankDegrees = 0.0D;
        closing = false;
        heightOffset = 0.0D;
        // currentType deliberately survives a reset: a builder laying several lift sections in a
        // row should not have to re-select it after every commit.
    }

    /** Bank applied to subsequently placed nodes, in degrees. */
    public double bankDegrees() {
        return bankDegrees;
    }

    public void setBankDegrees(double degrees) {
        // Beyond a full turn the roll is indistinguishable from its wrapped equivalent, but the
        // value stays unbounded on purpose elsewhere (a corkscrew authors +/-360); this is just
        // the manual adjustment, so clamping it keeps the tool usable.
        this.bankDegrees = Math.max(-180.0D, Math.min(180.0D, degrees));
    }

    /** Whether the committed section will be a closed circuit. */
    public boolean isClosing() {
        return closing;
    }

    public void setClosing(boolean value) {
        this.closing = value;
    }
}
