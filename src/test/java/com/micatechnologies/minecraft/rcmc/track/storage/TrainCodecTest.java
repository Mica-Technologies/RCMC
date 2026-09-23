package com.micatechnologies.minecraft.rcmc.track.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitDrives;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for train and service persistence.
 *
 * <p>Trains were the last thing in a park that did not survive a restart. A codec bug here does not
 * throw — it silently loses somebody's metro, or brings it back in the wrong place — which is the
 * class of bug that only a round-trip assertion catches.</p>
 */
class TrainCodecTest {

    private static final double TICK = 1.0D / 20.0D;

    private static PhysicsIntegrator integrator() {
        return new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D);
    }

    /** A long, flat run — enough track for three stations to be walkable between. */
    private static TrackNetwork network() {
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i <= 8; i++) {
            nodes.add(new TrackNode(new Vec3(i * 30.0D, 64.0D, 0.0D)));
        }
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, nodes, false, null));
        return network;
    }

    private static TransitSystem transitOn(TrackNetwork network) {
        TransitSystem transit = new TransitSystem();
        TransitStation north = new TransitStation("North", new TrackRef(1, 20.0D));
        TransitStation centre = new TransitStation("Centre", new TrackRef(1, 120.0D));
        TransitStation south = new TransitStation("South", new TrackRef(1, 220.0D));
        transit.addStation(north);
        transit.addStation(centre);
        transit.addStation(south);
        transit.addLine(new TransitLine("Metro", Arrays.asList(north, centre, south), false));
        return transit;
    }

    @Test
    @DisplayName("a train round-trips its position, speed and every part of its spec")
    void trainRoundTrips() {
        TrainManager trains = new TrainManager();
        TrainSpec spec = new TrainSpec(4, 15.65D, 0.6D, 10, 5, 2, 9,
            TrainSpec.CarStyle.METRO);
        trains.add(7, new Train(spec, integrator(), new TrackRef(3, 128.25D), 13.5D));

        NBTTagCompound tag = new NBTTagCompound();
        TrainCodec.write(trains, null, tag);
        TrainManager restored = TrainCodec.read(tag, integrator());

        Train train = restored.train(7);
        assertNotNull(train, "train 7 did not survive the round trip");
        assertEquals(3, train.reference().sectionId());
        // Double precision, not float: distance accumulates along a section thousands of samples
        // long, and a float would move the train on every single reload.
        assertEquals(128.25D, train.reference().distance(), 1e-9);
        assertEquals(13.5D, train.velocity(), 1e-9);

        TrainSpec out = train.spec();
        assertEquals(4, out.carCount());
        assertEquals(15.65D, out.carLength(), 1e-9);
        assertEquals(0.6D, out.couplingGap(), 1e-9);
        assertEquals(10, out.seatsPerCar());
        assertEquals(TrainSpec.CarStyle.METRO, out.carStyle());
        // Paint is authored state a player chose; losing it on restart is a visible regression.
        assertEquals(5, out.bodyColour());
        assertEquals(2, out.trimColour());
        assertEquals(9, out.seatColour());
    }

    @Test
    @DisplayName("a coaster train keeps the car it was built from")
    void coasterModelRoundTrips() {
        TrainManager trains = new TrainManager();
        trains.add(3, new Train(new TrainSpec(5, 3.0D, 0.5D, 4)
            .withCoasterModel(TrainSpec.CoasterModel.SHOULDER), integrator(), new TrackRef(1, 10.0D), 0.0D));
        NBTTagCompound tag = new NBTTagCompound();
        TrainCodec.write(trains, null, tag);
        assertEquals(TrainSpec.CoasterModel.SHOULDER,
            TrainCodec.read(tag, integrator()).train(3).spec().coasterModel());
    }

    @Test
    @DisplayName("train ids survive, so a reloaded world cannot hand out one already in use")
    void idsSurvive() {
        TrainManager trains = new TrainManager();
        trains.add(4, new Train(TrainSpec.singleCar(), integrator(), new TrackRef(1, 0.0D), 0.0D));
        trains.add(9, new Train(TrainSpec.singleCar(), integrator(), new TrackRef(1, 5.0D), 0.0D));

        NBTTagCompound tag = new NBTTagCompound();
        TrainCodec.write(trains, null, tag);
        TrainManager restored = TrainCodec.read(tag, integrator());

        assertEquals(2, restored.count());
        int next = restored.allocateTrainId();
        assertTrue(next > 9, "allocated " + next + ", which would collide with a restored train");
    }

    @Test
    @DisplayName("an empty world round-trips to an empty world rather than throwing")
    void emptyRoundTrips() {
        NBTTagCompound tag = new NBTTagCompound();
        TrainCodec.write(new TrainManager(), new TransitSystem(), tag);
        assertTrue(TrainCodec.read(tag, integrator()).isEmpty());
    }

    @Test
    @DisplayName("a save from before train persistence reads as no trains, not as an error")
    void olderSaveReadsEmpty() {
        // Every world that exists today is one of these. Reading it must be uneventful.
        assertTrue(TrainCodec.read(new NBTTagCompound(), integrator()).isEmpty());
        assertEquals(0, TrainCodec.readServices(new NBTTagCompound(), new TrainManager(),
            new TransitSystem(), network(), TICK));
    }

    @Test
    @DisplayName("a train in service resumes on the same line after a round trip")
    void serviceResumes() {
        TrackNetwork network = network();
        TransitSystem transit = transitOn(network);
        TrainManager trains = new TrainManager();
        Train train = new Train(TrainSpec.metroTrain(3), integrator(),
            new TrackRef(1, 40.0D), 0.0D);
        trains.add(1, train);
        transit.enterService(1, train, network, "Metro", TransitDrives.metro(15.0D, TICK));

        NBTTagCompound tag = new NBTTagCompound();
        TrainCodec.write(trains, transit, tag);

        // A fresh world: the same authored transit, the same trains, no services.
        TransitSystem reloadedTransit = transitOn(network);
        TrainManager reloadedTrains = TrainCodec.read(tag, integrator());
        assertNull(reloadedTransit.serviceFor(1), "nothing should be in service before the resume");

        int resumed = TrainCodec.readServices(tag, reloadedTrains, reloadedTransit, network, TICK);

        assertEquals(1, resumed);
        assertNotNull(reloadedTransit.serviceFor(1), "train 1 is not back in service");
        assertEquals("Metro", reloadedTransit.serviceFor(1).line().name());
        // The cruise speed is the one operator decision a service carries; a line quietly resuming
        // at a different speed than it was running at is exactly the kind of drift nobody notices.
        assertEquals(15.0D, reloadedTransit.serviceFor(1).controller().cruiseSpeed(), 1e-9);
    }

    @Test
    @DisplayName("a service whose line is gone is dropped, leaving the train parked")
    void serviceWithoutItsLineIsDropped() {
        // The line can be deleted while the world is closed, or by an undo. Restoring a service
        // that points at nothing must not abort the load.
        TrackNetwork network = network();
        TransitSystem transit = transitOn(network);
        TrainManager trains = new TrainManager();
        Train train = new Train(TrainSpec.metroTrain(3), integrator(),
            new TrackRef(1, 40.0D), 0.0D);
        trains.add(1, train);
        transit.enterService(1, train, network, "Metro", TransitDrives.metro(15.0D, TICK));

        NBTTagCompound tag = new NBTTagCompound();
        TrainCodec.write(trains, transit, tag);

        TransitSystem withoutTheLine = new TransitSystem();
        TrainManager reloadedTrains = TrainCodec.read(tag, integrator());
        int resumed = TrainCodec.readServices(tag, reloadedTrains, withoutTheLine, network, TICK);

        assertEquals(0, resumed, "a service with no line must not resume");
        assertNotNull(reloadedTrains.train(1), "the train itself must still be there, just parked");
    }

    @Test
    @DisplayName("a service for a train that no longer exists is never written")
    void serviceForAMissingTrainIsNotWritten() {
        // Dropping it at write time means the save never contains a state the loader must defend
        // against — cheaper than defending against it on every load forever.
        TrackNetwork network = network();
        TransitSystem transit = transitOn(network);
        TrainManager trains = new TrainManager();
        Train train = new Train(TrainSpec.metroTrain(3), integrator(),
            new TrackRef(1, 40.0D), 0.0D);
        trains.add(1, train);
        transit.enterService(1, train, network, "Metro", TransitDrives.metro(15.0D, TICK));
        trains.remove(1);

        NBTTagCompound tag = new NBTTagCompound();
        TrainCodec.write(trains, transit, tag);

        assertEquals(0, TrainCodec.readServices(tag, TrainCodec.read(tag, integrator()),
            transitOn(network), network, TICK));
    }
}
