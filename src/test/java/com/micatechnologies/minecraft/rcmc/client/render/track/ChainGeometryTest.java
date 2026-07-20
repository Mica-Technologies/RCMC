package com.micatechnologies.minecraft.rcmc.client.render.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.ElementSpan;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards against the lift chain rendering as visual noise.
 *
 * <p>Written after the chain shipped visibly shimmering. Two causes, and this file pins both:
 * every link was emitted as a flat plate <em>plus</em> a reversed copy at identical coordinates
 * (two coplanar quads fighting for the same pixels), and the chain sat at a height that had just
 * been swallowed by the ties when those were extended to close the rail-to-spine gap.</p>
 *
 * <p>The second cause is the more instructive one: the chain was fine until an unrelated fix moved
 * something else into its space. Geometry constants interact, and nothing was checking that they
 * still cleared each other.</p>
 */
class ChainGeometryTest {

    /** A straight climbing section, the shape a lift hill actually is. */
    private static TrackSection liftHill() {
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i <= 6; i++) {
            nodes.add(new TrackNode(new Vec3(i * 10.0D, 64.0D + i * 4.0D, 0.0D)));
        }
        return new TrackSection(1, nodes, false, null);
    }

    private static List<ElementSpan> liftSpan(TrackSection section) {
        return Arrays.asList(new ElementSpan("chain_lift", section.id(), 0.0D, section.totalLength()));
    }

    /** A quad's four corners as an order-independent key, for spotting exact duplicates. */
    private static String keyOf(MeshQuad q) {
        List<String> corners = new ArrayList<>();
        for (Vec3 v : Arrays.asList(q.a, q.b, q.c, q.d)) {
            corners.add(String.format("%.5f/%.5f/%.5f", v.x, v.y, v.z));
        }
        java.util.Collections.sort(corners);
        return String.join("|", corners);
    }

    @Test
    @DisplayName("a lift span actually emits chain geometry")
    void liftEmitsChain() {
        TrackSection section = liftHill();
        int plain = TrackMeshBuilder.build(section).quads.size();
        int withChain = TrackMeshBuilder.build(section, liftSpan(section)).quads.size();
        assertTrue(withChain > plain,
            "a chain lift should add geometry; got " + plain + " -> " + withChain);
    }

    @Test
    @DisplayName("no two quads occupy exactly the same four corners")
    void noCoincidentQuads() {
        // The direct cause of the shimmer: a front face and a reversed backface at identical
        // coordinates have no depth ordering between them, so which one wins varies per pixel and
        // per frame.
        TrackSection section = liftHill();
        TrackMesh mesh = TrackMeshBuilder.build(section, liftSpan(section));

        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (MeshQuad quad : mesh.quads) {
            String key = keyOf(quad);
            if (!seen.add(key)) {
                duplicates.add(key);
            }
        }
        assertEquals(0, duplicates.size(),
            duplicates.size() + " coincident quad(s) — these will z-fight. First: "
                + (duplicates.isEmpty() ? "" : duplicates.get(0)));
    }

    @Test
    @DisplayName("chain links clear the ties rather than sitting inside them")
    void chainClearsTheTies() {
        // The subtler cause of the shimmer, and the one worth pinning: the chain was fine until an
        // unrelated fix extended the ties upward into the space it occupied.
        //
        // Chain quads are identified by SET DIFFERENCE against the same section built without a
        // lift, not by colour. Colour looks like the obvious discriminator and is not one: faces
        // are shaded by orientation before being stored, so a shaded spine underside comes out
        // darker than the chain's nominal colour. An earlier version of this test filtered on
        // "dark" and was quietly measuring the spine.
        //
        // Level track, so the frame's up axis and world up coincide and the measurement is exact.
        TrackSection level = levelStraight();
        Set<String> plain = new HashSet<>();
        for (MeshQuad quad : TrackMeshBuilder.build(level).quads) {
            plain.add(keyOf(quad));
        }

        double tieTop = 64.0D - 0.23D;   // SPINE_CENTER_U + SPINE_HALF_HEIGHT, in world terms
        int chainVertices = 0;
        for (MeshQuad quad : TrackMeshBuilder.build(level, liftSpan(level)).quads) {
            if (plain.contains(keyOf(quad))) {
                continue;
            }
            for (Vec3 v : Arrays.asList(quad.a, quad.b, quad.c, quad.d)) {
                chainVertices++;
                assertTrue(v.y > tieTop,
                    "chain vertex at y=" + String.format("%.3f", v.y)
                        + " is at or below the tie top at " + String.format("%.3f", tieTop)
                        + " — it will fight the webbing");
            }
        }
        assertTrue(chainVertices > 0, "no chain geometry was added at all");
    }

    /** Level straight track, so the frame's up axis coincides with world up. */
    private static TrackSection levelStraight() {
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i <= 6; i++) {
            nodes.add(new TrackNode(new Vec3(i * 10.0D, 64.0D, 0.0D)));
        }
        return new TrackSection(1, nodes, false, null);
    }

    @Test
    @DisplayName("a section with no lift gets no chain")
    void noLiftNoChain() {
        TrackSection section = liftHill();
        List<ElementSpan> brakes =
            Arrays.asList(new ElementSpan("brake", section.id(), 0.0D, section.totalLength()));
        assertEquals(TrackMeshBuilder.build(section).quads.size(),
            TrackMeshBuilder.build(section, brakes).quads.size(),
            "only a chain lift should add chain geometry");
    }
}
