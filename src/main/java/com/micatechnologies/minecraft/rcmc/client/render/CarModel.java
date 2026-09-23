package com.micatechnologies.minecraft.rcmc.client.render;

import com.micatechnologies.minecraft.rcmc.physics.CoasterCarLayout;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;

/**
 * A coaster car, in the car's own local frame: <b>+X</b> its right, <b>+Y</b> up out of its seats,
 * <b>+Z</b> the way it travels, the origin on the track centreline.
 *
 * <p>Three cars, one per {@link TrainSpec.CoasterModel}, on one running gear:</p>
 * <ul>
 *   <li><b>Sit-down</b> — a flared tub, bucket seats, a T-shaped lap bar per row.</li>
 *   <li><b>Over-the-shoulder</b> — the same tub with tall seats, headrests, and a harness over
 *       each rider: what a car that goes upside down needs, and looks like it needs.</li>
 *   <li><b>Wooden classic</b> — straight high sides with planking and a capped top rail, a bench
 *       per row, one bar across it.</li>
 * </ul>
 *
 * <p>The seats are where {@link CoasterCarLayout} says, at its seat height, which is where a rider
 * is put — so riders sit on cushions, not in the air beside them. The lead car carries a sculpted
 * nose; the others a short cowl, so the front of the train reads as the front.</p>
 *
 * <p>The running gear sits outboard of the rails, as it does on a real car: road wheels on top of
 * each rail, guide wheels on its outer face, a yoke round them. Nothing of the car comes below the
 * railheads between them — the ties and the spine are there.</p>
 */
final class CarModel {

    /** Top of the running rails, from the track's coaster style: ±0.55 gauge, 0.1 square rail. */
    static final float RAIL_TOP = 0.05F;
    private static final float RAIL_CENTRE = 0.55F;
    private static final float RAIL_OUTER = 0.60F;

    // Chassis and tub.
    private static final float CHASSIS_HALF_WIDTH = 0.40F;
    private static final float CHASSIS_BOTTOM = RAIL_TOP + 0.01F;
    private static final float FLOOR_BOTTOM = RAIL_TOP + 0.11F;
    private static final float FLOOR_TOP = (float) CoasterCarLayout.SEAT_HEIGHT - 0.07F;
    private static final float TUB_HALF_WIDTH = (float) CoasterCarLayout.BODY_HALF_WIDTH;
    private static final float TUB_TOP = 0.64F;
    private static final float TUB_FLARE = 0.06F;
    private static final float TUB_WALL = 0.07F;

    // Seats.
    private static final float SEAT_TOP = (float) CoasterCarLayout.SEAT_HEIGHT;
    private static final float SEAT_HALF_WIDTH = 0.24F;
    private static final float SEAT_DEPTH_FRONT = 0.22F;
    private static final float SEAT_DEPTH_BACK = 0.30F;
    private static final float BACK_THICKNESS = 0.10F;

    // Running gear.
    private static final float ROAD_WHEEL_RADIUS = 0.075F;
    private static final float GUIDE_WHEEL_RADIUS = 0.05F;
    private static final float YOKE_OUTER = 0.78F;
    private static final float YOKE_PLATE = 0.06F;

    private static final float[] CHASSIS = {0.22F, 0.22F, 0.24F};
    private static final float[] YOKE = {0.28F, 0.29F, 0.32F};
    private static final float[] WHEEL = {0.12F, 0.12F, 0.13F};
    private static final float[] HUB = {0.55F, 0.56F, 0.58F};
    private static final float[] METAL = {0.62F, 0.63F, 0.66F};
    private static final float[] LAMP = {1.0F, 0.94F, 0.72F};

    private CarModel() {
        throw new AssertionError("No instances.");
    }

