package com.micatechnologies.minecraft.rcmc.client.render.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.TrackStyleIds;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Style-driven meshing — buildable on a bare JVM because {@code TrackMeshBuilder} is pure by
 * design. A straight section along +X at y=64 makes the mesh bounds directly interpretable:
 * width is the z extent, height above rail is {@code maxY - 64}.
 */
class TrackStyleMeshTest {

    private static final double TRACK_Y = 64.0D;

    private static TrackSection straight(String styleId) {
        return new TrackSection(1, Arrays.asList(
            new TrackNode(new Vec3(0, TRACK_Y, 0)),
            new TrackNode(new Vec3(50, TRACK_Y, 0)),
            new TrackNode(new Vec3(100, TRACK_Y, 0))), false, styleId);
    }

    @Test
    @DisplayName("transit track is visually wider than coaster track")
    void transitTrackIsWider() {
        TrackMesh coaster = TrackMeshBuilder.build(straight(null));
        TrackMesh transit = TrackMeshBuilder.build(straight(TrackStyleIds.TRANSIT));
        double coasterWidth = coaster.maxZ - coaster.minZ;
        double transitWidth = transit.maxZ - transit.minZ;
        assertTrue(transitWidth > coasterWidth + 0.4D,
            "expected transit visibly wider: coaster " + coasterWidth + ", transit " + transitWidth);
    }

    @Test
    @DisplayName("unelectrified track builds nothing overhead")
    void unelectrifiedHasNothingOverhead() {
        TrackMesh transit = TrackMeshBuilder.build(straight(TrackStyleIds.TRANSIT));
        assertTrue(transit.maxY < TRACK_Y + 1.0D,
            "expected nothing overhead, mesh reaches " + (transit.maxY - TRACK_Y));
    }

    @Test
    @DisplayName("catenary poles raise the mesh to mast height, portals likewise with more steel")
    void catenaryReachesMastHeight() {
        // The contact wire runs 6 blocks up with masts above it — the project owner's world
        // scale; see TrackMeshBuilder's catenary constants.
        TrackMesh poles = TrackMeshBuilder.build(straight(TrackStyleIds.TRANSIT_CATENARY));
        assertTrue(poles.maxY > TRACK_Y + 7.0D,
            "expected masts and wires overhead, mesh reaches " + (poles.maxY - TRACK_Y));

        TrackMesh portals = TrackMeshBuilder.build(straight(TrackStyleIds.TRANSIT_PORTAL));
        assertTrue(portals.maxY > TRACK_Y + 7.0D);
        assertTrue(portals.quads.size() > TrackMeshBuilder.build(
                straight(TrackStyleIds.TRANSIT)).quads.size(),
            "electrification must add geometry");
    }

    @Test
    @DisplayName("the tunnel style builds a low rigid bar and no masts")
    void tunnelStyleBuildsARigidBar() {
        TrackMesh tunnel = TrackMeshBuilder.build(straight(TrackStyleIds.TRANSIT_TUNNEL));
        double overhead = tunnel.maxY - TRACK_Y;
        assertTrue(overhead > 4.0D, "expected an overhead bar clear of the car roof, mesh reaches " + overhead);
        assertTrue(overhead < 6.0D, "a tunnel bar must stay below mast height, reached " + overhead);
    }

    @Test
    @DisplayName("an unknown style id degrades to the coaster look instead of failing the render")
    void unknownStyleDegradesToCoaster() {
        TrackMesh unknown = TrackMeshBuilder.build(straight("from-a-newer-build"));
        TrackMesh coaster = TrackMeshBuilder.build(straight(null));
        assertEquals(coaster.maxZ - coaster.minZ, unknown.maxZ - unknown.minZ, 1e-9D);
        assertEquals(coaster.quads.size(), unknown.quads.size());
    }

    @Test
    @DisplayName("style ids resolve case-insensitively, coaster resolves to the null default")
    void styleIdResolution() {
        assertNull(TrackStyleIds.resolve("coaster"));
        assertNull(TrackStyleIds.resolve(null));
        assertEquals(TrackStyleIds.TRANSIT_CATENARY, TrackStyleIds.resolve("Transit-Catenary"));
        assertThrows(IllegalArgumentException.class, () -> TrackStyleIds.resolve("monorail"));
    }
}
