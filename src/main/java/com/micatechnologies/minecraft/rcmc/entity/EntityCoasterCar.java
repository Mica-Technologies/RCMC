package com.micatechnologies.minecraft.rcmc.entity;

import com.micatechnologies.minecraft.rcmc.physics.CarSeating;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
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
import net.minecraft.util.math.AxisAlignedBB;
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

        // The BODY frame, not the raw track-point frame: identical for coaster cars, and the
        // two-bogie chord placement for long metro cars — see Train.bodyFrameOfCar.
        this.frame = train.bodyFrameOfCar(network, index);
        setPosition(frame.position.x, frame.position.y, frame.position.z);

        // Metro cars are much larger than the default coaster box; size the entity to match once
        // the spec is known (idempotent — setSize is a no-op when unchanged).
        if (train.spec().carStyle() == TrainSpec.CarStyle.METRO) {
            setSize(3.8F, 5.95F);
        }

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

        boardWalkIns();
    }

    private static double clamp(double v) {
        return v < -1.0D ? -1.0D : (v > 1.0D ? 1.0D : v);
    }

    /**
     * How long after stepping off a train that player is left alone, in ticks.
     *
     * <p>Without this, walking aboard and being seated automatically would be a trap: dismounting
     * puts the player back inside the car's own volume, where the very next tick would seat them
     * again and they could never get off.</p>
     */
    private static final int REBOARD_GRACE_TICKS = 30;

    /** Server-side scratch: when each player last stepped off a train. */
    private static final java.util.Map<java.util.UUID, Long> RECENT_DISMOUNTS =
        new java.util.HashMap<>();

    @Override
    protected void addPassenger(Entity passenger) {
        // Where they were standing when they stepped aboard becomes where they are standing in the
        // car — so boarding through a door puts you in that doorway rather than teleporting you to
        // a seat you did not choose.
        double[] offset = {0.0D, 0.0D};
        if (frame != null && passenger instanceof EntityPlayer) {
            double dx = passenger.posX - frame.position.x;
            double dz = passenger.posZ - frame.position.z;
            offset[0] = dx * frame.forward.x + dz * frame.forward.z;
            offset[1] = dx * frame.right.x + dz * frame.right.z;
            standingOffsets.put(passenger.getUniqueID(), offset);
        }
        super.addPassenger(passenger);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        standingOffsets.remove(passenger.getUniqueID());
        if (!this.world.isRemote && passenger instanceof EntityPlayer) {
            RECENT_DISMOUNTS.put(passenger.getUniqueID(), this.world.getTotalWorldTime());
        }
    }

    /**
     * Seats any player standing inside this car while its doors are open.
     *
     * <p>Boarding is "walk in", not "right-click the side of the train". Riders still have to be
     * seated rather than merely standing inside, because the car's position is recomputed from the
     * track every tick and never moves anyone with it — an unseated passenger would be left on the
     * platform the moment the train pulled away.</p>
     */
    private void boardWalkIns() {
        if (this.world.isRemote) {
            return;
        }
        boolean doorsOpen = com.micatechnologies.minecraft.rcmc.world.MetroDoors
            .areOpen(this.world, trainId());
        Train train = trainOrNull();
        boolean moving = train != null && Math.abs(train.velocity()) > 1.0D;
        if (!doorsOpen && !moving) {
            return;
        }
        long now = this.world.getTotalWorldTime();
        for (EntityPlayer player : this.world.getEntitiesWithinAABB(EntityPlayer.class,
            getEntityBoundingBox().grow(-0.2D, 0.0D, -0.2D))) {
            if (player.isRiding() || !canFitPassenger(player)) {
                continue;
            }
            if (!doorsOpen) {
                // Inside a moving train with the doors shut — they pressed sneak. Vanilla would
                // now shove them out through a solid car at line speed, which is a worse outcome
                // than simply not letting go: you cannot step off a moving train. The dismount
                // grace is deliberately ignored here, because honouring it is what would let them
                // fall out.
                player.startRiding(this);
                continue;
            }
            Long left = RECENT_DISMOUNTS.get(player.getUniqueID());
            if (left != null && now - left < REBOARD_GRACE_TICKS) {
                continue;
            }
            player.startRiding(this);
        }
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
        Train train = trainOrNull();
        TrainSpec spec = train == null ? null : train.spec();
        double acrossOffset;
        double alongOffset;
        if (spec != null && spec.carStyle() == TrainSpec.CarStyle.METRO
            && passenger instanceof EntityPlayer) {
            // Standing rider: their place in the car is theirs to choose, and walking is what
            // moves it. See walkStandingRider for why riding is the mechanism.
            double[] offset = walkStandingRider((EntityPlayer) passenger, spec);
            alongOffset = offset[0];
            acrossOffset = offset[1];
        } else {
            // Coaster stock, and anything that is not a player, keeps its assigned seat.
            int index = getPassengers().indexOf(passenger);
            acrossOffset = CarSeating.acrossOffset(spec, index);
            alongOffset = CarSeating.alongOffset(spec, index);
        }
        Vec3d seat = new Vec3d(
            frame.position.x + frame.up.x * getMountedYOffset()
                + frame.right.x * acrossOffset + frame.forward.x * alongOffset,
            frame.position.y + frame.up.y * getMountedYOffset()
                + frame.right.y * acrossOffset + frame.forward.y * alongOffset,
            frame.position.z + frame.up.z * getMountedYOffset()
                + frame.right.z * acrossOffset + frame.forward.z * alongOffset);
        passenger.setPosition(seat.x, seat.y, seat.z);

        float yawDelta = this.rotationYaw - this.prevRotationYaw;
        passenger.rotationYaw += yawDelta;
        passenger.setRotationYawHead(passenger.getRotationYawHead() + yawDelta);
    }

    /**
     * Where a rider sits, relative to the track centreline — the seat cushions in {@code CarModel},
     * or the bench tops in {@code MetroCarModel} for metro stock, whose high floor puts riders a
     * full block higher. The heights themselves live in {@link CarSeating}, beside the lateral and
     * longitudinal offsets that go with them.
     */
    @Override
    public double getMountedYOffset() {
        Train train = trainOrNull();
        TrainSpec spec = train == null ? null : train.spec();
        // A standing rider's feet are on the floor, not on a cushion.
        if (spec != null && spec.carStyle() == TrainSpec.CarStyle.METRO) {
            return CarSeating.METRO_FLOOR_HEIGHT;
        }
        return CarSeating.seatHeight(spec);
    }

    /**
     * Where each standing rider is within this car: {@code [along, across]} in blocks from centre.
     *
     * <p>Per entity rather than static: two cars of a consist are different rooms.</p>
     */
    private final java.util.Map<java.util.UUID, double[]> standingOffsets =
        new java.util.HashMap<>();

    /** How far a standing rider moves per tick at full input, in blocks. */
    private static final double WALK_SPEED = 0.16D;

    /**
     * Moves a standing rider around the saloon under their own steam, and returns where they are.
     *
     * <p><b>Standing is implemented as riding.</b> That is the whole trick, and it is worth stating
     * plainly: Minecraft has no moving reference frames, so a player merely <em>standing</em> in a
     * car doing 15 blocks/s would need teleporting by the car's delta every tick, which fights
     * client prediction and the server's own movement checks. As a passenger, vanilla already moves
     * them with the vehicle perfectly — so all that is left is choosing <em>where in the car</em>
     * they are, which is what this does. The hard problem is sidestepped rather than solved.</p>
     *
     * <p>Input arrives for free: 1.12.2's client sends {@code CPacketInput} every tick while a
     * player is riding, and {@code NetHandlerPlayServer} writes it onto {@code moveForward} and
     * {@code moveStrafing}. No custom packet is needed, and the values are already the server's.</p>
     *
     * <p>The walk direction is built from the player's look vector projected onto the car's own
     * frame axes, rather than from yaw arithmetic. Yaw would have to agree with the frame's
     * handedness, and that basis is deliberately left-handed (see {@code RenderCoasterCar}) — a
     * projection cannot get that wrong, and mirrored strafing would be a maddening bug to chase.</p>
     *
     * <p>Bounds come from {@link CarSeating}, and they are load-bearing: a rider's position is
     * written directly every tick, so vanilla collision never runs and those clamps <em>are</em>
     * the walls and the seat fronts.</p>
     */
    private double[] walkStandingRider(EntityPlayer player, TrainSpec spec) {
        double[] offset = standingOffsets.computeIfAbsent(player.getUniqueID(),
            id -> new double[] {0.0D, 0.0D});

        double forward = player.moveForward;
        double strafe = player.moveStrafing;
        if (forward != 0.0D || strafe != 0.0D) {
            net.minecraft.util.math.Vec3d look = player.getLookVec();
            double lookX = look.x;
            double lookZ = look.z;
            double lookLength = Math.sqrt(lookX * lookX + lookZ * lookZ);
            if (lookLength > 1.0e-4D) {
                lookX /= lookLength;
                lookZ /= lookLength;
                // Vanilla's strafe basis: right = (look.z, -look.x).
                double moveX = lookX * forward + lookZ * strafe;
                double moveZ = lookZ * forward - lookX * strafe;
                double length = Math.sqrt(moveX * moveX + moveZ * moveZ);
                if (length > 1.0D) {
                    moveX /= length;
                    moveZ /= length;
                }
                // Project the intended world-space step onto the car's own axes.
                double along = moveX * frame.forward.x + moveZ * frame.forward.z;
                double across = moveX * frame.right.x + moveZ * frame.right.z;
                offset[0] += along * WALK_SPEED;
                offset[1] += across * WALK_SPEED;
            }
        }
        double halfLength = CarSeating.walkableHalfLength(spec);
        double halfWidth = CarSeating.walkableHalfWidth(spec);
        offset[0] = Math.max(-halfLength, Math.min(halfLength, offset[0]));
        offset[1] = Math.max(-halfWidth, Math.min(halfWidth, offset[1]));
        return offset;
    }

    /**
     * How many riders this car holds — see {@link CarSeating#capacity}, which owns the rule and is
     * tested there rather than here, where nothing can reach it.
     */
    private int seatCapacity() {
        Train train = trainOrNull();
        return CarSeating.capacity(train == null ? null : train.spec());
    }

    private Train trainOrNull() {
        RcmcWorldState state = RcmcWorldState.of(this.world);
        return state == null ? null : state.trains().train(trainId());
    }

    @Override
    protected boolean canFitPassenger(Entity passenger) {
        return getPassengers().size() < seatCapacity();
    }

    /**
     * Collidable — and mouse-over-able — except by the rider of this same train.
     *
     * <p>Vanilla's {@code EntityRenderer.getMouseOver} only declines to target the vehicle you are
     * riding, and only when something else was hit first: the check is
     * {@code entity1.getLowestRidingEntity() == entity.getLowestRidingEntity() &&
     * !entity1.canRiderInteract()}, guarded by {@code d2 == 0.0D}. The <em>other</em> cars of your
     * own train are not your vehicle at all, so they are freely targetable, and looking down the
     * saloon put a probe tooltip on screen that flickered as the ray crossed between cars.</p>
     *
     * <p>Refusing to be collided with, client-side, takes the whole train out of the mouse-over
     * candidate list for the person riding it. Nothing is lost: a rider is a passenger, so they are
     * never pushed by the car anyway, and this is client-only so no simulation sees it.</p>
     */
    @Override
    public boolean canBeCollidedWith() {
        if (this.isDead) {
            return false;
        }
        return !(this.world.isRemote
            && com.micatechnologies.minecraft.rcmc.Rcmc.proxy.isLocalPlayerAboard(trainId()));
    }

    /**
     * Makes the car solid to <em>other</em> entities, so a player cannot walk through a train.
     *
     * <p>Returning a non-null box here is what Minecraft uses to push other entities out; it does
     * not affect this entity's own movement, which stays governed by the track. That distinction
     * is the whole reason this is safe: {@link #noClip} keeps the CAR from colliding with the
     * world — which must remain true, since at 1.5 blocks per tick vanilla collision does not
     * behave and "off the rails" is not a representable state — while this makes the WORLD collide
     * with the car.</p>
     *
     * <p>Riders are exempt automatically: Minecraft never collides an entity with the thing it is
     * riding, so boarding does not eject you.</p>
     */
    /**
     * Solid as a whole car normally; solid as <em>just its floor</em> while the doors are open.
     *
     * <p>A single {@code AxisAlignedBB} cannot be hollow, so a car that admits people has to give
     * up being a box. Dropping the box entirely was the first attempt and it left nothing at all to
     * stand on — you walked through the doorway and straight out of the bottom of the train.
     * Returning the floor slab instead keeps the one surface that matters and loses only the walls,
     * which the doorway was going to breach anyway.</p>
     *
     * <p>This is the entity's own collision box rather than a shape contributed through
     * {@code GetCollisionBoxesEvent}: {@code World.getCollisionBoxes} reads
     * {@code entity.getCollisionBoundingBox()} directly for every nearby entity, which is the same
     * path that made the car solid in the first place and is therefore the one already known to
     * work here. One mechanism, not two.</p>
     *
     * <p>Safe precisely because doors only open when the train is berthed and stationary: there is
     * no moment where a moving car is missing its walls and could sweep through someone.</p>
     */
    @Override
    public AxisAlignedBB getCollisionBoundingBox() {
        if (this.isDead) {
            return null;
        }
        if (!com.micatechnologies.minecraft.rcmc.world.MetroDoors.areOpen(this.world, trainId())) {
            return getEntityBoundingBox();
        }
        AxisAlignedBB body = getEntityBoundingBox();
        double top = body.minY + CarSeating.METRO_FLOOR_HEIGHT;
        return new AxisAlignedBB(
            body.minX + FLOOR_EDGE_INSET, top - FLOOR_SLAB_THICKNESS, body.minZ + FLOOR_EDGE_INSET,
            body.maxX - FLOOR_EDGE_INSET, top, body.maxZ - FLOOR_EDGE_INSET);
    }

    /** Thickness of the floor slab left solid while the doors are open. */
    private static final double FLOOR_SLAB_THICKNESS = 0.5D;

    /**
     * How far the floor slab is held in from the car's outer skin, so nobody stands on a sliver of
     * floor while still outside the train.
     */
    private static final double FLOOR_EDGE_INSET = 0.2D;

    /**
     * Pushed aside rather than pushing. A player walking into a stationary train should be stopped
     * by it; a train should not be shoved off its own geometry by being leaned on — and could not
     * be anyway, since its position is recomputed from the track every tick.
     */
    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        if (this.world.isRemote) {
            return true;
        }
        if (!canFitPassenger(player)) {
            say(player, net.minecraft.util.text.TextFormatting.YELLOW, "This car is full.");
            return false;
        }
        // A train in metro service boards through its doors: riders may only mount while the
        // service has them open at a platform. Trains not in service board as always.
        //
        // Refusing SILENTLY was indistinguishable from the mod having no boarding at all, which is
        // exactly how it was reported: a player right-clicks a metro, nothing happens, and there is
        // no way to tell a closed door from a broken feature. Say which it is.
        RcmcWorldState state = RcmcWorldState.of(this.world);
        if (state != null && !state.transit().mayBoard(trainId())) {
            say(player, net.minecraft.util.text.TextFormatting.YELLOW,
                "The doors are closed — board while the train is stopped at a platform.");
            return false;
        }
        player.startRiding(this);
        return true;
    }

    private static void say(EntityPlayer player, net.minecraft.util.text.TextFormatting colour,
                            String message) {
        player.sendMessage(new net.minecraft.util.text.TextComponentString(colour + message));
    }

    /**
     * Metro riders stand; coaster riders sit.
     *
     * <p>This is the visible half of standing-as-riding: a passenger rendered in the sitting pose
     * while walking up the aisle would give the whole thing away.</p>
     */
    @Override
    public boolean shouldRiderSit() {
        Train train = trainOrNull();
        return train == null || train.spec().carStyle() != TrainSpec.CarStyle.METRO;
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
