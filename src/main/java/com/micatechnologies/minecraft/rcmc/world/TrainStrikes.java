package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.RcmcConfig;
import com.micatechnologies.minecraft.rcmc.entity.EntityCoasterCar;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.TrainStrike;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * Moving trains hit whoever is standing in their way: players, mobs and animals alike.
 *
 * <p>A car used to be solid only as its one square entity box, which a train at speed swept
 * through people without touching them, and on a long car covered only its middle. Now every car of
 * a moving train is tested against the living things around it in the car's own frame, and anyone it
 * touches is thrown clear and hurt by how fast it was going — see {@link TrainStrike} for the
 * numbers. {@link RcmcConfig#trainDamageMultiplier} scales the harm; 0 leaves only the shove.</p>
 *
 * <p>Server only: damage and knockback are server decisions, and the knockback reaches the struck
 * player's client through the usual velocity update.</p>
 */
public final class TrainStrikes {

    /** Below this speed, blocks/s, a train is standing: its body is solid instead (see {@link TrainFloors}). */
    static final double STANDING_SPEED = 0.25D;

    /** Ticks before the same train can hit the same one again — one blow per pass, not one per car. */
    private static final int COOLDOWN_TICKS = 10;

    /** Hurt by a train. The death message is {@code death.attack.rcmc.train}. */
    public static final DamageSource TRAIN = new DamageSource("rcmc.train");

    private TrainStrikes() {
    }

    /** Who was last hit by which train, and when: train id and entity, to world time. */
    private static final Map<String, Long> LAST_HIT = new HashMap<>();

    public static void tick(World world, RcmcWorldState state) {
        long now = world.getTotalWorldTime();
        if (now % 200 == 0) {
            Iterator<Map.Entry<String, Long>> it = LAST_HIT.entrySet().iterator();
            while (it.hasNext()) {
                if (now - it.next().getValue() > COOLDOWN_TICKS) {
                    it.remove();
                }
            }
        }
        for (Map.Entry<Integer, Train> entry : state.trains().asMap().entrySet()) {
            Train train = entry.getValue();
            double velocity = train.velocity();
            if (Math.abs(velocity) < STANDING_SPEED || train.reference() == null
                || !state.network().hasSection(train.reference().sectionId())) {
                continue;
            }
            TrainSpec spec = train.spec();
            TrainStrike.Body body = TrainStrike.bodyOf(spec);
            for (int car = 0; car < spec.carCount(); car++) {
                TrackFrame frame = train.bodyFrameOfCar(state.network(), car);
                strikeAround(world, entry.getKey(), frame, body, velocity, now);
            }
        }
    }

    private static void strikeAround(World world, int trainId, TrackFrame frame, TrainStrike.Body body,
                                     double velocity, long now) {
        double reach = Math.max(body.halfLength, body.halfWidth) + 1.0D;
        AxisAlignedBB search = new AxisAlignedBB(frame.position.x - reach, frame.position.y - reach,
            frame.position.z - reach, frame.position.x + reach, frame.position.y + body.top + reach,
            frame.position.z + reach);
        for (EntityLivingBase entity : world.getEntitiesWithinAABB(EntityLivingBase.class, search)) {
            if (!entity.isEntityAlive() || ridesTrain(entity, trainId)
                || (entity instanceof EntityPlayer && ((EntityPlayer) entity).isSpectator())) {
                continue;
            }
            double[] contact = TrainStrike.contact(frame, body,
                new Vec3(entity.posX, entity.posY, entity.posZ), entity.width * 0.5D, entity.height);
            if (contact == null) {
                continue;
            }
            String key = trainId + ":" + entity.getUniqueID();
            Long last = LAST_HIT.get(key);
            if (last != null && now - last < COOLDOWN_TICKS) {
                continue;
            }
            LAST_HIT.put(key, now);

            Vec3 shove = TrainStrike.shove(frame, velocity, contact[1]);
            entity.motionX = shove.x;
            entity.motionY = shove.y;
            entity.motionZ = shove.z;
            entity.velocityChanged = true;
            float damage = (float) TrainStrike.damage(Math.abs(velocity), RcmcConfig.trainDamageMultiplier);
            if (damage > 0.0F) {
                entity.attackEntityFrom(TRAIN, damage);
            }
        }
    }

    /** Whether {@code entity} is riding a car of train {@code trainId} — its own riders are never hit. */
    private static boolean ridesTrain(Entity entity, int trainId) {
        Entity vehicle = entity.getLowestRidingEntity();
        return vehicle instanceof EntityCoasterCar && ((EntityCoasterCar) vehicle).trainId() == trainId;
    }
}
