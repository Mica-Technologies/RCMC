package com.micatechnologies.minecraft.rcmc.physics.block;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
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
 * A block boundary laid exactly on a circuit's seam — a station or block brake on the span that
 * closes the loop, ending at node 0 — must not stop trains anywhere but at a boundary.
 */
class BlockLayoutSeamTest {

    @Test
    @DisplayName("with a boundary on the seam, a train in the next block runs on to its boundary")
    void seamBoundaryDoesNotHoldTrainsMidBlock() {
        List<TrackNode> ring = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            double t = 2.0D * Math.PI * i / 24.0D;
            ring.add(new TrackNode(new Vec3(Math.cos(t) * 40.0D, 64.0D, Math.sin(t) * 40.0D)));
        }
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(1, ring, true, null));
        double length = network.section(1).totalLength();

        BlockSystem system = new BlockSystem(true, true, 4.0D, 0.05D);
        for (BlockSection block : BlockLayout.forCircuit(1, length, Arrays.asList(30.0D, 60.0D, length))) {
            system.addBlock(block);
        }
        TrainManager trains = new TrainManager();
        TrainSpec spec = new TrainSpec(1, 3.0D, 0.5D, 4);
        // Train 1 runs in the block 60 -> seam; train 2 stands in the seam's own block, 0 -> 30.
        trains.add(1, new Train(spec, new PhysicsIntegrator(9.81D, 0.0D, 0.0D, 1000.0D), new TrackRef(1, 100.0D), 8.0D));
        trains.add(2, new Train(spec, new PhysicsIntegrator(9.81D, 0.0D, 0.0D, 1000.0D), new TrackRef(1, 15.0D), 0.0D));

        for (int t = 0; t < 600; t++) {
            system.updateOccupancy(trains, network);
            trains.tick(network, system, 4, 0.05D);
        }
        // It is held short of the seam, not stopped a few blocks from where it started.
        double s = trains.train(1).reference().distance();
        assertTrue(s > length - 20.0D, "train 1 stopped at " + s + " of " + length + ", far short of the seam");
    }
}
