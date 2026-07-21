package com.micatechnologies.minecraft.rcmc.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the piece tool owes a builder: a chain that is continuous everywhere, and specifically no
 * worse at the joins between pieces than it is inside them.
 *
 * <p>The assertions below are deliberately written against that requirement rather than against the
 * numbers the current elements happen to produce. A join test that hard-codes "the gap here is
 * 3.7 blocks" passes just as happily when a piece starts a full spacing late and leaves a hole a
 * train's nose would dip into; comparing each join against the spacing the adjacent pieces chose
 * for themselves cannot.</p>
 */
class PieceBuildSessionTest {

    /** Below this two nodes are effectively coincident, which makes the spline turn through a gap
     *  it cannot turn through and is the one placement that reliably looks broken. */
    private static final double DUPLICATE_THRESHOLD = 0.05D;

    private static TrackFrame anchor() {
        return new TrackFrame(new Vec3(100.0D, 64.0D, 100.0D),
            new Vec3(1.0D, 0.0D, 0.0D), Vec3.UP);
    }

    private static PieceBuildSession started() {
        PieceBuildSession session = new PieceBuildSession();
        session.start(anchor());
        return session;
    }

    @Test
    void startPlacesAnAnchorNodeAtTheGivenFrame() {
        PieceBuildSession session = started();
        assertTrue(session.isStarted());
        assertEquals(1, session.nodeCount());
        assertEquals(0.0D, session.nodes().get(0).position().distanceTo(anchor().position), 1.0e-9D);
        assertEquals(0, session.pieceCount());
    }

    @Test
    void appendingBeforeStartingDoesNothing() {
        PieceBuildSession session = new PieceBuildSession();
        assertTrue(session.append().isEmpty());
        assertTrue(session.previewNodes().isEmpty());
        assertEquals(0, session.nodeCount());
    }

    @Test
    void previewMatchesWhatAppendActuallyAdds() {
        for (int index = 0; index < PiecePalette.size(); index++) {
            PieceBuildSession session = started();
            session.cycleSelected(index);
            List<TrackNode> preview = new ArrayList<>(session.previewNodes());
            List<TrackNode> appended = session.append();
            assertEquals(preview, appended,
                "preview must be exactly what the click builds, entry " + index);
        }
    }

    /**
     * The core requirement: chaining any piece after any other leaves a continuous node run.
     *
     * <p>Every ordered pair in the palette is tried, because the failures this catches are
     * pair-specific — an element that assumes it starts level, or that reports an exit frame it did
     * not actually finish at, only shows up behind particular predecessors.</p>
     */
    @Test
    void everyPairOfPiecesJoinsWithoutGapOrDuplicate() {
        for (int first = 0; first < PiecePalette.size(); first++) {
            for (int second = 0; second < PiecePalette.size(); second++) {
                PieceBuildSession session = started();
                session.cycleSelected(first);
                List<TrackNode> firstNodes = session.append();
                assertFalse(firstNodes.isEmpty(), "entry " + first + " generated nothing");
                session.cycleSelected(second - first);
                List<TrackNode> secondNodes = session.append();
                assertFalse(secondNodes.isEmpty(), "entry " + second + " generated nothing");

                String where = "entry " + first + " -> entry " + second;
                double joinGap = firstNodes.get(firstNodes.size() - 1).position()
                    .distanceTo(secondNodes.get(0).position());

                assertTrue(joinGap > DUPLICATE_THRESHOLD,
                    where + ": pieces meet in a duplicate node, gap " + joinGap);

                // The join may be no coarser than the pieces either side of it are internally. This
                // is the assertion that would fail if an element ever started a whole spacing late,
                // or reported an exit position it had not reached.
                double tolerated = 1.1D * Math.max(
                    maximumInternalGap(firstNodes), maximumInternalGap(secondNodes));
                assertTrue(joinGap <= tolerated,
                    where + ": join gap " + joinGap + " exceeds the " + tolerated
                        + " the adjacent pieces use internally");
            }
        }
    }

