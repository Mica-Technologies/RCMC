package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.world.RideOperations;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: the operator pressed something on a ride's panel. The server checks the player
 * is still at a panel for that ride, applies it, and answers with a fresh {@link PacketRideView}.
 */
public class PacketRideAction implements IMessage {

    /** What was pressed. Ordinals travel on the wire; add new actions at the end only. */
    public enum Action {
        REFRESH,
        OPEN,
        TEST,
        CLOSE,
        EMERGENCY_STOP,
        RESET_EMERGENCY,
        MODE_AUTOMATIC,
        MODE_MANUAL,
        DISPATCH,
        SET_CARS,
        ADD_TRAIN,
        REMOVE_TRAIN,
        TUNE,
        /** Store the next train at the transfer table; pressed again, cancel. */
        STORE_TRAIN,
        /** Bring the stored train back; pressed again, cancel. */
        RETRIEVE_TRAIN,
        /** The next car type for new trains. */
        CYCLE_CAR_MODEL
    }

    private int sectionId;
    private Action action = Action.REFRESH;
    private int first;
    private int second;
    private double value;

    public PacketRideAction() {
    }

    /**
     * @param first  a count or an id: cars for SET_CARS, the train for REMOVE_TRAIN, the element for
     *               TUNE
     * @param second the parameter ordinal for TUNE
     * @param value  the new value for TUNE
     */
    public PacketRideAction(int sectionId, Action action, int first, int second, double value) {
        this.sectionId = sectionId;
        this.action = action;
        this.first = first;
        this.second = second;
        this.value = value;
    }

    public static PacketRideAction of(int sectionId, Action action) {
        return new PacketRideAction(sectionId, action, 0, 0, 0.0D);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        sectionId = buf.readInt();
        int ordinal = buf.readByte();
        Action[] all = Action.values();
        action = ordinal >= 0 && ordinal < all.length ? all[ordinal] : Action.REFRESH;
        first = buf.readInt();
        second = buf.readInt();
        value = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(sectionId);
        buf.writeByte(action.ordinal());
        buf.writeInt(first);
        buf.writeInt(second);
        buf.writeDouble(value);
    }

    public static class Handler implements IMessageHandler<PacketRideAction, IMessage> {
        @Override
        public IMessage onMessage(PacketRideAction m, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() ->
                RideOperations.handle(player, m.sectionId, m.action, m.first, m.second, m.value));
            return null;
        }
    }
}
