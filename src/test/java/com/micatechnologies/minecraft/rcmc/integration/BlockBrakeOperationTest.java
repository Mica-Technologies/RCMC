package com.micatechnologies.minecraft.rcmc.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.debug.DemoCoaster;
import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.block.BlockLayout;
import com.micatechnologies.minecraft.rcmc.physics.block.BlockSection;
import com.micatechnologies.minecraft.rcmc.physics.block.BlockSignaledElementSet;
import com.micatechnologies.minecraft.rcmc.physics.block.BlockSystem;
import com.micatechnologies.minecraft.rcmc.physics.block.BlockSystems;
import com.micatechnologies.minecraft.rcmc.physics.element.BrakeRun;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.physics.ride.TrainCollisions;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.track.storage.BlockCodec;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Block brakes: blocks bounded by the ride's hardware, and two trains running them for real.
 *
 * <p>{@code /rcmc block N} could only cut a circuit into equal lengths, so trains were held wherever
 * the arithmetic put a boundary — mid-drop included — by a brake that did not exist. The builder's
 * block brake, and {@code /rcmc block <id> auto}, put the boundaries where a real ride has them.</p>
 */
class BlockBrakeOperationTest {

    private static final double TICK = RcmcConstants.SECONDS_PER_TICK;

    private static final class Ride {
        final DemoCoaster.Result demo = DemoCoaster.build(1, new Vec3(0, 64, 0), 1.0D, 34.0D);
        final TrackNetwork network = new TrackNetwork();
        final RideElementSet elements = new RideElementSet();
        final StationPlatform station;

        Ride() {
            network.addSection(demo.section);
            station = new StationPlatform(1, demo.stationStart, demo.stationEnd, demo.stationStop,
                6.0D, 60, 4.0D, 6.0D, TICK);
            elements.add(station);
            elements.add(new ChainLift(1, demo.liftStart, demo.liftEnd, 5.0D, 12.0D, TICK));
            // The demo's final brakes, laid as a block brake instead of a trim brake.
            elements.add(new BrakeRun(1, demo.brakeStart, demo.brakeEnd, 4.0D, 6.0D,
                BrakeRun.Mode.BLOCK, TICK));
        }
    }

    @Test
    @DisplayName("boundaries fall at the station, the lift top and the block brake, and nowhere else")
    void boundariesAreTheHardware() {
        Ride ride = new Ride();
        List<Double> at = BlockLayout.boundaries(1, ride.elements.elements());
        assertEquals(3, at.size(), "station, lift, block brake: " + at);
        assertEquals(ride.demo.stationEnd, at.get(0), 1e-9);
        assertEquals(ride.demo.liftEnd, at.get(1), 1e-9);
        assertEquals(ride.demo.brakeEnd, at.get(2), 1e-9);
    }

    @Test
    @DisplayName("a trim brake is not a boundary: it cannot hold a train")
    void trimBrakeIsNotABoundary() {
        Ride ride = new Ride();
        ride.elements.add(new BrakeRun(1, 300.0D, 310.0D, 6.0D, 6.0D, BrakeRun.Mode.TRIM, TICK));
        assertEquals(3, BlockLayout.boundaries(1, ride.elements.elements()).size());
    }

    @Test
    @DisplayName("the block containing the seam wraps through it, and survives a save")
    void wrappingBlock() {
        Ride ride = new Ride();
        double length = ride.demo.section.totalLength();
        List<BlockSection> blocks = BlockLayout.forCircuit(1, length,
            BlockLayout.boundaries(1, ride.elements.elements()));
        BlockSection closing = blocks.get(blocks.size() - 1);
        assertTrue(closing.wraps());
        assertTrue(closing.contains(new TrackRef(1, length - 1.0D)), "before the seam");
        assertTrue(closing.contains(new TrackRef(1, 1.0D)), "after the seam");
        assertFalse(closing.contains(new TrackRef(1, 200.0D)), "mid-course");

        BlockSystems systems = new BlockSystems();
        BlockSystem system = new BlockSystem(true, true, 4.0D, TICK);
        blocks.forEach(system::addBlock);
        systems.put(1, system);
        NBTTagCompound root = new NBTTagCompound();
        BlockCodec.write(systems, root);
        BlockSection read = BlockCodec.read(root).get(1).blocks().get(blocks.size() - 1);
        assertTrue(read.wraps() && read.contains(new TrackRef(1, 1.0D)));
    }

