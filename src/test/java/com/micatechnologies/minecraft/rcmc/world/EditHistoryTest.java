package com.micatechnologies.minecraft.rcmc.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The undo/redo stack logic, tested with tagged NBT snapshots standing in for real park states.
 *
 * <p>NBT is a plain data container needing no game bootstrap, so these run on a bare JVM. The stack
 * is deliberately free of any world so its behaviour can be pinned down here rather than only in a
 * live client, which is where the "complete but unreachable" bugs this project keeps recording
 * would otherwise hide.</p>
 */
class EditHistoryTest {

    /** A snapshot tagged with a state id, so restores can be identified. */
    private static NBTTagCompound state(int id) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("s", id);
        return tag;
    }

    private static int idOf(NBTTagCompound tag) {
        return tag == null ? -1 : tag.getInteger("s");
    }

    @Test
    @DisplayName("undo walks back through committed states in reverse order")
    void undoWalksBack() {
        EditHistory history = new EditHistory(state(0), EditHistory.DEFAULT_DEPTH);
        history.record(state(1));
        history.record(state(2));

        assertTrue(history.canUndo());
        assertFalse(history.canRedo());
        assertEquals(1, idOf(history.undo()), "undo of edit-2 returns the pre-2 state");
        assertEquals(0, idOf(history.undo()), "undo of edit-1 returns the loaded state");
        assertNull(history.undo(), "nothing left to undo");
    }

    @Test
    @DisplayName("redo replays states undone, in order")
    void redoReplays() {
        EditHistory history = new EditHistory(state(0), EditHistory.DEFAULT_DEPTH);
        history.record(state(1));
        history.record(state(2));

        history.undo();
        history.undo();
        assertTrue(history.canRedo());
        assertEquals(1, idOf(history.redo()));
        assertEquals(2, idOf(history.redo()));
        assertNull(history.redo(), "nothing left to redo");
    }

    @Test
    @DisplayName("a fresh edit after an undo discards the redo branch")
    void editClearsRedo() {
        EditHistory history = new EditHistory(state(0), EditHistory.DEFAULT_DEPTH);
        history.record(state(1));
        history.record(state(2));
        history.undo();               // back to state 1, redo now holds state 2

        assertTrue(history.canRedo());
        history.record(state(3));      // a new edit branches history
        assertFalse(history.canRedo(), "the abandoned redo branch is gone");
        assertEquals(1, idOf(history.undo()), "undo of the new edit-3 returns state 1");
    }

    @Test
    @DisplayName("record is ignored while a restore is being applied")
    void restoreGuardStopsSelfRecording() {
        EditHistory history = new EditHistory(state(0), EditHistory.DEFAULT_DEPTH);
        history.record(state(1));

        // Simulate applying an undo: undo() hands back the target, then the world marks itself
        // dirty as it installs it — which must NOT be recorded as a new edit.
        NBTTagCompound target = history.undo();
        assertEquals(0, idOf(target));
        history.beginRestore();
        history.record(state(99));     // the dirty-mark from installing the restore
        history.endRestore();

        assertFalse(history.canUndo(), "the spurious record did not push a new undo step");
        assertEquals(1, idOf(history.redo()), "redo still leads back to state 1, not state 99");
    }

    @Test
    @DisplayName("the undo stack is bounded to its depth, dropping the oldest states")
    void depthIsBounded() {
        EditHistory history = new EditHistory(state(0), 3);
        for (int i = 1; i <= 10; i++) {
            history.record(state(i));
        }
        // Depth 3 keeps the three most-recent pre-edit states: 9, 8, 7.
        assertEquals(9, idOf(history.undo()));
        assertEquals(8, idOf(history.undo()));
        assertEquals(7, idOf(history.undo()));
        assertNull(history.undo(), "older states were dropped to honour the depth cap");
    }
}
