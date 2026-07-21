package com.micatechnologies.minecraft.rcmc.physics.block;

import static com.micatechnologies.minecraft.rcmc.physics.block.BlockTestSupport.SECTION_ID;
import static com.micatechnologies.minecraft.rcmc.physics.block.BlockTestSupport.TICK;
import static com.micatechnologies.minecraft.rcmc.physics.block.BlockTestSupport.flatNetwork;
import static com.micatechnologies.minecraft.rcmc.physics.block.BlockTestSupport.frictionless;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlockSignaledElementSetTest {

    @Test
    @DisplayName("a chain lift keeps pulling while far from a boundary, even with the next block occupied")
    void chainLiftIsUnaffectedFarFromABoundary() {
        TrackNetwork network = flatNetwork(300.0D);
        // Span stops short of block 1's boundary (100) and well short of block 2 (100-200), so it
        // never reaches the stalled occupant parked at 150 — this test is about the lift being
        // left alone until braking is genuinely needed, not about accelerating the blocker away.
        ChainLift lift = new ChainLift(SECTION_ID, 0.0D, 90.0D, 10.0D, 5.0D, TICK);
        RideElementSet elements = new RideElementSet();
        elements.add(lift);

        BlockSystem blocks = new BlockSystem(false, true, 6.0D, TICK);
        blocks.addBlock(new BlockSection("b1", SECTION_ID, 0.0D, 100.0D));
        blocks.addBlock(new BlockSection("b2", SECTION_ID, 100.0D, 200.0D));

        BlockSignaledElementSet composite = new BlockSignaledElementSet(elements, blocks);

        TrainManager trains = new TrainManager();
        Train train = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, 10.0D), 2.0D);
        trains.add(1, train);
        // Block 2 is occupied for the entire test — a stalled train sitting well inside it.
        trains.add(2, new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, 150.0D), 0.0D));

        blocks.updateOccupancy(trains, network);
        assertTrue(blocks.isHolding(1, train), "next block is occupied, so this is eager-true by design");
        assertEquals(lift.accelerationFor(train), composite.forTrain(1, train), 1e-9D,
            "far from the boundary the block system has nothing to say, so the lift must pass through unchanged");

        // Run it forward: the lift should keep climbing the train toward chain speed for a good
        // while, then the block brake should take over and actually stop it at the boundary —
        // proving the lift was never silently disabled just because isHolding was already true.
        for (int tick = 0; tick < 400; tick++) {
            blocks.updateOccupancy(trains, network);
            trains.tick(network, composite, 4, TICK);
        }
        assertTrue(Math.abs(train.velocity()) < 0.1D,
            "expected the block brake to have overridden the lift and stopped the train, v=" + train.velocity());
        assertTrue(train.reference().distance() <= 100.001D,
            "the lift must not have been allowed to carry the train into the occupied block, got "
                + train.reference().distance());
    }

    @Test
    @DisplayName("isHolding is true if either the block system or a ride element is holding")
    void isHoldingIsTrueFromEitherSide() {
        TrackNetwork network = flatNetwork(300.0D);

        // Side A: a station platform holding a train (ARRIVING phase, isHolding() == true by
        // itself), with an empty block system that has no opinion about anything.
        StationPlatform platform = new StationPlatform(SECTION_ID, 0.0D, 100.0D,
            50.0D, 4.0D, 5, 6.0D, 8.0D, TICK);
        RideElementSet elementsWithStation = new RideElementSet();
        elementsWithStation.add(platform);
        BlockSystem noBlocks = new BlockSystem(false, true, 6.0D, TICK);
        BlockSignaledElementSet stationHeld = new BlockSignaledElementSet(elementsWithStation, noBlocks);

        TrainManager soloTrain = new TrainManager();
        Train arriving = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, 10.0D), 3.0D);
        soloTrain.add(1, arriving);
        noBlocks.updateOccupancy(soloTrain, network);

        assertFalse(noBlocks.isHolding(1, arriving), "no blocks configured — the block side has no opinion");
        assertTrue(platform.isHolding(), "the station itself should be holding during ARRIVING");
        assertTrue(stationHeld.isHolding(1, arriving), "composite must reflect the element side holding");

        // Side B: an empty ride-element set (never holds anything) with a block system genuinely
        // holding a train because the next block is occupied by another train.
        RideElementSet noElements = new RideElementSet();
        BlockSystem blocking = new BlockSystem(false, true, 6.0D, TICK);
        blocking.addBlock(new BlockSection("b1", SECTION_ID, 0.0D, 100.0D));
        blocking.addBlock(new BlockSection("b2", SECTION_ID, 100.0D, 200.0D));
        BlockSignaledElementSet blockHeld = new BlockSignaledElementSet(noElements, blocking);

        TrainManager twoTrains = new TrainManager();
        Train held = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, 95.0D), 0.0D);
        twoTrains.add(1, held);
        twoTrains.add(2, new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, 150.0D), 0.0D));
        blocking.updateOccupancy(twoTrains, network);

        assertFalse(noElements.isHolding(1, held), "an empty element set never holds anything");
        assertTrue(blocking.isHolding(1, held), "the block side should be holding — next block is occupied");
        assertTrue(blockHeld.isHolding(1, held), "composite must reflect the block side holding");
    }
}
