package com.micatechnologies.minecraft.rcmc.client.build;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The client's mirror of its own player's pending build nodes.
 *
 * <p>Exists purely so the ghost preview has something to draw. The server's
 * {@code TrackBuildSession} remains the authority on what actually gets committed — this is
 * updated from a packet and never edited locally, so it cannot drift into disagreeing about what
 * a click will produce.</p>
 *
 * <p>Static because there is exactly one local player, and threading a per-player object through
 * a render path that can only ever concern that one player would be ceremony without benefit.</p>
 */
@SideOnly(Side.CLIENT)
public final class ClientBuildSession {

    private static List<TrackNode> nodes = Collections.emptyList();
    private static double bankDegrees;
    private static boolean closing;

    private ClientBuildSession() {
        throw new AssertionError("No instances.");
    }

    public static void update(List<TrackNode> newNodes, double newBankDegrees, boolean newClosing) {
        nodes = newNodes == null ? Collections.<TrackNode>emptyList() : new ArrayList<>(newNodes);
        bankDegrees = newBankDegrees;
        closing = newClosing;
    }

    public static void clear() {
        update(null, 0.0D, false);
    }

    public static List<TrackNode> nodes() {
        return nodes;
    }

    /** Bank that would be applied to the next placed node. */
    public static double bankDegrees() {
        return bankDegrees;
    }

    public static boolean isClosing() {
        return closing;
    }

    public static boolean isEmpty() {
        return nodes.isEmpty();
    }
}
