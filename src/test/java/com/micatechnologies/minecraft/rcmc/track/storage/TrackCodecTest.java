package com.micatechnologies.minecraft.rcmc.track.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for the NBT codec.
 *
 * <p>These touch Minecraft's NBT classes, which are plain data containers needing no game
 * bootstrap — so they run on a bare JVM like the rest of the suite. They are worth having despite
 * bending the "no Minecraft types in tests" habit: a codec bug does not throw, it silently
 * corrupts a player's park and is only discovered after the save has already been overwritten.</p>
 */
class TrackCodecTest {

    private static TrackNode node(double x, double y, double z, double bank, String style) {
        return new TrackNode(new Vec3(x, y, z), bank, style);
    }

    private static TrackNetwork sampleNetwork() {
        TrackNetwork n = new TrackNetwork();

        n.addSection(new TrackSection(1, Arrays.asList(
            node(0.5D, 64.25D, 0.75D, 0.0D, null),
            node(40.0D, 70.0D, 0.0D, 22.5D, "wooden"),
            node(80.0D, 64.0D, 10.0D, -15.0D, null)), false, "steel"));

        List<TrackNode> ring = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double a = 2.0D * Math.PI * i / 6;
            ring.add(node(Math.cos(a) * 30.0D, 64.0D, Math.sin(a) * 30.0D, i * 5.0D, null));
        }
        n.addSection(new TrackSection(2, ring, true, null));

        // Starts exactly where section 1 ends — connect() rejects gapped joins.
        n.addSection(new TrackSection(3, Arrays.asList(
            node(80.0D, 64.0D, 10.0D, 0, null), node(150.0D, 64.0D, 10.0D, 0, null)), false, null));

        n.connect(new TrackNetwork.SectionEnd(1, TrackNetwork.End.END),
                  new TrackNetwork.SectionEnd(3, TrackNetwork.End.START));
        return n;
    }

    @Test
    @DisplayName("a network survives a write/read round trip unchanged")
    void networkRoundTrips() {
        TrackNetwork original = sampleNetwork();
        TrackNetwork restored = TrackCodec.readNetwork(TrackCodec.writeNetwork(original));

        assertEquals(original.sectionCount(), restored.sectionCount());

        for (TrackSection before : original.sections()) {
            TrackSection after = restored.section(before.id());
            assertNotNull(after, "section " + before.id() + " went missing");
            assertEquals(before.isClosed(), after.isClosed(), "closed flag");
            assertEquals(before.styleId(), after.styleId(), "section style");
            assertEquals(before.nodes(), after.nodes(), "nodes of section " + before.id());
            // Geometry is derived, so equal nodes must reproduce equal length exactly.
            assertEquals(before.totalLength(), after.totalLength(), 1e-9, "length");
        }
    }

    @Test
    @DisplayName("joins survive, in both directions, and are written only once")
    void joinsRoundTrip() {
        TrackNetwork restored = TrackCodec.readNetwork(TrackCodec.writeNetwork(sampleNetwork()));

        TrackNetwork.SectionEnd endOfOne = new TrackNetwork.SectionEnd(1, TrackNetwork.End.END);
        TrackNetwork.SectionEnd startOfThree = new TrackNetwork.SectionEnd(3, TrackNetwork.End.START);

        assertEquals(startOfThree, restored.joinedTo(endOfOne));
        assertEquals(endOfOne, restored.joinedTo(startOfThree), "join lost its symmetry");
        // Two map entries for one logical join.
        assertEquals(2, restored.joins().size());
    }

    @Test
    @DisplayName("node positions keep full double precision")
    void positionsKeepDoublePrecision() {
        // Floats would round these, and since node positions accumulate into arc length over
        // thousands of samples, a circuit's length would shift on every reload.
        double x = 12.345678901234567D;
        double y = 64.98765432109876D;
        double z = -87.65432109876543D;
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, Arrays.asList(
            node(x, y, z, 33.333333333333336D, null),
            node(x + 40.0D, y, z, 0, null),
            node(x + 80.0D, y, z, 0, null)), false, null));

        TrackNode restored = TrackCodec.readNetwork(TrackCodec.writeNetwork(n))
            .section(1).nodes().get(0);

        assertEquals(x, restored.position().x, 0.0D, "x lost precision");
        assertEquals(y, restored.position().y, 0.0D, "y lost precision");
        assertEquals(z, restored.position().z, 0.0D, "z lost precision");
        assertEquals(33.333333333333336D, restored.bankDegrees(), 0.0D, "bank lost precision");
    }

    @Test
    @DisplayName("the payload records its format version")
    void payloadIsVersioned() {
        NBTTagCompound tag = TrackCodec.writeNetwork(sampleNetwork());
        assertEquals(TrackCodec.DATA_VERSION, tag.getInteger("DataVersion"));
    }

    @Test
    @DisplayName("data from a newer format version is refused, not silently dropped")
    void refusesNewerVersions() {
        // Loading it as "empty" would present to the player as their park vanishing — with the
        // save already overwritten by the time they noticed.
        NBTTagCompound tag = TrackCodec.writeNetwork(sampleNetwork());
        tag.setInteger("DataVersion", TrackCodec.DATA_VERSION + 1);

        IllegalStateException thrown =
            assertThrows(IllegalStateException.class, () -> TrackCodec.readNetwork(tag));
        assertTrue(thrown.getMessage().contains("version"), thrown.getMessage());
    }

    @Test
    @DisplayName("absent or empty data reads as an empty network, not a crash")
    void emptyDataIsHandled() {
        assertTrue(TrackCodec.readNetwork(null).isEmpty());
        assertTrue(TrackCodec.readNetwork(new NBTTagCompound()).isEmpty());
    }

    @Test
    @DisplayName("a join referencing a missing section is skipped, keeping the rest of the park")
    void danglingJoinsAreSkipped() {
        TrackNetwork original = sampleNetwork();
        NBTTagCompound tag = TrackCodec.writeNetwork(original);
        // Simulate a section failing to load: drop it, leaving its join dangling.
        tag.getTagList("Sections", 10).removeTag(2);

        TrackNetwork restored = TrackCodec.readNetwork(tag);
        assertEquals(2, restored.sectionCount());
        assertTrue(restored.joins().isEmpty(), "dangling join should have been skipped");
        assertNotNull(restored.section(1), "surviving sections must still load");
    }
}
