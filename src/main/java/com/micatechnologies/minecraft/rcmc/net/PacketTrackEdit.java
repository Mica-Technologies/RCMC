package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.Rcmc;
import com.micatechnologies.minecraft.rcmc.world.TrackEditOperations;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * The track editor screen's two messages: the server's {@link View} of a node, which opens or
 * refreshes the screen, and a {@link Action} pressed on it.
 */
public final class PacketTrackEdit {

    private PacketTrackEdit() {
    }

    /** What a press on the editor screen asks for. */
    public enum Action {
        REFRESH,
        /** Show node {@code value} instead. */
        SELECT_NODE,
        /** Nudge the node by {@code value} blocks along X, Y or Z. */
        MOVE_X,
        MOVE_Y,
        MOVE_Z,
        /** Change the node's bank by {@code value} degrees. */
        BANK,
        /** Add a node halfway along the span from this one (or past the end of an open section). */
        INSERT_AFTER,
        DELETE_NODE,
        /** Lay segment type {@code value} on the span from this node. */
        SET_SPAN_TYPE,
        CYCLE_PAINT_PART,
        CYCLE_COLOUR,
        /** Delete the whole section; {@code value} 1 confirms. */
        DELETE_SECTION,
        /** Split the section at this node into two, still joined. */
        SPLIT,
        /** Merge this end node's end with the nearest end in reach, or close the circuit. */
        JOIN,
        /** Turn the section round. */
        REVERSE,
        /** Give the section the next track style. */
        CYCLE_STYLE
    }

    /** Server to client: open or refresh the editor on a node. */
    public static class View implements IMessage {

        private TrackEditView view;

        public View() {
        }

        public View(TrackEditView view) {
            this.view = view;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            view = TrackEditView.read(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            view.write(buf);
        }

        public static class Handler implements IMessageHandler<View, IMessage> {
            @Override
            public IMessage onMessage(View message, MessageContext ctx) {
                Rcmc.proxy.showTrackEditor(message.view);
                return null;
            }
        }
    }

    /** Client to server: a press on the editor screen. */
    public static class Press implements IMessage {

        private int sectionId;
        private int nodeIndex;
        private Action action = Action.REFRESH;
        private double value;

        public Press() {
        }

        public Press(int sectionId, int nodeIndex, Action action, double value) {
            this.sectionId = sectionId;
            this.nodeIndex = nodeIndex;
            this.action = action;
            this.value = value;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            sectionId = buf.readInt();
            nodeIndex = buf.readShort();
            int ordinal = buf.readByte();
            Action[] all = Action.values();
            action = ordinal >= 0 && ordinal < all.length ? all[ordinal] : Action.REFRESH;
            value = buf.readDouble();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(sectionId);
            buf.writeShort(nodeIndex);
            buf.writeByte(action.ordinal());
            buf.writeDouble(value);
        }

        public static class Handler implements IMessageHandler<Press, IMessage> {
            @Override
            public IMessage onMessage(Press m, MessageContext ctx) {
                EntityPlayerMP player = ctx.getServerHandler().player;
                player.getServerWorld().addScheduledTask(() ->
                    TrackEditOperations.handle(player, m.sectionId, m.nodeIndex, m.action, m.value));
                return null;
            }
        }
    }
}
