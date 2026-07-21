package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.RcmcConfig;
import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * One train's authoritative state.
 *
 * <p>This is the entire per-train payload: a track address, a speed, and enough of the train's
 * shape to reconstruct it. <b>No car positions.</b> The client runs the same deterministic
 * integrator over the same geometry, so it can rebuild every car itself — which is what makes
 * smooth motion possible at coaster speeds where vanilla position tracking cannot keep up.</p>
 *
 * <p>The correction is applied as a hard set rather than eased toward. That is a knowingly
 * temporary choice: at this stage the client has only just received the train and has nothing
 * better to blend from, and a visible snap is more diagnostic than a silent smoothing that hides
 * how far the two sides had drifted. Easing belongs with the reconciliation work in Phase 4.4,
 * where the divergence can first be measured.</p>
 */
public class PacketTrainSync implements IMessage {

    private int trainId;
    private int carCount;
    private float carLength;
    private float couplingGap;
    private int seatsPerCar;
    private int bodyColour;
    private int trimColour;
    private int seatColour;
    private int sectionId;
    private double distance;
    private double velocity;

    /** Required by the network system's reflective instantiation. */
    public PacketTrainSync() {
    }

    public PacketTrainSync(int trainId, Train train) {
        TrainSpec spec = train.spec();
        this.trainId = trainId;
        this.carCount = spec.carCount();
        this.carLength = (float) spec.carLength();
        this.couplingGap = (float) spec.couplingGap();
        this.seatsPerCar = spec.seatsPerCar();
        this.bodyColour = spec.bodyColour();
        this.trimColour = spec.trimColour();
        this.seatColour = spec.seatColour();
        this.sectionId = train.reference().sectionId();
        this.distance = train.reference().distance();
        this.velocity = train.velocity();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        trainId = buf.readInt();
        carCount = buf.readInt();
        carLength = buf.readFloat();
        couplingGap = buf.readFloat();
        seatsPerCar = buf.readInt();
        bodyColour = buf.readInt();
        trimColour = buf.readInt();
        seatColour = buf.readInt();
        sectionId = buf.readInt();
        // Doubles, not floats: distance is what the client's integrator continues from, and float
        // rounding here would inject a position error on every correction rather than removing one.
        distance = buf.readDouble();
        velocity = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(trainId);
        buf.writeInt(carCount);
        buf.writeFloat(carLength);
        buf.writeFloat(couplingGap);
        buf.writeInt(seatsPerCar);
        buf.writeInt(bodyColour);
        buf.writeInt(trimColour);
        buf.writeInt(seatColour);
        buf.writeInt(sectionId);
        buf.writeDouble(distance);
        buf.writeDouble(velocity);
    }

    public static class Handler implements IMessageHandler<PacketTrainSync, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketTrainSync message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> apply(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void apply(PacketTrainSync message) {
            RcmcWorldState state =
                RcmcWorldState.of(net.minecraft.client.Minecraft.getMinecraft().world);
            if (state == null) {
                return;
            }
            TrainManager manager = state.trains();
            Train existing = manager.train(message.trainId);
            TrackRef ref = new TrackRef(message.sectionId, message.distance);

            if (existing == null) {
                // Physics constants come from the client's own config here. They MUST match the
                // server's or prediction diverges — syncing them on join is outstanding work, and
                // is why RcmcConfig documents the physics category as server-authoritative.
                manager.add(message.trainId, new Train(
                    new TrainSpec(message.carCount, message.carLength, message.couplingGap,
                        message.seatsPerCar, message.bodyColour, message.trimColour,
                        message.seatColour),
                    new PhysicsIntegrator(RcmcConfig.gravity, RcmcConfig.rollingResistance,
                        RcmcConfig.airDrag, RcmcConfig.maxSpeed),
                    ref, message.velocity));
            }
            else {
                existing.setState(ref, message.velocity);
            }
        }
    }
}
