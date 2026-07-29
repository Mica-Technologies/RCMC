package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.entity.EntityCoasterCar;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
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
 *
 * <p><b>The simulation does not depend on any of this.</b> A train's physics is one number advanced
 * against saved geometry; it never reads the world, so a train in unloaded chunks keeps running
 * exactly as it would in loaded ones. Cars are what the world can see of that, and they exist only
 * where there is a world to see them in.</p>
 */
public final class TrainEntities {

    /**
     * Margin around a train's own extent when looking for its cars, in blocks.
     *
     * <p>Generous, because the box is built from bogie-centre frames while a car body overhangs
     * them, and because a car is only ever a tick behind its train. The cost of being too generous
     * is examining a few more nearby entities; the cost of being too tight is spawning a duplicate
     * of a car that was there all along.</p>
     */
    private static final double SEARCH_MARGIN = 8.0D;

    private TrainEntities() {
        throw new AssertionError("No instances.");
    }

    /**
     * Spawns car entities for every train that is missing them.
     *
     * <p><b>This must be called repeatedly, not once.</b> Two independent reasons, either of which
     * alone makes a one-shot spawn wrong:</p>
     *
     * <ol>
     *   <li>{@code World.spawnEntity} <b>silently returns false</b> if the target chunk is not
     *       loaded ({@code World:1303}, read in the decompiled sources). On the first tick after a
     *       world loads, the chunks around a joining player have not arrived yet — so a single
     *       attempt at load time spawns nothing at all, reports no error, and leaves the world
     *       looking as though train persistence did not work. It is exactly that bug this comment
     *       exists to prevent a second time.</li>
     *   <li>Cars are not saved with their chunks, so when a chunk unloads its cars are gone for
     *       good. Recreating them is the only way they come back.</li>
     * </ol>
     *
     * <p><b>Cost.</b> Deliberately proportional to the number of <em>trains</em>, not to the number
     * of entities in the world. A train whose lead car sits in an unloaded chunk costs one
     * {@code isBlockLoaded} check and nothing else — no entity scan, no spawn attempt, because no
     * car could exist there anyway. A loaded train costs one chunk-local box query. Scanning
     * {@code loadedEntityList} instead would have made a quiet background check scale with a busy
     * server's entity count, which is the wrong thing for this to depend on.</p>
     *
     * @return how many cars were spawned
     */
    public static int spawnMissingCars(World world, RcmcWorldState state) {
        if (world == null || world.isRemote || state == null) {
            return 0;
        }
        int spawned = 0;
        for (Map.Entry<Integer, Train> entry : state.trains().asMap().entrySet()) {
            int trainId = entry.getKey();
            Train train = entry.getValue();
            if (!state.network().hasSection(train.reference().sectionId())) {
                // The train points at track this world does not have — the section was deleted
                // while the world was closed, or an undo removed it. There is no position to
                // compute, so leave the train alone rather than throwing out of a world tick.
                continue;
            }

            int carCount = train.spec().carCount();
            TrackFrame lead = train.bodyFrameOfCar(state.network(), 0);
            if (!world.isBlockLoaded(new BlockPos(lead.position.x, lead.position.y, lead.position.z))) {
                // Nothing can be spawned here and nothing can be found here. The train keeps
                // running regardless; it simply has no cars until somebody is near enough to
                // need them.
                continue;
            }

            TrackFrame tail = train.bodyFrameOfCar(state.network(), carCount - 1);
            AxisAlignedBB box = new AxisAlignedBB(
                Math.min(lead.position.x, tail.position.x), Math.min(lead.position.y, tail.position.y),
                Math.min(lead.position.z, tail.position.z), Math.max(lead.position.x, tail.position.x),
                Math.max(lead.position.y, tail.position.y), Math.max(lead.position.z, tail.position.z))
                .grow(SEARCH_MARGIN);

            Set<Integer> present = new HashSet<>();
            List<EntityCoasterCar> nearby = world.getEntitiesWithinAABB(EntityCoasterCar.class, box);
            for (EntityCoasterCar car : nearby) {
                if (!car.isDead && car.trainId() == trainId) {
                    present.add(car.carIndex());
                }
            }
            if (present.size() >= carCount) {
                continue;
            }

            for (int index = 0; index < carCount; index++) {
                if (present.contains(index)) {
                    continue;
                }
                EntityCoasterCar car = new EntityCoasterCar(world, trainId, index);
                TrackFrame frame = train.bodyFrameOfCar(state.network(), index);
                car.setPosition(frame.position.x, frame.position.y, frame.position.z);
                // The return value is the whole point: a refused spawn (this car's own chunk not
                // loaded, even though the lead car's is) must not be counted as done, or a partly
                // visible train would never complete itself. forceSpawn would bypass the check at
                // the cost of putting a car in a chunk nobody can see; waiting is more honest.
                if (world.spawnEntity(car)) {
                    spawned++;
                }
            }
        }
        return spawned;
    }
}