    /** No gap anywhere in a long mixed chain exceeds the spacing the session asked for. */
    @Test
    void aLongMixedChainNeverGapsWiderThanTheRequestedSpacing() {
        PieceBuildSession session = started();
        for (int index = 0; index < PiecePalette.size(); index++) {
            session.cycleSelected(index - session.selectedIndex());
            session.append();
        }
        List<TrackNode> chain = session.nodes();
        assertTrue(chain.size() > PiecePalette.size());

        // Node spacing is an authoring request for "roughly this far apart"; elements round the
        // segment count up, never down, so no interval may come out longer than it.
        double limit = session.nodeSpacing() * 1.05D;
        for (int i = 1; i < chain.size(); i++) {
            double gap = chain.get(i - 1).position().distanceTo(chain.get(i).position());
            assertTrue(gap > DUPLICATE_THRESHOLD, "duplicate node at index " + i + ", gap " + gap);
            assertTrue(gap <= limit, "gap of " + gap + " at index " + i + " exceeds " + limit);
        }
    }

    /**
     * A chain must not kink at a join.
     *
     * <p>Measured as the turn between the chord entering the join and the chord leaving it, against
     * the sharpest turn either adjacent piece makes internally. A loop turns hard by design, so an
     * absolute angle limit would either fail on the loop or be too loose to catch anything; what is
     * not allowed is for the seam to turn harder than the pieces meeting at it do.</p>
     */
    @Test
    void noJoinTurnsMoreSharplyThanThePiecesItConnects() {
        for (int first = 0; first < PiecePalette.size(); first++) {
            for (int second = 0; second < PiecePalette.size(); second++) {
                PieceBuildSession session = started();
                session.cycleSelected(first);
                session.append();
                int joinIndex = session.nodeCount() - 1;
                session.cycleSelected(second - first);
                session.append();

                List<TrackNode> chain = session.nodes();
                double atJoin = turnDegreesAt(chain, joinIndex);
                double sharpest = Math.max(sharpestTurn(chain, 1, joinIndex - 1),
                    sharpestTurn(chain, joinIndex + 1, chain.size() - 2));
                assertTrue(atJoin <= Math.max(sharpest, 1.0D) * 1.5D + 5.0D,
                    "entry " + first + " -> entry " + second + ": join turns " + atJoin
                        + " degrees, against " + sharpest + " internally");
            }
        }
    }

    /** The chain must be a section a train could actually be put on. */
    @Test
    void theChainCommitsToAValidSection() {
        PieceBuildSession session = started();
        for (int index = 0; index < PiecePalette.size(); index++) {
            session.cycleSelected(index - session.selectedIndex());
            session.append();
        }
        TrackSection section = new TrackSection(1, session.nodes(), false, null);
        assertTrue(section.totalLength() > 50.0D,
            "nine pieces should be a substantial length, got " + section.totalLength());
    }

    @Test
    void undoRemovesOnePieceNotOneNode() {
        PieceBuildSession session = started();
        List<TrackNode> before = new ArrayList<>(session.nodes());
        session.append();
        assertTrue(session.nodeCount() > before.size() + 1,
            "a prefab piece must contribute more than one node, or undo has nothing to distinguish");

        assertNotNull(session.undoPiece());
        assertEquals(before, session.nodes());
        assertEquals(0, session.pieceCount());
    }

    /** Undo has to restore the context, not just the node list — otherwise the re-appended piece
     *  lands somewhere subtly different from where it was. */
    @Test
    void reAppendingAfterUndoReproducesTheSamePieceExactly() {
        PieceBuildSession session = started();
        session.cycleSelected(4);
        session.append();
        List<TrackNode> firstAttempt = new ArrayList<>(session.nodes());

        session.undoPiece();
        session.append();
        assertEquals(firstAttempt, session.nodes());
    }

    @Test
    void undoRestoresThePieceAndParameterThatWerePlaced() {
        PieceBuildSession session = started();
        session.cycleSelected(2);
        session.adjustParameter(3);
        double parameter = session.selectedParameter();
        session.append();
        session.cycleSelected(1);
        session.adjustParameter(-2);

        session.undoPiece();
        assertEquals(2, session.selectedIndex());
        assertEquals(parameter, session.selectedParameter(), 1.0e-9D);
    }

    @Test
    void undoOnAnEmptyChainReportsNothingToRemove() {
        assertNull(started().undoPiece());
        assertNull(new PieceBuildSession().undoPiece());
    }

    @Test
    void undoUnwindsAWholeChainBackToTheAnchor() {
        PieceBuildSession session = started();
        for (int index = 0; index < PiecePalette.size(); index++) {
            session.cycleSelected(index - session.selectedIndex());
            session.append();
        }
        for (int index = 0; index < PiecePalette.size(); index++) {
            assertNotNull(session.undoPiece());
        }
        assertEquals(1, session.nodeCount());
        assertEquals(0, session.pieceCount());
        assertTrue(session.isStarted(), "the anchor survives; only the pieces are undone");
    }

