package com.micatechnologies.minecraft.rcmc.physics;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for a train left referencing track that no longer exists.
 *
 * <p>This crashed a real client. {@code /rcmc clear} removed the track and trains on the server and
 * broadcast the emptied track, but nothing told clients to drop their trains — so every client was
 * left holding a train pointing at a deleted section, and the entity tick threw
 * {@code IllegalArgumentException: No section with id 1} straight out of Minecraft's world update.</p>
 *
 * <p>The general lesson, and the reason these tests exist rather than just the fix: the client's
 * track and train state arrive in separate packets and are applied independently, so they are only
 * <em>eventually</em> consistent. Any code path that assumes a train's section is present is a
 * crash waiting for the right ordering.</p>
 */
class OrphanedTrainTest {

    private static final double TICK = 1.0D / 20.0D;

    private static TrackNetwork networkWithSection() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, Arrays.asList(
            new TrackNode(new Vec3(0, 64, 0)),
            new TrackNode(new Vec3(50, 64, 0)),
            new TrackNode(new Vec3(100, 64, 0))), false, null));
        return n;
    }

    private static Train trainOnSectionOne() {
        return new Train(TrainSpec.singleCar(),
            new PhysicsIntegrator(9.81D, 0.0D, 0.0D, 60.0D), new TrackRef(1, 10.0D), 5.0D);
    }

    @Test
    @DisplayName("hasSection reports presence without throwing")
    void hasSectionIsSafeToAsk() {
        TrackNetwork n = networkWithSection();
        assertTrue(n.hasSection(1));
        assertFalse(n.hasSection(99));

        n.clear();
        assertFalse(n.hasSection(1), "cleared network should report nothing present");
    }

    @Test
    @DisplayName("advance still throws for an unknown section — that is correct on the server")
    void advanceStillThrows() {
        // The guard belongs in callers that can legitimately be out of sync, not in advance().
        // On the server, advancing onto a section that does not exist really is a bug and should
        // be loud rather than silently producing a wrong position.
        assertThrows(IllegalArgumentException.class,
            () -> networkWithSection().advance(new TrackRef(99, 0.0D), 1.0D));
    }

    @Test
    @DisplayName("ticking a train whose section was deleted does not throw")
    void tickingAnOrphanedTrainIsSafe() {
        TrackNetwork network = networkWithSection();
        TrainManager manager = new TrainManager();
        manager.add(1, trainOnSectionOne());

        manager.tick(network, null, 4, TICK);

        // Simulate /rcmc clear arriving as track-then-train, the ordering that crashed the client.
        network.clear();

        assertDoesNotThrow(() -> manager.tick(network, null, 4, TICK),
            "a train on deleted track must not throw out of the tick loop");
    }

    @Test
    @DisplayName("an orphaned train holds position rather than moving to nowhere")
    void orphanedTrainDoesNotDrift() {
        TrackNetwork network = networkWithSection();
        TrainManager manager = new TrainManager();
        Train train = trainOnSectionOne();
        manager.add(1, train);

        network.clear();
        double before = train.reference().distance();
        for (int i = 0; i < 20; i++) {
            manager.tick(network, null, 4, TICK);
        }

        assertEquals(before, train.reference().distance(), 0.0D,
            "an orphaned train should be frozen, not integrating against missing geometry");
    }

    @Test
    @DisplayName("a train recovers when its section comes back")
    void trainResumesWhenTrackReturns() {
        // The client case: track arrives a tick after the train that references it. Skipping must
        // be a pause, not a permanent fault, or a train synced slightly early would never move.
        TrackNetwork network = new TrackNetwork();
        TrainManager manager = new TrainManager();
        Train train = trainOnSectionOne();
        manager.add(1, train);

        manager.tick(network, null, 4, TICK);
        assertTrue(train.isRunning(), "a missing section must not fault the train");

        network.addSection(networkWithSection().section(1));
        for (int i = 0; i < 10; i++) {
            manager.tick(network, null, 4, TICK);
        }
        assertTrue(train.reference().distance() > 10.0D,
            "train should resume once its track is present");
    }
}
