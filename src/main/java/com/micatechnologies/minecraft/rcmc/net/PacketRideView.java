package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.Rcmc;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client: a ride's state, for the operator panel. Opens the panel, or refreshes it if it
 * is already open on this ride.
 *
 * <p>The handler goes through the proxy — the GUI is client-only, and this class is loaded on a
 * dedicated server too.</p>
 */
public class PacketRideView implements IMessage {

    private RideView view;

    public PacketRideView() {
    }

    public PacketRideView(RideView view) {
        this.view = view;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        view = RideView.read(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        view.write(buf);
    }

    public static class Handler implements IMessageHandler<PacketRideView, IMessage> {
        @Override
        public IMessage onMessage(PacketRideView message, MessageContext ctx) {
            Rcmc.proxy.showRideController(message.view);
            return null;
        }
    }
}
