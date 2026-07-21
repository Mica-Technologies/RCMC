package com.micatechnologies.minecraft.rcmc.physics.transit;

import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.TICK;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.realistic;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.debug.DemoMetro;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Replays the exact in-game flow of the metro demo — the same geometry ({@link DemoMetro}), the
 * same spawn-idle-enter-service sequence the commands produce, and the same
 * {@code TrainManager.tick} + {@code TransitSystem.composedWith} path {@code RcmcWorldState}
 * runs — because the first live session found a train VALLEYED at the Eastvale terminus that no
 * isolated unit test had caught.
 */
class TransitServiceWorldFlowTest {

    @Test
    @DisplayName("a demo-metro service survives multiple full out-and-back cycles without valleying")
    void surviveOutAndBackCyclesThroughTheWorldTickPath() {
        DemoMetro.Result demo = DemoMetro.build(1, new Vec3(0.0D, 64.0D, 0.0D));
        TrackNetwork network = new TrackNetwork();
        network.addSection(demo.section);

        TransitSystem transit = new TransitSystem();
        List<TransitStation> stops = new ArrayList<>();
        for (int i = 0; i < demo.stationNames.length; i++) {
            TransitStation station = new TransitStation(demo.stationNames[i],
                new TrackRef(1, demo.stationDistances[i]));
            transit.addStation(station);
            stops.add(station);
        }
        transit.addLine(new TransitLine("Metro", stops, false));

        TrainManager trains = new TrainManager();
        Train train = new Train(TrainSpec.metroTrain(3), realistic(), new TrackRef(1, 0.0D), 0.0D);
        trains.add(1, train);

        // The operator pause between /rcmc train and /rcmc line start — the train valleys here.
        for (int i = 0; i < 200; i++) {
            trains.tick(network, null, 4, TICK);
        }

        // The command's controller defaults.
        TrainDriver driver = new TrainDriver(new TractionProfile(1.2D, 24.0D, 22.0D),
            1.2D, 2.0D, 1.5D, TICK);
        TransitStopController controller = new TransitStopController(driver, 15.0D, 0.75D, 30, 100, 30);
        transit.enterService(1, train, network, "Metro", controller);

        int lastServed = 0;
        for (int tick = 0; tick < 60000 && controller.stopsServed() < 7; tick++) {
            transit.beginTick(trains, network);
            trains.tick(network, transit.composedWith(null), 4, TICK);
            if (controller.stopsServed() != lastServed) {
                lastServed = controller.stopsServed();
            }
            assertTrue(train.isRunning(), "train valleyed at tick " + tick
                + " after serving " + controller.stopsServed() + " stops, at "
                + train.reference() + " v=" + train.velocity());
        }
        assertTrue(controller.stopsServed() >= 7,
            "expected at least seven stops served (two full out-and-backs), got "
                + controller.stopsServed());
    }
}
