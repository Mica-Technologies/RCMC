package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.block.signal.SignalHeadMount;
import com.micatechnologies.minecraft.rcmc.block.signal.SignalHeadStyle;
import com.micatechnologies.minecraft.rcmc.block.signal.TileSignalHead;
import com.micatechnologies.minecraft.rcmc.physics.transit.SignalAspect;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: a change made in a signal head's screen. The server checks the player is
 * within reach of the head, applies it, and the head's own update packet carries the result back
 * to every client, the screen included.
 */
public class PacketSignalHeadEdit implements IMessage {

    /** What to change. Ordinals travel on the wire; add new edits at the end only. */
    public enum Edit {
        /** {@code value}: style ordinal. */
        STYLE,
        /** {@code value}: mount ordinal. */
        MOUNT,
        /** {@code value}: degrees to turn by. */
        TURN,
        FACE_TRACK,
        /** {@code value}: +1 right, -1 left. */
        HAND,
        /** {@code value}: control ordinal. */
        CONTROL,
        /** {@code value}: aspect ordinal; switches to manual control. */
        ASPECT,
        /** {@code value}: 1 lit, 0 dark. */
        SUBSIDIARY,
        /** {@code value}: 1 on, 0 off. */
        APPROACH_LIT,
        LINK_SIGNAL,
        UNLINK_SIGNAL,
        LINK_SWITCH,
        UNLINK_SWITCH,
        /** {@code value}: branch index. */
        ROUTE_BRANCH,
        /** {@code value}: branch index; {@code text}: what to show. */
        ROUTE_TEXT,
        /** {@code value}: blocks. */
        SPAN
    }

    /** How near a player must be to a head to change it, squared. */
    private static final double REACH_SQ = 10.0D * 10.0D;

    private BlockPos pos = BlockPos.ORIGIN;
    private Edit edit = Edit.FACE_TRACK;
    private int value;
    private String text = "";

    public PacketSignalHeadEdit() {
    }

    public PacketSignalHeadEdit(BlockPos pos, Edit edit, int value, String text) {
        this.pos = pos;
        this.edit = edit;
        this.value = value;
        this.text = text == null ? "" : text;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        int ordinal = buf.readByte();
        Edit[] all = Edit.values();
        edit = ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
        value = buf.readInt();
        text = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeByte(edit.ordinal());
        buf.writeInt(value);
        ByteBufUtils.writeUTF8String(buf, text.length() > 16 ? text.substring(0, 16) : text);
    }

    /** Applies an edit to a head. Server side. */
    static void apply(EntityPlayerMP player, PacketSignalHeadEdit m) {
        if (m.edit == null || player.getDistanceSqToCenter(m.pos) > REACH_SQ
            || !player.world.isBlockLoaded(m.pos)) {
            return;
        }
        TileEntity tile = player.world.getTileEntity(m.pos);
        if (!(tile instanceof TileSignalHead)) {
            return;
        }
        TileSignalHead head = (TileSignalHead) tile;
        switch (m.edit) {
            case STYLE:
                head.setStyle(SignalHeadStyle.fromOrdinal(m.value));
                break;
            case MOUNT:
                head.setMount(SignalHeadMount.fromOrdinal(m.value));
                break;
            case TURN:
                head.setFacingDegrees(head.facingDegrees() + m.value);
                break;
            case FACE_TRACK:
                if (!head.faceNearestTrack()) {
                    tell(player, "No track within " + (int) TileSignalHead.LINK_RANGE + " blocks.");
                }
                break;
            case HAND:
                head.setHand(m.value);
                break;
            case CONTROL:
                TileSignalHead.Control wanted = TileSignalHead.Control.fromOrdinal(m.value);
                head.setControl(wanted);
                if (head.control() != wanted) {
                    tell(player, "No signalled line within " + (int) TileSignalHead.LINK_RANGE
                        + " blocks; add signalling with /rcmc line signals <name> <count>");
                }
                break;
            case ASPECT:
                head.setManualAspect(SignalAspect.fromOrdinal(m.value));
                break;
            case SUBSIDIARY:
                head.setManualSubsidiary(m.value != 0);
                break;
            case APPROACH_LIT:
                head.setApproachLit(m.value != 0);
                break;
            case LINK_SIGNAL:
                if (!head.linkToNearestBoundary()) {
                    tell(player, "No signalled line within " + (int) TileSignalHead.LINK_RANGE
                        + " blocks; add signalling with /rcmc line signals <name> <count>");
                }
                break;
            case UNLINK_SIGNAL:
                head.unlink();
                break;
            case LINK_SWITCH:
                if (!head.linkToNearestSwitch()) {
                    tell(player, "No switch within " + (int) TileSignalHead.SWITCH_LINK_RANGE + " blocks.");
                }
                break;
            case UNLINK_SWITCH:
                head.unlinkSwitch();
                break;
            case ROUTE_BRANCH:
                head.setRouteBranch(m.value);
                break;
            case ROUTE_TEXT:
                head.setRouteText(m.value, m.text);
                break;
            case SPAN:
                head.setSpan(m.value);
                break;
            default:
                break;
        }
    }

    private static void tell(EntityPlayerMP player, String message) {
        player.sendMessage(new TextComponentString(TextFormatting.RED + message));
    }

    public static class Handler implements IMessageHandler<PacketSignalHeadEdit, IMessage> {
        @Override
        public IMessage onMessage(PacketSignalHeadEdit m, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, m));
            return null;
        }
    }
}