    @Test
    @DisplayName("a train is held out of a block that only another train's tail is in, however the station pushes")
    void stationHoldsAtOccupiedBoundary() {
        List<com.micatechnologies.minecraft.rcmc.track.TrackNode> nodes = new java.util.ArrayList<>();
        for (double[] p : new double[][] {{0, 0}, {150, 0}, {150, 100}, {0, 100}}) {
            nodes.add(new com.micatechnologies.minecraft.rcmc.track.TrackNode(new Vec3(p[0], 64, p[1])));
        }
        TrackNetwork network = new TrackNetwork();
        network.addSection(new com.micatechnologies.minecraft.rcmc.track.TrackSection(1, nodes, true, null));
        RideElementSet elements = new RideElementSet();
        elements.add(new StationPlatform(1, 0.0D, 50.0D, 47.0D, 6.0D, 60, 4.0D, 6.0D, TICK));
        // A short block straight after the platform, ended by a block brake.
        elements.add(new BrakeRun(1, 55.0D, 60.0D, 4.0D, 6.0D, BrakeRun.Mode.BLOCK, TICK));
        elements.add(new BrakeRun(1, 200.0D, 210.0D, 4.0D, 6.0D, BrakeRun.Mode.BLOCK, TICK));
        BlockSystems systems = new BlockSystems();
        BlockSystem system = new BlockSystem(true, true, 4.0D, TICK);
        BlockLayout.forCircuit(1, network.section(1).totalLength(),
            BlockLayout.boundaries(1, elements.elements())).forEach(system::addBlock);
        systems.put(1, system);

        TrainManager trains = new TrainManager();
        TrainSpec spec = new TrainSpec(5, 3.0D, 0.5D, 4);
        trains.add(1, new Train(spec, new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, 47.0D), 0.0D));
        // Parked, dead, with its lead car in the block beyond and its tail still in the one after
        // the station — the station's next block is occupied by nothing but that tail.
        trains.add(2, new Train(spec, new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, 70.0D), 0.0D));
        for (int t = 0; t < 20 * 90; t++) {
            systems.updateOccupancy(trains, network);
            trains.tick(network, new BlockSignaledElementSet(elements, systems), 4, TICK);
            assertTrue(trains.train(1).reference().distance() < 50.0D,
                "crept into the occupied block: s=" + trains.train(1).reference().distance() + " at tick " + t);
        }
    }

    @Test
    @DisplayName("a train held at a boundary on a downhill stays put, and does not creep over it")
    void holdsOnADownhill() {
        List<com.micatechnologies.minecraft.rcmc.track.TrackNode> nodes = new java.util.ArrayList<>();
        // Straight down a 1-in-4 grade for 200 blocks, then level. The train starts at rest a few
        // blocks above the boundary: stopping it is easy, keeping it stopped is the test.
        for (double[] p : new double[][] {{0, 100}, {100, 75}, {200, 50}, {300, 50}}) {
            nodes.add(new com.micatechnologies.minecraft.rcmc.track.TrackNode(new Vec3(p[0], p[1], 0)));
        }
        TrackNetwork network = new TrackNetwork();
        network.addSection(new com.micatechnologies.minecraft.rcmc.track.TrackSection(1, nodes, false, null));
        BlockSystems systems = new BlockSystems();
        BlockSystem system = new BlockSystem(false, true, 4.0D, TICK);
        system.addBlock(new BlockSection("upper", 1, 0.0D, 120.0D));
        system.addBlock(new BlockSection("lower", 1, 120.0D, network.section(1).totalLength()));
        systems.put(1, system);
        TrainManager trains = new TrainManager();
        TrainSpec spec = new TrainSpec(1, 3.0D, 0.5D, 4);
        trains.add(1, new Train(spec, new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, 114.0D), 0.0D));
        trains.add(2, new Train(spec, new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, 250.0D), 0.0D));
        RideElementSet none = new RideElementSet();
        double settled = -1.0D;
        for (int t = 0; t < 20 * 90; t++) {
            systems.updateOccupancy(trains, network);
            trains.tick(network, new BlockSignaledElementSet(none, systems), 4, TICK);
            double s = trains.train(1).reference().distance();
            assertTrue(s < 120.0D, "rolled into the occupied block: s=" + s + " at tick " + t);
            if (t == 20 * 30) {
                settled = s;
            }
        }
        assertEquals(settled, trains.train(1).reference().distance(), 0.05D,
            "held still for the last minute, not creeping down the grade");
    }

    @Test
    @DisplayName("two trains run ten minutes on hardware blocks without meeting, and both keep lapping")
    void twoTrainsRunSafely() {
        Ride ride = new Ride();
        BlockSystems systems = new BlockSystems();
        BlockSystem system = new BlockSystem(true, true, 4.0D, TICK);
        BlockLayout.forCircuit(1, ride.demo.section.totalLength(),
            BlockLayout.boundaries(1, ride.elements.elements())).forEach(system::addBlock);
        systems.put(1, system);

        TrainManager trains = new TrainManager();
        TrainSpec spec = new TrainSpec(5, 3.0D, 0.5D, 4);
        trains.add(1, new Train(spec, new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, ride.demo.stationStop), 0.0D));
        // The second is already on the chain, climbing: the first must wait in the station for it.
        trains.add(2, new Train(spec, new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, ride.demo.liftStart + 25.0D), 5.0D));

        int dwells = 0;
        StationPlatform.Phase previous = ride.station.phase();
        for (int t = 0; t < 20 * 600; t++) {
            systems.updateOccupancy(trains, ride.network);
            trains.tick(ride.network, new BlockSignaledElementSet(ride.elements, systems), 4, TICK);
            assertTrue(TrainCollisions.sectionsWithCollisions(trains.asMap(), ride.network).isEmpty(),
                "trains met at tick " + t);
            for (int id = 1; id <= 2; id++) {
                assertTrue(trains.train(id).isRunning(), "train " + id + " faulted "
                    + trains.train(id).status() + " at s=" + trains.train(id).reference().distance());
            }
            StationPlatform.Phase phase = ride.station.phase();
            if (phase == StationPlatform.Phase.DWELLING && previous != StationPlatform.Phase.DWELLING) {
                dwells++;
            }
            previous = phase;
        }
        // A lap is under two minutes; two trains over ten minutes call at the station many times.
        assertTrue(dwells >= 8, "only " + dwells + " station stops in ten minutes");
    }
}
