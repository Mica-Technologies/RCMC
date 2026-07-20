package com.micatechnologies.minecraft.rcmc.entity;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * One car of a coaster train.
 *
 * <p><b>This entity does not move itself.</b> Its position is a pure function of its train's
 * along-track state and the track geometry, recomputed every tick. Vanilla movement and collision
 * are bypassed entirely — {@code Entity.move()} at 1.5 blocks per tick does not behave, and
 * "somewhere off the rails" is not a state this design can represent. The entity exists to be
 * rendered, to be ridden, and to be tracked by the client; the truth lives in
 * {@link Train}.</p>
 *
 * <p><b>{@code getControllingPassenger()} is deliberately not overridden.</b> The base
 * implementation returns null, and {@code NetHandlerPlayServer} gates its "Vehicle moved too
 * quickly!" check on the rider being the controlling passenger — so leaving it alone grants
 * immunity outright, and the client never even sends {@code CPacketVehicleMove}. This is also the
 * honest model: a coaster rider has no control input. Overriding this to return the rider would
 * both break the physics ownership model and get players kicked at speed.</p>
 */
public class EntityCoasterCar extends Entity {

    private static final DataParameter<Integer> TRAIN_ID =
        EntityDataManager.createKey(EntityCoasterCar.class, DataSerializers.VARINT);

    private static final DataParameter<Integer> CAR_INDEX =
        EntityDataManager.createKey(EntityCoasterCar.class, DataSerializers.VARINT);

    /** Orientation last computed, kept for the renderer to interpolate from. */
    private TrackFrame frame;
    private TrackFrame previousFrame;

    public EntityCoasterCar(World world) {
        super(world);
        // Vanilla collision and movement are entirely bypassed: position comes from the track.
        this.noClip = true;
        this.preventEntitySpawning = true;
        // Cars are large and are routinely viewed from on board, where the entity's own bounding
        // box can fall outside the frustum test while the model is still visible.
        this.ignoreFrustumCheck = true;
        setSize(1.6F, 1.4F);
    }

    public EntityCoasterCar(World world, int trainId, int carIndex) {
        this(world);
        this.dataManager.set(TRAIN_ID, trainId);
        this.dataManager.set(CAR_INDEX, carIndex);
    }

    @Override
    protected void entityInit() {
        this.dataManager.register(TRAIN_ID, 0);
        this.dataManager.register(CAR_INDEX, 0);
    }

    public int trainId() {
        return this.dataManager.get(TRAIN_ID);
    }

    public int carIndex() {
        return this.dataManager.get(CAR_INDEX);
    }

    /** Orientation of this car, or {@code null} before the first successful update. */
    public TrackFrame frame() {
        return frame;
    }

    public TrackFrame previousFrame() {
        return previousFrame == null ? frame : previousFrame;
    }

    @Override
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.previousFrame = this.frame;

        RcmcWorldState state = RcmcWorldState.of(this.world);
        TrainManager manager = state == null ? null : state.trains();
        TrackNetwork network = state == null ? null : state.network();
        Train train = manager == null ? null : manager.train(trainId());

        if (train == null || network == null) {
            // No train behind this entity — it outlived its train, or the client has not yet
            // received the state for it. Hold position rather than dropping to 0,0,0; on the
            // client this is a normal transient during join.
            return;
        }

        if (!network.hasSection(train.reference().sectionId())) {
            // The train references track this side does not have. On the client that is a normal
            // transient: track and train state arrive in separate packets and are applied
            // independently, so a track update can land a tick before or after the train one — and
            // clearing the network leaves trains pointing at sections that no longer exist until
            // their removal packet arrives.
            //
            // Holding position is the only safe response. Letting this reach TrackNetwork.advance
            // throws IllegalArgumentException out of the entity tick, which Minecraft turns into a
            // hard crash — that is exactly what /rcmc clear used to do.
            return;
        }

        int index = carIndex();
        if (index >= train.spec().carCount()) {
            setDead();
            return;
        }

        this.frame = train.frameOfCar(network, index);
        setPosition(frame.position.x, frame.position.y, frame.position.z);

        // Motion is reported rather than integrated: several vanilla systems (knockback, the
        // player-movement check, sound attenuation) read it, and leaving it at zero while the
        // entity teleports each tick makes those misbehave.
        this.motionX = this.posX - this.prevPosX;
        this.motionY = this.posY - this.prevPosY;
        this.motionZ = this.posZ - this.prevPosZ;

        // Yaw/pitch are display-only for vanilla consumers. The renderer uses the full frame,
        // because 1.12.2 entities have no roll and a banked car cannot be expressed in two angles.
        this.rotationYaw = (float) Math.toDegrees(Math.atan2(-frame.forward.x, frame.forward.z));
        this.rotationPitch = (float) Math.toDegrees(Math.asin(-clamp(frame.forward.y)));
    }

    private static double clamp(double v) {
        return v < -1.0D ? -1.0D : (v > 1.0D ? 1.0D : v);
    }

    /**
     * Seat position for a passenger, derived from the car's frame so riders bank with the car.
     *
     * <p>Rider yaw is assigned rather than left to vanilla: {@code EntityLivingBase.updateDistance}
     * chases torso yaw toward head yaw at 30% per tick clamped to ±75°/tick, which visibly smears
     * a rider's body behind their head through a helix.</p>
     */
    @Override
    public void updatePassenger(Entity passenger) {
        if (!isPassenger(passenger) || frame == null) {
            return;
        }
        Vec3d seat = new Vec3d(
            frame.position.x + frame.up.x * getMountedYOffset(),
            frame.position.y + frame.up.y * getMountedYOffset(),
            frame.position.z + frame.up.z * getMountedYOffset());
        passenger.setPosition(seat.x, seat.y, seat.z);

        float yawDelta = this.rotationYaw - this.prevRotationYaw;
        passenger.rotationYaw += yawDelta;
        passenger.setRotationYawHead(passenger.getRotationYawHead() + yawDelta);
    }

    /**
     * Where a rider sits, relative to the track centreline.
     *
     * <p>Matches the seat cushions in {@code CarModel}. The whole car body sits ABOVE the
     * railheads — anything below them clips through the track — so the cushion is well clear of
     * the centreline and a rider has to be raised to meet it. Change this whenever the model's
     * floor height changes; the two are one measurement expressed in two places.</p>
     */
    @Override
    public double getMountedYOffset() {
        return 0.31D;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !this.isDead;
    }

    @Override
    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        if (this.world.isRemote) {
            return true;
        }
        if (this.isBeingRidden()) {
            return false;
        }
        player.startRiding(this);
        return true;
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    /**
     * No-op. Vanilla smears every incoming position packet — teleports and relative moves alike —
     * over three ticks, which would fight the client's own reconstruction of this car from train
     * state rather than complement it. Position comes from the track, not from the wire.
     */
    @Override
    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch,
                                             int posRotationIncrements, boolean teleport) {
        // intentionally empty
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        this.dataManager.set(TRAIN_ID, compound.getInteger("TrainId"));
        this.dataManager.set(CAR_INDEX, compound.getInteger("CarIndex"));
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setInteger("TrainId", trainId());
        compound.setInteger("CarIndex", carIndex());
    }
}
