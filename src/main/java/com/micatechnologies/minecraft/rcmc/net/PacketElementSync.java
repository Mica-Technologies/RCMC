package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.track.ElementSpan;
import com.micatechnologies.minecraft.rcmc.track.storage.ElementCodec;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Tells clients where ride hardware sits, so it can be drawn.
 *
 * <p>Carries only {@link ElementSpan}s — a type name and a stretch of track. Not the elements
 * themselves: their behaviour stays entirely server-side, and the client learns their effect from
 * the corrected train state it already receives. Sending the real elements would mean syncing a
 * station's dwell counter and a launch's phase every tick to change nothing a player can see.</p>
 *
 * <p>Sent whole on any change, like {@link PacketTrackSync}, and for the same reason: ride hardware
 * changes when a builder edits it, which is rare next to the tick rate.</p>
 */
public class PacketElementSync implements IMessage {

    private List<ElementSpan> spans = new ArrayList<>();

    /** Required by the network system's reflective instantiation. */
    public PacketElementSync() {
    }

    public PacketElementSync(RideElementSet set) {
        for (RideElement element : set.elements()) {
            String type = ElementCodec.typeOf(element);
            if (type != null) {
                spans.add(new ElementSpan(type,
                    element.sectionId(), element.startDistance(), element.endDistance()));
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        spans = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String type = ByteBufUtils.readUTF8String(buf);
            int section = buf.readInt();
            double start = buf.readDouble();
            double end = buf.readDouble();
            spans.add(new ElementSpan(type, section, start, end));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(spans.size());
        for (ElementSpan span : spans) {
            ByteBufUtils.writeUTF8String(buf, span.type == null ? "" : span.type);
            buf.writeInt(span.sectionId);
            buf.writeDouble(span.startDistance);
            buf.writeDouble(span.endDistance);
        }
    }

    public static class Handler implements IMessageHandler<PacketElementSync, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketElementSync message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> apply(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void apply(PacketElementSync message) {
            RcmcWorldState state =
                RcmcWorldState.of(net.minecraft.client.Minecraft.getMinecraft().world);
            if (state != null) {
                state.setElementSpans(message.spans);
            }
        }
    }
}
