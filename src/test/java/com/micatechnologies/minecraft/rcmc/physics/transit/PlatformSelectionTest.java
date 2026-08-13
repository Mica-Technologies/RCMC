package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A train berths at the platform on <em>its own</em> track, and opens the doors on that platform's
 * side.
 *
 * <p>This is the property multi-platform stations exist for. An island platform has a running line
 * down each side, so the same named station is reached from two tracks and the doors face opposite
 * ways depending on which one you are on. Answering from the station — which is all a single stop
 * point could do — is right for one track and opens the doors at the tunnel wall on the other.</p>
 *
 * <p>The double-track loop is modelled the way the demo builds one: a single circuit section whose
 * two passes are the two tracks, so the station's berths sit at very different distances along the
 * same section.</p>
 */
class PlatformSelectionTest {

    private static final double TICK = 1.0D / 20.0D;

    private static TrackNetwork ring() {
        List<TrackNode> nodes = new ArrayList<>();
        double side = 195.0D;
        for (double[] p : new double[][] {{0, 0}, {side, 0}, {side, side}, {0, side}}) {
            nodes.add(new TrackNode(new Vec3(p[0], 64.0D, p[1])));
        }
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, nodes, true, null));
        return network;
    }

    /**
     * "Central" is an island: inbound berth a quarter of the way round, outbound three quarters,
     * opening on opposite hands. "Fairview" is an ordinary single-track stop between them.
     */
    private static TransitSystem lineOn(TrackNetwork network) {
        double length = network.section(1).totalLength();
        TransitSystem transit = new TransitSystem();
        TransitStation central = new TransitStation("Central", Arrays.asList(
            new TransitPlatform(new TrackRef(1, length * 0.25D), DoorSide.RIGHT, "Inbound"),
            new TransitPlatform(new TrackRef(1, length * 0.75D), DoorSide.LEFT, "Outbound")));
        TransitStation fairview =
            new TransitStation("Fairview", new TrackRef(1, length * 0.5D), DoorSide.RIGHT);
        transit.addStation(central);
        transit.addStation(fairview);
        transit.addLine(new TransitLine("Loop", Arrays.asList(central, fairview), true));
        return transit;
    }

    /**
     * A train already running forward at line speed — a service in traffic, not one being spawned.
     * The velocity matters to the setup: a train at rest may be sent either way, so
     * {@code enterService} would weigh berths behind it too and could pick a nearer station in the
     * other direction. A running train can only reach what is ahead of it, which is what makes
     * "the berth on this train's track" a question with one answer.
     */
    private static Train trainAt(double distance) {
        return new Train(TrainSpec.metroTrain(3),
            new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, distance), 15.0D);
    }

    /** Enters service and runs one tick, which is what resolves the berth. */
    private static LineService serviceFrom(TransitSystem transit, TrackNetwork network, Train train) {
        LineService service =
            transit.enterService(1, train, network, "Loop", TransitDrives.metro(15.0D, TICK));
        service.tick(train, network, TrainDriver.NO_STOP);
        return service;
    }

    @Test
    @DisplayName("a train approaching the inbound side berths there and opens on its hand")
    void picksTheInboundBerth() {
        TrackNetwork network = ring();
        TransitSystem transit = lineOn(network);
        double length = network.section(1).totalLength();

        Train train = trainAt(length * 0.10D);
        LineService service = serviceFrom(transit, network, train);

        assertNotNull(service.currentBerth(), "the first tick resolves a berth");
        assertEquals("Inbound", service.currentBerth().label(),
            "the berth ahead on this track, not the one across the island");
        assertEquals(DoorSide.RIGHT, transit.doorSideFor(service));
    }

    @Test
    @DisplayName("a train approaching the outbound side berths there and opens on the other hand")
    void picksTheOutboundBerth() {
        TrackNetwork network = ring();
        TransitSystem transit = lineOn(network);
        double length = network.section(1).totalLength();

        Train train = trainAt(length * 0.60D);
        LineService service = serviceFrom(transit, network, train);

        assertEquals("Outbound", service.currentBerth().label());
        assertEquals(DoorSide.LEFT, transit.doorSideFor(service),
            "the same station, the other track, and therefore the other side — this is the whole "
                + "reason a door side cannot live on the station");
    }

    /**
     * The berth must not be re-derived once the train is standing on it. A berthed train has passed
     * its stop point, so measuring forward again finds the berth across the island — which would
     * flip the door side while the doors are open.
     */
    @Test
    @DisplayName("the berth does not flip once the train has arrived")
    void berthIsStableAcrossArrival() {
        TrackNetwork network = ring();
        TransitSystem transit = lineOn(network);
        double length = network.section(1).totalLength();

        Train train = trainAt(length * 0.10D);
        LineService service = serviceFrom(transit, network, train);
        String chosen = service.currentBerth().label();

        for (int t = 0; t < 1200; t++) {
            double acceleration = service.tick(train, network, TrainDriver.NO_STOP);
            train.setHeld(service.isHolding(train));
            train.tick(network, acceleration, 4, TICK);
            if (service.controller().phase() == TransitStopController.Phase.BOARDING) {
                assertEquals(chosen, service.currentBerth().label(),
                    "the doors are open — the berth cannot change now");
                assertEquals(DoorSide.RIGHT, transit.doorSideFor(service));
                return;
            }
        }
        throw new AssertionError("the train never berthed");
    }

    /**
     * A line snapshots its stations by value, so its copy of a station is frozen at the moment the
     * line was created. A berth authored afterwards — which is exactly what
     * {@code /rcmc station platform add} does — exists only in the registry, and a service reading
     * its own line's copy would never see the second platform of an island: it would keep berthing
     * on the first track forever, from either direction.
     */
    @Test
    @DisplayName("a berth added after the line was created is still served")
    void berthAddedAfterTheLineIsSeen() {
        TrackNetwork network = ring();
        double length = network.section(1).totalLength();

        // The line is built while Central has one berth, on the inbound side only.
        TransitSystem transit = new TransitSystem();
        TransitStation central =
            new TransitStation("Central", new TrackRef(1, length * 0.25D), DoorSide.RIGHT);
        TransitStation fairview =
            new TransitStation("Fairview", new TrackRef(1, length * 0.5D), DoorSide.RIGHT);
        transit.addStation(central);
        transit.addStation(fairview);
        transit.addLine(new TransitLine("Loop", Arrays.asList(central, fairview), true));

        // Then the outbound berth is authored, into the registry only.
        transit.addStation(central.withPlatform(
            new TransitPlatform(new TrackRef(1, length * 0.75D), DoorSide.LEFT, "Outbound")));

        Train train = trainAt(length * 0.60D);
        LineService service = serviceFrom(transit, network, train);

        assertEquals("Outbound", service.currentBerth().label(),
            "the service has to read the registry, not the line's frozen copy");
        assertEquals(DoorSide.LEFT, transit.doorSideFor(service));
    }

    /**
     * An arrival board never sees a {@code LineService} — it renders from the synced snapshots. So
     * the berth has to survive the trip onto the wire, and it has to be the berth the train is
     * pulling into: the primary here is "Inbound", and a board that reported it would put a
     * platform number in front of riders that sends them to the far side of the island.
     */
    @Test
    @DisplayName("the synced snapshot names the berth the train is pulling into")
    void snapshotCarriesTheServedBerth() {
        TrackNetwork network = ring();
        TransitSystem transit = lineOn(network);
        double length = network.section(1).totalLength();

        Train train = trainAt(length * 0.60D);
        serviceFrom(transit, network, train);

        List<ServiceSnapshot> snapshots = transit.serviceSnapshots();
        assertEquals(1, snapshots.size(), "one train in service, one snapshot");
        assertEquals("Outbound", snapshots.get(0).platformLabel(),
            "the berth on this train's own track, not the station's first");
        assertEquals(DoorSide.LEFT, snapshots.get(0).doorSide(),
            "and the side that berth opens on, from the same lookup");

        // Renaming the berth to a platform number is an ordinary thing to do to a live station,
        // and the line's frozen copy will never hear about it. Boards must read the registry.
        TransitStation central = transit.station("Central");
        transit.addStation(central.withPlatformAt(1, central.platform(1).withLabel("2")));

        assertEquals("2", transit.serviceSnapshots().get(0).platformLabel(),
            "the label comes from the registry, not from the line's copy of the station");
    }

    @Test
    @DisplayName("a single-platform station still answers exactly as it always did")
    void singlePlatformIsUnchanged() {
        TrackNetwork network = ring();
        TransitSystem transit = lineOn(network);
        double length = network.section(1).totalLength();

        // Just short of Fairview, which has one berth, so there is nothing to choose between.
        Train train = trainAt(length * 0.45D);
        LineService service = serviceFrom(transit, network, train);

        assertEquals("Fairview", service.line().station(service.currentStopIndex()).name());
        assertEquals(DoorSide.RIGHT, transit.doorSideFor(service));
    }
}
