package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.Rcmc;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * A spoken station announcement, sent from a station speaker to the players near it.
 *
 * <p>Text over the wire, never audio — exactly as CSM's own TTS block does it (see the integration
 * note in project memory). The server decides <em>who</em> is in earshot and what to say
 * ({@code TileStationSpeaker} via {@link com.micatechnologies.minecraft.rcmc.physics.transit.TransitSignText});
 * the client turns text into sound. That keeps the payload tiny and lets the client choose its
 * backend — CSM's MaryTTS when present, an on-screen subtitle otherwise — through the sided proxy,
 * so no client-only type is named here in common code.</p>
 */
public class PacketStationAnnounce implements IMessage {

    private String text;
    private String voice;

    /** Required by the network system's reflective instantiation. */
    public PacketStationAnnounce() {
    }

    public PacketStationAnnounce(String text, String voice) {
        this.text = text == null ? "" : text;
        this.voice = voice == null ? "" : voice;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        text = ByteBufUtils.readUTF8String(buf);
        voice = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, text);
        ByteBufUtils.writeUTF8String(buf, voice);
    }

    public static class Handler implements IMessageHandler<PacketStationAnnounce, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketStationAnnounce message, MessageContext ctx) {
            // Off the network thread and onto the client thread — TTS init and any GUI touch must
            // not run here. The proxy is the only sanctioned door into client-only code.
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(
                () -> Rcmc.proxy.speakTts(message.text, message.voice));
            return null;
        }
    }
}
