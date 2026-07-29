package com.micatechnologies.minecraft.rcmc.track.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the split between what a save holds and what the undo history holds.
 *
 * <p>These are the same serialisation for everything a builder authored, and deliberately not the
 * same for trains. Getting that backwards has a specific, nasty symptom: undoing a track edit would
 * also drag every running train back to wherever it stood several edits ago, teleporting riders
 * along the track as a side effect of an unrelated undo.</p>
 */
class RcmcTrackDataTest {

    private static RcmcTrackData sample() {
        RcmcTrackData data = new RcmcTrackData();
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i <= 4; i++) {
            nodes.add(new TrackNode(new Vec3(i * 20.0D, 64.0D, 0.0D)));
        }
        data.network().addSection(new TrackSection(1, nodes, false, null));
        data.trains().add(1, new Train(TrainSpec.singleCar(),
            new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, 42.0D), 7.0D));
        return data;
    }

    @Test
    @DisplayName("a save holds the trains")
    void saveHoldsTrains() {
        NBTTagCompound saved = sample().writeToNBT(new NBTTagCompound());

        Train train = TrainCodec.read(saved,
            new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D)).train(1);
        assertNotNull(train, "the train is missing from the save");
        assertEquals(42.0D, train.reference().distance(), 1e-9);
        assertEquals(7.0D, train.velocity(), 1e-9);
    }

    @Test
    @DisplayName("an undo snapshot does NOT hold the trains")
    void snapshotOmitsTrains() {
        // The contract RcmcWorldState.undo documents: "affects only the authored state — never
        // running trains". Enforced structurally here rather than remembered at each call site.
        NBTTagCompound snapshot = sample().snapshot();

        assertTrue(TrainCodec.read(snapshot,
            new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D)).isEmpty(),
            "undo would restore trains, dragging a running train backwards along the track");
    }

    @Test
    @DisplayName("an undo snapshot still holds everything a builder authored")
    void snapshotKeepsAuthoredState() {
        // The other half of the split, and the reason snapshot() shares its serialisation with the
        // save rather than being written separately: a snapshot must never capture LESS authored
        // state than a save does, or an undo would quietly delete work.
        NBTTagCompound snapshot = sample().snapshot();
        NBTTagCompound saved = sample().writeToNBT(new NBTTagCompound());

        assertFalse(TrackCodec.readNetwork(snapshot).isEmpty(), "the track is missing from a snapshot");
        assertEquals(TrackCodec.readNetwork(saved).sections().size(),
            TrackCodec.readNetwork(snapshot).sections().size(),
            "a snapshot and a save disagree about the authored track");
    }

    @Test
    @DisplayName("a full save and load cycle brings the train back where it was")
    void roundTripThroughSavedData() {
        NBTTagCompound saved = sample().writeToNBT(new NBTTagCompound());

        RcmcTrackData reloaded = new RcmcTrackData();
        reloaded.readFromNBT(saved);

        assertEquals(1, reloaded.network().sections().size());
        Train train = reloaded.trains().train(1);
        assertNotNull(train, "the train did not survive a save and load");
        assertEquals(42.0D, train.reference().distance(), 1e-9);
        // The services tag is handed on for the world state to resume once the network exists;
        // readFromNBT cannot do it itself, because the network is still being parsed in that call.
        assertNotNull(reloaded.takePendingServices(), "services were not held for the resume");
        assertEquals(null, reloaded.takePendingServices(),
            "taking the pending services twice would resume every service a second time");
    }
}