    /**
     * One car.
     *
     * @param lead         whether this is the front car, which gets the nose
     * @param drawCoupling whether to draw the bar to the car behind — not on the last car
     */
    static CarMesh build(TrainSpec spec, boolean lead, boolean drawCoupling,
                         float[] body, float[] trim, float[] seats) {
        CarMesh mesh = new CarMesh();
        float half = (float) spec.carLength() * 0.5F;
        TrainSpec.CoasterModel model = spec.coasterModel();

        mesh.box(-CHASSIS_HALF_WIDTH, CHASSIS_BOTTOM, -half, CHASSIS_HALF_WIDTH, FLOOR_BOTTOM, half, CHASSIS);
        mesh.box(-TUB_HALF_WIDTH, FLOOR_BOTTOM, -half, TUB_HALF_WIDTH, FLOOR_TOP, half, CHASSIS);
        runningGear(mesh, half * 0.62F);
        runningGear(mesh, -half * 0.62F);

        if (model == TrainSpec.CoasterModel.WOODEN) {
            woodenBody(mesh, half, lead, body, trim);
        }
        else {
            modernBody(mesh, half, lead, body, trim);
        }
        for (int row = 0; row < CoasterCarLayout.rows(spec); row++) {
            float centre = (float) CoasterCarLayout.rowCentre(spec, row);
            switch (model) {
                case WOODEN:
                    benchRow(mesh, centre, seats, trim);
                    break;
                case SHOULDER:
                    shoulderRow(mesh, centre, seats);
                    break;
                case SIT_DOWN:
                default:
                    lapBarRow(mesh, centre, seats);
                    break;
            }
        }

        float gap = (float) spec.couplingGap();
        if (drawCoupling && gap > 0.0F) {
            mesh.box(-0.07F, CHASSIS_BOTTOM + 0.02F, -half - gap, 0.07F, FLOOR_BOTTOM - 0.02F, -half, CHASSIS);
        }
        return mesh;
    }

    // --- Running gear. -------------------------------------------------------------------------

    /** A wheel assembly across the car at {@code z}: a yoke each side round a road and a guide wheel. */
    private static void runningGear(CarMesh mesh, float z) {
        for (float s : new float[] {1.0F, -1.0F}) {
            // Outer plate and the top plate over the road wheels.
            box(mesh, s, YOKE_OUTER - YOKE_PLATE, YOKE_OUTER, RAIL_TOP - 0.10F, FLOOR_TOP, z - 0.24F, z + 0.24F, YOKE);
            box(mesh, s, RAIL_CENTRE - 0.07F, YOKE_OUTER, FLOOR_TOP - 0.05F, FLOOR_TOP, z - 0.24F, z + 0.24F, YOKE);
            for (float along : new float[] {-0.12F, 0.12F}) {
                mesh.cylinderX(s * RAIL_CENTRE, RAIL_TOP + ROAD_WHEEL_RADIUS, z + along,
                    ROAD_WHEEL_RADIUS, 0.035F, 10, WHEEL);
                mesh.cylinderX(s * (RAIL_CENTRE + 0.037F), RAIL_TOP + ROAD_WHEEL_RADIUS, z + along,
                    0.03F, 0.004F, 6, HUB);
            }
            // Guide wheel against the rail's outer face.
            mesh.cylinderY(s * (RAIL_OUTER + GUIDE_WHEEL_RADIUS + 0.005F), 0.0F, z,
                GUIDE_WHEEL_RADIUS, 0.03F, 8, WHEEL);
        }
    }

    /** A box on one side of the car: {@code x0..x1} measured outward, mirrored for the left. */
    private static void box(CarMesh mesh, float side, float x0, float x1, float y0, float y1,
                            float z0, float z1, float[] colour) {
        if (side > 0.0F) {
            mesh.box(x0, y0, z0, x1, y1, z1, colour);
        }
        else {
            mesh.box(-x1, y0, z0, -x0, y1, z1, colour);
        }
    }

    // --- Bodies. -------------------------------------------------------------------------------

