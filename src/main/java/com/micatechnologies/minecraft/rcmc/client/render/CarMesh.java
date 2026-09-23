package com.micatechnologies.minecraft.rcmc.client.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.renderer.BufferBuilder;

/**
 * Flat-shaded geometry for a car, built from shaped primitives in the car's own frame.
 *
 * <p>Boxes alone made the old coaster car: legible, but a stack of bricks. A real car is sculpted —
 * a flared tub, a nose that narrows and drops, round wheels, bent restraint bars — so this builds
 * prisms swept from a cross-section, lofts between two sections, cylinders and bars as well. Every
 * face is shaded by its own outward normal, the way the track is, so a shape reads as a shape under
 * flat colour and no texture.</p>
 *
 * <p>Built first, emitted after: the geometry is plain data until {@link #emit}, which is what lets
 * the tests check where the seats are and that nothing cuts through the rails without a game.</p>
 */
final class CarMesh {

    /** One quad: four corners and a colour already shaded for the face's facing. */
    static final class Quad {
        final float[] xyz;
        final float r;
        final float g;
        final float b;
        /** The unshaded colour it was given — what a test asks "is this a seat?" of. */
        final float[] colour;

        Quad(float[] xyz, float[] colour, float shade) {
            this.xyz = xyz;
            this.colour = colour;
            this.r = colour[0] * shade;
            this.g = colour[1] * shade;
            this.b = colour[2] * shade;
        }

        float x(int corner) {
            return xyz[corner * 3];
        }

        float y(int corner) {
            return xyz[corner * 3 + 1];
        }

        float z(int corner) {
            return xyz[corner * 3 + 2];
        }
    }

    private final List<Quad> quads = new ArrayList<>();

    List<Quad> quads() {
        return Collections.unmodifiableList(quads);
    }

    void emit(BufferBuilder buffer) {
        for (Quad q : quads) {
            for (int c = 0; c < 4; c++) {
                buffer.pos(q.x(c), q.y(c), q.z(c)).color(q.r, q.g, q.b, 1.0F).endVertex();
            }
        }
    }

    // --- Primitives. -----------------------------------------------------------------------------

    /** An axis-aligned box between two corners. */
    void box(float x1, float y1, float z1, float x2, float y2, float z2, float[] colour) {
        float[][] section = {{x1, y1}, {x2, y1}, {x2, y2}, {x1, y2}};
        prismZ(section, Math.min(z1, z2), Math.max(z1, z2), colour, true, true);
    }

    /**
     * A cross-section in X-Y, swept along Z from {@code z0} to {@code z1}. The section is a closed
     * outline; it need not be convex, but it should not cross itself.
     */
    void prismZ(float[][] section, float z0, float z1, float[] colour, boolean capBack, boolean capFront) {
        loftZ(section, z0, section, z1, colour, capBack, capFront);
    }

