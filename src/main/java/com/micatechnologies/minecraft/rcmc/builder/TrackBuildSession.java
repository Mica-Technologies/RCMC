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

    private final List<TrackNode> pending = new ArrayList<>();
    private double bankDegrees;
    private boolean closing;

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
    }

    /** Removes and returns the most recently placed node, or {@code null} if there is none. */
    public TrackNode undo() {
        return pending.isEmpty() ? null : pending.remove(pending.size() - 1);
    }

    public void reset() {
        pending.clear();
        bankDegrees = 0.0D;
        closing = false;
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