    /** Outer skin of the modern tub at height {@code y}: it flares out toward the top. */
    private static float tubOuter(float y) {
        return TUB_HALF_WIDTH + TUB_FLARE * (y - FLOOR_BOTTOM) / (TUB_TOP - FLOOR_BOTTOM);
    }

    private static void modernBody(CarMesh mesh, float half, boolean lead, float[] body, float[] trim) {
        float front = half - (float) CoasterCarLayout.FRONT_CLEARANCE;
        for (float s : new float[] {1.0F, -1.0F}) {
            // A flared side with a rolled top edge.
            float[][] side = {
                {s * TUB_HALF_WIDTH, FLOOR_BOTTOM},
                {s * tubOuter(TUB_TOP - 0.04F), TUB_TOP - 0.04F},
                {s * (tubOuter(TUB_TOP) - 0.02F), TUB_TOP},
                {s * (tubOuter(TUB_TOP) - TUB_WALL - 0.02F), TUB_TOP},
                {s * (TUB_HALF_WIDTH - TUB_WALL), FLOOR_TOP},
            };
            mesh.prismZ(side, -half, front, body, true, false);
            // A trim stripe standing just proud of the skin.
            float y0 = 0.44F;
            float y1 = 0.50F;
            float[][] stripe = {
                {s * tubOuter(y0), y0}, {s * (tubOuter(y0) + 0.012F), y0},
                {s * (tubOuter(y1) + 0.012F), y1}, {s * tubOuter(y1), y1},
            };
            mesh.prismZ(stripe, -half, front, trim, true, true);
        }
        // Rear bulkhead with a bumper under it.
        mesh.box(-TUB_HALF_WIDTH, FLOOR_TOP, -half, TUB_HALF_WIDTH, TUB_TOP - 0.06F, -half + 0.07F, body);
        mesh.box(-TUB_HALF_WIDTH + 0.08F, FLOOR_BOTTOM - 0.02F, -half - 0.05F,
            TUB_HALF_WIDTH - 0.08F, FLOOR_BOTTOM + 0.10F, -half + 0.01F, trim);

        float[][] full = section(tubOuter(TUB_TOP) + 0.0F, FLOOR_BOTTOM, TUB_TOP);
        if (lead) {
            // The nose: narrowing and dropping in three stages to a rounded point.
            float[][] mid = section(0.50F, FLOOR_BOTTOM, 0.52F);
            float[][] tip = section(0.26F, FLOOR_BOTTOM + 0.04F, 0.32F);
            mesh.loftZ(full, front, mid, half + 0.05F, body, false, false);
            mesh.loftZ(mid, half + 0.05F, tip, half + 0.40F, body, false, true);
            // A trim chevron over the nose, and a pair of lamps.
            float[][] band = section(0.51F, 0.44F, 0.50F);
            float[][] bandTip = section(0.28F, 0.28F, 0.33F);
            mesh.loftZ(band, half + 0.052F, bandTip, half + 0.402F, trim, true, true);
            mesh.box(-0.20F, 0.20F, half + 0.39F, -0.12F, 0.25F, half + 0.42F, LAMP);
            mesh.box(0.12F, 0.20F, half + 0.39F, 0.20F, 0.25F, half + 0.42F, LAMP);
        }
        else {
            // A short sloped cowl in front of the first row.
            float[][] low = section(0.54F, FLOOR_BOTTOM, 0.44F);
            mesh.loftZ(full, front, low, half, body, false, true);
        }
    }

