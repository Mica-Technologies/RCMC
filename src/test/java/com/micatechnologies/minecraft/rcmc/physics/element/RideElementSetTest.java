package com.micatechnologies.minecraft.rcmc.physics.element;

import static com.micatechnologies.minecraft.rcmc.physics.element.ElementTestSupport.TICK;
import static com.micatechnologies.minecraft.rcmc.physics.element.ElementTestSupport.flatNetwork;
import static com.micatechnologies.minecraft.rcmc.physics.element.ElementTestSupport.frictionless;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RideElementSetTest {

    @Test
    @DisplayName("a train on track covered by no element feels zero external acceleration")
    void noElementMeansZeroAcceleration() {
        RideElementSet set = new RideElementSet();
        set.add(new BrakeRun(1, 100.0D, 150.0D, 0.0D, 8.0D, BrakeRun.Mode.BLOCK, TICK));
        Train train = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 10.0D), 5.0D);

        assertNull(set.find(train.reference()));
        assertEquals(0.0D, set.forTrain(1, train), 0.0D);
    }

    @Test
    @DisplayName("forTrain delegates to whichever element contains the train's position")
    void delegatesToTheContainingElement() {
        RideElementSet set = new RideElementSet();
        BrakeRun block = new BrakeRun(1, 100.0D, 150.0D, 0.0D, 8.0D, BrakeRun.Mode.BLOCK, TICK);
        set.add(block);
        Train train = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 120.0D), 10.0D);

        assertSame(block, set.find(train.reference()));
        assertEquals(block.accelerationFor(train), set.forTrain(1, train), 0.0D);
    }

    @Test
    @DisplayName("overlapping elements resolve deterministically to the first one added")
    void overlappingElementsFirstMatchWins() {
        BrakeRun outer = new BrakeRun(1, 0.0D, 200.0D, 5.0D, 8.0D, BrakeRun.Mode.TRIM, TICK);
        ChainLift inner = new ChainLift(1, 50.0D, 100.0D, 5.0D, 12.0D, TICK);
        TrackRef ref = new TrackRef(1, 75.0D);

        RideElementSet outerFirst = new RideElementSet();
        outerFirst.add(outer);
        outerFirst.add(inner);
        assertSame(outer, outerFirst.find(ref), "both elements cover this point; the first added must win");

        // Re-adding in the opposite order flips the winner, proving it is genuinely insertion
        // order and not some property of the elements themselves (span size, type, etc).
        RideElementSet innerFirst = new RideElementSet();
        innerFirst.add(inner);
        innerFirst.add(outer);
        assertSame(inner, innerFirst.find(ref));
    }

    @Test
    @DisplayName("elements() reflects insertion order and cannot be mutated by callers")
    void elementsListIsInsertionOrderedAndUnmodifiable() {
        RideElementSet set = new RideElementSet();
        ChainLift a = new ChainLift(1, 0.0D, 50.0D, 5.0D, 12.0D, TICK);
        ChainLift b = new ChainLift(1, 50.0D, 100.0D, 5.0D, 12.0D, TICK);
        set.add(a);
        set.add(b);

        assertEquals(2, set.count());
        assertSame(a, set.elements().get(0));
        assertSame(b, set.elements().get(1));
        assertThrows(UnsupportedOperationException.class, () -> set.elements().add(a));
    }

    @Test
    @DisplayName("remove() drops an element from consideration")
    void removeDropsAnElement() {
        RideElementSet set = new RideElementSet();
        BrakeRun block = new BrakeRun(1, 0.0D, 200.0D, 0.0D, 8.0D, BrakeRun.Mode.BLOCK, TICK);
        set.add(block);
        TrackRef ref = new TrackRef(1, 50.0D);
        assertSame(block, set.find(ref));

        set.remove(block);
        assertNull(set.find(ref));
    }

    @Test
    @DisplayName("a full tick loop through the set is deterministic — identical inputs, identical outputs")
    void tickLoopIsDeterministic() {
        RideElementSet setA = new RideElementSet();
        setA.add(new ChainLift(1, 0.0D, 60.0D, 5.0D, 12.0D, TICK));
        RideElementSet setB = new RideElementSet();
        setB.add(new ChainLift(1, 0.0D, 60.0D, 5.0D, 12.0D, TICK));

        TrackNetwork flat = flatNetwork(200.0D);
        Train trainA = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 0.0D), 1.0D);
        Train trainB = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 0.0D), 1.0D);

        for (int tick = 0; tick < 100; tick++) {
            trainA.tick(flat, setA.forTrain(1, trainA), 4, TICK);
            trainB.tick(flat, setB.forTrain(1, trainB), 4, TICK);
        }
        assertEquals(trainA.reference().distance(), trainB.reference().distance(), 0.0D);
        assertEquals(trainA.velocity(), trainB.velocity(), 0.0D);
    }
}
