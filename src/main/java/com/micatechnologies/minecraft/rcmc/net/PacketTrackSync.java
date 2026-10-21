package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.storage.TrackCodec;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Ships track to clients: the whole network to a player joining or changing dimension, and after
 * that only what changed.
 *
 * <p>Correctness matters more than bytes here — a client whose geometry disagrees with the
 * server's does not merely render wrongly, it <em>predicts</em> wrongly, because the same geometry
 * feeds the physics on both sides. So a delta is built to be safe rather than small: it carries
 * every section that is a different instance from the one last broadcast to that dimension
 * ({@code TrackSection} is immutable, so a new instance is exactly an edited section), the ids of
 * those since removed, and every join and switch, and applying it is idempotent. A player who
 * joined after the last broadcast holds a full network at least that new, and a delta applied over
 * it changes nothing it should not.</p>
 *
 * <p>Reuses {@link TrackCodec}, so the wire format and the save format cannot drift apart.</p>
 */
public class PacketTrackSync implements IMessage {

    private NBTTagCompound payload;

    /**
     * Server side: the section instances last broadcast to each dimension, what the next delta is
     * worked out against. Synchronized because more than one world can tick at once with a mod
     * that parallelises them, and the map is shared.
     */
    private static final java.util.Map<Integer, java.util.Map<Integer, TrackSection>> BROADCAST =
        java.util.Collections.synchronizedMap(new java.util.HashMap<>());

    /** Required by the network system's reflective instantiation. */
    public PacketTrackSync() {
    }

    /** The whole network: for a player joining, or arriving in a dimension. */
    public PacketTrackSync(TrackNetwork network) {
        this.payload = TrackCodec.writeNetwork(network);
    }

    private PacketTrackSync(NBTTagCompound payload) {
        this.payload = payload;
    }

    /**
     * Tells everyone in {@code dimension} what changed in {@code network} since the last broadcast
     * to it. The first broadcast to a dimension, with nothing to compare against, is whole.
     */
    public static void broadcast(TrackNetwork network, int dimension) {
        PacketTrackSync packet;
        synchronized (BROADCAST) {
            java.util.Map<Integer, TrackSection> last = BROADCAST.get(dimension);
            java.util.Map<Integer, TrackSection> now = new java.util.HashMap<>();
            for (TrackSection section : network.sections()) {
                now.put(section.id(), section);
            }
            if (last == null) {
                packet = new PacketTrackSync(network);
            }
            else {
                java.util.List<Integer> changed = new java.util.ArrayList<>();
                for (TrackSection section : network.sections()) {
                    if (last.get(section.id()) != section) {
                        changed.add(section.id());
                    }
                }
                java.util.List<Integer> removed = new java.util.ArrayList<>();
                for (int id : last.keySet()) {
                    if (!now.containsKey(id)) {
                        removed.add(id);
                    }
                }
                packet = new PacketTrackSync(TrackCodec.writeNetworkDelta(network, changed, removed));
            }
            BROADCAST.put(dimension, now);
        }
        RcmcNetwork.sendToAllIn(packet, dimension);
    }

    /** Forgets {@code dimension}'s baseline, so the next broadcast to it is whole: its world unloading. */
    public static void forget(int dimension) {
        BROADCAST.remove(dimension);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.payload = NbtWire.read(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NbtWire.write(buf, payload);
    }

    public static class Handler implements IMessageHandler<PacketTrackSync, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketTrackSync message, MessageContext ctx) {
            // Packet handlers run on the netty thread. Touching the world from there races the
            // client tick; schedule onto the main thread instead.
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> apply(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void apply(PacketTrackSync message) {
            net.minecraft.world.World world = net.minecraft.client.Minecraft.getMinecraft().world;
            RcmcWorldState state = RcmcWorldState.of(world);
            if (state == null || message.payload == null) {
                return;
            }
            TrackNetwork local = state.network();
            if (TrackCodec.isDelta(message.payload)) {
                // Only what changed: every other section keeps its instance, and with it every
                // mesh and cache built from it.
                TrackCodec.applyDelta(message.payload, local);
                return;
            }
            // Unchanged sections keep their instance, and with it every mesh and cache built
            // from it; only what the edit touched is rebuilt.
            TrackNetwork incoming = TrackCodec.readNetwork(message.payload, local);
            local.clear();
            for (TrackSection section : incoming.sections()) {
                local.addSection(section);
            }
            for (java.util.Map.Entry<TrackNetwork.SectionEnd, TrackNetwork.SectionEnd> join
                : incoming.joins().entrySet()) {
                // connect() is symmetric and idempotent, so replaying both mirrored entries is
                // harmless; it re-validates the geometry on arrival, which is the cheapest place
                // to notice that a client and server disagree about the track.
                local.connect(join.getKey(), join.getValue());
            }
            for (TrackNetwork.TrackSwitch sw : incoming.switches()) {
                // Selection included: a client predicting a train through a switch the server
                // lined the other way would rubber-band exactly like mismatched geometry.
                local.addSwitch(sw.throat(), sw.branches());
                local.setSwitchSelection(sw.throat(), sw.selectedIndex());
            }
        }
    }
}