    private static void woodenBody(CarMesh mesh, float half, boolean lead, float[] body, float[] trim) {
        float top = 0.70F;
        float front = half - (float) CoasterCarLayout.FRONT_CLEARANCE;
        float[] plank = {body[0] * 0.78F, body[1] * 0.78F, body[2] * 0.78F};
        for (float s : new float[] {1.0F, -1.0F}) {
            box(mesh, s, TUB_HALF_WIDTH - TUB_WALL, TUB_HALF_WIDTH + 0.02F, FLOOR_BOTTOM, top, -half, front, body);
            // Planking: darker seams along the side.
            for (float y : new float[] {0.28F, 0.42F, 0.56F}) {
                box(mesh, s, TUB_HALF_WIDTH + 0.02F, TUB_HALF_WIDTH + 0.03F, y, y + 0.02F, -half, front, plank);
            }
            // A capping rail along the top, wider than the side.
            box(mesh, s, TUB_HALF_WIDTH - TUB_WALL - 0.02F, TUB_HALF_WIDTH + 0.05F, top, top + 0.05F,
                -half - 0.02F, front + 0.02F, trim);
        }
        mesh.box(-TUB_HALF_WIDTH, FLOOR_TOP, -half, TUB_HALF_WIDTH, top, -half + 0.07F, body);
        mesh.box(-TUB_HALF_WIDTH - 0.02F, top, -half - 0.02F, TUB_HALF_WIDTH + 0.05F, top + 0.05F, -half + 0.08F, trim);
        if (lead) {
            // A dashboard front sweeping up, with a single lamp.
            float[][] base = section(TUB_HALF_WIDTH + 0.02F, FLOOR_BOTTOM, top);
            float[][] dash = section(TUB_HALF_WIDTH - 0.02F, FLOOR_BOTTOM, 0.88F);
            mesh.loftZ(base, front, dash, half + 0.10F, body, false, true);
            mesh.box(-TUB_HALF_WIDTH + 0.02F, 0.86F, half + 0.02F, TUB_HALF_WIDTH - 0.02F, 0.92F, half + 0.14F, trim);
            mesh.cylinderX(0.0F, 0.55F, half + 0.12F, 0.07F, 0.0F, 10, METAL);
            mesh.box(-0.05F, 0.50F, half + 0.10F, 0.05F, 0.60F, half + 0.13F, LAMP);
        }
        else {
            mesh.box(-TUB_HALF_WIDTH - 0.02F, FLOOR_BOTTOM, front, TUB_HALF_WIDTH + 0.02F, top - 0.08F, half, body);
        }
    }

    /** A symmetric rectangle section of half-width {@code halfWidth} from {@code y0} to {@code y1}. */
    private static float[][] section(float halfWidth, float y0, float y1) {
        return new float[][] {{-halfWidth, y0}, {halfWidth, y0}, {halfWidth, y1}, {-halfWidth, y1}};
    }

    // --- Seats and restraints. ------------------------------------------------------------------

    /** A bucket seat for one rider at {@code x}, hips at {@code centre}, back rising to {@code backTop}. */
    private static void bucket(CarMesh mesh, float x, float centre, float backTop, float[] seats) {
        float x0 = x - SEAT_HALF_WIDTH;
        float x1 = x + SEAT_HALF_WIDTH;
        mesh.box(x0, FLOOR_TOP, centre - SEAT_DEPTH_BACK, x1, SEAT_TOP, centre + SEAT_DEPTH_FRONT, seats);
        // The back leans rearward as it rises.
        float back = centre - SEAT_DEPTH_BACK;
        // Built as a slanted bar through the middle of the back.
        mesh.barYZ(x, FLOOR_TOP, back - BACK_THICKNESS * 0.5F, backTop, back - BACK_THICKNESS * 0.5F - 0.08F,
            SEAT_HALF_WIDTH, BACK_THICKNESS * 0.5F, seats);
        // Side bolsters.
        mesh.box(x0, SEAT_TOP, centre - SEAT_DEPTH_BACK + 0.02F, x0 + 0.05F, SEAT_TOP + 0.06F,
            centre + SEAT_DEPTH_FRONT - 0.04F, seats);
        mesh.box(x1 - 0.05F, SEAT_TOP, centre - SEAT_DEPTH_BACK + 0.02F, x1, SEAT_TOP + 0.06F,
            centre + SEAT_DEPTH_FRONT - 0.04F, seats);
    }

