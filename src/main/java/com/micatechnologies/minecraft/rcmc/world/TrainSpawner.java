package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.RcmcConfig;
import com.micatechnologies.minecraft.rcmc.entity.EntityCoasterCar;
import com.micatechnologies.minecraft.rcmc.net.PacketTrainRemove;
import com.micatechnologies.minecraft.rcmc.net.PacketTrainSync;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import java.util.ArrayList;
import net.minecraft.world.World;

/**
 * Puts a train on the track, or takes one off, with everything that has to go with it: the car
 * entities, the save mark, and telling every client. Shared by {@code /rcmc train} and the ride
 * operator panel, so there is one way to do each and they cannot drift apart.
 */
public final class TrainSpawner {

    private TrainSpawner() {
    }

    /** Creates a train at {@code distance} on {@code sectionId}, returning its id. Server side. */
    public static int spawn(World world, RcmcWorldState state, int sectionId, TrainSpec spec,
                            double distance, double speed) {
        PhysicsIntegrator integrator = new PhysicsIntegrator(
            RcmcConfig.gravity, RcmcConfig.rollingResistance, RcmcConfig.airDrag, RcmcConfig.maxSpeed);
        Train train = new Train(spec, integrator, new TrackRef(sectionId, distance), speed);

        int trainId = state.trains().allocateTrainId();
        state.trains().add(trainId, train);
        // Persist the new train now rather than relying on the tick hook's mark: a train spawned
        // and then saved in the same tick would otherwise be absent from that save.
        state.markTrainsDirty(world);

        for (int i = 0; i < spec.carCount(); i++) {
            EntityCoasterCar car = new EntityCoasterCar(world, trainId, i);
            TrackFrame frame = train.frameOfCar(state.network(), i);
            car.setPosition(frame.position.x, frame.position.y, frame.position.z);
            world.spawnEntity(car);
        }

        // Push the new train immediately rather than waiting for the periodic correction — until
        // a client has the train, its car entities have nothing to derive a position from.
        RcmcNetwork.sendToAllIn(new PacketTrainSync(trainId, train), world.provider.getDimension());
        return trainId;
    }

    /**
     * Takes one train off the track: out of any line service, its cars gone, every client told.
     *
     * @return false if there is no such train
     */
    public static boolean remove(World world, RcmcWorldState state, int trainId) {
        if (state.trains().train(trainId) == null) {
            return false;
        }
        state.transit().exitService(trainId);
        state.trains().remove(trainId);
        for (EntityCoasterCar car : new ArrayList<>(
            world.getEntities(EntityCoasterCar.class, car -> car.trainId() == trainId))) {
            // Riders are put off first, so nobody is left sitting in mid-air in a car that is gone.
            car.removePassengers();
            car.setDead();
        }
        // The last train leaving means the tick hook stops marking the save; mark it here so the
        // removed train does not reappear on the next load.
        state.markTrainsDirty(world);
        RcmcNetwork.sendToAllIn(new PacketTrainRemove(trainId), world.provider.getDimension());
        return true;
    }
}
