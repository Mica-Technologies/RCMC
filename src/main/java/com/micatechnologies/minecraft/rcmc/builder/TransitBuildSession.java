package com.micatechnologies.minecraft.rcmc.builder;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a player is part-way through authoring with the transit tool.
 *
 * <p>The transit family shipped M1–M8 with no authoring path at all: stations, lines, styles and
 * signalling were reachable only by typing chat commands, and switches were not reachable at
 * <em>all</em> — {@code TrackNetwork.addSwitch} had no caller outside the save codec. This session
 * is the state behind the tool that fixes that, and it is deliberately the transit analogue of
 * {@link TrackBuildSession}: server-side, authoritative, scratch-only, never persisted.</p>
 *
 * <p><b>Why a separate tool rather than a mode on the track wand.</b> The wand already cycles
 * segment type, colour and painted part on three keybinds, and Phase 2.2 exists because that is
 * already at its discoverability ceiling. Hanging a second domain off the same item would move the
 * problem rather than solve it. What the two share is the infrastructure — sessions, look-ray
 * picking, preview, chat feedback — not the item.</p>
 *
 * <p><b>Why the modes are what they are.</b> Each corresponds to one thing the underlying
 * transit model can already do and previously could not be asked to do by pointing at it. Nothing
 * here implements new transit behaviour; it makes existing, tested behaviour reachable, which is
 * the entire point of the phase.</p>
 *
 * <p>Free of Minecraft types, like {@link TrackBuildSession}'s data, so the state machine that
 * decides what a click means is unit-testable on a bare JVM. The item layer converts.</p>
 */
public final class TransitBuildSession {

    private static final Map<UUID, TransitBuildSession> SESSIONS = new HashMap<>();

    /**
     * What the next click does. Ordered as a metro is actually built: stations first, then the line
     * through them, then the junctions, then the wires.
     */
    public enum Mode {
        STATION("Station", "Click track to place a stop; sneak+click a stop to remove it"),
        LINE("Line", "Click each stop in order, then press C to create the line"),
        SWITCH("Switch", "Click the throat end, then each branch end, then press C"),
        STYLE("Track style", "Click track to cycle its style through the transit looks");

        private final String label;
        private final String help;

        Mode(String label, String help) {
            this.label = label;
            this.help = help;
        }

        public String label() {
            return label;
        }

        public String help() {
            return help;
        }

        public Mode next() {
            Mode[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    private Mode mode = Mode.STATION;

    /** Station names picked for the line being assembled, in click order. */
    private final List<String> lineStops = new ArrayList<>();

    /** Whether the line under assembly closes back on itself. */
    private boolean loop;

    /** The throat of the switch under assembly, or {@code null}. */
    private TrackNetwork.SectionEnd switchThroat;

    /** Branch ends picked so far for the switch under assembly. */
    private final List<TrackNetwork.SectionEnd> switchBranches = new ArrayList<>();

    public static TransitBuildSession of(UUID playerId) {
        return SESSIONS.computeIfAbsent(playerId, id -> new TransitBuildSession());
    }

    /** Drops a player's scratch state — on logout, exactly as the track builder does. */
    public static void clear(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    public Mode mode() {
        return mode;
    }

    /**
     * Moves to the next mode, abandoning whatever the current one was assembling.
     *
     * <p>Half a line and half a switch are meaningless to any other mode, and carrying them
     * silently across a mode change would make a later commit act on stops the player picked
     * minutes ago in another context. Clearing is the honest behaviour; the tool says so.</p>
     */
    public Mode cycleMode() {
        mode = mode.next();
        clearPending();
        return mode;
    }

    public boolean isLoop() {
        return loop;
    }

    public boolean toggleLoop() {
        loop = !loop;
        return loop;
    }

    // --- Line assembly. ----------------------------------------------------------------------

    /**
     * Adds a stop to the line under assembly.
     *
     * @return false if that station is already the most recent stop — a double click on the same
     *         platform is a slip, not an instruction to serve it twice in a row. Non-adjacent
     *         repeats are allowed: a line legitimately passes back through an interchange.
     */
    public boolean addStop(String stationName) {
        if (stationName == null || stationName.isEmpty()) {
            return false;
        }
        if (!lineStops.isEmpty() && lineStops.get(lineStops.size() - 1).equalsIgnoreCase(stationName)) {
            return false;
        }
        lineStops.add(stationName);
        return true;
    }

    public List<String> lineStops() {
        return new ArrayList<>(lineStops);
    }

    /** Whether the stops picked so far could form a line: at least two distinct stations. */
    public boolean canCommitLine() {
        return new LinkedHashSet<>(lineStops).size() >= 2;
    }

    // --- Switch assembly. --------------------------------------------------------------------

    /**
     * Records a clicked section end for the switch under assembly: the first is the throat, the
     * rest are branches.
     *
     * @return false if that end was already picked — a switch's ends must be distinct, and
     *         {@code addSwitch} would reject the whole thing at commit time with a message about
     *         an end leading two places, long after the click that caused it
     */
    public boolean addSwitchEnd(TrackNetwork.SectionEnd end) {
        if (end == null) {
            return false;
        }
        if (end.equals(switchThroat) || switchBranches.contains(end)) {
            return false;
        }
        if (switchThroat == null) {
            switchThroat = end;
        } else {
            switchBranches.add(end);
        }
        return true;
    }

    public TrackNetwork.SectionEnd switchThroat() {
        return switchThroat;
    }

    public List<TrackNetwork.SectionEnd> switchBranches() {
        return new ArrayList<>(switchBranches);
    }

    /** Whether the ends picked so far form a switch: a throat and at least two branches. */
    public boolean canCommitSwitch() {
        return switchThroat != null && switchBranches.size() >= 2;
    }

    /** Forgets whatever the current mode was assembling, leaving the mode itself alone. */
    public void clearPending() {
        lineStops.clear();
        switchThroat = null;
        switchBranches.clear();
    }

    /** A one-line summary of what is pending, for the tool's feedback line. */
    public String pendingSummary() {
        switch (mode) {
            case LINE:
                return lineStops.isEmpty()
                    ? "no stops picked yet"
                    : String.join(" → ", lineStops) + (loop ? "  (loop)" : "  (shuttle)");
            case SWITCH:
                if (switchThroat == null) {
                    return "no throat picked yet";
                }
                return "throat " + switchThroat + ", " + switchBranches.size() + " branch(es)";
            case STATION:
            case STYLE:
            default:
                return "click track";
        }
    }
}
