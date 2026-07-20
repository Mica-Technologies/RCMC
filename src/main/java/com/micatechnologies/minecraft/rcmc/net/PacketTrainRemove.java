package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Tells clients to forget a train, or all of them.
 *
 * <p>The counterpart to {@link PacketTrainSync}, and its absence was a real bug: clearing the park
 * server-side removed the track and the trains and broadcast the empty track, but nothing told
 * clients to drop their copies of the trains. Each client was left holding a train pointing at a
 * section it no longer had, which crashed the entity tick.</p>
 *
 * <p>Removal has to be explicit rather than inferred from a train's absence in some periodic
 * update, because the periodic update is per-train — a train that stops being sent is
 * indistinguishable from one whose packet was simply not sent this tick.</p>
 */
public class PacketTrainRemove implements IMessage {

    /** Sentinel meaning "every train in this world". */
    public static final int ALL_TRAINS = -1;

    private int trainId;

    /** Required by the network system's reflective instantiation. */
    public PacketTrainRemove() {
    }

    public PacketTrainRemove(int trainId) {
        this.trainId = trainId;
    }

    /** Removes every train — what {@code /rcmc clear} sends. */
    public static PacketTrainRemove all() {
        return new PacketTrainRemove(ALL_TRAINS);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        trainId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(trainId);
    }

    public static class Handler implements IMessageHandler<PacketTrainRemove, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketTrainRemove message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> apply(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void apply(PacketTrainRemove message) {
            RcmcWorldState state =
                RcmcWorldState.of(net.minecraft.client.Minecraft.getMinecraft().world);
            if (state == null) {
                return;
            }
            if (message.trainId == ALL_TRAINS) {
                state.trains().clear();
            }
            else {
                state.trains().remove(message.trainId);
            }
        }
    }
}
