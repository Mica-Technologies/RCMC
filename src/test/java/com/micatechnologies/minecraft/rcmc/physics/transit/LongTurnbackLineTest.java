package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * A train on a city-scale turnback line keeps stopping at its stations (#28).
 *
 * <p>In play, one Blue Line train ran a whole 50-minute lap without stopping. Its next station was
 * more than the route walk's old 10,000-block horizon away, so it could not see either platform,
 * fell back to the station's first one — the platform on the other track — and kept that for the
 * whole leg. It drove past the platform it should have used, and past every station before it.</p>
 *
 * <p>The fixture is the turnback ring of {@link TurnbackLoopTest} at the real line's scale: the
 * first half of the ring is outbound, the second half inbound.</p>
 */
class LongTurnbackLineTest {

    private static final double TICK = 1.0D / 20.0D;

    private static TrackNetwork ring(double side) {
        List<TrackNode> nodes = new ArrayList<>();
        for (double[] p : new double[][] {{0, 0}, {side, 0}, {side, side}, {0, side}}) {
            nodes.add(new TrackNode(new Vec3(p[0], 64.0D, p[1])));
        }
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, nodes, true, null));
        return network;
    }

    /**
     * Kingsway (start terminus), Guildhall (a berth on each track, outbound listed first), Lakeshore
     * (far terminus), and optionally Fairview between Kingsway and Guildhall.
     */
    private static TransitSystem lineOn(TrackNetwork network, boolean withFairview) {
        double length = network.section(1).totalLength();
        TransitSystem transit = new TransitSystem();
        TransitStation start = new TransitStation("Kingsway", Arrays.asList(
            new TransitPlatform(new TrackRef(1, length * 0.02D), DoorSide.RIGHT, "1")));
        TransitStation fairview = new TransitStation("Fairview", Arrays.asList(
            new TransitPlatform(new TrackRef(1, length * 0.10D), DoorSide.RIGHT, "1"),
            new TransitPlatform(new TrackRef(1, length * 0.90D), DoorSide.LEFT, "2")));
        TransitStation middle = new TransitStation("Guildhall", Arrays.asList(
            new TransitPlatform(new TrackRef(1, length * 0.20D), DoorSide.RIGHT, "1"),
            new TransitPlatform(new TrackRef(1, length * 0.80D), DoorSide.LEFT, "2")));
        TransitStation end = new TransitStation("Lakeshore", Arrays.asList(
            new TransitPlatform(new TrackRef(1, length * 0.45D), DoorSide.RIGHT, "1")));
        transit.addStation(start);
        transit.addStation(middle);
        transit.addStation(end);
        List<TransitStation> route = new ArrayList<>(Arrays.asList(start, middle, end));
        if (withFairview) {
            transit.addStation(fairview);
            route.add(1, fairview);
        }
        transit.addLine(new TransitLine("Blue Line", route, false, true, "INBOUND", "OUTBOUND"));
        return transit;
    }

    private static Train runningTrain(double distance) {
        return new Train(TrainSpec.metroTrain(3),
            new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, distance), 15.0D);
    }

    /** The first berth served, as "Station/label", or {@code null} if none within {@code ticks}. */
    private static String firstServed(LineService service, Train train, TrackNetwork network, int ticks) {
        for (int t = 0; t < ticks; t++) {
            double acceleration = service.tick(train, network, TrainDriver.NO_STOP);
            train.setHeld(service.isHolding(train));
            train.tick(network, acceleration, 4, TICK);
            if (service.controller().phase() == TransitStopController.Phase.BOARDING
                && service.currentBerth() != null) {
                return service.line().station(service.currentStopIndex()).name()
                    + "/" + service.currentBerth().label();
            }
        }
        return null;
    }

    @Test
    @DisplayName("a station more than 10,000 blocks ahead is served at the platform reached first")
    void farStationServedAtNearerPlatform() {
        // 48,000-block ring: from just past the far terminus, Guildhall's inbound berth is 14,400
        // blocks ahead and its outbound berth (listed first) 33,600.
        TrackNetwork network = ring(12_000.0D);
        TransitSystem transit = lineOn(network, false);
        TransitLine line = transit.line("Blue Line");
        double length = network.section(1).totalLength();

        Train train = runningTrain(length * 0.50D);
        LineService service = new LineService(line, TransitDrives.metro(15.0D, TICK),
            line.indexOfStation("Guildhall"), -1, 1.0D, transit::station);

        service.tick(train, network, TrainDriver.NO_STOP);
        assertNotNull(service.currentBerth(), "a berth 14,400 blocks ahead is in sight");
        assertEquals("2", service.currentBerth().label(), "the inbound berth, the one reached first");
        assertFalse(Double.isInfinite(service.distanceToNextStop()),
            "the status line has a distance to show, not a bare speed");

        assertEquals("Guildhall/2", firstServed(service, train, network, 30_000),
            "the train stops at Guildhall's inbound platform rather than running on to the far one");
    }

    @Test
    @DisplayName("a service whose stop index slipped is put back on its route at the next station")
    void slippedStopIndexIsRepaired() {
        TrackNetwork network = ring(1_000.0D);
        TransitSystem transit = lineOn(network, true);
        TransitLine line = transit.line("Blue Line");
        double length = network.section(1).totalLength();

        // Outbound just past Kingsway, so Fairview is next — but the service is counting on
        // Guildhall INBOUND, most of a lap away. Left alone it passes Fairview and Guildhall 1.
        Train train = runningTrain(length * 0.04D);
        LineService service = new LineService(line, TransitDrives.metro(15.0D, TICK),
            line.indexOfStation("Guildhall"), -1, 1.0D, transit::station);

        assertEquals("Fairview/1", firstServed(service, train, network, 20_000),
            "the next station ahead is served, not skipped");
        assertEquals("OUTBOUND", line.labelFor(service.serviceDirection()),
            "and the service counts outbound again, so Guildhall 1 comes next");
        assertNotNull(service.takeRouteNote(), "the correction is reported for the log");
    }

    @Test
    @DisplayName("a correctly routed service is never second-guessed")
    void correctServiceIsLeftAlone() {
        TrackNetwork network = ring(1_000.0D);
        TransitSystem transit = lineOn(network, true);
        double length = network.section(1).totalLength();

        Train train = runningTrain(length * 0.04D);
        LineService service = transit.enterService(1, train, network, "Blue Line",
            TransitDrives.metro(15.0D, TICK));
        List<String> served = new ArrayList<>();
        for (int t = 0; t < 40_000; t++) {
            double acceleration = service.tick(train, network, TrainDriver.NO_STOP);
            train.setHeld(service.isHolding(train));
            train.tick(network, acceleration, 4, TICK);
            assertEquals(null, service.takeRouteNote(), "no correction on a service that is on its route");
            if (service.controller().phase() == TransitStopController.Phase.BOARDING
                && service.currentBerth() != null) {
                String berth = service.line().station(service.currentStopIndex()).name()
                    + "/" + service.currentBerth().label();
                if (served.isEmpty() || !served.get(served.size() - 1).equals(berth)) {
                    served.add(berth);
                }
            }
        }
        assertTrue(served.size() >= 6, "a full cycle and more — got " + served);
        assertEquals(Arrays.asList("Fairview/1", "Guildhall/1", "Lakeshore/1", "Guildhall/2",
            "Fairview/2", "Kingsway/1"), served.subList(0, 6), "every berth in order");
    }
}
