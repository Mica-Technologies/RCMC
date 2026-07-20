package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.builder.TrackBuildSession;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/**
 * A builder adjusting their tool: cycling segment type, or nudging the placement height.
 *
 * <p>The first client-to-server message in the mod. Both adjustments have to travel this way
 * because {@link TrackBuildSession} is server-side and authoritative — the client may only ask.
 * Letting the client hold the value instead would mean the node it previews and the node the
 * server places could disagree, which is the one thing the preview must never do.</p>
 *
 * <p>The server answers with a {@link PacketBuildSessionSync}, so the client's preview updates from
 * the authoritative value rather than from its own optimistic guess.</p>
 */
public class PacketBuildAdjust implements IMessage {

    public enum Action {
        CYCLE_TYPE,
        ADJUST_HEIGHT
    }

    private Action action;
    private double value;

    /** Required by the network system's reflective instantiation. */
    public PacketBuildAdjust() {
    }

    public PacketBuildAdjust(Action action, double value) {
        this.action = action;
        this.value = value;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int ordinal = buf.readInt();
        Action[] all = Action.values();
        // Clamped rather than trusted: this is the one packet a client controls the contents of,
        // and an out-of-range ordinal from a malformed or hostile client should not throw inside
        // the network thread.
        action = ordinal >= 0 && ordinal < all.length ? all[ordinal] : Action.CYCLE_TYPE;
        value = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action.ordinal());
        buf.writeDouble(value);
    }

    public static class Handler implements IMessageHandler<PacketBuildAdjust, IMessage> {

        @Override
        public IMessage onMessage(PacketBuildAdjust message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            // Packet handlers run on the netty thread; touching session state or sending chat from
            // there races the server tick.
            player.getServerWorld().addScheduledTask(() -> apply(message, player));
            return null;
        }

        private static void apply(PacketBuildAdjust message, EntityPlayerMP player) {
            TrackBuildSession session = TrackBuildSession.of(player.getUniqueID());

            if (message.action == Action.CYCLE_TYPE) {
                TrackBuildSession.SegmentType now = session.cycleType();
                player.sendStatusMessage(new TextComponentString(
                    TextFormatting.AQUA + "Segment: " + now.label()), true);
            }
            else {
                // Bounded here as well as in the session: a client sending a huge delta should not
                // be able to walk the offset to its limit in one packet.
                double delta = Math.max(-4.0D, Math.min(4.0D, message.value));
                session.adjustHeightOffset(delta);
                player.sendStatusMessage(new TextComponentString(
                    TextFormatting.AQUA + String.format("Height offset: %+.1f blocks",
                        session.heightOffset())), true);
            }

            RcmcNetwork.sendTo(new PacketBuildSessionSync(session), player);
        }
    }
}
