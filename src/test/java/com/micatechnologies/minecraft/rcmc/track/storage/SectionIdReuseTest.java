package com.micatechnologies.minecraft.rcmc.track.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A section id, once used, is never handed out again.
 *
 * <p>Found in review: {@code /rcmc clear} reset the counter to 1. Blocks in the world name track by
 * section id — an operator panel links to its ride that way — so the next coaster built after a
 * clear silently took over the old one's panel. The counter was also not saved, so deleting the
 * newest section and reloading reused its id the same way.</p>
 */
class SectionIdReuseTest {

    private static TrackSection section(int id) {
        return new TrackSection(id, Arrays.asList(new TrackNode(new Vec3(0, 64, id * 10)),
            new TrackNode(new Vec3(30, 64, id * 10))), false, null);
    }

    private static TrackNetwork threeSections() {
        TrackNetwork network = new TrackNetwork();
        for (int i = 0; i < 3; i++) {
            network.addSection(section(network.allocateSectionId()));
        }
        return network;
    }

    @Test
    @DisplayName("clearing the park does not recycle its section ids")
    void clearKeepsIds() {
        TrackNetwork network = threeSections();
        network.clear();
        assertEquals(4, network.allocateSectionId());
    }

    @Test
    @DisplayName("the counter survives a save, even when it is ahead of every section")
    void counterIsSaved() {
        TrackNetwork network = threeSections();
        network.removeSection(3);
        TrackNetwork loaded = TrackCodec.readNetwork(TrackCodec.writeNetwork(network));
        assertEquals(4, loaded.allocateSectionId());

        network.clear();
        loaded = TrackCodec.readNetwork(TrackCodec.writeNetwork(network));
        assertEquals(4, loaded.allocateSectionId());
    }

    @Test
    @DisplayName("a save written before the counter was stored still loads, one past its highest id")
    void olderSaveWithoutCounter() {
        TrackNetwork network = threeSections();
        net.minecraft.nbt.NBTTagCompound saved = TrackCodec.writeNetwork(network);
        saved.removeTag("NextSectionId");
        assertTrue(TrackCodec.readNetwork(saved).allocateSectionId() >= 4);
    }
}
