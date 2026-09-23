package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.Rcmc;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client: a metro line's state, for the line control desk. Opens the desk, or refreshes
 * it if it is already open. The handler goes through the proxy: the GUI is client-only.
 */
public class PacketLineView implements IMessage {

    private LineView view;
    /** Only the view a player asked for by using the desk may open it; see {@link PacketRideView}. */
    private boolean open;

    public PacketLineView() {
    }

    public PacketLineView(LineView view, boolean open) {
        this.view = view;
        this.open = open;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        view = LineView.read(buf);
        open = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        view.write(buf);
        buf.writeBoolean(open);
    }

    public static class Handler implements IMessageHandler<PacketLineView, IMessage> {
        @Override
        public IMessage onMessage(PacketLineView message, MessageContext ctx) {
            Rcmc.proxy.showLineControl(message.view, message.open);
            return null;
        }
    }
}
