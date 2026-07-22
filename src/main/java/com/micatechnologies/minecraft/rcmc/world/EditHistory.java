package com.micatechnologies.minecraft.rcmc.world;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.nbt.NBTTagCompound;

/**
 * A server-authoritative undo/redo stack of whole-world edit snapshots.
 *
 * <h3>Why snapshots, not per-operation inverses</h3>
 *
 * <p>The editable state is the track network, its ride elements and its transit — exactly what
 * {@code RcmcTrackData} serialises to persist. Rather than teach every edit how to invert itself
 * (a table that would silently miss the next new edit, the recurring failure mode this project
 * keeps recording), history records the <em>whole</em> authored state as an NBT snapshot on each
 * commit. Undo restores a snapshot. Whatever persists, undoes; a new edit type is covered the day
 * it is written, for free, because it goes through the same save path.</p>
 *
 * <p>The cost is that a snapshot is the size of the park. Edits are user-driven and occasional, not
 * per-tick, so this is cheap in practice; the depth cap bounds the memory a long session can hold.</p>
 *
 * <h3>The recording model</h3>
 *
 * <p>There is no "before the edit" hook in the codebase — {@link RcmcWorldState#markTrackDirty} is
 * called <em>after</em> a mutation, and it is the one choke point every edit already passes through.
 * So history keeps the last committed snapshot ({@link #current}) and, on each new commit, pushes
 * that <em>previous</em> snapshot onto the undo stack before adopting the new one. The state seeded
 * at construction (the freshly loaded world) is therefore the pre-edit state of the very first edit,
 * which is what makes that first edit undoable.</p>
 *
 * <p>This class is pure: it moves {@link NBTTagCompound} snapshots between two deques and never
 * touches a world. {@link RcmcWorldState} supplies the snapshots and applies the restores. That
 * split is what lets the stack logic be unit-tested on a bare JVM.</p>
 *
 * <p><b>Scope, deliberately global per world.</b> One stack for the world, not one per player. A
 * shared world has one authored state; a per-player stack would let player A's undo reach back past
 * player B's later edit into a state that no longer makes sense. Single-builder is the common case
 * and is exactly right; per-player undo over shared state is a genuinely harder problem left for
 * later. {@code /rcmc undo} steps the world back one edit whoever runs it.</p>
 */
public final class EditHistory {

    /** How many undo steps to retain. Bounds the memory a long building session accumulates. */
    public static final int DEFAULT_DEPTH = 40;

    private final Deque<NBTTagCompound> undo = new ArrayDeque<>();
    private final Deque<NBTTagCompound> redo = new ArrayDeque<>();

    /** The last committed state — the pre-edit snapshot for the next edit, and the redo anchor. */
    private NBTTagCompound current;

    /** True while a restore is being applied, so the {@code markTrackDirty} it triggers is ignored. */
    private boolean restoring;

    private final int depth;

    public EditHistory(NBTTagCompound initial, int depth) {
        this.current = initial;
        this.depth = depth;
    }

    public boolean isRestoring() {
        return restoring;
    }

    /**
     * Records that the world just committed a new state. The previously committed state becomes an
     * undo step, and the redo stack is discarded because history has branched.
     */
    public void record(NBTTagCompound snapshot) {
        if (restoring) {
            // The change came from applying an undo/redo, not from a fresh edit — leave the stacks
            // as undo()/redo() already arranged them.
            return;
        }
        if (current != null) {
            undo.addLast(current);
            while (undo.size() > depth) {
                undo.removeFirst();
            }
        }
        redo.clear();
        current = snapshot;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /**
     * Returns the snapshot to restore for an undo and moves the current state onto the redo stack,
     * or {@code null} if there is nothing to undo. The caller applies the returned snapshot to the
     * world under {@link #beginRestore()}/{@link #endRestore()}.
     */
    public NBTTagCompound undo() {
        if (undo.isEmpty()) {
            return null;
        }
        if (current != null) {
            redo.addLast(current);
        }
        current = undo.removeLast();
        return current;
    }

    /** Mirror of {@link #undo()} for redo. */
    public NBTTagCompound redo() {
        if (redo.isEmpty()) {
            return null;
        }
        if (current != null) {
            undo.addLast(current);
        }
        current = redo.removeLast();
        return current;
    }

    public void beginRestore() {
        restoring = true;
    }

    public void endRestore() {
        restoring = false;
    }
}
