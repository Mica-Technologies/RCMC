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

    /**
     * True for a car loaded out of chunk NBT — which now means only one thing: it was saved by a
     * version of the mod that still persisted cars.
     *
     * <p>Cars are no longer written to disk ({@link #writeToNBTOptional}); {@code TrainEntities}
     * creates exactly the cars the saved trains call for. A car arriving from disk is therefore a
     * duplicate of one this world has already made, and it removes itself. Without this, a world
     * saved before train persistence would grow a second, unowned car for every one it restored —
     * identical, superimposed, and separately boardable — as each old chunk loaded.</p>
     */
    private boolean restoredFromDisk;

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

        if (!this.world.isRemote && restoredFromDisk) {
            // A car from a save that predates train persistence. The world has already created the
            // cars its trains call for, so this one is a duplicate of an existing car — see the
            // field javadoc. Removed on its first tick, before it can be boarded or collided with.
            setDead();
            return;
        }

        RcmcWorldState state = RcmcWorldState.of(this.world);
        TrainManager manager = state == null ? null : state.trains();
        TrackNetwork network = state == null ? null : state.network();
        Train train = manager == null ? null : manager.train(trainId());

        if (train == null && !this.world.isRemote && state != null) {
            // Server-side there is no such thing as a car whose train has not arrived yet: a train
            // is registered before its cars are spawned, in both the command and the restore path.
            // So this car has outlived its train — /rcmc rmsection or /rcmc clear removed it — and
            // nothing else will ever clean it up. (Client-side this IS a normal transient, which is
            // why the check is sided.)
            setDead();
            return;
        }

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

        // Drained here, and before boardWalkIns, so a rider stepping out is put on the platform
        // before the boarding check looks at who is standing inside — otherwise they would be
        // seated again on their way through the door.
        //
        // The queue is filled from updatePassenger, which vanilla calls from the PASSENGER's
        // updateRidden (Entity:2273), not from this entity's update — so an exit is applied on this
        // car's next tick, which may be this one or the following one depending on iteration order.
        // Either way it is never applied while the passenger list is being walked, which is the
        // point of queueing it.
        applyPendingDismounts();
        applyStepOffs();
        boardWalkIns();
    }

    /** Coaster riders who got off, with where along the car and which side they sat. */
    private final java.util.Map<java.util.UUID, double[]> stepOffs = new java.util.HashMap<>();

    /**
     * Remembers where a coaster rider sat as they get off, before vanilla moves them.
     *
     * <p>Vanilla's dismount search looks for room around the vehicle and, with the car on open
     * track, settled on its roof: a rider who got off at the station was left standing on top of
     * the car. They are stepped off beside their own seat instead, on the next tick, since vanilla
     * places them after this returns.</p>
     */
    private void noteStepOff(Entity passenger) {
        if (this.world.isRemote || frame == null || !(passenger instanceof EntityPlayer)) {
            return;
        }
        Train train = trainOrNull();
        if (train == null || train.spec().carStyle() == TrainSpec.CarStyle.METRO) {
            return;
        }
        double dx = passenger.posX - frame.position.x;
        double dz = passenger.posZ - frame.position.z;
        double along = dx * frame.forward.x + dz * frame.forward.z;
        double across = dx * frame.right.x + dz * frame.right.z;
        stepOffs.put(passenger.getUniqueID(), new double[] {along, across});
    }

    private void applyStepOffs() {
        if (stepOffs.isEmpty() || this.world.isRemote) {
            return;
        }
        Train train = trainOrNull();
        boolean moving = train != null && Math.abs(train.velocity()) > 1.0D;
        for (java.util.Map.Entry<java.util.UUID, double[]> entry : stepOffs.entrySet()) {
            EntityPlayer player = this.world.getPlayerEntityByUUID(entry.getKey());
            // Moving: the restraint puts them back in their seat. Far off: they were teleported, and
            // are not dragged back to the car.
            if (moving || frame == null || player == null || player.isRiding() || !player.isEntityAlive()
                || player.getDistanceSq(this) > 16.0D) {
                continue;
            }
            double along = entry.getValue()[0];
            double across = com.micatechnologies.minecraft.rcmc.physics.CoasterCarLayout
                .stepOffAcross(entry.getValue()[1]);
            double x = frame.position.x + frame.forward.x * along + frame.right.x * across;
            double z = frame.position.z + frame.forward.z * along + frame.right.z * across;
            // Track level first, then up a little for a platform built at car-floor height. Where
            // every spot is blocked, vanilla's choice stands.
            for (double lift : new double[] {0.0D, 0.5D, 1.0D}) {
                double y = frame.position.y + lift;
                AxisAlignedBB box = player.getEntityBoundingBox()
                    .offset(x - player.posX, y - player.posY, z - player.posZ);
                if (this.world.getCollisionBoxes(player, box).isEmpty()) {
                    player.setPositionAndUpdate(x, y, z);
                    break;
                }
            }
        }
        stepOffs.clear();
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
        noteStepOff(passenger);
        super.removePassenger(passenger);
        standingOffsets.remove(passenger.getUniqueID());
        if (!this.world.isRemote && passenger instanceof EntityPlayer) {
            RECENT_DISMOUNTS.put(passenger.getUniqueID(), this.world.getTotalWorldTime());
            if (passenger.isEntityAlive() && !isDead) {
                leftThisCar.put(passenger.getUniqueID(), this.world.getTotalWorldTime());
            }
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
        Train train = trainOrNull();
        boolean moving = train != null && Math.abs(train.velocity()) > 1.0D;
        if (train == null || train.spec().carStyle() != TrainSpec.CarStyle.METRO) {
            holdCoasterRiders(moving);
            return;
        }
        boolean doorsOpen = com.micatechnologies.minecraft.rcmc.world.MetroDoors
            .areOpen(this.world, trainId());
        if (!doorsOpen && !moving) {
            return;
        }
        TrainSpec spec = train == null ? null : train.spec();
        long now = this.world.getTotalWorldTime();
        // Searched by the car's OWN footprint, not by its bounding box. The box is square in plan
        // (setSize cannot be anything else), so on a 20-block car it describes the middle fifth —
        // and a player boarding through a doorway is nowhere near the middle. The box is only used
        // to bound the search now; the footprint test below decides.
        for (EntityPlayer player : this.world.getEntitiesWithinAABB(EntityPlayer.class,
            getEntityBoundingBox().grow(searchReach(spec), 0.0D, searchReach(spec)))) {
            if (player.isRiding() || !canFitPassenger(player) || !isInsideBody(player, spec)) {
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

    /** Who stepped off this coaster car, and on which tick — the restraint below reads it. */
    private final java.util.Map<java.util.UUID, Long> leftThisCar = new java.util.HashMap<>();

    /**
     * Coaster restraints: a rider who lets go while the car is moving is put straight back.
     *
     * <p>Coaster cars are boarded by right-clicking, never by walking in. They used to share the
     * metro walk-in check, and for coaster stock that check accepted anyone near the car — so a
     * train spawned on a player, or one that ran through somebody standing on the track, seated
     * them, past the ride's open/closed gate. Only a rider who was in THIS car a moment ago is
     * re-seated now, and only while it moves; at rest they step off as normal.</p>
     */
    private void holdCoasterRiders(boolean moving) {
        if (leftThisCar.isEmpty()) {
            return;
        }
        long now = this.world.getTotalWorldTime();
        java.util.Iterator<java.util.Map.Entry<java.util.UUID, Long>> it =
            leftThisCar.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<java.util.UUID, Long> left = it.next();
            if (now - left.getValue() > 2) {
                it.remove();
                continue;
            }
            if (!moving) {
                continue;
            }
            EntityPlayer player = this.world.getPlayerEntityByUUID(left.getKey());
            // Near the car: a rider teleported away (/tp, a portal) is not dragged back.
            if (player != null && !player.isRiding() && player.isEntityAlive()
                && player.getDistanceSq(this) < 16.0D && canFitPassenger(player)) {
                it.remove();
                player.startRiding(this);
            }
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
     * How far beyond the entity box to look for players, so the search covers the whole car.
     *
     * <p>Half the body length plus a block. Deliberately generous: this only bounds the candidate
     * list, and {@link #isInsideBody} does the deciding.</p>
     */
    private static double searchReach(TrainSpec spec) {
        return spec == null ? 0.0D : CarSeating.bodyLength(spec) * 0.5D + 1.0D;
    }

    /**
     * Whether {@code entity} stands within this car's body, tested in the car's own axes.
     *
     * <p>Exact for a car at any angle, where an axis-aligned box is exact only for one pointing due
     * north or east. Boarding used the box, which is why walking in at a doorway of a long car did
     * nothing until the player wandered into the middle of it.</p>
     */
    private boolean isInsideBody(Entity entity, TrainSpec spec) {
        if (spec == null || frame == null || spec.carStyle() != TrainSpec.CarStyle.METRO) {
            // Only metro cars are walked into; coaster stock is boarded by right-clicking.
            return false;
        }
        double dx = entity.posX - frame.position.x;
        double dy = entity.posY - frame.position.y;
        double dz = entity.posZ - frame.position.z;
        double along = dx * frame.forward.x + dy * frame.forward.y + dz * frame.forward.z;
        double across = dx * frame.right.x + dy * frame.right.y + dz * frame.right.z;
        double up = dx * frame.up.x + dy * frame.up.y + dz * frame.up.z;
        return Math.abs(along) <= CarSeating.bodyLength(spec) * 0.5D
            && Math.abs(across) <= CarSeating.METRO_BODY_HALF_WIDTH
            // Feet at floor level, with room for the step up and the height of the saloon — so
            // somebody on the roof, or under the car, is not seated.
            && up >= CarSeating.METRO_FLOOR_HEIGHT - 1.0D
            && up <= CarSeating.METRO_ROOF_HEIGHT;
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
        offset[0] = Math.max(-halfLength, Math.min(halfLength, offset[0]));

        // The side wall is a clamp, not a collision — a standing rider's position is written
        // directly, so vanilla never gets a say and this bound IS the wall. Which means the only
        // way to walk OUT of an open door is to move the bound: at a doorway, with the doors
        // actually open, the wall is not there.
        boolean doorsOpen = com.micatechnologies.minecraft.rcmc.world.MetroDoors
            .areOpen(this.world, trainId());
        // Only the side that actually opened is a way out. The side is track-relative and so is
        // the sign of `across` (it is measured along frame.right), so they compare directly — no
        // conversion, which is why this reads as simply as it does.
        com.micatechnologies.minecraft.rcmc.physics.transit.DoorSide openSide =
            com.micatechnologies.minecraft.rcmc.world.MetroDoors.openSide(this.world, trainId());
        boolean towardOpenSide = offset[1] >= 0.0D ? openSide.opensRight() : openSide.opensLeft();
        boolean throughDoorway = doorsOpen && towardOpenSide
            && CarSeating.isAtDoorway(spec, offset[0]);
        double exitLimit = CarSeating.exitHalfWidth(spec);
        double halfWidth = throughDoorway ? exitLimit : CarSeating.walkableHalfWidth(spec);
        offset[1] = Math.max(-halfWidth, Math.min(halfWidth, offset[1]));

        if (throughDoorway && Math.abs(offset[1]) >= exitLimit - 1.0e-6D
            && !this.world.isRemote && player.getRidingEntity() == this) {
            // Walked clear of the body through an open door: they have got off. Queued rather than
            // done here because this runs from the passenger loop in onUpdate, and dismounting
            // mid-iteration mutates the list being walked.
            pendingDismounts.add(player.getUniqueID());
        }
        return offset;
    }

    /** Riders who have walked out through a door this tick; see {@link #walkStandingRider}. */
    private final java.util.List<java.util.UUID> pendingDismounts = new java.util.ArrayList<>();

    /**
     * Puts riders who walked out of a door onto the platform.
     *
     * <p>Placed just outside the doorway they used rather than left to vanilla's dismount search,
     * which looks for somewhere safe near the <em>vehicle</em> and would happily pick the far side
     * of the train, or the track. A platform laid at car-floor height is level with the doorway, so
     * stepping out lands on it exactly as stepping in came off it.</p>
     */
    private void applyPendingDismounts() {
        if (pendingDismounts.isEmpty() || this.world.isRemote) {
            return;
        }
        Train train = trainOrNull();
        TrainSpec spec = train == null ? null : train.spec();
        for (java.util.UUID id : pendingDismounts) {
            for (Entity passenger : new java.util.ArrayList<>(getPassengers())) {
                if (!id.equals(passenger.getUniqueID())) {
                    continue;
                }
                if (frame == null || spec == null) {
                    // Nothing to place them against. Letting go is still better than holding them,
                    // and vanilla's own dismount search takes it from here.
                    passenger.dismountRidingEntity();
                    continue;
                }
                double[] offset = standingOffsets.get(id);
                double along = offset == null ? 0.0D : offset[0];
                double side = offset == null || offset[1] >= 0.0D ? 1.0D : -1.0D;
                // A step further out than the exit threshold, so they land clear of the car rather
                // than in the doorway, where the boarding check would seat them straight back in.
                double across = side * (CarSeating.exitHalfWidth(spec) + 0.55D);
                double x = frame.position.x + frame.forward.x * along + frame.right.x * across;
                double y = frame.position.y + frame.up.y * CarSeating.METRO_FLOOR_HEIGHT;
                double z = frame.position.z + frame.forward.z * along + frame.right.z * across;
                // Dismount first: removePassenger clears their offset and starts the re-board
                // grace, and setPosition on a passenger is overwritten by the vehicle otherwise.
                passenger.dismountRidingEntity();
                passenger.setPositionAndUpdate(x, y, z);
            }
        }
        pendingDismounts.clear();
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
        // Doors open: nothing solid from the entity itself. The floor a passenger walks in on is
        // contributed by TrainFloorCollision instead, because it takes more than one box to
        // describe — this entity's box is square in plan, so the slab this method used to return
        // covered about a fifth of a 20-block car, in the middle, nowhere near a doorway. Walking
        // in off a platform put a player over the gap and dropped them through the floor.
        return null;
    }


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
        // A coaster boards only while its ride is open. Closed and testing rides run without
        // guests; an e-stopped one runs nobody. Say which, for the same reason as the doors above.
        if (state != null) {
            com.micatechnologies.minecraft.rcmc.physics.Train train = state.trains().train(trainId());
            com.micatechnologies.minecraft.rcmc.physics.ride.RideController ride = train == null
                ? null : state.rides().get(train.reference().sectionId());
            if (ride != null && !ride.ridersMayBoard()) {
                say(player, net.minecraft.util.text.TextFormatting.YELLOW, ride.isEmergencyStopped()
                    ? "This ride has been emergency-stopped."
                    : ride.state() == com.micatechnologies.minecraft.rcmc.physics.ride.RideController.State.TESTING
                        ? "This ride is being tested — no riders yet."
                        : "This ride is closed.");
                return false;
            }
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
        // Only a car saved before train persistence can arrive here — see the field javadoc.
        this.restoredFromDisk = true;
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setInteger("TrainId", trainId());
        compound.setInteger("CarIndex", carIndex());
    }

    /**
     * Refuses to be saved with the chunk.
     *
     * <p>The train in {@code RcmcTrackData} is the only thing that decides how many cars exist and
     * where they are; a car is a rendering of it. Persisting cars as well would create a second
     * source of truth that disagrees with the first whenever a chunk's loaded state at save time
     * differs from its state at load time — which is most of the time, for a coaster spanning
     * hundreds of blocks.</p>
     *
     * <p>Vanilla calls this from {@code Chunk.writeToNBT}'s entity loop and skips the entity
     * entirely when it returns false; that is the same mechanism a mounted entity uses to avoid
     * being written twice. {@link #writeEntityToNBT} is deliberately left intact rather than
     * emptied, so that anything which serialises a car for another reason still produces something
     * meaningful.</p>
     */
    @Override
    public boolean writeToNBTOptional(NBTTagCompound compound) {
        return false;
    }
}