    @Test
    void parametersAreRememberedPerPieceAcrossCycling() {
        PieceBuildSession session = started();
        session.cycleSelected(2);
        session.adjustParameter(4);
        double tuned = session.selectedParameter();
        session.cycleSelected(1);
        session.adjustParameter(-1);
        session.cycleSelected(-1);
        assertEquals(tuned, session.selectedParameter(), 1.0e-9D,
            "cycling away from a piece must not reset the size it was tuned to");
    }

    @Test
    void parametersClampToTheirEntryRange() {
        for (int index = 0; index < PiecePalette.size(); index++) {
            PieceBuildSession session = started();
            session.cycleSelected(index);
            PiecePalette.Entry entry = session.selectedEntry();

            session.adjustParameter(10_000);
            assertEquals(entry.maximum(), session.selectedParameter(), 1.0e-9D);
            assertFalse(session.append().isEmpty(),
                "entry " + index + " must generate at its maximum parameter");

            session.adjustParameter(-10_000);
            assertEquals(entry.minimum(), session.selectedParameter(), 1.0e-9D);
            assertFalse(session.append().isEmpty(),
                "entry " + index + " must generate at its minimum parameter");
        }
    }

    @Test
    void cyclingWrapsInBothDirections() {
        PieceBuildSession session = started();
        session.cycleSelected(-1);
        assertEquals(PiecePalette.size() - 1, session.selectedIndex());
        session.cycleSelected(1);
        assertEquals(0, session.selectedIndex());
    }

    @Test
    void everyEntryHasANameAndABoundedParameter() {
        for (int index = 0; index < PiecePalette.size(); index++) {
            PiecePalette.Entry entry = PiecePalette.get(index);
            assertFalse(entry.displayName(entry.defaultValue()).isEmpty());
            assertFalse(entry.parameterLabel().isEmpty());
            assertTrue(entry.minimum() < entry.maximum(), "entry " + index + " has an empty range");
            assertTrue(entry.step() > 0.0D);
            assertTrue(entry.defaultValue() >= entry.minimum()
                && entry.defaultValue() <= entry.maximum(),
                "entry " + index + " defaults outside its own range");
        }
    }

    /** A slope must never be asked for a path shorter than the height it has to climb — the one
     *  combination {@code Slope} rejects outright. */
    @Test
    void slopeLengthAlwaysExceedsItsRise() {
        for (double rise = -24.0D; rise <= 24.0D; rise += 0.5D) {
            assertTrue(PiecePalette.slopeLength(rise) > Math.abs(rise),
                "slope of rise " + rise + " would have to be vertical or steeper");
        }
    }

    @Test
    void resetDropsTheChainButKeepsTheSelection() {
        PieceBuildSession session = started();
        session.cycleSelected(3);
        session.adjustParameter(2);
        double parameter = session.selectedParameter();
        session.append();

        session.reset();
        assertFalse(session.isStarted());
        assertEquals(0, session.nodeCount());
        assertEquals(3, session.selectedIndex());
        assertEquals(parameter, session.selectedParameter(), 1.0e-9D);
    }

    private static double maximumInternalGap(List<TrackNode> piece) {
        double worst = 0.0D;
        for (int i = 1; i < piece.size(); i++) {
            worst = Math.max(worst,
                piece.get(i - 1).position().distanceTo(piece.get(i).position()));
        }
        return worst;
    }

    /** Angle in degrees between the chord arriving at {@code index} and the one leaving it. */
    private static double turnDegreesAt(List<TrackNode> chain, int index) {
        Vec3 incoming = chain.get(index).position().subtract(chain.get(index - 1).position());
        Vec3 outgoing = chain.get(index + 1).position().subtract(chain.get(index).position());
        if (incoming.length() < 1.0e-9D || outgoing.length() < 1.0e-9D) {
            return 180.0D;
        }
        double cos = incoming.normalize().dot(outgoing.normalize());
        return Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, cos))));
    }

    private static double sharpestTurn(List<TrackNode> chain, int from, int to) {
        double worst = 0.0D;
        for (int i = Math.max(1, from); i <= Math.min(to, chain.size() - 2); i++) {
            worst = Math.max(worst, turnDegreesAt(chain, i));
        }
        return worst;
    }
}
