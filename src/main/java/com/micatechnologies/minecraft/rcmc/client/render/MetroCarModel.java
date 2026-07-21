package com.micatechnologies.minecraft.rcmc.client.render;

import net.minecraft.client.renderer.BufferBuilder;

/**
 * Geometry for a metro car, emitted in the same local frame as {@link CarModel} (+X right,
 * +Y up, +Z travel, origin on the track centreline) and under the same conventions: flat-shaded
 * placeholder boxes, winding unmaintained because the renderer draws with culling off.
 *
 * <p>Proportioned from real North American heavy-rail stock at 1 block ≈ 1 metre (see
 * {@code TrainSpec}'s metro presets): the body is <b>3 blocks wide</b> — real bodies run
 * 2.7–3.05 m — with a high floor over the trucks, a continuous window band, door pairs along
 * each side, and longitudinal bench seating. The body is deliberately <b>double-ended and
 * symmetric</b>, like real metro stock: a metro reverses at every terminus, so unlike the
 * coaster model there must be no "nose" for the direction flip to point the wrong way.</p>
 *
 * <p><b>Length convention.</b> The {@code bogieSpacing} passed in is {@code TrainSpec.carLength}
 * — bogie-centre distance, per that class's documented convention. The visible body extends
 * beyond the bogies by the ratio real stock uses (trucks at ~72% of body length), so
 * {@code bodyLength = bogieSpacing / 0.72}. The metro presets size their coupling gaps against
 * this same ratio, leaving {@code GAP_CLEARANCE} of daylight between bodies; keep the two in
 * step or couplers will float or overlap.</p>
 *
 * <p>The window band is left as genuine openings (pillars only, no glass), so riders inside are
 * visible — the placeholder-model equivalent of looking into a lit car.</p>
 */
final class MetroCarModel {

    /** Trucks sit at this fraction of body length; see the class javadoc. */
    private static final float TRUCK_CENTRE_RATIO = 0.72F;

    /** Daylight between adjacent car bodies; must match the metro presets' coupling gaps. */
    private static final float GAP_CLEARANCE = 0.7F;

    private static final float RAIL_TOP = 0.05F;

    // --- Underframe and floor: high-floor stock riding above its trucks. -----------------------
    private static final float SKIRT_HALF_WIDTH = 1.20F;
    private static final float SKIRT_BOTTOM = RAIL_TOP + 0.25F;
    private static final float FLOOR_TOP = 1.00F;

    // --- Body shell. ---------------------------------------------------------------------------
    // Heights sized against the real thing at 1 block ≈ 1 m: metro car bodies run ~3.5 m over
    // the rails. The first cut stood 2.75 and read as a toy next to full-height platforms —
    // exactly the mistake the 3-block width was chosen to avoid.
    private static final float BODY_HALF_WIDTH = 1.50F;
    private static final float WALL_THICKNESS = 0.10F;
    private static final float WINDOW_SILL = 1.85F;
    private static final float WINDOW_HEAD = 2.95F;
    private static final float ROOF_BASE = 3.35F;
    private static final float ROOF_TOP = 3.55F;

    // --- Pantograph: a simple raised frame reaching from the roof to just under the contact
    // wire (6.0 over the rails, per TrackMeshBuilder's catenary heights — the two are one
    // measurement in two places, like the seat heights). Drawn on alternate cars.
    private static final float PANTO_BASE_TOP = ROOF_TOP + 0.18F;

    /** Contact wire runs at 6.0 with a 0.03 half-height; the shoe stops a hair under its
     *  underside so the two read as touching without z-fighting. */
    private static final float PANTO_SHOE_U = 5.93F;
    private static final float PANTO_HALF_SPAN = 0.85F;

    /** Window pillars, roughly one per body panel. */
    private static final float PILLAR_WIDTH = 0.15F;
    private static final float PANEL_PITCH = 2.2F;

    // --- Doors: paired sliding doors, drawn slightly proud of the wall in trim colour. ---------
    private static final float DOOR_WIDTH = 1.4F;
    private static final float DOOR_PROUD = 0.03F;

    // --- Longitudinal bench seating along each wall. -------------------------------------------
    private static final float SEAT_DEPTH = 0.45F;
    private static final float SEAT_TOP = FLOOR_TOP + 0.35F;
    private static final float SEAT_BACK_TOP = FLOOR_TOP + 0.80F;

    // --- Trucks: longer and heavier than a coaster bogie, straddling the rails outboard. -------
    private static final float TRUCK_INNER = 0.62F;
    private static final float TRUCK_OUTER = 0.92F;
    private static final float TRUCK_HALF_LENGTH = 0.85F;
    private static final float TRUCK_TOP = RAIL_TOP + 0.45F;
    private static final float TRUCK_BOTTOM = RAIL_TOP - 0.12F;

