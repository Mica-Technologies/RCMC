package com.micatechnologies.minecraft.rcmc.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The cached boxes that support collision rejects queries against. They are read on every entity
 * move and rebuilt only when track changes, so the property that matters is that they follow the
 * track: a box that went stale after an edit would let a player walk through a new support.
 */
class TrackExtentTest {

    private static TrackNode node(double x, double y, double z) {
        return new TrackNode(new Vec3(x, y, z));
    }

    private static TrackSection straight(int id, double x0, double length, double y) {
        return new TrackSection(id, Arrays.asList(
            node(x0, y, 0),
            node(x0 + length / 2.0D, y + 5, 3),
            node(x0 + length, y, -2)), false, null);
    }

    @Test
    @DisplayName("a section's extent bounds every node")
    void sectionExtentBoundsNodes() {
        TrackSection.Extent e = straight(1, 10, 20, 64).extent();
        assertEquals(10.0D, e.minX, 1e-9);
        assertEquals(30.0D, e.maxX, 1e-9);
        assertEquals(64.0D, e.minY, 1e-9);
        assertEquals(69.0D, e.maxY, 1e-9);
        assertEquals(-2.0D, e.minZ, 1e-9);
        assertEquals(3.0D, e.maxZ, 1e-9);
    }

    @Test
    @DisplayName("near() rejects a query far away and accepts one under the track within the margin")
    void nearRespectsSlackAndMargin() {
        TrackSection.Extent e = straight(1, 0, 20, 100).extent();
        // A player standing at ground level 60 blocks under the track, where a column is.
        assertTrue(e.near(9.5, 40, -0.5, 10.5, 41.8, 0.5, 4, 256));
        // Same height, 50 blocks to the side: nothing of this section can be there.
        assertFalse(e.near(9.5, 40, 49.5, 10.5, 41.8, 50.5, 4, 256));
        // Without the downward margin the column base is out of reach — the margin is load-bearing.
        assertFalse(e.near(9.5, 40, -0.5, 10.5, 41.8, 0.5, 4, 0));
        // Just inside the slack past the last node.
        assertTrue(e.near(23.5, 100, 0, 24, 101, 1, 4, 0));
        assertFalse(e.near(24.5, 100, 0, 25, 101, 1, 4, 0));
    }

    @Test
    @DisplayName("the network extent follows sections being added, replaced, removed and cleared")
    void networkExtentFollowsEdits() {
        TrackNetwork n = new TrackNetwork();
        assertNull(n.extent());

        n.addSection(straight(1, 0, 20, 64));
        assertEquals(20.0D, n.extent().maxX, 1e-9);

        n.addSection(straight(2, 100, 20, 64));
        assertEquals(120.0D, n.extent().maxX, 1e-9);

        n.replaceSection(straight(2, 200, 20, 64));
        assertEquals(220.0D, n.extent().maxX, 1e-9);
        assertEquals(0.0D, n.extent().minX, 1e-9);

        n.removeSection(1);
        assertEquals(200.0D, n.extent().minX, 1e-9);

        n.clear();
        assertNull(n.extent());
    }
}
