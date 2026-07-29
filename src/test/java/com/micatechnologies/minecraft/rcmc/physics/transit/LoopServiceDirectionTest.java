package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * A service must run its stations in the order it will physically reach them.
 *
 * <p>Reported from a live underground demo: <em>the metro skips stations</em>. {@code enterService}
 * picks the first stop as the nearest station <b>in either direction</b>, but then hardcoded the
 * service direction to {@code +1} — so a train that happened to be pointed against the order its
 * stations are listed in would serve stop {@code k}, target {@code k+1}, and find it most of a lap
 * away in its direction of travel. Every station in between was driven straight past.</p>
 *
 * <p>A loop is where this is fatal, because nothing ever corrects it. A shuttle limps: it reaches a
 * terminus and the turnback flips the direction, so it looks merely odd for one leg.</p>
 */
class LoopServiceDirectionTest {

    /** A closed ring, laid out as a square so the geometry is easy to reason about. */
    private static TrackNetwork ring() {
        List<TrackNode> nodes = new ArrayList<>();
        double side = 100.0D;
        for (double[] p : new double[][] {{0, 0}, {side, 0}, {side, side}, {0, side}}) {
            nodes.add(new TrackNode(new Vec3(p[0], 64.0D, p[1])));
        }
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, nodes, true, null));
        return network;
    }

    /** Four stations spread around the ring, named in the order the distances run. */
    private static TransitSystem lineOn(TrackNetwork network, boolean loop) {
        double length = network.section(1).totalLength();
        TransitSystem transit = new TransitSystem();
        List<TransitStation> stops = new ArrayList<>();
        String[] names = {"North", "East", "South", "West"};
        for (int i = 0; i < names.length; i++) {
            TransitStation station =
                new TransitStation(names[i], new TrackRef(1, length * (i + 0.5D) / 4.0D));
            transit.addStation(station);
            stops.add(station);
        }
        transit.addLine(new TransitLine("Ring", stops, loop));
        return transit;
    }

    private static Train trainAt(double distance) {
        return new Train(TrainSpec.metroTrain(3),
            new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, distance), 0.0D);
    }

    @Test
    @DisplayName("a train facing against the station order serves the stops it actually passes")
    void servesStationsInTheOrderItReachesThem() {
        TrackNetwork network = ring();
        TransitSystem transit = lineOn(network, true);
        double length = network.section(1).totalLength();

        // Just past the first station, so the nearest stop is BEHIND: enterService picks it and
        // faces the train backwards to reach it. That is the situation the old code got wrong, and
        // it is exactly what a train reloading at a platform can land in.
        Train train = trainAt(length * 0.125D + 3.0D);
        LineService service =
            transit.enterService(1, train, network, "Ring", TransitDrives.metro(15.0D, 0.05D));

        assertEquals(0, service.currentStopIndex(), "the nearest stop is the one just behind");
        assertEquals(-1.0D, service.facing(), 1e-9D, "reaching it means facing backwards");
        assertEquals(-1, service.serviceDirection(),
            "running backwards round a ring serves the stations in descending order; going up "
                + "would send the train most of a lap past three stations to reach the next one");
    }

    @Test
    @DisplayName("a train facing with the station order still counts up")
    void forwardFacingIsUnchanged() {
        TrackNetwork network = ring();
        TransitSystem transit = lineOn(network, true);

        // Well before the first station, so the nearest is ahead — the ordinary case, which must
        // behave exactly as it always did.
        Train train = trainAt(1.0D);
        LineService service =
            transit.enterService(1, train, network, "Ring", TransitDrives.metro(15.0D, 0.05D));

        assertEquals(0, service.currentStopIndex());
        assertEquals(1.0D, service.facing(), 1e-9D);
        assertEquals(1, service.serviceDirection());
    }

    @Test
    @DisplayName("the next stop is never most of a lap away when a nearer one is coming first")
    void nextStopIsTheNextOneAlong() {
        // The property that actually matters, stated without reference to signs: whatever the
        // service targets after serving its first stop, no OTHER station of the line may sit
        // between the train and it. That is what "skipping stations" means.
        TrackNetwork network = ring();
        TransitSystem transit = lineOn(network, true);
        double length = network.section(1).totalLength();

        Train train = trainAt(length * 0.125D + 3.0D);
        LineService service =
            transit.enterService(1, train, network, "Ring", TransitDrives.metro(15.0D, 0.05D));

        TransitLine line = service.line();
        int served = service.currentStopIndex();
        int next = Math.floorMod(served + service.serviceDirection(), line.stationCount());

        // Measured from the station just served, in the direction of travel.
        TrackRef from = line.station(served).stopPoint();
        double toNext = com.micatechnologies.minecraft.rcmc.track.TrackWalk.distanceTo(
            network, from, service.facing(), line.station(next).stopPoint(), 10_000.0D);
        for (int i = 0; i < line.stationCount(); i++) {
            if (i == served || i == next) {
                continue;
            }
            double toOther = com.micatechnologies.minecraft.rcmc.track.TrackWalk.distanceTo(
                network, from, service.facing(), line.station(i).stopPoint(), 10_000.0D);
            assertTrue(toOther > toNext,
                line.station(i).name() + " comes before the next scheduled stop "
                    + line.station(next).name() + " and would be driven straight past");
        }
    }

    @Test
    @DisplayName("a shuttle starting at its far terminus runs inward instead of turning back first")
    void shuttleFromFarTerminus() {
        TrackNetwork network = ring();
        TransitSystem transit = lineOn(network, false);
        double length = network.section(1).totalLength();

        // Nearest stop is the LAST station of a shuttle: the only way to serve the line from there
        // is to count down. The old code started at +1 and relied on the terminus turnback noticing.
        Train train = trainAt(length * 0.875D + 2.0D);
        LineService service =
            transit.enterService(1, train, network, "Ring", TransitDrives.metro(15.0D, 0.05D));

        assertEquals(3, service.currentStopIndex());
        assertEquals(-1, service.serviceDirection());
    }
}