    private static final float COUPLER_HALF_WIDTH = 0.10F;
    private static final float COUPLER_BOTTOM = RAIL_TOP + 0.30F;
    private static final float COUPLER_TOP = RAIL_TOP + 0.55F;

    private static final float[] UNDERFRAME_COLOR = {0.20F, 0.20F, 0.22F};
    private static final float[] TRUCK_COLOR = {0.28F, 0.29F, 0.32F};
    private static final float[] ROOF_COLOR = {0.45F, 0.46F, 0.48F};

    private MetroCarModel() {
        throw new AssertionError("No instances.");
    }

    /**
     * Emits one metro car.
     *
     * @param bogieSpacing   {@code TrainSpec.carLength} — bogie-centre distance in blocks; the
     *                       body is derived longer, see the class javadoc
     * @param drawCoupling   whether to draw the rear coupler bar (not the last car)
     * @param drawPantograph whether this car carries a pantograph — alternate cars, like a real
     *                       EMU consist
     */
    static void emit(BufferBuilder buffer, float bogieSpacing, boolean drawCoupling,
                     boolean drawPantograph,
                     float[] bodyColour, float[] trimColour, float[] seatColour) {
        float bodyLength = bogieSpacing / TRUCK_CENTRE_RATIO;
        float half = bodyLength * 0.5F;

        // Underframe skirt between the trucks, and the floor slab the whole interior stands on.
        box(buffer, -SKIRT_HALF_WIDTH, SKIRT_BOTTOM, -half + 0.3F,
            SKIRT_HALF_WIDTH, FLOOR_TOP - 0.1F, half - 0.3F, UNDERFRAME_COLOR);
        box(buffer, -BODY_HALF_WIDTH, FLOOR_TOP - 0.1F, -half,
            BODY_HALF_WIDTH, FLOOR_TOP, half, UNDERFRAME_COLOR);

        emitSide(buffer, half, BODY_HALF_WIDTH - WALL_THICKNESS, BODY_HALF_WIDTH,
            bodyColour, trimColour);
        emitSide(buffer, half, -BODY_HALF_WIDTH, -BODY_HALF_WIDTH + WALL_THICKNESS,
            bodyColour, trimColour);

        // Double-ended cabs: identical at both ends, windshield band in trim. See the class
        // javadoc for why nothing here may distinguish front from back.
        emitEnd(buffer, half - WALL_THICKNESS, half, bodyColour, trimColour);
        emitEnd(buffer, -half, -half + WALL_THICKNESS, bodyColour, trimColour);

        box(buffer, -BODY_HALF_WIDTH, ROOF_BASE, -half,
            BODY_HALF_WIDTH, ROOF_TOP, half, ROOF_COLOR);

        emitBenches(buffer, half, seatColour);

        float truckAt = bogieSpacing * 0.5F;
        emitTruck(buffer, truckAt);
        emitTruck(buffer, -truckAt);

        if (drawPantograph) {
            emitPantograph(buffer);
        }

        if (drawCoupling) {
            box(buffer, -COUPLER_HALF_WIDTH, COUPLER_BOTTOM, -half - GAP_CLEARANCE,
                COUPLER_HALF_WIDTH, COUPLER_TOP, -half, UNDERFRAME_COLOR);
        }
    }

    /**
     * One side wall: solid below the sill and above the head, pillars through the window band,
     * and door pairs (in trim) spaced along the car. Between pillars the band is open — that is
     * the "glass".
     */
    private static void emitSide(BufferBuilder buffer, float half, float xInner, float xOuter,
                                 float[] bodyColour, float[] trimColour) {
        // Solid band below the windows and the cant rail strip above them.
        box(buffer, xInner, FLOOR_TOP, -half, xOuter, WINDOW_SILL, half, bodyColour);
        box(buffer, xInner, WINDOW_HEAD, -half, xOuter, ROOF_BASE, half, bodyColour);

        // Window pillars.
        for (float at = -half + PANEL_PITCH; at < half - PANEL_PITCH * 0.5F; at += PANEL_PITCH) {
            box(buffer, xInner, WINDOW_SILL, at - PILLAR_WIDTH * 0.5F,
                xOuter, WINDOW_HEAD, at + PILLAR_WIDTH * 0.5F, bodyColour);
        }

        // Door pairs at the quarter points — where real metro doors cluster.
        float sign = xOuter > 0.0F ? 1.0F : -1.0F;
        float doorOuter = Math.abs(xOuter) + DOOR_PROUD;
        for (float at : new float[] {-half * 0.5F, half * 0.5F}) {
            box(buffer, sign * (doorOuter - 0.04F), FLOOR_TOP, at - DOOR_WIDTH * 0.5F,
                sign * doorOuter, WINDOW_HEAD, at + DOOR_WIDTH * 0.5F, trimColour);
        }
    }

