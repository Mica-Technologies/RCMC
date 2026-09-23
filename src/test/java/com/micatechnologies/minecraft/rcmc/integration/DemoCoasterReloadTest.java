package com.micatechnologies.minecraft.rcmc.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.debug.DemoCoaster;
import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.element.BrakeRun;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.track.storage.ElementCodec;
import com.micatechnologies.minecraft.rcmc.track.storage.TrainCodec;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The demo coaster keeps running across saves and reloads.
 *
 * <p>Seen in review: after a reload the demo train crept to about zero speed partway round and stayed
 * there, still reported RUNNING; another reload left a train VALLEYED in the station. The save and
 * load paths are exercised here exactly as a world load does them — trains through {@link TrainCodec},
 * ride hardware through {@link ElementCodec}, both into fresh objects — at many different moments of
 * the lap, because which moment the save lands on is what decides whether a reload breaks.</p>
 */
class DemoCoasterReloadTest {

    private static final double TICK = RcmcConstants.SECONDS_PER_TICK;

    private static PhysicsIntegrator integrator() {
        return new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D);
    }

    @Test
    @DisplayName("reloading at any moment of a lap leaves the train lapping and stopping at the station")
    void keepsRunningAcrossReloads() {
        DemoCoaster.Result demo = DemoCoaster.build(1, new Vec3(0, 64, 0), 1.0D, 34.0D);
        TrackNetwork network = new TrackNetwork();
        network.addSection(demo.section);

        RideElementSet elements = new RideElementSet();
        elements.add(new StationPlatform(1, demo.stationStart, demo.stationEnd, demo.stationStop,
            6.0D, 60, 4.0D, 6.0D, TICK));
        elements.add(new ChainLift(1, demo.liftStart, demo.liftEnd, 5.0D, 12.0D, TICK));
        elements.add(new BrakeRun(1, demo.brakeStart, demo.brakeEnd, 6.0D, 6.0D,
            BrakeRun.Mode.TRIM, TICK));
        TrainManager trains = new TrainManager();
        trains.add(1, new Train(new TrainSpec(5, 3.0D, 0.5D, 4), integrator(),
            new TrackRef(1, demo.stationStop), 0.0D));

        // Reload at uneven intervals so the saves land all over the lap: in the station, on the
        // lift, on the drop, in the hills, in the brakes.
        int[] runBetweenReloads = {137, 411, 290, 653, 180, 520, 377, 244, 700, 95, 460, 333};
        int dwells = 0;
        for (int round = 0; round < runBetweenReloads.length; round++) {
            StationPlatform station = stationIn(elements);
            StationPlatform.Phase previous = station.phase();
            for (int t = 0; t < runBetweenReloads[round] * 3; t++) {
                trains.tick(network, elements, 4, TICK);
                StationPlatform.Phase phase = station.phase();
                if (phase == StationPlatform.Phase.DWELLING && previous != StationPlatform.Phase.DWELLING) {
                    dwells++;
                }
                previous = phase;
                Train train = trains.train(1);
                assertTrue(train.isRunning(), "faulted " + train.status() + " at s="
                    + train.reference().distance() + " in round " + round);
            }

            // Save and load, the way a world does.
            NBTTagCompound saved = new NBTTagCompound();
            TrainCodec.write(trains, null, saved);
            NBTTagCompound savedElements = ElementCodec.write(elements);
            trains = TrainCodec.read(saved, integrator());
            elements = ElementCodec.read(savedElements);
        }

        // About 16 minutes of running in all: a lap takes under two, so it must have called at the
        // station many times — a train stuck somewhere would stop adding to this.
        assertTrue(dwells >= 6, "only " + dwells + " station stops across the reloads");
    }

    private static StationPlatform stationIn(RideElementSet elements) {
        for (RideElement element : elements.elements()) {
            if (element instanceof StationPlatform) {
                return (StationPlatform) element;
            }
        }
        throw new AssertionError("no station");
    }
}