    private static void lapBarRow(CarMesh mesh, float centre, float[] seats) {
        for (int i = 0; i < CoasterCarLayout.ABREAST; i++) {
            bucket(mesh, (float) CoasterCarLayout.across(i), centre, 0.80F, seats);
        }
        // A T-bar from a post between the seats: the bar across both laps, padded.
        float barZ = centre + SEAT_DEPTH_FRONT - 0.02F;
        mesh.box(-0.04F, FLOOR_TOP, barZ - 0.03F, 0.04F, 0.50F, barZ + 0.03F, METAL);
        mesh.box(-0.52F, 0.48F, barZ - 0.04F, 0.52F, 0.53F, barZ + 0.04F, METAL);
        for (int i = 0; i < CoasterCarLayout.ABREAST; i++) {
            float x = (float) CoasterCarLayout.across(i);
            mesh.box(x - 0.16F, 0.47F, barZ - 0.06F, x + 0.16F, 0.55F, barZ + 0.05F, seats);
        }
    }

    private static void shoulderRow(CarMesh mesh, float centre, float[] seats) {
        for (int i = 0; i < CoasterCarLayout.ABREAST; i++) {
            float x = (float) CoasterCarLayout.across(i);
            bucket(mesh, x, centre, 1.02F, seats);
            float back = centre - SEAT_DEPTH_BACK - 0.08F;
            // Headrest with wings, above the seat back.
            mesh.box(x - 0.15F, 1.00F, back - 0.10F, x + 0.15F, 1.16F, back + 0.02F, seats);
            mesh.box(x - 0.17F, 0.98F, back - 0.08F, x - 0.13F, 1.14F, back + 0.10F, seats);
            mesh.box(x + 0.13F, 0.98F, back - 0.08F, x + 0.17F, 1.14F, back + 0.10F, seats);
            // The harness: over each shoulder from the seat back, down the chest to the lap.
            for (float offset : new float[] {-0.13F, 0.13F}) {
                float hx = x + offset;
                mesh.barYZ(hx, 0.96F, back + 0.02F, 0.86F, centre - 0.06F, 0.03F, 0.03F, METAL);
                mesh.barYZ(hx, 0.86F, centre - 0.06F, 0.58F, centre + 0.10F, 0.035F, 0.035F, seats);
                mesh.barYZ(hx, 0.58F, centre + 0.10F, 0.40F, centre + 0.14F, 0.03F, 0.03F, METAL);
            }
            // The chest pad between the two rails.
            mesh.box(x - 0.13F, 0.56F, centre + 0.06F, x + 0.13F, 0.74F, centre + 0.14F, seats);
        }
    }

    private static void benchRow(CarMesh mesh, float centre, float[] seats, float[] trim) {
        float inner = TUB_HALF_WIDTH - TUB_WALL;
        mesh.box(-inner, FLOOR_TOP, centre - SEAT_DEPTH_BACK, inner, SEAT_TOP, centre + SEAT_DEPTH_FRONT, seats);
        float back = centre - SEAT_DEPTH_BACK;
        mesh.box(-inner, FLOOR_TOP, back - BACK_THICKNESS, inner, 0.62F, back, seats);
        mesh.box(-inner, 0.62F, back - BACK_THICKNESS - 0.01F, inner, 0.66F, back + 0.01F, trim);
        // One bar across the row, carried on the car's sides.
        float barZ = centre + SEAT_DEPTH_FRONT - 0.02F;
        mesh.box(-inner, 0.48F, barZ - 0.035F, inner, 0.53F, barZ + 0.035F, METAL);
        mesh.box(-inner, 0.48F, barZ - 0.06F, -inner + 0.06F, 0.66F, barZ + 0.06F, METAL);
        mesh.box(inner - 0.06F, 0.48F, barZ - 0.06F, inner, 0.66F, barZ + 0.06F, METAL);
    }
}
