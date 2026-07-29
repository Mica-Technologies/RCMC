package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.physics.transit.DoorSide;
import com.micatechnologies.minecraft.rcmc.physics.transit.ServiceSnapshot;
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
 * The live half of the signage data: one {@link ServiceSnapshot} per train in service.
 *
 * <p>A snapshot is three small fields and a flag, and it only changes when a service passes a
 * stop or opens its doors — so this is sent on a slow periodic tick rather than per movement,
 * and the arrival boards recompute their "N stops away" rows from whatever the latest snapshot
 * says. Full-list replacement, not a delta: the list is tiny and idempotent application beats
 * delta bookkeeping at this size, the same trade {@link PacketTrackSync} makes.</p>
 */
public class PacketServiceSync implements IMessage {

    private List<ServiceSnapshot> snapshots = new ArrayList<>();

    /** Required by the network system's reflective instantiation. */
    public PacketServiceSync() {
    }

    public PacketServiceSync(List<ServiceSnapshot> snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        snapshots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String lineName = ByteBufUtils.readUTF8String(buf);
            int trainId = buf.readInt();
            int direction = buf.readInt();
            int nextStop = buf.readInt();
            boolean atPlatform = buf.readBoolean();
            boolean doorsOpen = buf.readBoolean();
            float doorFraction = buf.readFloat();
            float distanceToNextStop = buf.readFloat();
            int doorSide = buf.readInt();
            snapshots.add(new ServiceSnapshot(trainId, lineName, direction, nextStop, atPlatform,
                doorsOpen, doorFraction, distanceToNextStop, DoorSide.byOrdinal(doorSide)));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(snapshots.size());
        for (ServiceSnapshot snapshot : snapshots) {
            ByteBufUtils.writeUTF8String(buf, snapshot.lineName());
            buf.writeInt(snapshot.trainId());
            buf.writeInt(snapshot.serviceDirection());
            buf.writeInt(snapshot.nextStopIndex());
            buf.writeBoolean(snapshot.atPlatform());
            buf.writeBoolean(snapshot.doorsOpen());
            buf.writeFloat((float) snapshot.doorFraction());
            buf.writeFloat((float) snapshot.distanceToNextStop());
            // Track-relative, which is the form the car model is drawn in — a client needs no
            // station registry and no facing arithmetic to know which doors to animate.
            buf.writeInt(snapshot.doorSide().ordinal());
        }
    }

    public static class Handler implements IMessageHandler<PacketServiceSync, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketServiceSync message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> apply(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void apply(PacketServiceSync message) {
            RcmcWorldState state =
                RcmcWorldState.of(net.minecraft.client.Minecraft.getMinecraft().world);
            if (state != null) {
                state.setServiceSnapshots(message.snapshots);
            }
        }
    }
}
