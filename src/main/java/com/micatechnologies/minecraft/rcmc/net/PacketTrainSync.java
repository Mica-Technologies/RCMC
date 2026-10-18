package com.micatechnologies.minecraft.rcmc.net;

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
 * <p>A correction is eased in rather than set ({@link Train#correctToward}): the two sides tick on
 * their own clocks, and at coaster speed one tick of difference is a block of track, which a hard
 * set showed as the train and the rider's view jumping four times a second. The packet also
 * carries what ride hardware is doing to the train, so the client predicts lifts, brakes and
 * launches between corrections instead of letting the train coast through them
 * ({@code PredictedControl}).</p>
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
    private int carStyle;
    private int coasterModel;
    private int sectionId;
    private double distance;
    private double velocity;
    /** What ride hardware is doing to the train, for the client to predict with until the next one. */
    private double externalAcceleration;
    private boolean held;

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
        this.carStyle = spec.carStyle().ordinal();
        this.coasterModel = spec.coasterModel().ordinal();
        this.sectionId = train.reference().sectionId();
        this.distance = train.reference().distance();
        this.velocity = train.velocity();
        this.externalAcceleration = train.externalAcceleration();
        this.held = train.isHeld();
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
        carStyle = buf.readInt();
        coasterModel = buf.readByte();
        sectionId = buf.readInt();
        // Doubles, not floats: distance is what the client's integrator continues from, and float
        // rounding here would inject a position error on every correction rather than removing one.
        distance = buf.readDouble();
        velocity = buf.readDouble();
        externalAcceleration = buf.readDouble();
        held = buf.readBoolean();
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
        buf.writeInt(carStyle);
        buf.writeByte(coasterModel);
        buf.writeInt(sectionId);
        buf.writeDouble(distance);
        buf.writeDouble(velocity);
        buf.writeDouble(externalAcceleration);
        buf.writeBoolean(held);
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

            TrainSpec spec = new TrainSpec(message.carCount, message.carLength, message.couplingGap,
                message.seatsPerCar, message.bodyColour, message.trimColour,
                message.seatColour, TrainSpec.CarStyle.byOrdinal(message.carStyle),
                TrainSpec.CoasterModel.byOrdinal(message.coasterModel));
            if (existing == null || !existing.spec().equals(spec)) {
                // New to this client — or repainted, or rebuilt from a different car. A train's
                // spec is immutable, so a changed one means a new train where the old one was;
                // only updating its position kept every watching player on the old paint.
                // The server's physics constants, sent on join — prediction with any others
                // diverges between corrections.
                existing = new Train(spec,
                    com.micatechnologies.minecraft.rcmc.client.ClientPhysics.current().integrator(),
                    ref, message.velocity);
                manager.add(message.trainId, existing);
            }
            else {
                existing.correctToward(state.network(), ref, message.velocity);
            }
            existing.setSyncedControl(message.externalAcceleration, message.held);
        }
    }
}
