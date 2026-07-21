package com.micatechnologies.minecraft.rcmc.physics.transit;

import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.TICK;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.flatNetwork;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.metroDriver;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.node;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.realistic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LineServiceTest {

    /** Short door timings so a multi-stop service test stays fast. */
    private static TransitStopController quickController() {
        return new TransitStopController(metroDriver(), TransitTestSupport.LINE_SPEED,
            0.5D, 5, 10, 5);
    }

    private static double tickInService(Train train, TrackNetwork network, LineService service,
                                        double authority) {
        double acceleration = service.tick(train, network, authority);
        train.setHeld(service.isHolding(train));
        train.tick(network, acceleration, 4, TICK);
        return acceleration;
    }

    private static int runUntilServed(Train train, TrackNetwork network, LineService service,
                                      int targetServed, int guardTicks) {
        int guard = 0;
        while (service.controller().stopsServed() < targetServed && guard++ < guardTicks) {
            tickInService(train, network, service, TrainDriver.NO_STOP);
        }
        return guard;
    }

    @Test
    @DisplayName("an out-and-back service runs A to B, reverses at the terminus, and runs back to A")
    void outAndBackServiceReversesAtTheTerminus() {
        TrackNetwork flat = flatNetwork(1000.0D);
        TransitLine line = new TransitLine("Test Line", Arrays.asList(
            new TransitStation("Alpha", new TrackRef(1, 200.0D)),
            new TransitStation("Beta", new TrackRef(1, 700.0D))), false);
        LineService service = new LineService(line, quickController(), 0, 1, 1.0D);
        Train train = new Train(TrainSpec.singleCar(), realistic(), new TrackRef(1, 100.0D), 0.0D);

        assertTrue(runUntilServed(train, flat, service, 1, 20000) < 20000,
            "expected the first station served");
        assertEquals(200.0D, train.reference().distance(), 1.0D, "expected a berth at Alpha");
        assertEquals(1, service.currentStopIndex(), "next target should be Beta");

        assertTrue(runUntilServed(train, flat, service, 2, 20000) < 20000,
            "expected the terminus served");
        assertEquals(700.0D, train.reference().distance(), 1.0D, "expected a berth at Beta");
        assertEquals(-1, service.serviceDirection(), "expected the terminus to reverse the service");
        assertEquals(0, service.currentStopIndex(), "next target should be Alpha again");
        assertEquals(-1.0D, service.facing(), 1e-9D, "expected facing flipped for the return leg");

        assertTrue(runUntilServed(train, flat, service, 3, 20000) < 20000,
            "expected the return to Alpha served");
        assertEquals(200.0D, train.reference().distance(), 1.0D,
            "expected the train back at Alpha, having run the line in reverse");
    }

    @Test
    @DisplayName("a loop service wraps from the last station straight back to the first")
    void loopServiceWraps() {
        TrackNetwork ring = new TrackNetwork();
        ring.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(150, 64, 0), node(150, 64, 150), node(0, 64, 150)), true, null));
        double length = ring.section(1).totalLength();

        TransitLine line = new TransitLine("Circle", Arrays.asList(
            new TransitStation("One", new TrackRef(1, length * 0.15D)),
            new TransitStation("Two", new TrackRef(1, length * 0.50D)),
            new TransitStation("Three", new TrackRef(1, length * 0.85D))), true);
        LineService service = new LineService(line, quickController(), 0, 1, 1.0D);
        Train train = new Train(TrainSpec.singleCar(), realistic(),
            new TrackRef(1, length * 0.05D), 0.0D);

        assertTrue(runUntilServed(train, ring, service, 4, 40000) < 40000,
            "expected four stops served around the loop");
        assertEquals(1, service.currentStopIndex(),
            "after serving One, Two, Three, One the next target wraps to Two");
        assertEquals(1, service.serviceDirection(), "a loop never reverses");
        assertEquals(line.station(0).stopPoint().distance(), train.reference().distance(), 1.5D,
            "fourth stop should be station One again, reached the long way around");
    }

    @Test
    @DisplayName("facingToward picks the direction that reaches the station sooner")
    void facingTowardPicksTheShorterDirection() {
        TrackNetwork flat = flatNetwork(1000.0D);
        TransitStation station = new TransitStation("Alpha", new TrackRef(1, 200.0D));
        assertEquals(1.0D, LineService.facingToward(flat, new TrackRef(1, 100.0D), station), 1e-9D);
        assertEquals(-1.0D, LineService.facingToward(flat, new TrackRef(1, 300.0D), station), 1e-9D);
    }
}
