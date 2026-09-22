package com.micatechnologies.minecraft.rcmc.track.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.transit.DoorSide;
import com.micatechnologies.minecraft.rcmc.physics.transit.LineService;
import com.micatechnologies.minecraft.rcmc.physics.transit.TrainDriver;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitDrives;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitPlatform;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitStopController;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Saving a world while a metro stands at a platform must not change where it goes next.
 *
 * <p>Found in play: after a reload, a Circle Line train berthed at a station ran backwards down the
 * track it had arrived on, and an Airport Line train berthed at its terminus ran backwards off the
 * end of the line. Three reloads, three trains, all the wrong way. A service is re-derived on load
 * rather than restored, and a train at rest on a stop point measures the station as zero blocks away
 * <em>in either direction</em> — so which way it set off was decided by a rounding tie.</p>
 *
 * <p>So the property tested is the rider's one: take a running service, reload at a berth, and the
 * next station it serves must be the one the uninterrupted service would have served.</p>
 */
class ServiceResumeAtBerthTest {

    private static final double TICK = 1.0D / 20.0D;
    private static final int RUN_TICKS = 20000;

    private static PhysicsIntegrator integrator() {
        return new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D);
    }

    /** A closed ring: outbound on its first half, inbound on its second, a turning loop between. */
    private static TrackNetwork ring() {
        List<TrackNode> nodes = new ArrayList<>();
        double side = 160.0D;
        for (double[] p : new double[][] {{0, 0}, {side, 0}, {side, side}, {0, side}}) {
            nodes.add(new TrackNode(new Vec3(p[0], 64.0D, p[1])));
        }
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, nodes, true, null));
        return network;
    }

    private static TransitSystem turnbackLineOn(TrackNetwork network) {
        double length = network.section(1).totalLength();
        TransitSystem transit = new TransitSystem();
        TransitStation start = new TransitStation("Kingsway", Arrays.asList(
            new TransitPlatform(new TrackRef(1, length * 0.95D), DoorSide.RIGHT, "1")));
        TransitStation middle = new TransitStation("Guildhall", Arrays.asList(
            new TransitPlatform(new TrackRef(1, length * 0.25D), DoorSide.RIGHT, "1"),
            new TransitPlatform(new TrackRef(1, length * 0.75D), DoorSide.LEFT, "2")));
        TransitStation end = new TransitStation("Lakeshore", Arrays.asList(
            new TransitPlatform(new TrackRef(1, length * 0.45D), DoorSide.RIGHT, "1")));
        transit.addStation(start);
        transit.addStation(middle);
        transit.addStation(end);
        transit.addLine(new TransitLine("Circle", Arrays.asList(start, middle, end),
            false, true, "INBOUND", "OUTBOUND"));
        return transit;
    }

    /** A dead-straight open track: a shuttle with a buffer stop past each terminus. */
    private static TrackNetwork straight() {
        List<TrackNode> nodes = new ArrayList<>();
        for (int x = 0; x <= 240; x += 60) {
            nodes.add(new TrackNode(new Vec3(x, 64.0D, 0.0D)));
        }
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, nodes, false, null));
        return network;
    }

    private static TransitSystem shuttleLineOn(TrackNetwork network) {
        TransitSystem transit = new TransitSystem();
        TransitStation airfield = new TransitStation("Airfield", new TrackRef(1, 40.0D));
        TransitStation docklands = new TransitStation("Docklands", new TrackRef(1, 120.0D));
        TransitStation parkway = new TransitStation("Parkway", new TrackRef(1, 200.0D));
        transit.addStation(airfield);
        transit.addStation(docklands);
        transit.addStation(parkway);
        transit.addLine(new TransitLine("Airport", Arrays.asList(airfield, docklands, parkway),
            false));
        return transit;
    }

    /** One tick of a train under its service, exactly as the world tick drives it. */
    private static void step(LineService service, Train train, TrackNetwork network) {
        double acceleration = service.tick(train, network, TrainDriver.NO_STOP);
        train.setHeld(service.isHolding(train));
        train.tick(network, acceleration, 4, TICK);
    }

    private static String berthOf(LineService service) {
        return service.line().station(service.currentStopIndex()).name()
            + (service.currentBerth() == null ? "" : "/" + service.currentBerth().label());
    }

    /**
     * Runs until the service starts its {@code occurrence}-th dwell, and returns the berth it is
     * dwelling at. Leaves the train standing there, doors open.
     */
    private static String runToDwell(LineService service, Train train, TrackNetwork network,
                                     int occurrence) {
        int seen = 0;
        TransitStopController.Phase previous = service.controller().phase();
        for (int t = 0; t < RUN_TICKS; t++) {
            step(service, train, network);
            TransitStopController.Phase phase = service.controller().phase();
            if (phase == TransitStopController.Phase.BOARDING
                && previous != TransitStopController.Phase.BOARDING) {
                seen++;
                if (seen == occurrence) {
                    return berthOf(service);
                }
            }
            previous = phase;
        }
        throw new AssertionError("the service never reached dwell #" + occurrence);
    }

    /** The berth of the next dwell after the current one, or a description of how the run failed. */
    private static String nextDwell(LineService service, Train train, TrackNetwork network) {
        TransitStopController.Phase previous = service.controller().phase();
        for (int t = 0; t < RUN_TICKS; t++) {
            step(service, train, network);
            if (train.status() == Train.Status.DEAD_END) {
                return "DEAD_END at s=" + train.reference().distance();
            }
            TransitStopController.Phase phase = service.controller().phase();
            if (phase == TransitStopController.Phase.BOARDING
                && previous != TransitStopController.Phase.BOARDING) {
                String berth = berthOf(service);
                // A resumed train re-serves the stop it was standing at (it has "just arrived"
                // again), which is fine; the question is where it goes after that.
                if (!berth.equals(standingAt)) {
                    return berth;
                }
            }
            previous = phase;
        }
        return "no further dwell in " + RUN_TICKS + " ticks";
    }

    /** The berth the train stood at when the world was saved, for {@link #nextDwell}. */
    private static String standingAt;

    /**
     * For each of the first {@code dwells} dwells: reload there, and compare the next station served
     * against the service that was never interrupted.
     */
    private static void assertReloadKeepsTheRoute(Function<TrackNetwork, TransitSystem> line,
                                                  TrackNetwork network, String lineName,
                                                  double startDistance, int dwells) {
        for (int occurrence = 1; occurrence <= dwells; occurrence++) {
            // The uninterrupted reference run.
            TransitSystem transit = line.apply(network);
            Train train = new Train(TrainSpec.metroTrain(3), integrator(),
                new TrackRef(1, startDistance), 0.0D);
            TrainManager trains = new TrainManager();
            trains.add(1, train);
            LineService service = transit.enterService(1, train, network, lineName,
                TransitDrives.metro(15.0D, TICK));
            standingAt = runToDwell(service, train, network, occurrence);

            NBTTagCompound saved = new NBTTagCompound();
            TrainCodec.write(trains, transit, saved);
            String expected = nextDwell(service, train, network);

            // The same moment, saved and loaded into a fresh world.
            TransitSystem reloadedTransit = line.apply(network);
            TrainManager reloadedTrains = TrainCodec.read(saved, integrator());
            assertEquals(1, TrainCodec.readServices(saved, reloadedTrains, reloadedTransit,
                network, TICK), "the service did not resume");
            Train reloaded = reloadedTrains.train(1);
            String actual = nextDwell(reloadedTransit.serviceFor(1), reloaded, network);

            assertNotEquals(expected.startsWith("DEAD_END"), true,
                "the uninterrupted service itself failed: " + expected);
            assertEquals(expected, actual, lineName + ": reloaded while dwelling at " + standingAt
                + " (dwell #" + occurrence + "), the train must go on to the same station");
        }
    }

    @Test
    @DisplayName("a turnback-loop train reloaded at any berth goes on to the same station")
    void turnbackLineKeepsItsRouteAcrossAReload() {
        TrackNetwork network = ring();
        double length = network.section(1).totalLength();
        // Started just short of Guildhall 1, so the reference run sets off forward. Six dwells cover
        // both tracks and both turning loops.
        assertReloadKeepsTheRoute(ServiceResumeAtBerthTest::turnbackLineOn, network, "Circle",
            length * 0.20D, 6);
    }

    @Test
    @DisplayName("a shuttle train reloaded at a terminus turns back instead of running off the end")
    void shuttleKeepsItsRouteAcrossAReload() {
        TrackNetwork network = straight();
        // Five dwells: out to one terminus, back through the middle, and into the other.
        assertReloadKeepsTheRoute(ServiceResumeAtBerthTest::shuttleLineOn, network, "Airport",
            90.0D, 5);
    }

    /**
     * The narrow case behind the Airport Line running off its end: a train still rolling into a
     * stub terminus, half a block short of the stop point, facing the buffers. Nothing on the line
     * is ahead of it except that terminus, so the next stop has to be worked out from what is behind
     * it — the station it just left. Treating "nothing ahead" as "carry on in +1" sent it on past the
     * platform into the buffer stop.
     */
    @Test
    @DisplayName("a train entering service just short of a stub terminus turns back there")
    void entersServiceShortOfATerminus() {
        TrackNetwork network = straight();
        TransitSystem transit = shuttleLineOn(network);
        // Airfield's stop point is at 40; the buffer stop is at 0. Facing -1 is toward the buffers.
        Train train = new Train(TrainSpec.metroTrain(3), integrator(),
            new TrackRef(1, 40.5D), 0.0D);
        LineService service = transit.enterService(1, train, network, "Airport",
            TransitDrives.metro(15.0D, TICK), -1.0D);
        standingAt = "none";

        assertEquals("Airfield", service.line().station(service.currentStopIndex()).name(),
            "the terminus in front of it comes first");
        String next = nextDwell(service, train, network);
        assertEquals("Airfield/", next, "it berths at the terminus");
        standingAt = "Airfield/";
        assertEquals("Docklands/", nextDwell(service, train, network),
            "and then turns back to the station it came from, not on into the buffer stop");
    }

    @Test
    @DisplayName("a resumed turnback-loop train never runs backwards")
    void resumedTurnbackTrainNeverReverses() {
        TrackNetwork network = ring();
        double length = network.section(1).totalLength();
        TransitSystem transit = turnbackLineOn(network);
        Train train = new Train(TrainSpec.metroTrain(3), integrator(),
            new TrackRef(1, length * 0.20D), 0.0D);
        TrainManager trains = new TrainManager();
        trains.add(1, train);
        LineService service = transit.enterService(1, train, network, "Circle",
            TransitDrives.metro(15.0D, TICK));
        // Stop at the inbound berth — the case seen in play, a train standing at Guildhall 2.
        String berth = "";
        for (int occurrence = 1; !berth.equals("Guildhall/2"); occurrence++) {
            berth = runToDwell(service, train, network, 1);
            assertTrue(occurrence < 10, "never reached Guildhall/2");
        }

        NBTTagCompound saved = new NBTTagCompound();
        TrainCodec.write(trains, transit, saved);
        TransitSystem reloadedTransit = turnbackLineOn(network);
        TrainManager reloadedTrains = TrainCodec.read(saved, integrator());
        TrainCodec.readServices(saved, reloadedTrains, reloadedTransit, network, TICK);
        Train reloaded = reloadedTrains.train(1);
        LineService resumed = reloadedTransit.serviceFor(1);

        double lowest = Double.MAX_VALUE;
        for (int t = 0; t < RUN_TICKS; t++) {
            step(resumed, reloaded, network);
            lowest = Math.min(lowest, reloaded.velocity());
        }
        assertTrue(lowest >= -0.001D,
            "a turnback line never reverses a train; after the reload the lowest velocity was "
                + lowest);
    }
}
