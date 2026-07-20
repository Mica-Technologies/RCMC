package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.builder.TrackBuildSession;
import com.micatechnologies.minecraft.rcmc.client.build.ClientBuildSession;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Sends a player their own pending track-building nodes.
 *
 * <p>Needed for the ghost preview: pending nodes live in a server-side {@link TrackBuildSession},
 * so without this the client has nothing to draw a provisional curve from.</p>
 *
 * <p>Sent to one player on change, never broadcast and never per tick. A half-built curve is that
 * builder's scratch work — nobody else has any use for it, and it changes only when they click.</p>
 *
 * <p>The server stays authoritative over what is ultimately committed. This is a mirror for
 * drawing, not a handoff of the session.</p>
 */
public class PacketBuildSessionSync implements IMessage {

    private List<TrackNode> nodes = new ArrayList<>();
    private double bankDegrees;
    private boolean closing;
    private double heightOffset;
    private int segmentType;

    /** Required by the network system's reflective instantiation. */
    public PacketBuildSessionSync() {
    }

    public PacketBuildSessionSync(TrackBuildSession session) {
        this.nodes = new ArrayList<>(session.pending());
        this.bankDegrees = session.bankDegrees();
        this.closing = session.isClosing();
        this.heightOffset = session.heightOffset();
        this.segmentType = session.currentType().ordinal();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            nodes.add(new TrackNode(new Vec3(x, y, z), buf.readDouble(), null));
        }
        bankDegrees = buf.readDouble();
        closing = buf.readBoolean();
        heightOffset = buf.readDouble();
        segmentType = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(nodes.size());
        for (TrackNode node : nodes) {
            buf.writeDouble(node.position().x);
            buf.writeDouble(node.position().y);
            buf.writeDouble(node.position().z);
            buf.writeDouble(node.bankDegrees());
        }
        buf.writeDouble(bankDegrees);
        buf.writeBoolean(closing);
        buf.writeDouble(heightOffset);
        buf.writeInt(segmentType);
    }

    public static class Handler implements IMessageHandler<PacketBuildSessionSync, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketBuildSessionSync message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(
                () -> ClientBuildSession.update(message.nodes, message.bankDegrees,
                    message.closing, message.heightOffset, message.segmentType));
            return null;
        }
    }
}
