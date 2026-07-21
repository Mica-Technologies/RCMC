package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem;
import com.micatechnologies.minecraft.rcmc.track.storage.TransitCodec;
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
 * Ships the authored transit — stations and lines — to a client, in full, whenever it changes.
 *
 * <p>Same shape and reasoning as {@link PacketTrackSync}: authored transit changes only when an
 * operator edits it, the payload is small, and reusing {@link TransitCodec} keeps the wire and
 * save formats from drifting apart. This is what station signage renders from; the fast-moving
 * part (which trains are how far away) travels separately in {@link PacketServiceSync}.</p>
 */
public class PacketTransitSync implements IMessage {

    private NBTTagCompound payload;

    /** Required by the network system's reflective instantiation. */
    public PacketTransitSync() {
    }

    public PacketTransitSync(TransitSystem transit) {
        this.payload = new NBTTagCompound();
        TransitCodec.write(transit, payload);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.payload = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, payload);
    }

    public static class Handler implements IMessageHandler<PacketTransitSync, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketTransitSync message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> apply(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void apply(PacketTransitSync message) {
            RcmcWorldState state =
                RcmcWorldState.of(net.minecraft.client.Minecraft.getMinecraft().world);
            if (state == null || message.payload == null) {
                return;
            }
            state.transit().replaceAuthoredFrom(TransitCodec.read(message.payload));
        }
    }
}
