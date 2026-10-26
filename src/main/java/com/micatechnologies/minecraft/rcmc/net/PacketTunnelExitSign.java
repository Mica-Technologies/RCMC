package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.block.sign.TileTunnelExitSign;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client to server: the distances typed into a tunnel exit sign's screen. */
public class PacketTunnelExitSign implements IMessage {

    /** How near a player must be to a sign to change it, squared. */
    private static final double REACH_SQ = 10.0D * 10.0D;

    private BlockPos pos = BlockPos.ORIGIN;
    private String left = "";
    private String right = "";

    public PacketTunnelExitSign() {
    }

    public PacketTunnelExitSign(BlockPos pos, String left, String right) {
        this.pos = pos;
        this.left = left;
        this.right = right;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        left = ByteBufUtils.readUTF8String(buf);
        right = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        ByteBufUtils.writeUTF8String(buf, left);
        ByteBufUtils.writeUTF8String(buf, right);
    }

    public static class Handler implements IMessageHandler<PacketTunnelExitSign, IMessage> {
        @Override
        public IMessage onMessage(PacketTunnelExitSign m, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.getDistanceSqToCenter(m.pos) > REACH_SQ || !player.world.isBlockLoaded(m.pos)) {
                    return;
                }
                TileEntity tile = player.world.getTileEntity(m.pos);
                if (tile instanceof TileTunnelExitSign) {
                    ((TileTunnelExitSign) tile).setDistances(m.left, m.right);
                }
            });
            return null;
        }
    }
}
