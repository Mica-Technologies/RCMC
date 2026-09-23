package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.world.LineControl;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: the operator pressed something on the line control desk. The server checks the
 * player is still at the desk, applies it, and answers with a fresh {@link PacketLineView}.
 */
public class PacketLineAction implements IMessage {

    /** What was pressed. Ordinals travel on the wire; add new actions at the end only. */
    public enum Action {
        REFRESH,
        PREVIOUS_LINE,
        NEXT_LINE,
        DWELL_DOWN,
        DWELL_UP,
        HEADWAY_DOWN,
        HEADWAY_UP,
        CARS_DOWN,
        CARS_UP,
        ADD_TRAIN,
        /** {@code train}: take it off the track. */
        REMOVE_TRAIN,
        /** {@code train}: hold it at its next platform, or release it. */
        TOGGLE_HOLD
    }

    private Action action = Action.REFRESH;
    private int train;

    public PacketLineAction() {
    }

    public PacketLineAction(Action action, int train) {
        this.action = action;
        this.train = train;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int ordinal = buf.readByte();
        Action[] all = Action.values();
        action = ordinal >= 0 && ordinal < all.length ? all[ordinal] : Action.REFRESH;
        train = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeInt(train);
    }

    public static class Handler implements IMessageHandler<PacketLineAction, IMessage> {
        @Override
        public IMessage onMessage(PacketLineAction m, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> LineControl.handle(player, m.action, m.train));
            return null;
        }
    }
}
