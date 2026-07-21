package com.micatechnologies.minecraft.rcmc.client.build;

import com.micatechnologies.minecraft.rcmc.builder.PiecePalette;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The client's mirror of its own player's prefab chain, and of the piece a click would add to it.
 *
 * <p>The counterpart of {@link ClientBuildSession}, for the piece tool. Updated from a packet and
 * never edited locally, so the ghost cannot drift into promising something the server would not
 * build.</p>
 *
 * <p>The piece's <em>name</em> and parameter text are derived here from {@link PiecePalette} rather
 * than shipped as strings, because the palette is pure Java and identical on both sides; only the
 * geometry, which depends on server-held state, has to travel.</p>
 */
@SideOnly(Side.CLIENT)
public final class ClientPieceSession {

    private static boolean started;
    private static List<TrackNode> chain = Collections.emptyList();
    private static List<TrackNode> preview = Collections.emptyList();
    private static int selectedIndex;
    private static double parameter = PiecePalette.get(0).defaultValue();
    private static int pieceCount;

    private ClientPieceSession() {
        throw new AssertionError("No instances.");
    }

    public static void update(boolean newStarted, List<TrackNode> newChain,
                              List<TrackNode> newPreview, int newSelectedIndex,
                              double newParameter, int newPieceCount) {
        started = newStarted;
        chain = newChain == null ? Collections.<TrackNode>emptyList() : new ArrayList<>(newChain);
        preview = newPreview == null ? Collections.<TrackNode>emptyList()
            : new ArrayList<>(newPreview);
        selectedIndex = PiecePalette.wrap(newSelectedIndex);
        parameter = newParameter;
        pieceCount = newPieceCount;
    }

    public static void clear() {
        update(false, null, null, 0, PiecePalette.get(0).defaultValue(), 0);
    }

    /** Whether a chain has been anchored; until then there is nothing but a selection to show. */
    public static boolean isStarted() {
        return started;
    }

    /** Nodes already committed to the chain. */
    public static List<TrackNode> chain() {
        return chain;
    }

    /** Nodes the next click would append. */
    public static List<TrackNode> preview() {
        return preview;
    }

    public static int pieceCount() {
        return pieceCount;
    }

    public static PiecePalette.Entry selectedEntry() {
        return PiecePalette.get(selectedIndex);
    }

    public static double parameter() {
        return parameter;
    }

    public static String selectedName() {
        return selectedEntry().displayName(parameter);
    }

    public static String selectedParameterText() {
        return selectedEntry().describeParameter(parameter);
    }
}
