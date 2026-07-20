package com.micatechnologies.minecraft.rcmc.client.render;

import net.minecraft.client.renderer.BufferBuilder;

/**
 * Geometry for a coaster car, emitted in the car's own local frame.
 *
 * <p>Local axes match the {@code TrackFrame} basis the renderer loads: <b>+X</b> is the car's
 * right, <b>+Y</b> is up out of its roof, <b>+Z</b> is the direction of travel. The origin sits on
 * the track centreline, so the chassis hangs slightly below zero and the body rises above it — the
 * same relationship a real car has to its rails.</p>
 *
 * <p>Still untextured flat-shaded boxes rather than a proper model. What it does buy over the
 * single box it replaces is <em>legibility</em>: a rider can see where the seats are, which end is
 * the front, and that the cars are coupled rather than merely adjacent. Those read at a glance and
 * are what made the single box look like a placeholder even at distance.</p>
 *
 * <p>Separated from {@code RenderCoasterCar} because the render class should be about <em>when</em>
 * and <em>where</em> to draw; this is about <em>what</em>. It also keeps the vertex maths in one
 * place, which matters now that there are a dozen boxes rather than one.</p>
 */
final class CarModel {

    // --- Chassis: the structural underframe that rides the rails. -----------------------------
    private static final float CHASSIS_HALF_WIDTH = 0.34F;
    private static final float CHASSIS_BOTTOM = -0.28F;
    private static final float CHASSIS_TOP = -0.10F;

    // --- Body tub: the part riders sit inside. ------------------------------------------------
    private static final float BODY_HALF_WIDTH = 0.62F;
    private static final float BODY_FLOOR = -0.10F;
    private static final float BODY_TOP = 0.42F;
    private static final float BODY_WALL = 0.09F;

    /** The nose tapers in, so the leading end reads as the front from any angle. */
    private static final float NOSE_TAPER = 0.22F;

    // --- Seats. --------------------------------------------------------------------------------
    private static final float SEAT_BACK_HEIGHT = 0.46F;
    private static final float SEAT_BACK_THICKNESS = 0.10F;
    private static final float SEAT_BASE_HEIGHT = 0.06F;

    // --- Bogies: the wheel assemblies, deliberately visible below the chassis. -----------------
    private static final float BOGIE_HALF_WIDTH = 0.66F;
    private static final float BOGIE_HALF_LENGTH = 0.16F;
    private static final float BOGIE_TOP = -0.24F;
    private static final float BOGIE_BOTTOM = -0.40F;

    private static final float COUPLING_HALF_WIDTH = 0.07F;
    private static final float COUPLING_TOP = -0.12F;
    private static final float COUPLING_BOTTOM = -0.22F;

    private static final float[] CHASSIS_COLOR = {0.22F, 0.22F, 0.24F};
    private static final float[] BODY_COLOR = {0.72F, 0.16F, 0.14F};
    private static final float[] BODY_TRIM_COLOR = {0.90F, 0.62F, 0.16F};
    private static final float[] SEAT_COLOR = {0.16F, 0.17F, 0.21F};
    private static final float[] BOGIE_COLOR = {0.30F, 0.31F, 0.34F};

    private CarModel() {
        throw new AssertionError("No instances.");
    }

    /**
     * Emits one car.
     *
     * @param length       car length in blocks, along the direction of travel
     * @param seatRows     number of seat rows; two riders abreast per row
     * @param couplingGap  distance to the next car's body, in blocks. A coupling bar is drawn only
     *                     when this is positive and this is not the last car — a bar projecting
     *                     off the back of the final car would read as a broken train.
     * @param drawCoupling whether to draw the rear coupling bar
     */
    static void emit(BufferBuilder buffer, float length, int seatRows, float couplingGap,
                     boolean drawCoupling) {
        float halfLength = length * 0.5F;

        box(buffer, -CHASSIS_HALF_WIDTH, CHASSIS_BOTTOM, -halfLength,
            CHASSIS_HALF_WIDTH, CHASSIS_TOP, halfLength, CHASSIS_COLOR);

        emitBody(buffer, halfLength);
        emitSeats(buffer, halfLength, Math.max(1, seatRows));

        // Bogies sit inboard of the ends, where the pivot points actually are on a real car — at
        // the very ends they read as skids rather than as wheel assemblies.
        float bogieAt = halfLength * 0.62F;
        emitBogie(buffer, bogieAt);
        emitBogie(buffer, -bogieAt);

        if (drawCoupling && couplingGap > 0.0F) {
            // Spans the full gap to meet the next car's nose, so a train reads as coupled rather
            // than as separate cars flying in formation.
            box(buffer, -COUPLING_HALF_WIDTH, COUPLING_BOTTOM, -halfLength - couplingGap,
                COUPLING_HALF_WIDTH, COUPLING_TOP, -halfLength, CHASSIS_COLOR);
        }
    }