    /** A car end: solid below the windshield band, trim band, solid cap above. */
    private static void emitEnd(BufferBuilder buffer, float zNear, float zFar,
                                float[] bodyColour, float[] trimColour) {
        box(buffer, -BODY_HALF_WIDTH, FLOOR_TOP, zNear,
            BODY_HALF_WIDTH, WINDOW_SILL, zFar, bodyColour);
        box(buffer, -BODY_HALF_WIDTH, WINDOW_SILL, zNear,
            BODY_HALF_WIDTH, WINDOW_HEAD, zFar, trimColour);
        box(buffer, -BODY_HALF_WIDTH, WINDOW_HEAD, zNear,
            BODY_HALF_WIDTH, ROOF_BASE, zFar, bodyColour);
    }

    /** Longitudinal benches down both walls, clear of the door bays. */
    private static void emitBenches(BufferBuilder buffer, float half, float[] seatColour) {
        float inner = BODY_HALF_WIDTH - WALL_THICKNESS;
        // Three bench runs per side: between the ends and the door bays.
        float doorHalf = DOOR_WIDTH * 0.5F + 0.15F;
        float[][] runs = {
            {-half + 0.4F, -half * 0.5F - doorHalf},
            {-half * 0.5F + doorHalf, half * 0.5F - doorHalf},
            {half * 0.5F + doorHalf, half - 0.4F},
        };
        for (float[] run : runs) {
            if (run[1] - run[0] < 0.5F) {
                continue;
            }
            box(buffer, inner - SEAT_DEPTH, FLOOR_TOP, run[0],
                inner, SEAT_TOP, run[1], seatColour);
            box(buffer, -inner, FLOOR_TOP, run[0],
                -inner + SEAT_DEPTH, SEAT_TOP, run[1], seatColour);
            // Low seat backs against the walls.
            box(buffer, inner - 0.08F, SEAT_TOP, run[0], inner, SEAT_BACK_TOP, run[1], seatColour);
            box(buffer, -inner, SEAT_TOP, run[0], -inner + 0.08F, SEAT_BACK_TOP, run[1], seatColour);
        }
    }

    /**
     * A raised pantograph, simplified to placeholder boxes: base frame on the roof, two slanted
     * arms approximated as stacked struts, and a contact shoe bar riding just under the wire.
     */
    private static void emitPantograph(BufferBuilder buffer) {
        // Base frame across the roof centre.
        box(buffer, -0.7F, ROOF_TOP, -0.55F, 0.7F, PANTO_BASE_TOP, 0.55F, TRUCK_COLOR);
        // Lower and upper arm struts, narrowing with height.
        float armMidU = (PANTO_BASE_TOP + PANTO_SHOE_U) * 0.5F;
        box(buffer, -0.09F, PANTO_BASE_TOP, -0.30F, 0.09F, armMidU, -0.14F, TRUCK_COLOR);
        box(buffer, -0.09F, PANTO_BASE_TOP, 0.14F, 0.09F, armMidU, 0.30F, TRUCK_COLOR);
        box(buffer, -0.07F, armMidU, -0.10F, 0.07F, PANTO_SHOE_U - 0.06F, 0.10F, TRUCK_COLOR);
        // The shoe: a wide bar across the direction of travel, kissing the wire height.
        box(buffer, -PANTO_HALF_SPAN, PANTO_SHOE_U - 0.06F, -0.10F,
            PANTO_HALF_SPAN, PANTO_SHOE_U, 0.10F, UNDERFRAME_COLOR);
    }

    private static void emitTruck(BufferBuilder buffer, float atZ) {
        box(buffer, TRUCK_INNER, TRUCK_BOTTOM, atZ - TRUCK_HALF_LENGTH,
            TRUCK_OUTER, TRUCK_TOP, atZ + TRUCK_HALF_LENGTH, TRUCK_COLOR);
        box(buffer, -TRUCK_OUTER, TRUCK_BOTTOM, atZ - TRUCK_HALF_LENGTH,
            -TRUCK_INNER, TRUCK_TOP, atZ + TRUCK_HALF_LENGTH, TRUCK_COLOR);
        // Truck bolster spanning under the car between the side frames.
        box(buffer, -TRUCK_INNER, RAIL_TOP + 0.20F, atZ - 0.25F,
            TRUCK_INNER, RAIL_TOP + 0.45F, atZ + 0.25F, TRUCK_COLOR);
    }

    /** Same box emitter as {@link CarModel}; see there for the winding/culling note. */
    private static void box(BufferBuilder buffer, float x1, float y1, float z1,
                            float x2, float y2, float z2, float[] color) {
        float r = color[0];
        float g = color[1];
        float b = color[2];
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
