package com.micatechnologies.minecraft.rcmc.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.debug.DemoShuttle;
import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.element.LaunchTrack;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The shuttle demo runs its whole program, over and over: out the front, back through the station,
 * out the back, and caught in the station going forward.
 */
class DemoShuttleRunTest {

    private static final double TICK = RcmcConstants.SECONDS_PER_TICK;

    /** Mirrors the shuttle branch of CommandRcmc.buildDemo — if that changes, this must too. */
    static RideElementSet hardware(DemoShuttle.Result demo) {
        RideElementSet elements = new RideElementSet();
        elements.add(new StationPlatform(1, demo.stationStart, demo.stationEnd, demo.stationStop,
            8.0D, 60, 4.0D, 6.0D, TICK, 1));
        elements.add(new LaunchTrack(1, demo.launchStart, demo.launchEnd, 22.0D, 8.0D));
        elements.add(new LaunchTrack(1, demo.backLaunchStart, demo.backLaunchEnd, -22.0D, 8.0D));
        return elements;
    }

    @Test
    @DisplayName("out the front, back through the station, out the back, caught — five times running")
    void runsTheProgram() {
        DemoShuttle.Result demo = DemoShuttle.build(1, new Vec3(0, 64, 0));
        TrackNetwork network = new TrackNetwork();
        network.addSection(demo.section);
        RideElementSet elements = hardware(demo);
        StationPlatform station = (StationPlatform) elements.elements().get(0);
        TrainManager trains = new TrainManager();
        trains.add(1, new Train(new TrainSpec(5, 3.0D, 0.5D, 4),
            new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D), new TrackRef(1, demo.stationStop), 0.0D));
        Train train = trains.train(1);

        int stops = 0;
        boolean frontSpike = false;
        boolean rearSpike = false;
        int fullCycles = 0;
        StationPlatform.Phase previous = station.phase();
        for (int t = 0; t < 20 * 400 && fullCycles < 5; t++) {
            trains.tick(network, elements, 4, TICK);
            assertTrue(train.isRunning(), "faulted " + train.status() + " at s="
                + train.reference().distance() + " after " + stops + " stops");
            double s = train.reference().distance();
            if (s > demo.launchEnd + 20.0D) {
                frontSpike = true;
            }
            if (s < demo.backLaunchStart - 10.0D) {
                rearSpike = true;
            }
            StationPlatform.Phase phase = station.phase();
            if (phase == StationPlatform.Phase.DWELLING && previous != StationPlatform.Phase.DWELLING) {
                stops++;
                if (frontSpike && rearSpike) {
                    fullCycles++;
                }
                frontSpike = false;
                rearSpike = false;
                assertTrue(s >= demo.stationStart && s <= demo.stationEnd, "caught on the platform: s=" + s);
            }
            previous = phase;
        }
        assertTrue(fullCycles >= 5, "complete rides: " + fullCycles + " (station stops " + stops + ")");
    }
}