    /**
     * A surface between two cross-sections with the same number of points, at {@code zA} and
     * {@code zB} — a nose that narrows, a cowl that slopes. Point {@code i} of one joins point
     * {@code i} of the other.
     */
    void loftZ(float[][] a, float zA, float[][] b, float zB, float[] colour, boolean capA, boolean capB) {
        int n = a.length;
        float cx = 0.0F;
        float cy = 0.0F;
        for (int i = 0; i < n; i++) {
            cx += a[i][0] + b[i][0];
            cy += a[i][1] + b[i][1];
        }
        float[] centre = {cx / (2 * n), cy / (2 * n), (zA + zB) * 0.5F};
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            face(centre, colour,
                a[i][0], a[i][1], zA, a[j][0], a[j][1], zA,
                b[j][0], b[j][1], zB, b[i][0], b[i][1], zB);
        }
        if (capA) {
            cap(a, zA, centre, colour);
        }
        if (capB) {
            cap(b, zB, centre, colour);
        }
    }

    /** A wheel: a cylinder whose axis runs across the car (X). */
    void cylinderX(float cx, float cy, float cz, float radius, float halfWidth, int sides, float[] colour) {
        float[][] ring = new float[sides][];
        for (int i = 0; i < sides; i++) {
            double a = 2.0D * Math.PI * i / sides;
            ring[i] = new float[] {cy + radius * (float) Math.cos(a), cz + radius * (float) Math.sin(a)};
        }
        float[] centre = {cx, cy, cz};
        for (int i = 0; i < sides; i++) {
            int j = (i + 1) % sides;
            face(centre, colour,
                cx - halfWidth, ring[i][0], ring[i][1], cx + halfWidth, ring[i][0], ring[i][1],
                cx + halfWidth, ring[j][0], ring[j][1], cx - halfWidth, ring[j][0], ring[j][1]);
        }
        for (float side : new float[] {-halfWidth, halfWidth}) {
            for (int i = 1; i + 1 < sides; i++) {
                face(centre, colour,
                    cx + side, ring[0][0], ring[0][1], cx + side, ring[i][0], ring[i][1],
                    cx + side, ring[i + 1][0], ring[i + 1][1], cx + side, ring[i + 1][0], ring[i + 1][1]);
            }
        }
    }

    /** A guide wheel: a cylinder standing upright (axis Y). */
    void cylinderY(float cx, float cy, float cz, float radius, float halfHeight, int sides, float[] colour) {
        float[][] ring = new float[sides][];
        for (int i = 0; i < sides; i++) {
            double a = 2.0D * Math.PI * i / sides;
            ring[i] = new float[] {cx + radius * (float) Math.cos(a), cz + radius * (float) Math.sin(a)};
        }
        float[] centre = {cx, cy, cz};
        for (int i = 0; i < sides; i++) {
            int j = (i + 1) % sides;
            face(centre, colour,
                ring[i][0], cy - halfHeight, ring[i][1], ring[i][0], cy + halfHeight, ring[i][1],
                ring[j][0], cy + halfHeight, ring[j][1], ring[j][0], cy - halfHeight, ring[j][1]);
        }
        for (float side : new float[] {-halfHeight, halfHeight}) {
            for (int i = 1; i + 1 < sides; i++) {
                face(centre, colour,
                    ring[0][0], cy + side, ring[0][1], ring[i][0], cy + side, ring[i][1],
                    ring[i + 1][0], cy + side, ring[i + 1][1], ring[i + 1][0], cy + side, ring[i + 1][1]);
            }
        }
    }

    /**
     * A bar of square-ish section running from {@code (y0, z0)} to {@code (y1, z1)} in the plane
     * {@code x}, {@code halfWidth} either side of it — a restraint rail, a lap-bar post.
     */
    void barYZ(float x, float y0, float z0, float y1, float z1, float halfWidth, float halfThickness,
               float[] colour) {
        float dy = y1 - y0;
        float dz = z1 - z0;
        float len = (float) Math.sqrt(dy * dy + dz * dz);
        if (len < 1.0e-5F) {
            return;
        }
        // Perpendicular to the bar within the Y-Z plane.
        float py = -dz / len * halfThickness;
        float pz = dy / len * halfThickness;
        float[][] a = {{x - halfWidth, y0 - py}, {x + halfWidth, y0 - py}, {x + halfWidth, y0 + py},
            {x - halfWidth, y0 + py}};
        float[][] b = {{x - halfWidth, y1 - py}, {x + halfWidth, y1 - py}, {x + halfWidth, y1 + py},
            {x - halfWidth, y1 + py}};
        // loftZ joins sections at two z values; a slanted bar needs its own z per corner.
        float[] centre = {x, (y0 + y1) * 0.5F, (z0 + z1) * 0.5F};
        float[][] za = {{z0 - pz}, {z0 - pz}, {z0 + pz}, {z0 + pz}};
        float[][] zb = {{z1 - pz}, {z1 - pz}, {z1 + pz}, {z1 + pz}};
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            face(centre, colour,
                a[i][0], a[i][1], za[i][0], a[j][0], a[j][1], za[j][0],
                b[j][0], b[j][1], zb[j][0], b[i][0], b[i][1], zb[i][0]);
        }
        face(centre, colour, a[0][0], a[0][1], za[0][0], a[1][0], a[1][1], za[1][0],
            a[2][0], a[2][1], za[2][0], a[3][0], a[3][1], za[3][0]);
        face(centre, colour, b[0][0], b[0][1], zb[0][0], b[1][0], b[1][1], zb[1][0],
            b[2][0], b[2][1], zb[2][0], b[3][0], b[3][1], zb[3][0]);
    }

    // --- Faces and shading. ---------------------------------------------------------------------

    private void cap(float[][] section, float z, float[] centre, float[] colour) {
        for (int i = 1; i + 1 < section.length; i++) {
            face(centre, colour,
                section[0][0], section[0][1], z, section[i][0], section[i][1], z,
                section[i + 1][0], section[i + 1][1], z, section[i + 1][0], section[i + 1][1], z);
        }
    }

    /**
     * One face, shaded by its normal turned to face away from {@code centre} — the middle of the
     * solid it belongs to. Winding is not relied on: the renderer draws with culling off, because the
     * frame it loads is left-handed, and a primitive assembled from sections cannot promise one.
     */
    private void face(float[] centre, float[] colour,
                      float x1, float y1, float z1, float x2, float y2, float z2,
                      float x3, float y3, float z3, float x4, float y4, float z4) {
        float ux = x2 - x1;
        float uy = y2 - y1;
        float uz = z2 - z1;
        float vx = x3 - x1;
        float vy = y3 - y1;
        float vz = z3 - z1;
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0e-9F) {
            // A triangle given as a quad with a repeated corner: take the other diagonal.
            vx = x4 - x1;
            vy = y4 - y1;
            vz = z4 - z1;
            nx = uy * vz - uz * vy;
            ny = uz * vx - ux * vz;
            nz = ux * vy - uy * vx;
            len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1.0e-9F) {
                return;
            }
        }
        nx /= len;
        ny /= len;
        nz /= len;
        float fx = (x1 + x2 + x3 + x4) * 0.25F - centre[0];
        float fy = (y1 + y2 + y3 + y4) * 0.25F - centre[1];
        float fz = (z1 + z2 + z3 + z4) * 0.25F - centre[2];
        if (nx * fx + ny * fy + nz * fz < 0.0F) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        quads.add(new Quad(new float[] {x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4}, colour,
            shade(nx, ny, nz)));
    }

    /**
     * Brightest facing up, darkest facing down, the ends a touch brighter than the sides — the same
     * fixed-light idea as the track's shading, so cars and track sit together under one light.
     */
    static float shade(float nx, float ny, float nz) {
        float up = Math.max(0.0F, ny);
        float down = Math.max(0.0F, -ny);
        return 0.72F + 0.10F * Math.abs(nz) + 0.28F * up - 0.17F * down;
    }
}
