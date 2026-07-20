package com.micatechnologies.minecraft.rcmc.client.render.track;

import java.util.Collections;
import java.util.List;

/**
 * Immutable, cacheable output of {@link TrackMeshBuilder#build}: every quad needed to draw one
 * {@code TrackSection}, plus its world-space bounding box for frustum/distance culling.
 *
 * <p>Pure data — no Minecraft types, no GPU handles — deliberately, so building one is unit
 * testable and so {@link TrackRenderer} can decide for itself how to get it onto the GPU. See
 * {@link TrackRenderer}'s javadoc for what it actually does with this (replaying it through a
 * {@code BufferBuilder} every frame, and why not a persistent VBO yet).</p>
 */
public final class TrackMesh {

    public final List<MeshQuad> quads;

    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    TrackMesh(List<MeshQuad> quads, double minX, double minY, double minZ,
              double maxX, double maxY, double maxZ) {
        this.quads = Collections.unmodifiableList(quads);
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }
}
