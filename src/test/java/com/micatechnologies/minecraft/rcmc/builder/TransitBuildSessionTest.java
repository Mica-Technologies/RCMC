package com.micatechnologies.minecraft.rcmc.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The transit tool's state machine — what a click means, and when a thing may be created.
 *
 * <p>Worth testing on its own rather than only through the item: this is where the rules a builder
 * bumps into live (you cannot make a line from one station, you cannot make a switch whose branch
 * is also its throat), and the item layer is untestable without a running game. The session holds
 * no Minecraft types precisely so this file can exist.</p>
 */
class TransitBuildSessionTest {

    private static TrackNetwork.SectionEnd end(int section, TrackNetwork.End which) {
        return new TrackNetwork.SectionEnd(section, which);
    }

    @Test
    @DisplayName("modes cycle back around to the first")
    void modesCycle() {
        TransitBuildSession session = new TransitBuildSession();

        assertEquals(TransitBuildSession.Mode.STATION, session.mode());
        TransitBuildSession.Mode[] all = TransitBuildSession.Mode.values();
        for (int i = 0; i < all.length; i++) {
            session.cycleMode();
        }
        assertEquals(TransitBuildSession.Mode.STATION, session.mode(),
            "a full cycle must return to where it started, or the tool has a dead end");
    }

    @Test
    @DisplayName("changing mode abandons the half-built line rather than carrying it over")
    void cyclingModeClearsPending() {
        TransitBuildSession session = new TransitBuildSession();
        session.cycleMode();
        session.addStop("North");
        session.addStop("South");
        assertTrue(session.canCommitLine());

        // All the way around, back to LINE — so the mode is the same one that collected the stops
        // and the only thing that can explain them being gone is the clearing under test.
        for (int i = 0; i < TransitBuildSession.Mode.values().length; i++) {
            session.cycleMode();
        }

        assertEquals(TransitBuildSession.Mode.LINE, session.mode());
        assertTrue(session.lineStops().isEmpty(),
            "stops picked before a mode change must not silently become part of a later line");
        assertFalse(session.canCommitLine());
    }

    @Test
    @DisplayName("a line needs two different stations")
    void lineNeedsTwoDistinctStops() {
        TransitBuildSession session = new TransitBuildSession();

        assertFalse(session.canCommitLine(), "no stops is not a line");
        session.addStop("North");
        assertFalse(session.canCommitLine(), "one stop is a place, not a line");
        session.addStop("South");
        assertTrue(session.canCommitLine());
    }

    @Test
    @DisplayName("clicking the same platform twice in a row is a slip, not a second stop")
    void consecutiveDuplicateStopIsRejected() {
        TransitBuildSession session = new TransitBuildSession();
        assertTrue(session.addStop("North"));

        assertFalse(session.addStop("North"));
        assertFalse(session.addStop("north"), "the check must not be case-sensitive");
        assertEquals(1, session.lineStops().size());
    }

    @Test
    @DisplayName("a line may pass back through a station it already served")
    void nonAdjacentRepeatIsAllowed() {
        TransitBuildSession session = new TransitBuildSession();
        session.addStop("North");
        session.addStop("Interchange");

        assertTrue(session.addStop("North"),
            "an out-and-back shuttle legitimately returns to a stop it already called at");
        assertEquals(Arrays.asList("North", "Interchange", "North"), session.lineStops());
    }

    @Test
    @DisplayName("the first switch end picked is the throat, the rest are branches")
    void firstEndIsTheThroat() {
        TransitBuildSession session = new TransitBuildSession();

        assertTrue(session.addSwitchEnd(end(1, TrackNetwork.End.END)));
        assertEquals(end(1, TrackNetwork.End.END), session.switchThroat());
        assertTrue(session.switchBranches().isEmpty());

        assertTrue(session.addSwitchEnd(end(2, TrackNetwork.End.START)));
        assertTrue(session.addSwitchEnd(end(3, TrackNetwork.End.START)));
        assertEquals(2, session.switchBranches().size());
    }

    @Test
    @DisplayName("a switch needs a throat and two branches before it can be created")
    void switchNeedsThroatAndTwoBranches() {
        TransitBuildSession session = new TransitBuildSession();

        assertFalse(session.canCommitSwitch());
        session.addSwitchEnd(end(1, TrackNetwork.End.END));
        assertFalse(session.canCommitSwitch(), "a throat alone leads nowhere");
        session.addSwitchEnd(end(2, TrackNetwork.End.START));
        assertFalse(session.canCommitSwitch(), "one branch is a join, not a switch");
        session.addSwitchEnd(end(3, TrackNetwork.End.START));
        assertTrue(session.canCommitSwitch());
    }

    @Test
    @DisplayName("an end already picked cannot be picked again")
    void duplicateEndsAreRejected() {
        TransitBuildSession session = new TransitBuildSession();
        session.addSwitchEnd(end(1, TrackNetwork.End.END));
        session.addSwitchEnd(end(2, TrackNetwork.End.START));

        assertFalse(session.addSwitchEnd(end(1, TrackNetwork.End.END)),
            "the throat must not also be a branch — addSwitch would reject the whole switch later");
        assertFalse(session.addSwitchEnd(end(2, TrackNetwork.End.START)));
        assertEquals(1, session.switchBranches().size());
    }

    @Test
    @DisplayName("the two ends of one section are different ends")
    void endsOfOneSectionAreDistinct() {
        TransitBuildSession session = new TransitBuildSession();
        session.addSwitchEnd(end(1, TrackNetwork.End.START));

        assertTrue(session.addSwitchEnd(end(1, TrackNetwork.End.END)),
            "a section end is (section, which end) — the same section's other end is a real choice");
    }

    @Test
    @DisplayName("clearing forgets the pending work but keeps the mode")
    void clearPendingKeepsMode() {
        TransitBuildSession session = new TransitBuildSession();
        session.cycleMode();
        session.addStop("North");

        session.clearPending();

        assertEquals(TransitBuildSession.Mode.LINE, session.mode());
        assertTrue(session.lineStops().isEmpty());
        assertNull(session.switchThroat());
    }

    @Test
    @DisplayName("stops are a copy, so a caller cannot edit the session's list behind its back")
    void lineStopsAreDefensivelyCopied() {
        TransitBuildSession session = new TransitBuildSession();
        session.addStop("North");

        session.lineStops().add("Ghost");

        assertEquals(1, session.lineStops().size());
    }

    @Test
    @DisplayName("a player's session persists between clicks, and is dropped on logout")
    void sessionsAreRememberedPerPlayerAndReleasable() {
        UUID player = UUID.randomUUID();
        TransitBuildSession first = TransitBuildSession.of(player);
        first.addStop("North");

        assertSame(first, TransitBuildSession.of(player),
            "a builder mid-line must not lose their stops between two clicks");

        TransitBuildSession.clear(player);

        assertNotSame(first, TransitBuildSession.of(player));
        assertTrue(TransitBuildSession.of(player).lineStops().isEmpty());
        TransitBuildSession.clear(player);
    }
}
