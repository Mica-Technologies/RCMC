package com.micatechnologies.minecraft.rcmc.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Server to client: the server's {@link PhysicsSettings}, sent with the rest of the full state. */
public class PacketPhysicsSettings implements IMessage {

    private PhysicsSettings settings;

    /** Required by the network system's reflective instantiation. */
    public PacketPhysicsSettings() {
    }

    public PacketPhysicsSettings(PhysicsSettings settings) {
        this.settings = settings;
    }

    public PhysicsSettings settings() {
        return settings;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        settings = new PhysicsSettings(buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readDouble(), buf.readInt());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(settings.gravity);
        buf.writeDouble(settings.rollingResistance);
        buf.writeDouble(settings.airDrag);
        buf.writeDouble(settings.maxSpeed);
        buf.writeInt(settings.subSteps);
    }

    public static class Handler implements IMessageHandler<PacketPhysicsSettings, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketPhysicsSettings message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(
                () -> com.micatechnologies.minecraft.rcmc.client.ClientPhysics.set(message.settings));
            return null;
        }
    }
}