    /** Floor, two side walls, and a tapered nose and tail. Open on top so riders are visible. */
    private static void emitBody(BufferBuilder buffer, float halfLength) {
        box(buffer, -BODY_HALF_WIDTH, BODY_FLOOR, -halfLength,
            BODY_HALF_WIDTH, BODY_FLOOR + 0.06F, halfLength, CHASSIS_COLOR);

        // Side walls, inset at the nose so the front narrows.
        box(buffer, BODY_HALF_WIDTH - BODY_WALL, BODY_FLOOR, -halfLength,
            BODY_HALF_WIDTH, BODY_TOP, halfLength - NOSE_TAPER, BODY_COLOR);
        box(buffer, -BODY_HALF_WIDTH, BODY_FLOOR, -halfLength,
            -BODY_HALF_WIDTH + BODY_WALL, BODY_TOP, halfLength - NOSE_TAPER, BODY_COLOR);

        // Tapered nose: narrower, and carried a little lower, which is what makes the leading end
        // read as the front rather than just as "the other end".
        box(buffer, -BODY_HALF_WIDTH + NOSE_TAPER, BODY_FLOOR, halfLength - NOSE_TAPER,
            BODY_HALF_WIDTH - NOSE_TAPER, BODY_TOP - 0.10F, halfLength, BODY_TRIM_COLOR);

        // Rear bulkhead, closing the tub off behind the last row.
        box(buffer, -BODY_HALF_WIDTH, BODY_FLOOR, -halfLength,
            BODY_HALF_WIDTH, BODY_TOP - 0.06F, -halfLength + BODY_WALL, BODY_COLOR);
    }

    /** Rows of seat bases and backs, evenly spread along the tub. */
    private static void emitSeats(BufferBuilder buffer, float halfLength, int rows) {
        float usable = halfLength * 2.0F - NOSE_TAPER - BODY_WALL;
        float rowPitch = usable / rows;
        float firstCentre = halfLength - NOSE_TAPER - rowPitch * 0.5F;

        for (int i = 0; i < rows; i++) {
            float centre = firstCentre - i * rowPitch;
            float inner = BODY_HALF_WIDTH - BODY_WALL;

            box(buffer, -inner, BODY_FLOOR + 0.06F, centre - rowPitch * 0.30F,
                inner, BODY_FLOOR + 0.06F + SEAT_BASE_HEIGHT, centre + rowPitch * 0.30F, SEAT_COLOR);

            // Back sits at the rear of its own row, so a rider occupies the space in front of it.
            float backAt = centre - rowPitch * 0.30F;
            box(buffer, -inner, BODY_FLOOR + 0.06F, backAt - SEAT_BACK_THICKNESS,
                inner, BODY_FLOOR + 0.06F + SEAT_BACK_HEIGHT, backAt, SEAT_COLOR);
        }
    }

    private static void emitBogie(BufferBuilder buffer, float atZ) {
        box(buffer, -BOGIE_HALF_WIDTH, BOGIE_BOTTOM, atZ - BOGIE_HALF_LENGTH,
            BOGIE_HALF_WIDTH, BOGIE_TOP, atZ + BOGIE_HALF_LENGTH, BOGIE_COLOR);
    }

    /**
     * An axis-aligned box between two corners.
     *
     * <p>Winding is not maintained: the renderer draws with culling disabled, because the basis it
     * loads is left-handed and therefore mirrors every face. See {@code RenderCoasterCar} for why
     * that is not fixed here.</p>
     */
    private static void box(BufferBuilder buffer, float x1, float y1, float z1,
                            float x2, float y2, float z2, float[] color) {
        float r = color[0];
        float g = color[1];
        float b = color[2];
        // Slight per-face shading so edges read against each other without any lighting.
        quad(buffer, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, 0.82F);
        quad(buffer, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2, r, g, b, 0.86F);
        quad(buffer, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, r, g, b, 0.72F);
        quad(buffer, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, 0.72F);
        quad(buffer, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, 1.0F);
        quad(buffer, x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2, r, g, b, 0.55F);
    }

    private static void quad(BufferBuilder buffer,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float r, float g, float b, float shade) {
        float sr = r * shade;
        float sg = g * shade;
        float sb = b * shade;
        buffer.pos(x1, y1, z1).color(sr, sg, sb, 1.0F).endVertex();
        buffer.pos(x2, y2, z2).color(sr, sg, sb, 1.0F).endVertex();
        buffer.pos(x3, y3, z3).color(sr, sg, sb, 1.0F).endVertex();
        buffer.pos(x4, y4, z4).color(sr, sg, sb, 1.0F).endVertex();
    }
}
