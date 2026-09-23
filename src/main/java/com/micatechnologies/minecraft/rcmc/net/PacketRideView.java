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
    /**
     * Whether this view may open the panel, or only refresh one already open. Only the view a
     * player asked for by using the panel opens it: a refresh arriving after they closed it, or
     * after they opened another screen, must not bring it back.
     */
    private boolean open;

    public PacketRideView() {
    }

    public PacketRideView(RideView view) {
        this(view, false);
    }

    public PacketRideView(RideView view, boolean open) {
        this.view = view;
        this.open = open;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        view = RideView.read(buf);
        open = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        view.write(buf);
        buf.writeBoolean(open);
    }

    public static class Handler implements IMessageHandler<PacketRideView, IMessage> {
        @Override
        public IMessage onMessage(PacketRideView message, MessageContext ctx) {
            Rcmc.proxy.showRideController(message.view, message.open);
            return null;
        }
    }
}
