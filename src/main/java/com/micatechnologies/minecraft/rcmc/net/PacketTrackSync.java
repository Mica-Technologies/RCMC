package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.storage.TrackCodec;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Ships the whole track network to a client.
 *
 * <p>Sending everything rather than a delta is a deliberate first cut. Track changes only when a
 * builder edits it, which is rare compared to the tick rate, and correctness here matters more
 * than bytes: a client whose geometry disagrees with the server's does not merely render wrongly,
 * it <em>predicts</em> wrongly, because the same geometry feeds the physics on both sides. Deltas
 * are a Phase 2 optimisation once the editor exists to generate them, and they will need a
 * generation counter to detect a missed update.</p>
 *
 * <p>Reuses {@link TrackCodec}, so the wire format and the save format cannot drift apart.</p>
 */
public class PacketTrackSync implements IMessage {

    private NBTTagCompound payload;

    /** Required by the network system's reflective instantiation. */
    public PacketTrackSync() {
    }

    public PacketTrackSync(TrackNetwork network) {
        this.payload = TrackCodec.writeNetwork(network);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.payload = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, payload);
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
            TrackNetwork incoming = TrackCodec.readNetwork(message.payload);
            TrackNetwork local = state.network();
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
