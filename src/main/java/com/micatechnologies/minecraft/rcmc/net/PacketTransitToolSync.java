package com.micatechnologies.minecraft.rcmc.net;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Server to client: the transit tool's mode and the stops picked so far for a line — what its
 * preview needs to show what a click will do.
 */
public class PacketTransitToolSync implements IMessage {

    private int mode;
    private List<String> stops = new ArrayList<>();

    public PacketTransitToolSync() {
    }

    public PacketTransitToolSync(int mode, List<String> stops) {
        this.mode = mode;
        this.stops = new ArrayList<>(stops);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        mode = buf.readByte();
        int count = buf.readShort();
        stops = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            stops.add(ByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(mode);
        buf.writeShort(stops.size());
        for (String stop : stops) {
            ByteBufUtils.writeUTF8String(buf, stop);
        }
    }

    public static class Handler implements IMessageHandler<PacketTransitToolSync, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketTransitToolSync message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() ->
                com.micatechnologies.minecraft.rcmc.client.build.TransitToolPreview.update(
                    message.mode, message.stops));
            return null;
        }
    }
}
