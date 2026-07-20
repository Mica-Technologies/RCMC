package com.micatechnologies.minecraft.rcmc.client.render.track;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;

/**
 * One flat-shaded quad's four corners plus its material color, as emitted by
 * {@link TrackMeshBuilder}.
 *
 * <p>Deliberately holds nothing but {@link Vec3} and primitives — no Minecraft types — so mesh
 * generation is unit-testable on a bare JVM, the same reason {@code track.math} stays pure.
 * {@link TrackRenderer} is the only place a quad ever meets a {@code BufferBuilder}; it also
 * multiplies in a world-light factor sampled once when the section's mesh is (re)built (see that
 * class's javadoc for exactly what that approximates and misses).</p>
 *
 * <p>Corners are listed in a consistent winding order but nothing downstream relies on which way
 * that winding faces — face culling is never enabled for track geometry, so a reversed winding
 * would waste a draw, not open a hole.</p>
 */
public final class MeshQuad {

    public final Vec3 a;
    public final Vec3 b;
    public final Vec3 c;
    public final Vec3 d;

    /** Material color, already multiplied by the fixed per-face directional shade computed in
     *  {@link TrackMeshBuilder}. World light is layered on top of this by {@link TrackRenderer}. */
    public final float red;
    public final float green;
    public final float blue;

    public MeshQuad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float red, float green, float blue) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.red = red;
        this.green = green;
        this.blue = blue;
    }
}
