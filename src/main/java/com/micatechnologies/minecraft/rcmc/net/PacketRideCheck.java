package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.rating.RideWarning;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Server to client: what the ride check found on the ride a player is editing, so the track editor
 * can show it on the track and in its screen. Sent with every editor view — each edit rechecks.
 */
public class PacketRideCheck implements IMessage {

    private List<RideWarning> warnings = new ArrayList<>();

    public PacketRideCheck() {
    }

    public PacketRideCheck(List<RideWarning> warnings) {
        this.warnings = new ArrayList<>(warnings);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readShort();
        warnings = new ArrayList<>(count);
        RideWarning.Kind[] kinds = RideWarning.Kind.values();
        RideWarning.Severity[] severities = RideWarning.Severity.values();
        for (int i = 0; i < count; i++) {
            int kind = buf.readByte();
            int severity = buf.readByte();
            int section = buf.readInt();
            double from = buf.readFloat();
            double to = buf.readFloat();
            double peak = buf.readFloat();
            if (kind >= 0 && kind < kinds.length && severity >= 0 && severity < severities.length) {
                warnings.add(new RideWarning(kinds[kind], severities[severity], section, from, to, peak));
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeShort(warnings.size());
        for (RideWarning warning : warnings) {
            buf.writeByte(warning.kind.ordinal());
            buf.writeByte(warning.severity.ordinal());
            buf.writeInt(warning.sectionId);
            buf.writeFloat((float) warning.from);
            buf.writeFloat((float) warning.to);
            buf.writeFloat((float) warning.peak);
        }
    }

    public static class Handler implements IMessageHandler<PacketRideCheck, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketRideCheck message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() ->
                com.micatechnologies.minecraft.rcmc.client.build.RideCheckOverlay.update(message.warnings));
            return null;
        }
    }
}
