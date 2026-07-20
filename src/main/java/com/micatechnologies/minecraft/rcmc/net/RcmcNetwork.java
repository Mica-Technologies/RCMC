package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * RCMC's network channel.
 *
 * <p>Two payloads, reflecting the sync design: track geometry, which changes rarely and is sent in
 * full when it does, and train state, which changes constantly but is only four numbers because
 * clients reconstruct every car from it locally.</p>
 *
 * <p>Deliberately <em>not</em> vanilla entity position tracking. A relative-move packet encodes
 * position as 1/4096-block fixed point in a {@code short}, so it cannot express more than ±8 blocks
 * of movement, and a ridden entity always takes the slower teleport path. That is a wire-format
 * limit rather than a tuning problem, which is why car positions never go over the wire at all.</p>
 */
public final class RcmcNetwork {

    private static final SimpleNetworkWrapper CHANNEL =
        NetworkRegistry.INSTANCE.newSimpleChannel(RcmcConstants.MOD_NAMESPACE);

    private static int nextId = 0;

    private RcmcNetwork() {
        throw new AssertionError("No instances.");
    }

    /**
     * Registers every message. Called from {@code preInit} on both sides — discriminator ids are
     * positional, so both sides must register the same messages in the same order or they will
     * disagree about what a given id means.
     */
    public static void init() {
        CHANNEL.registerMessage(PacketTrackSync.Handler.class, PacketTrackSync.class,
            nextId++, Side.CLIENT);
        CHANNEL.registerMessage(PacketTrainSync.Handler.class, PacketTrainSync.class,
            nextId++, Side.CLIENT);
        CHANNEL.registerMessage(PacketTrainRemove.Handler.class, PacketTrainRemove.class,
            nextId++, Side.CLIENT);
        CHANNEL.registerMessage(PacketElementSync.Handler.class, PacketElementSync.class,
            nextId++, Side.CLIENT);
    }

    public static void sendTo(Object message, EntityPlayerMP player) {
        CHANNEL.sendTo((net.minecraftforge.fml.common.network.simpleimpl.IMessage) message, player);
    }

    public static void sendToAllIn(Object message, int dimension) {
        CHANNEL.sendToDimension(
            (net.minecraftforge.fml.common.network.simpleimpl.IMessage) message, dimension);
    }
}
