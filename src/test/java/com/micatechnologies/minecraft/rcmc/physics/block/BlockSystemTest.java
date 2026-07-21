package com.micatechnologies.minecraft.rcmc.physics.block;

import static com.micatechnologies.minecraft.rcmc.physics.block.BlockTestSupport.SECTION_ID;
import static com.micatechnologies.minecraft.rcmc.physics.block.BlockTestSupport.TICK;
import static com.micatechnologies.minecraft.rcmc.physics.block.BlockTestSupport.flatNetwork;
import static com.micatechnologies.minecraft.rcmc.physics.block.BlockTestSupport.frictionless;
import static com.micatechnologies.minecraft.rcmc.physics.block.BlockTestSupport.ringNetwork;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlockSystemTest {

    // ---- boundary hold / release --------------------------------------------------------

    @Test
    @DisplayName("a train stops at a block boundary when the next block is occupied")
    void trainStopsAtBoundaryWhenNextBlockOccupied() {
        TrackNetwork network = flatNetwork(300.0D);
        BlockSystem blocks = new BlockSystem(false, true, 4.0D, TICK);
        blocks.addBlock(new BlockSection("b1", SECTION_ID, 0.0D, 100.0D));
        blocks.addBlock(new BlockSection("b2", SECTION_ID, 100.0D, 200.0D));

        TrainManager trains = new TrainManager();
        // Parked in block 2: a stalled single-car train, valleyed on this flat section, but that
        // only affects ITS own tick() — TrainManager.tick still reports its position to the block
        // system every tick regardless of running status, exactly as a real occupied block would.
        trains.add(1, new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, 150.0D), 0.0D));
        Train approaching = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, 0.0D), 8.0D);
        trains.add(2, approaching);

        for (int tick = 0; tick < 400; tick++) {
            BlockTestSupport.tick(trains, network, blocks);
        }

        assertTrue(Math.abs(approaching.velocity()) < 0.05D,
            "expected the approaching train to have braked to a stop, velocity=" + approaching.velocity());
        assertTrue(approaching.reference().distance() > 90.0D,
            "train braked far too early: " + approaching.reference().distance());
        assertTrue(approaching.reference().distance() <= 100.001D,
            "train must not cross into the still-occupied block: " + approaching.reference().distance());
    }

    @Test
    @DisplayName("a held train proceeds once the next block clears")
    void trainProceedsOnceNextBlockClears() {
        TrackNetwork network = flatNetwork(300.0D);
        BlockSystem blocks = new BlockSystem(false, true, 4.0D, TICK);
        blocks.addBlock(new BlockSection("b1", SECTION_ID, 0.0D, 100.0D));
        blocks.addBlock(new BlockSection("b2", SECTION_ID, 100.0D, 200.0D));

        TrainManager trains = new TrainManager();
        Train occupant = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, 150.0D), 0.0D);
        trains.add(1, occupant);
        Train approaching = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, 0.0D), 8.0D);
        trains.add(2, approaching);

        for (int tick = 0; tick < 400; tick++) {
            BlockTestSupport.tick(trains, network, blocks);
        }
        double stoppedAt = approaching.reference().distance();
        assertTrue(Math.abs(approaching.velocity()) < 0.05D, "should be stopped before release");

        // Move the occupant well clear of block 2, exactly as a departing train would.
        occupant.setState(new TrackRef(SECTION_ID, 250.0D), 0.0D);
        // A dispatch nudge — on flat, frictionless track nothing else would ever restart a train
        // sitting at exactly zero velocity; this stands in for a station drive tyre or gravity.
        approaching.setState(approaching.reference(), 5.0D);

        for (int tick = 0; tick < 100; tick++) {
            BlockTestSupport.tick(trains, network, blocks);
        }

        assertFalse(blocks.isHolding(2, approaching), "block 2 is clear; nothing should be holding train 2");
        assertTrue(approaching.reference().distance() > stoppedAt + 15.0D,
            "train should have run on well past where it was held, got " + approaching.reference().distance());
    }

    @Test
    @DisplayName("a train held at a block boundary is not misreported as valleyed")
    void heldTrainIsNotMisreportedAsValleyed() {
        TrackNetwork network = flatNetwork(300.0D);
        BlockSystem blocks = new BlockSystem(false, true, 4.0D, TICK);
        blocks.addBlock(new BlockSection("b1", SECTION_ID, 0.0D, 100.0D));
        blocks.addBlock(new BlockSection("b2", SECTION_ID, 100.0D, 200.0D));

        TrainManager trains = new TrainManager();
        trains.add(1, new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, 150.0D), 0.0D));
        Train approaching = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, 0.0D), 8.0D);
        trains.add(2, approaching);

        // Block 2 never clears in this test — long enough for a genuinely stalled train to have
        // been flagged VALLEYED many times over if the hold signal were not wired through.
        for (int tick = 0; tick < 3000; tick++) {
            BlockTestSupport.tick(trains, network, blocks);
            assertEquals(Train.Status.RUNNING, approaching.status(),
                "train incorrectly flagged " + approaching.status() + " at tick " + tick
                    + " while a block brake was holding it — Train.setHeld was not honoured");
        }
    }

    // ---- mutual exclusion -----------------------------------------------------------------

    @Test
    @DisplayName("two trains on a closed circuit never occupy the same block")
    void twoTrainsNeverShareABlock() {
        TrackNetwork network = ringNetwork(50.0D);
        double total = network.section(SECTION_ID).totalLength();
        double third = total / 3.0D;

        BlockSystem blocks = new BlockSystem(true, true, 6.0D, TICK);
        blocks.addBlock(new BlockSection("b0", SECTION_ID, 0.0D, third));
        blocks.addBlock(new BlockSection("b1", SECTION_ID, third, 2.0D * third));
        blocks.addBlock(new BlockSection("b2", SECTION_ID, 2.0D * third, total));

        TrainManager trains = new TrainManager();
        trains.add(1, new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, 5.0D), 15.0D));
        trains.add(2, new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, third + 5.0D), 15.0D));

        for (int tick = 0; tick < 4000; tick++) {
            BlockTestSupport.tick(trains, network, blocks);
            int a = blocks.blockIndexOf(1);
            int b = blocks.blockIndexOf(2);
            if (a >= 0 && b >= 0) {
                assertFalse(a == b, "both trains in block " + a + " at tick " + tick);
            }
            assertFalse(blocks.hasCollision(), "unexpected collision at tick " + tick + ": " + blocks.collisions());
        }
    }

    // ---- collisions when safety is disabled ------------------------------------------------

    @Test
    @DisplayName("with safety disabled, two trains can collide, and the collision is detected")
    void safetyDisabledLetsTrainsCollideAndDetectsIt() {
        TrackNetwork network = flatNetwork(200.0D);
        BlockSystem blocks = new BlockSystem(false, false, 4.0D, TICK);
        blocks.addBlock(new BlockSection("b1", SECTION_ID, 0.0D, 200.0D));

        TrainManager trains = new TrainManager();
        // Stalled dead ahead: v=0 on flat track with no hold flags will valley immediately and
        // stay frozen at distance 100 — a stand-in for any train blocking the path.
        trains.add(1, new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, 100.0D), 0.0D));
        trains.add(2, new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, 0.0D), 10.0D));

        boolean collided = false;
        for (int tick = 0; tick < 400 && !collided; tick++) {
            BlockTestSupport.tick(trains, network, blocks);
            collided = blocks.hasCollision();
        }

        assertTrue(collided, "safety is off; the trailing train should have run into the stalled one");
        assertEquals(1, blocks.collisions().size());
        Collision collision = blocks.collisions().get(0);
        assertTrue(collision.involves(1) && collision.involves(2));
        assertEquals(SECTION_ID, collision.sectionId());
        assertTrue(collision.overlapDistance() > 0.0D);
    }

    @Test
    @DisplayName("the same layout, with safety enabled, never collides")
    void safetyEnabledPreventsTheSameCollision() {
        TrackNetwork network = flatNetwork(200.0D);
        BlockSystem blocks = new BlockSystem(false, true, 4.0D, TICK);
        blocks.addBlock(new BlockSection("b1", SECTION_ID, 0.0D, 90.0D));
        blocks.addBlock(new BlockSection("b2", SECTION_ID, 90.0D, 200.0D));

        TrainManager trains = new TrainManager();
        trains.add(1, new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, 100.0D), 0.0D));
        trains.add(2, new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, 0.0D), 10.0D));

        for (int tick = 0; tick < 400; tick++) {
            BlockTestSupport.tick(trains, network, blocks);
            assertFalse(blocks.hasCollision(), "block safety should have prevented this at tick " + tick);
        }
    }

    // ---- the deadlock case, both ways -------------------------------------------------------

    @Test
    @DisplayName("three trains on a three-block circuit with slack keep running, never all stuck at once")
    void threeTrainsThreeBlocksWithSlackNeverDeadlock() {
        TrackNetwork network = ringNetwork(50.0D);
        double total = network.section(SECTION_ID).totalLength();
        double third = total / 3.0D;
        // Each block covers 60% of its third, leaving 40% of neutral track between blocks — the
        // spare capacity a closed ring needs so trains never fully saturate every block at once.
        // See BlockSystem's class javadoc for why wall-to-wall coverage cannot avoid this.
        double blockLength = third * 0.6D;

        BlockSystem blocks = new BlockSystem(true, true, 8.0D, TICK);
        for (int i = 0; i < 3; i++) {
            double start = i * third;
            blocks.addBlock(new BlockSection("b" + i, SECTION_ID, start, start + blockLength));
        }

        TrainManager trains = new TrainManager();
        for (int i = 0; i < 3; i++) {
            trains.add(i + 1, new Train(TrainSpec.singleCar(), frictionless(),
                new TrackRef(SECTION_ID, i * third + 1.0D), 12.0D));
        }

        boolean allSlowSimultaneously = false;
        int ticks = 6000;
        for (int tick = 0; tick < ticks; tick++) {
            BlockTestSupport.tick(trains, network, blocks);

            Set<Integer> occupiedBlocks = new HashSet<>();
            for (int id = 1; id <= 3; id++) {
                int idx = blocks.blockIndexOf(id);
                if (idx >= 0) {
                    assertTrue(occupiedBlocks.add(idx), "two trains shared block " + idx + " at tick " + tick);
                }
            }
            assertFalse(blocks.hasCollision(), "unexpected collision at tick " + tick);

            if (tick > 100) {
                boolean allSlow = true;
                for (int id = 1; id <= 3; id++) {
                    if (trains.train(id).speed() >= 1.0D) {
                        allSlow = false;
                        break;
                    }
                }
                if (allSlow) {
                    allSlowSimultaneously = true;
                }
            }
        }

        assertFalse(allSlowSimultaneously,
            "all three trains were simultaneously near-stationary — that is exactly the deadlock "
                + "this layout's slack is supposed to prevent");
        for (int id = 1; id <= 3; id++) {
            assertTrue(trains.train(id).speed() > 5.0D,
                "train " + id + " should still be cruising at the end, got v=" + trains.train(id).velocity());
            assertFalse(blocks.isStuck(id), "train " + id + " should never have approached the stuck threshold");
        }
    }

    @Test
    @DisplayName("KNOWN LIMITATION: exactly N trains on N wall-to-wall blocks deadlocks the ring")
    void exactlySaturatedRingDeadlocks() {
        // See BlockSystem's class javadoc, "Deadlock" section, for the proof this is structural
        // and not specific to this implementation: with no spare block capacity, every train's
        // next block is always occupied, block brakes only ever remove energy, so every train
        // eventually latches at zero velocity on its own boundary and the whole ring locks
        // permanently. This test exists so the limitation is visible in the suite, not hidden.
        TrackNetwork network = ringNetwork(50.0D);
        double total = network.section(SECTION_ID).totalLength();
        double third = total / 3.0D;

        BlockSystem blocks = new BlockSystem(true, true, 8.0D, TICK, 100);
        blocks.addBlock(new BlockSection("b0", SECTION_ID, 0.0D, third));
        blocks.addBlock(new BlockSection("b1", SECTION_ID, third, 2.0D * third));
        blocks.addBlock(new BlockSection("b2", SECTION_ID, 2.0D * third, total));

        TrainManager trains = new TrainManager();
        for (int i = 0; i < 3; i++) {
            trains.add(i + 1, new Train(TrainSpec.singleCar(), frictionless(),
                new TrackRef(SECTION_ID, i * third + 1.0D), 12.0D));
        }

        for (int tick = 0; tick < 3000; tick++) {
            BlockTestSupport.tick(trains, network, blocks);
        }

        for (int id = 1; id <= 3; id++) {
            Train train = trains.train(id);
            assertTrue(Math.abs(train.velocity()) < 0.1D,
                "expected train " + id + " to have latched at rest, v=" + train.velocity());
            assertTrue(blocks.isStuck(id), "train " + id + " should be reported stuck, not silently fine");
            assertEquals(Train.Status.RUNNING, train.status(),
                "a held train stays RUNNING even while stuck — that is why isStuck exists");
        }
        // Safety still holds even though liveness does not: nobody actually collided.
        assertFalse(blocks.hasCollision());

        double[] frozenAt = new double[4];
        for (int id = 1; id <= 3; id++) {
            frozenAt[id] = trains.train(id).reference().distance();
        }
        for (int tick = 0; tick < 500; tick++) {
            BlockTestSupport.tick(trains, network, blocks);
        }
        for (int id = 1; id <= 3; id++) {
            assertEquals(frozenAt[id], trains.train(id).reference().distance(), 1e-6D,
                "train " + id + " drifted after supposedly freezing — not a permanent deadlock");
        }
    }

    // ---- determinism ------------------------------------------------------------------------

    @Test
    @DisplayName("block signalling is deterministic — identical inputs give identical results")
    void tickLoopIsDeterministic() {
        TrackNetwork networkA = ringNetwork(50.0D);
        TrackNetwork networkB = ringNetwork(50.0D);
        double total = networkA.section(SECTION_ID).totalLength();
        double half = total / 2.0D;

        BlockSystem blocksA = new BlockSystem(true, true, 6.0D, TICK);
        BlockSystem blocksB = new BlockSystem(true, true, 6.0D, TICK);
        for (BlockSystem b : new BlockSystem[] {blocksA, blocksB}) {
            b.addBlock(new BlockSection("b0", SECTION_ID, 0.0D, half));
            b.addBlock(new BlockSection("b1", SECTION_ID, half, total));
        }

        TrainManager trainsA = new TrainManager();
        TrainManager trainsB = new TrainManager();
        trainsA.add(1, new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, 0.0D), 10.0D));
        trainsB.add(1, new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, 0.0D), 10.0D));
        trainsA.add(2, new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, half + 2.0D), 10.0D));
        trainsB.add(2, new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(SECTION_ID, half + 2.0D), 10.0D));

        for (int tick = 0; tick < 1000; tick++) {
            BlockTestSupport.tick(trainsA, networkA, blocksA);
            BlockTestSupport.tick(trainsB, networkB, blocksB);
        }

        for (int id = 1; id <= 2; id++) {
            Train a = trainsA.train(id);
            Train b = trainsB.train(id);
            assertEquals(a.reference().distance(), b.reference().distance(), 0.0D, "distance for train " + id);
            assertEquals(a.velocity(), b.velocity(), 0.0D, "velocity for train " + id);
        }
    }
}
