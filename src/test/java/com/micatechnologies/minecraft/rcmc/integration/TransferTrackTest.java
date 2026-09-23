package com.micatechnologies.minecraft.rcmc.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.debug.DemoCoaster;
import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.element.BrakeRun;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.physics.element.StorageBerth;
import com.micatechnologies.minecraft.rcmc.physics.element.TransferTrack;
import com.micatechnologies.minecraft.rcmc.physics.ride.Transfers;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A transfer table takes a running coaster's train out to storage, keeps it there, and brings it
 * back to run again — the operator panel's Store and Retrieve, as the world tick carries them out.
 */
class TransferTrackTest {

    private static final double TICK = RcmcConstants.SECONDS_PER_TICK;

    private final DemoCoaster.Result demo = DemoCoaster.build(1, new Vec3(0, 64, 0), 1.0D, 34.0D);
    private final TrackNetwork network = new TrackNetwork();
    private final RideElementSet elements = new RideElementSet();
    private final TrainManager trains = new TrainManager();
    private StationPlatform station;
    private TransferTrack transfer;

    private void build() {
        network.addSection(demo.section);
        double from = demo.brakeEnd + 1.0D;
        double to = demo.section.totalLength() - 0.1D;
        // Storage: straight and level, six blocks off to the side of the table, and longer than it.
        TrackFrame first = demo.section.frameAtDistance(from);
        Vec3 start = first.position.add(first.right.scale(6.0D));
        Vec3 along = demo.section.positionAtDistance(to).subtract(first.position).normalize();
        List<TrackNode> nodes = new ArrayList<>();
        for (double s = -6.0D; s <= (to - from) + 12.0D; s += 6.0D) {
            nodes.add(new TrackNode(start.add(along.scale(s))));
        }
        network.addSection(new TrackSection(2, nodes, false, null));

        station = new StationPlatform(1, demo.stationStart, demo.stationEnd, demo.stationStop,
            6.0D, 60, 4.0D, 6.0D, TICK);
        elements.add(station);
        elements.add(new ChainLift(1, demo.liftStart, demo.liftEnd, 5.0D, 12.0D, TICK));
        elements.add(new BrakeRun(1, demo.brakeStart, demo.brakeEnd, 6.0D, 6.0D,
            BrakeRun.Mode.TRIM, TICK));
        TransferTrack unlinked = new TransferTrack(1, from, to, 2.0D, 3.0D, TICK,
            TransferTrack.UNLINKED, 0.0D);
        double offset = Transfers.nearestOn(network.section(2), demo.section.positionAtDistance(from));
        // What /rcmc transfer checks before it links: the storage reaches past the table's end.
        assertTrue(offset + (to - from) <= network.section(2).totalLength(), "storage long enough");
        transfer = unlinked.linkedTo(2, offset);
        elements.add(transfer);
        elements.add(new StorageBerth(2, offset, offset + (to - from), TICK));
        trains.add(1, new Train(new TrainSpec(5, 3.0D, 0.5D, 4),
            new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D), new TrackRef(1, demo.stationStop), 0.0D));
    }

    /** One tick as the world runs it, with a pending STORE ({@code storing}) or RETRIEVE. */
    private void tick(boolean storing, boolean retrieving) {
        transfer.setHolding(storing && Transfers.stored(transfer, trains.asMap()) == null);
        trains.tick(network, elements, 4, TICK);
        if (storing) {
            Integer ready = Transfers.readyToStore(transfer, trains.asMap());
            if (ready != null) {
                Transfers.store(trains.train(ready), transfer);
            }
        }
        if (retrieving) {
            Integer stored = Transfers.stored(transfer, trains.asMap());
            if (stored != null && Transfers.tableClear(transfer, trains.asMap(), 17.5D)) {
                Transfers.retrieve(trains.train(stored), transfer);
            }
        }
    }

    @Test
    @DisplayName("store a running train, keep it a minute, bring it back, and it runs to the station")
    void storeAndRetrieve() {
        build();
        Train train = trains.train(1);

        int t = 0;
        while (Transfers.stored(transfer, trains.asMap()) == null && t++ < 20 * 240) {
            tick(true, false);
            assertTrue(train.isRunning(), "faulted " + train.status() + " at s=" + train.reference().distance());
        }
        assertEquals(Integer.valueOf(1), Transfers.stored(transfer, trains.asMap()), "stored within a lap");
        assertEquals(2, train.reference().sectionId());

        double parked = train.reference().distance();
        for (int i = 0; i < 20 * 60; i++) {
            tick(false, false);
            assertTrue(train.isRunning(), "a stored train is held, not valleyed");
        }
        assertEquals(parked, train.reference().distance(), 0.05D, "and it stays put");

        tick(false, true);
        assertNull(Transfers.stored(transfer, trains.asMap()), "retrieved");
        assertEquals(1, train.reference().sectionId());

        boolean dwelt = false;
        StationPlatform.Phase previous = station.phase();
        for (int i = 0; i < 20 * 60 && !dwelt; i++) {
            tick(false, false);
            assertTrue(train.isRunning(), "faulted " + train.status() + " at s=" + train.reference().distance());
            dwelt = station.phase() == StationPlatform.Phase.DWELLING
                && previous != StationPlatform.Phase.DWELLING;
            previous = station.phase();
        }
        assertTrue(dwelt, "back on the circuit, it rolls on into the station");
    }

    @Test
    @DisplayName("a train is only slid across whole and at rest")
    void onlyWholeAndAtRest() {
        build();
        Train train = trains.train(1);
        // Lead on the table, tail still on the brakes.
        train.setState(new TrackRef(1, transfer.startDistance() + 5.0D), 0.0D);
        assertNull(Transfers.readyToStore(transfer, trains.asMap()));
        // Whole, but moving.
        train.setState(new TrackRef(1, transfer.endDistance() - 0.5D), 1.5D);
        assertNull(Transfers.readyToStore(transfer, trains.asMap()));
        train.setState(new TrackRef(1, transfer.endDistance() - 0.5D), 0.0D);
        assertNotNull(Transfers.readyToStore(transfer, trains.asMap()));
    }
}
