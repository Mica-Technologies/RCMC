package com.micatechnologies.minecraft.rcmc.client.render.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exercises the pure-geometry parts of {@link TrackMeshBuilder} on a bare JVM — no
 * {@code World}, no rendering, no game instance. This is the whole reason
 * {@link TrackMeshBuilder} is kept free of Minecraft types (see its class javadoc): every
 * assertion here would otherwise need a running client.
 */
class TrackMeshBuilderTest {

    private static TrackNode node(double x, double y, double z) {
        return new TrackNode(new Vec3(x, y, z));
    }

    private static TrackNode node(double x, double y, double z, double bankDegrees) {
        return new TrackNode(new Vec3(x, y, z), bankDegrees, null);
    }

    private static TrackSection straight(double length) {
        return new TrackSection(1, java.util.Arrays.asList(
            node(0, 64, 0), node(length / 3, 64, 0), node(2 * length / 3, 64, 0), node(length, 64, 0)),
            false, null);
    }

    /** A tight, roughly circular open arc: high curvature packed into a short run. */
    private static TrackSection tightArc(double radius, int nodeCount) {
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            double a = Math.PI * i / (nodeCount - 1); // half circle
            nodes.add(node(Math.cos(a) * radius, 64.0D, Math.sin(a) * radius));
        }
        return new TrackSection(2, nodes, false, null);
    }

    // ---- adaptive tessellation ----

    @Test
    @DisplayName("ring spacing adapts: a tight curve gets far more rings per block than a straight run")
    void tessellationIsCurvatureAdaptive() {
        TrackSection straight = straight(60.0D);
        TrackSection tight = tightArc(3.0D, 12);

        double[] straightRings = TrackMeshBuilder.ringDistances(straight, straight.totalLength());
        double[] tightRings = TrackMeshBuilder.ringDistances(tight, tight.totalLength());

        double straightDensity = straightRings.length / straight.totalLength();
        double tightDensity = tightRings.length / tight.totalLength();

        assertTrue(tightDensity > straightDensity * 3.0D,
            "expected the tight curve to need far denser rings per block; straight="
                + straightDensity + " tight=" + tightDensity);
    }

    @Test
    @DisplayName("ring samples always include both endpoints")
    void ringsSpanTheWholeSection() {
        TrackSection section = straight(40.0D);
        double[] rings = TrackMeshBuilder.ringDistances(section, section.totalLength());

        assertEquals(0.0D, rings[0], 1e-9);
        assertEquals(section.totalLength(), rings[rings.length - 1], 1e-6);
        // Monotonically increasing — a ring list that went backwards would sweep a self-
        // intersecting tube.
        for (int i = 1; i < rings.length; i++) {
            assertTrue(rings[i] > rings[i - 1], "ring distances must strictly increase");
        }
    }

    @Test
    @DisplayName("a degenerately short section still tessellates without looping or throwing")
    void shortSectionDoesNotHang() {
        TrackSection section = straight(0.05D);
        double[] rings = TrackMeshBuilder.ringDistances(section, section.totalLength());
        assertTrue(rings.length >= 2);
        assertEquals(section.totalLength(), rings[rings.length - 1], 1e-6);
    }

    // ---- cross-section / banking ----

    @Test
    @DisplayName("an unbanked frame's two rails sit at the same height")
    void unbankedRailsAreLevel() {
        TrackFrame level = new TrackFrame(new Vec3(0, 10, 0), new Vec3(0, 0, 1), Vec3.UP);
        Vec3 left = TrackMeshBuilder.worldPoint(level, -0.55D, 0.0D);
        Vec3 right = TrackMeshBuilder.worldPoint(level, 0.55D, 0.0D);
        assertEquals(left.y, right.y, 1e-9);
    }

    @Test
    @DisplayName("banking a frame raises the outer rail relative to the inner one — the whole point of sweeping through right/up")
    void bankedFrameTiltsTheRails() {
        TrackFrame level = new TrackFrame(new Vec3(0, 10, 0), new Vec3(0, 0, 1), Vec3.UP);
        TrackFrame banked = level.withBank(Math.toRadians(30.0D));

        Vec3 left = TrackMeshBuilder.worldPoint(banked, -0.55D, 0.0D);
        Vec3 right = TrackMeshBuilder.worldPoint(banked, 0.55D, 0.0D);

        // Don't hardcode which side rises — that depends on the rotation sign convention in
        // TrackFrame.withBank (Rodrigues about +forward), which is that class's contract to
        // define, not this one's to assume. What TrackMeshBuilder must get right is simply that
        // sweeping through the frame's rotated right/up actually moves the two rails to different
        // heights, matching whatever the banked frame's own `right` vector says.
        double expectedRightY = banked.position.y + banked.right.y * 0.55D;
        assertEquals(expectedRightY, right.y, 1e-9);
        assertTrue(Math.abs(right.y - left.y) > 0.3D,
            "banking must actually change rail height, or it is invisible; left.y=" + left.y
                + " right.y=" + right.y);
    }

    @Test
    @DisplayName("static face shade is brighter facing up than facing down, like vanilla's per-face block shading")
    void shadeIsDirectional() {
        float top = TrackMeshBuilder.shadeFor(Vec3.UP);
        float bottom = TrackMeshBuilder.shadeFor(new Vec3(0, -1, 0));
        float side = TrackMeshBuilder.shadeFor(new Vec3(1, 0, 0));
        assertTrue(top > side);
        assertTrue(side >= bottom);
    }

    // ---- full build() ----

    @Test
    @DisplayName("build() produces geometry for an ordinary open section, all within its own bounds")
    void buildProducesBoundedGeometry() {
        TrackSection section = straight(30.0D);
        TrackMesh mesh = TrackMeshBuilder.build(section);

        assertFalse(mesh.quads.isEmpty());
        for (MeshQuad q : mesh.quads) {
            for (Vec3 v : new Vec3[] {q.a, q.b, q.c, q.d}) {
                assertTrue(v.x >= mesh.minX - 1e-6 && v.x <= mesh.maxX + 1e-6);
                assertTrue(v.y >= mesh.minY - 1e-6 && v.y <= mesh.maxY + 1e-6);
                assertTrue(v.z >= mesh.minZ - 1e-6 && v.z <= mesh.maxZ + 1e-6);
            }
        }
    }

    @Test
    @DisplayName("build() handles a closed circuit without throwing and without an empty mesh")
    void buildHandlesClosedCircuit() {
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double a = 2.0D * Math.PI * i / 8;
            nodes.add(node(Math.cos(a) * 20.0D, 64.0D, Math.sin(a) * 20.0D));
        }
        TrackSection ring = new TrackSection(3, nodes, true, null);
        TrackMesh mesh = TrackMeshBuilder.build(ring);
        assertFalse(mesh.quads.isEmpty());
    }

    @Test
    @DisplayName("cross-ties land at the expected count for the section's length and fixed spacing")
    void tieCountMatchesArcLengthSpacing() {
        TrackSection section = straight(30.0D); // TIE_SPACING = 1.5 -> expect 21 ties (open: +1)
        TrackMesh mesh = TrackMeshBuilder.build(section);

        double[] rings = TrackMeshBuilder.ringDistances(section, section.totalLength());
        int tubeQuadsPerProfile = 4 * (rings.length - 1) + 2; // + end caps (open section)
        int tubeQuads = tubeQuadsPerProfile * 3; // left rail, right rail, spine

        int tieQuads = mesh.quads.size() - tubeQuads;
        assertEquals(0, tieQuads % 6, "each tie contributes exactly 6 quads (4 sides + 2 caps)");
        int tieCount = tieQuads / 6;

        int expected = (int) Math.floor(section.totalLength() / 1.5D) + 1;
        assertEquals(expected, tieCount);
    }

    @Test
    @DisplayName("banking shows up end-to-end in a built mesh, not just in the frame math")
    void bankedSectionProducesAsymmetricRailHeights() {
        TrackSection banked = new TrackSection(4, java.util.Arrays.asList(
            node(0, 64, 0, 0.0D),
            node(10, 64, 10, 45.0D),
            node(20, 64, 20, 45.0D),
            node(30, 64, 30, 0.0D)),
            false, null);

        TrackMesh mesh = TrackMeshBuilder.build(banked);
        // Somewhere in the middle of the curve, the frame is banked; the mesh must contain
        // vertices whose height varies well beyond the flat, unbanked rail thickness, which is
        // only 0.1 blocks (RAIL_HALF_HEIGHT * 2) — a much larger spread proves the cross-section
        // rode the frame's tilted `up`/`right`, not a fixed world-vertical rail.
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (MeshQuad q : mesh.quads) {
            for (Vec3 v : new Vec3[] {q.a, q.b, q.c, q.d}) {
                minY = Math.min(minY, v.y);
                maxY = Math.max(maxY, v.y);
            }
        }
        assertTrue(maxY - minY > 0.3D,
            "banked section should show real height spread across the cross-section; got " + (maxY - minY));
    }
}
