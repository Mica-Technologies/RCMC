package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.entity.EntityCoasterCar;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/**
 * Keeps a train's car entities in step with the train itself.
 *
 * <p><b>The train is the truth; the cars are a rendering of it.</b> A car entity holds no state of
 * its own beyond which train and which index it is — every tick it asks the train where it should
 * be. So the number of cars in the world is a function of the trains in the save, and this class is
 * where that function is applied.</p>
 *
 * <p><b>Why cars are not saved with their chunks.</b> They used to be, by default, and it was the
 * wrong shape once trains began to persist. A car in a chunk that happened to be loaded at save
 * time would come back; a car in a chunk that did not would not, and would then reappear the moment
 * someone walked over there — duplicating a car this class had already spawned. Two entities, same
 * train, same index, same position, both boardable. Not saving them at all removes the second
 * source of truth rather than trying to reconcile it.</p>
 */
public final class TrainEntities {

    private TrainEntities() {
        throw new AssertionError("No instances.");
    }

    /**
     * Spawns car entities for every train that is missing them.
     *
     * <p>Counts what is already there rather than assuming: on a freshly loaded world nothing
     * exists yet, but this is also safe to call at any other time, which matters because the
     * alternative — trusting a flag — is how duplicates get created.</p>
     *
     * @return how many cars were spawned
     */
    public static int spawnMissingCars(World world, RcmcWorldState state) {
        if (world == null || world.isRemote || state == null) {
            return 0;
        }
        Set<Long> present = new HashSet<>();
        for (Entity entity : world.loadedEntityList) {
            if (entity instanceof EntityCoasterCar && !entity.isDead) {
                EntityCoasterCar car = (EntityCoasterCar) entity;
                present.add(key(car.trainId(), car.carIndex()));
            }
        }

        int spawned = 0;
        for (Map.Entry<Integer, Train> entry : state.trains().asMap().entrySet()) {
            int trainId = entry.getKey();
            Train train = entry.getValue();
            if (!state.network().hasSection(train.reference().sectionId())) {
                // The train points at track that no longer exists — the section was deleted while
                // the world was closed, or an undo removed it. Spawning cars would put them at a
                // position that cannot be computed; leave the train alone and unrendered rather
                // than throwing out of a world tick.
                continue;
            }
            for (int index = 0; index < train.spec().carCount(); index++) {
                if (present.contains(key(trainId, index))) {
                    continue;
                }
                EntityCoasterCar car = new EntityCoasterCar(world, trainId, index);
                TrackFrame frame = train.bodyFrameOfCar(state.network(), index);
                car.setPosition(frame.position.x, frame.position.y, frame.position.z);
                world.spawnEntity(car);
                spawned++;
            }
        }
        return spawned;
    }

    /** Packs a train id and car index into one key, so the "already there" test is a set hit. */
    private static long key(int trainId, int carIndex) {
        return ((long) trainId << 32) | (carIndex & 0xFFFFFFFFL);
    }
}
