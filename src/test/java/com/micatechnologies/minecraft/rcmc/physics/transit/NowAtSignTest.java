package com.micatechnologies.minecraft.rcmc.physics.transit;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The in-car sign says "Now at X" only while the train is standing at X.
 *
 * <p>Seen in game: a train berthed at Harbor with its sign reading "Now at: Central", the next stop.
 * The sign reads {@link ServiceSnapshot#atPlatform()} and {@link ServiceSnapshot#nextStopIndex()};
 * this drives whole service cycles and checks the pair against where the train actually is, on
 * every kind of line.</p>
 */
class NowAtSignTest {

    private static final double TICK = 1.0D / 20.0D;

    /** How close to a berth's stop point a train counts as standing at it. */
    private static final double AT_BERTH = 3.0D;

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

    private static TransitSystem lineOn(TrackNetwork network, String kind) {
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
        transit.addLine(new TransitLine("Circle Line", Arrays.asList(start, middle, end),
            kind.startsWith("loop"), "turnback".equals(kind), "INBOUND", "OUTBOUND"));
        return transit;
    }

    /** Along-track distance from {@code at} to the nearest of {@code station}'s berths, on the ring. */
    private static double fromBerth(TransitStation station, TrackRef at, double length) {
        double best = Double.MAX_VALUE;
        for (TransitPlatform platform : station.platforms()) {
            double d = Math.abs(platform.stopPoint().distance() - at.distance());
            best = Math.min(best, Math.min(d, length - d));
        }
        return best;
    }

    @ParameterizedTest(name = "{0} line")
    @ValueSource(strings = {"turnback", "stub", "loop", "loop-reversed"})
    @DisplayName("\"Now at\" names the station the train is standing at")
    void nowAtIsWhereTheTrainIs(String kind) {
        TrackNetwork network = ring();
        TransitSystem transit = lineOn(network, kind);
        double length = network.section(1).totalLength();
        Train train = new Train(TrainSpec.metroTrain(3), new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, length * 0.10D), kind.endsWith("reversed") ? -15.0D : 15.0D);
        LineService service = transit.enterService(1, train, network, "Circle Line",
            TransitDrives.metro(15.0D, TICK));

        int nowAtTicks = 0;
        for (int t = 0; t < 20000; t++) {
            double acceleration = service.tick(train, network, TrainDriver.NO_STOP);
            train.setHeld(service.isHolding(train));
            train.tick(network, acceleration, 4, TICK);

            ServiceSnapshot sign = ServiceSnapshot.of(1, service, DoorSide.RIGHT, "");
            if (sign.atPlatform()) {
                nowAtTicks++;
                TransitStation named = service.line().station(sign.nextStopIndex());
                double off = fromBerth(named, train.reference(), length);
                assertTrue(off <= AT_BERTH, "tick " + t + ": the sign says \"Now at " + named.name()
                    + "\" but the train is " + off + " blocks from it, at s=" + train.reference().distance());
            }
        }
        assertTrue(nowAtTicks > 100, "the train must actually stop somewhere for this to test anything");
    }
}
