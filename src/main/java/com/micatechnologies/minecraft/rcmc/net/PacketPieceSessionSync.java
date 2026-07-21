package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.builder.PieceBuildSession;
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
 * Sends a player their prefab chain, plus the piece the next click would add.
 *
 * <p><b>The preview nodes are generated on the server and shipped, rather than regenerated on the
 * client.</b> The palette is pure Java and present on both sides, so the client could compute them
 * — but it would have to be told the running exit frame to compute them <em>from</em>, and any
 * disagreement about that frame produces a ghost that does not match what the click builds. Sending
 * the nodes themselves makes that class of divergence unrepresentable, and it is cheap: a chain
 * changes when a builder clicks or scrolls, not per tick.</p>
 *
 * <p>To one player only, like {@link PacketBuildSessionSync}; a half-assembled coaster is that
 * builder's scratch work.</p>
 */
public class PacketPieceSessionSync implements IMessage {

    private boolean started;
    private List<TrackNode> chain = new ArrayList<>();
    private List<TrackNode> preview = new ArrayList<>();
    private int selectedIndex;
    private double parameter;
    private int pieceCount;

    /** Required by the network system's reflective instantiation. */
    public PacketPieceSessionSync() {
    }

    public PacketPieceSessionSync(PieceBuildSession session) {
        this.started = session.isStarted();
        this.chain = new ArrayList<>(session.nodes());
        this.preview = new ArrayList<>(session.previewNodes());
        this.selectedIndex = session.selectedIndex();
        this.parameter = session.selectedParameter();
        this.pieceCount = session.pieceCount();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        started = buf.readBoolean();
        chain = readNodes(buf);
        preview = readNodes(buf);
        selectedIndex = buf.readInt();
        parameter = buf.readDouble();
        pieceCount = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(started);
        writeNodes(buf, chain);
        writeNodes(buf, preview);
        buf.writeInt(selectedIndex);
        buf.writeDouble(parameter);
        buf.writeInt(pieceCount);
    }

    private static List<TrackNode> readNodes(ByteBuf buf) {
        int count = buf.readInt();
        List<TrackNode> nodes = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            nodes.add(new TrackNode(new Vec3(x, y, z), buf.readDouble(), null));
        }
        return nodes;
    }

    private static void writeNodes(ByteBuf buf, List<TrackNode> nodes) {
        buf.writeInt(nodes.size());
        for (TrackNode node : nodes) {
            buf.writeDouble(node.position().x);
            buf.writeDouble(node.position().y);
            buf.writeDouble(node.position().z);
            buf.writeDouble(node.bankDegrees());
        }
    }

    public static class Handler implements IMessageHandler<PacketPieceSessionSync, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketPieceSessionSync message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(
                () -> com.micatechnologies.minecraft.rcmc.client.build.ClientPieceSession.update(
                    message.started, message.chain, message.preview,
                    message.selectedIndex, message.parameter, message.pieceCount));
            return null;
        }
    }
}
