package com.micatechnologies.minecraft.rcmc.builder;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.element.ElementContext;
import com.micatechnologies.minecraft.rcmc.track.element.ElementResult;
import com.micatechnologies.minecraft.rcmc.track.element.TrackElement;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A coaster being assembled out of prefab pieces, one appended to the end of the last.
 *
 * <p>The sibling of {@link TrackBuildSession}, and deliberately not a mode of it. Freeform node
 * placement and prefab assembly disagree about what the unit of work is: there, it is a point in
 * the world and undo removes a point; here, it is a maneuver and undo removes the whole maneuver.
 * Sharing one session type would mean every operation asking which kind of session it was really
 * in, which is how the two behaviours end up half-implemented in each other's paths.</p>
 *
 * <p><b>The running {@link ElementContext} is the whole design.</b> A piece is generated from the
 * exit state of the piece before it — position, direction, up, authored bank — so continuity is not
 * something this class arranges after the fact, it is the only thing it can produce. That is why
 * undo has to restore the context that was in force before the removed piece rather than
 * recomputing one from the remaining nodes: the nodes do not carry the frame, and re-deriving it
 * from the last two node positions would quietly lose the bank and the up vector.</p>
 *
 * <p>Pure Java, no Minecraft types, for the same reason {@link TrackBuildSession} is: the chaining
 * is the part that can be wrong in ways nobody notices until a train reaches the join, and it must
 * be checkable without a game instance.</p>
 *
 * <p>Server-side and not persisted; an unfinished chain is scratch work, exactly as it is for the
 * freeform tool.</p>
 */
public final class PieceBuildSession {

    private static final Map<UUID, PieceBuildSession> SESSIONS = new HashMap<>();

    /** One appended piece, with everything needed to take it back off again. */
    private static final class Placed {

        private final int paletteIndex;
        private final double parameter;
        private final int nodeCount;
        private final ElementContext contextBefore;

        private Placed(int paletteIndex, double parameter, int nodeCount,
                       ElementContext contextBefore) {
            this.paletteIndex = paletteIndex;
            this.parameter = parameter;
            this.nodeCount = nodeCount;
            this.contextBefore = contextBefore;
        }
    }

    private final List<TrackNode> nodes = new ArrayList<>();
    private final List<Placed> placed = new ArrayList<>();

    /**
     * Parameter for every palette entry, not just the selected one.
     *
     * <p>Per-entry rather than a single current value so that cycling past a piece does not reset
     * it: a builder who has widened their curves to 20 blocks and then drops in a straight expects
     * the next curve to still be 20, not back to the default.</p>
     */
    private final double[] parameters = new double[PiecePalette.size()];

    private ElementContext context;
    private int selected;
    private double nodeSpacing = ElementContext.DEFAULT_NODE_SPACING;

    public PieceBuildSession() {
        for (int i = 0; i < parameters.length; i++) {
            parameters[i] = PiecePalette.get(i).defaultValue();
        }
    }

    public static PieceBuildSession of(UUID playerId) {
        PieceBuildSession session = SESSIONS.get(playerId);
        if (session == null) {
            session = new PieceBuildSession();
            SESSIONS.put(playerId, session);
        }
        return session;
    }

    public static void clear(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    /** Whether a chain has been anchored yet. Until it is, there is nowhere to append to. */
    public boolean isStarted() {
        return context != null;
    }

    /**
     * Anchors a new chain at {@code entryFrame}, discarding anything already pending.
     *
     * <p>The anchor node is placed at the frame's position, so the first piece's nodes continue
     * from a node that exists rather than from an implied point — {@code TrackElement.generate}
     * documents that it does not repeat the entry position, and a chain that started at the first
     * generated node would be a spacing short at the very beginning.</p>
     */
    public void start(TrackFrame entryFrame) {
        nodes.clear();
        placed.clear();
        nodes.add(new TrackNode(entryFrame.position, 0.0D, null));
        context = new ElementContext(entryFrame, 0.0D, nodeSpacing);
    }

    public List<TrackNode> nodes() {
        return Collections.unmodifiableList(nodes);
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int pieceCount() {
        return placed.size();
    }

    public int selectedIndex() {
        return selected;
    }

    public PiecePalette.Entry selectedEntry() {
        return PiecePalette.get(selected);
    }

    public double selectedParameter() {
        return parameters[selected];
    }

    public double nodeSpacing() {
        return nodeSpacing;
    }

    public TrackElement selectedElement() {
        return selectedEntry().element(selectedParameter());
    }

    /** Advances the selection by {@code delta} entries, wrapping, and returns the new selection. */
    public PiecePalette.Entry cycleSelected(int delta) {
        selected = PiecePalette.wrap(selected + delta);
        return selectedEntry();
    }

    /** Nudges the selected piece's parameter by {@code notches} steps, clamped to its range. */
    public double adjustParameter(int notches) {
        PiecePalette.Entry entry = selectedEntry();
        parameters[selected] = entry.clamp(parameters[selected] + notches * entry.step());
        return parameters[selected];
    }

    /**
     * The nodes appending the selected piece would add, without adding them.
     *
     * <p>Generated from the same context the append would use, so the ghost preview cannot show
     * something the click would not build. Returns empty rather than throwing when the piece is not
     * currently buildable — the preview's job is to show what will happen, and "nothing" is a
     * truthful answer where an exception through a render path is not.</p>
     */
    public List<TrackNode> previewNodes() {
        if (context == null) {
            return Collections.emptyList();
        }
        try {
            return selectedElement().generate(context).nodes;
        }
        catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Appends the selected piece to the end of the chain.
     *
     * @return the nodes added, or an empty list if there is no chain yet or the piece could not be
     *         generated
     */
    public List<TrackNode> append() {
        if (context == null) {
            return Collections.emptyList();
        }
        ElementResult result;
        try {
            result = selectedElement().generate(context);
        }
        catch (RuntimeException e) {
            return Collections.emptyList();
        }
        placed.add(new Placed(selected, parameters[selected], result.nodes.size(), context));
        nodes.addAll(result.nodes);
        context = result.asNextContext(nodeSpacing);
        return result.nodes;
    }

    /**
     * Removes the most recently appended piece, restoring the state that produced it.
     *
     * <p>The removed piece's selection and parameter are restored too, so undo followed by a
     * re-append with a different parameter — the actual way a builder fits a curve to a gap — does
     * not require re-selecting the piece first.</p>
     *
     * @return the palette entry removed, or {@code null} if there was nothing to remove
     */
    public PiecePalette.Entry undoPiece() {
        if (placed.isEmpty()) {
            return null;
        }
        Placed last = placed.remove(placed.size() - 1);
        for (int i = 0; i < last.nodeCount; i++) {
            nodes.remove(nodes.size() - 1);
        }
        context = last.contextBefore;
        selected = PiecePalette.wrap(last.paletteIndex);
        parameters[selected] = last.parameter;
        return selectedEntry();
    }

    /** Drops the chain entirely. The selection and its parameters survive, as they do for the
     *  freeform tool: a builder committing one coaster and starting another has not changed their
     *  mind about what piece they were holding. */
    public void reset() {
        nodes.clear();
        placed.clear();
        context = null;
    }
}
