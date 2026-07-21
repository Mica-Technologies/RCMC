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
        /** Cycle the segment type of an existing span, via the editor wand. */
        CYCLE_SELECTED_TYPE,
        /** Cycle the colour of the part currently being painted. */
        CYCLE_COLOUR,
        /** Switch which part of the track colour changes apply to. */
        CYCLE_PAINT_PART,
        CYCLE_TYPE,
        ADJUST_HEIGHT,
        ADJUST_BANK,
        RESET,
        /** Piece tool: move the selection through the prefab palette. Value is the number of
         *  entries to move, signed. */
        CYCLE_PIECE,
        /** Piece tool: resize the selected prefab. Value is the number of steps, signed. */
        ADJUST_PIECE_PARAMETER,
        /** Piece tool: take the last appended piece back off. */
        UNDO_PIECE,
        /** Transit tool: move to the next authoring mode. */
        CYCLE_TRANSIT_MODE,
        /** Transit tool: create the line or switch currently being assembled. */
        COMMIT_TRANSIT,
        /** Transit tool: loop or shuttle, for the line being assembled. */
        TOGGLE_LINE_KIND
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

            // Deltas are bounded here as well as in the session: this is the one packet whose
            // contents a client controls, and a huge value should not walk a setting to its limit
            // in a single message.
            switch (message.action) {
                case CYCLE_PIECE:
                case ADJUST_PIECE_PARAMETER:
                case UNDO_PIECE:
                    applyToPieceSession(message, player);
                    return;
                case CYCLE_TRANSIT_MODE:
                    com.micatechnologies.minecraft.rcmc.item.ItemTransitTool.cycleMode(player);
                    return;
                case COMMIT_TRANSIT:
                    com.micatechnologies.minecraft.rcmc.item.ItemTransitTool
                        .commit(player, player.world);
                    return;
                case TOGGLE_LINE_KIND:
                    com.micatechnologies.minecraft.rcmc.item.ItemTransitTool.toggleLineKind(player);
                    return;
                case CYCLE_COLOUR:
                    com.micatechnologies.minecraft.rcmc.item.ItemTrackEditor
                        .cycleSelectedColour(player, player.world);
                    return;
                case CYCLE_PAINT_PART:
                    com.micatechnologies.minecraft.rcmc.item.ItemTrackEditor.cyclePaintPart(player);
                    return;
                case CYCLE_SELECTED_TYPE:
                    com.micatechnologies.minecraft.rcmc.item.ItemTrackEditor
                        .cycleSelectedType(player, player.world);
                    return;
                case CYCLE_TYPE:
                    player.sendStatusMessage(new TextComponentString(
                        TextFormatting.AQUA + "Segment: " + session.cycleType().label()), true);
                    break;
                case ADJUST_BANK:
                    session.setBankDegrees(session.bankDegrees()
                        + Math.max(-15.0D, Math.min(15.0D, message.value)));
                    player.sendStatusMessage(new TextComponentString(
                        TextFormatting.AQUA + String.format("Bank: %+.0f deg",
                            session.bankDegrees())), true);
                    break;
                case RESET:
                    session.setBankDegrees(0.0D);
                    session.adjustHeightOffset(-session.heightOffset());
                    player.sendStatusMessage(new TextComponentString(
                        TextFormatting.AQUA + "Bank and height reset"), true);
                    break;
                case ADJUST_HEIGHT:
                default:
                    session.adjustHeightOffset(Math.max(-4.0D, Math.min(4.0D, message.value)));
                    player.sendStatusMessage(new TextComponentString(
                        TextFormatting.AQUA + String.format("Height offset: %+.1f blocks",
                            session.heightOffset())), true);
                    break;
            }

            RcmcNetwork.sendTo(new PacketBuildSessionSync(session), player);
        }

        /**
         * The piece tool's half of the adjustments.
         *
         * <p>Split out rather than folded into the switch above because it touches a different
         * session type entirely — the two tools share this packet, not their state, and a method
         * that reached into both would be one edit away from a cycle on one tool quietly resetting
         * the other.</p>
         */
        private static void applyToPieceSession(PacketBuildAdjust message, EntityPlayerMP player) {
            com.micatechnologies.minecraft.rcmc.builder.PieceBuildSession session =
                com.micatechnologies.minecraft.rcmc.builder.PieceBuildSession.of(
                    player.getUniqueID());
            switch (message.action) {
                case UNDO_PIECE:
                    // Reports and syncs on its own, since it is the same action the sneak gesture
                    // performs and has to say the same things.
                    com.micatechnologies.minecraft.rcmc.item.ItemPieceTool.undo(player, session);
                    return;
                case CYCLE_PIECE:
                    // One entry per message. A client controls this value, and a palette that could
                    // be spun by a thousand entries in one packet is a pointless thing to allow.
                    session.cycleSelected(message.value >= 0.0D ? 1 : -1);
                    break;
                case ADJUST_PIECE_PARAMETER:
                default:
                    session.adjustParameter((int) Math.max(-4.0D, Math.min(4.0D, message.value)));
                    break;
            }
            player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA
                + session.selectedEntry().displayName(session.selectedParameter())
                + " — " + session.selectedEntry().describeParameter(session.selectedParameter())),
                true);
            com.micatechnologies.minecraft.rcmc.item.ItemPieceTool.pushSession(player, session);
        }
    }
}
