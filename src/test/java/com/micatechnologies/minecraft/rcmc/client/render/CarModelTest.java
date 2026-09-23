package com.micatechnologies.minecraft.rcmc.client.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.CarSeating;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The coaster car models: riders sit on the seats, the car rides on its rails without cutting
 * through the track, and each car type is recognisably the one it says it is.
 */
class CarModelTest {

    private static final float[] BODY = {0.8F, 0.1F, 0.1F};
    private static final float[] TRIM = {0.9F, 0.9F, 0.9F};
    private static final float[] SEATS = {0.1F, 0.1F, 0.6F};

    private static TrainSpec spec(TrainSpec.CoasterModel model) {
        return new TrainSpec(5, 3.0D, 0.5D, 4).withCoasterModel(model);
    }

    private static CarMesh car(TrainSpec.CoasterModel model, boolean lead) {
        return CarModel.build(spec(model), lead, true, BODY, TRIM, SEATS);
    }

    @ParameterizedTest
    @EnumSource(TrainSpec.CoasterModel.class)
    void everyRiderSitsOnACushion(TrainSpec.CoasterModel model) {
        TrainSpec spec = spec(model);
        CarMesh mesh = car(model, false);
        for (int seat = 0; seat < CarSeating.capacity(spec); seat++) {
            double x = CarSeating.acrossOffset(spec, seat);
            double y = CarSeating.seatHeight(spec);
            double z = CarSeating.alongOffset(spec, seat);
            assertTrue(onSeatTop(mesh, x, y, z), model + ": rider " + seat + " at (" + x + ", " + y
                + ", " + z + ") is not sitting on a seat");
        }
    }

    @ParameterizedTest
    @EnumSource(TrainSpec.CoasterModel.class)
    void nothingCutsThroughTheTrack(TrainSpec.CoasterModel model) {
        for (boolean lead : new boolean[] {true, false}) {
            for (CarMesh.Quad q : car(model, lead).quads()) {
                for (int c = 0; c < 4; c++) {
                    // Between and over the rails, nothing may come below the railheads: the rails,
                    // the ties and the spine are all down there.
                    if (Math.abs(q.x(c)) < 0.60F) {
                        assertTrue(q.y(c) >= CarModel.RAIL_TOP - 1.0e-4F, model + (lead ? " lead" : "")
                            + ": geometry at (" + q.x(c) + ", " + q.y(c) + ", " + q.z(c)
                            + ") is below the railheads");
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(TrainSpec.CoasterModel.class)
    void wheelsRestOnTheRails(TrainSpec.CoasterModel model) {
        // The lowest point over each railhead is a wheel touching it.
        for (float side : new float[] {1.0F, -1.0F}) {
            float lowest = Float.MAX_VALUE;
            for (CarMesh.Quad q : car(model, false).quads()) {
                for (int c = 0; c < 4; c++) {
                    if (Math.abs(q.x(c) - side * 0.55F) < 0.04F) {
                        lowest = Math.min(lowest, q.y(c));
                    }
                }
            }
            assertTrue(Math.abs(lowest - CarModel.RAIL_TOP) < 0.01F,
                model + ": lowest point over the " + (side > 0 ? "right" : "left") + " rail is " + lowest);
        }
    }

    @ParameterizedTest
    @EnumSource(TrainSpec.CoasterModel.class)
    void onlyTheLeadCarHasANose(TrainSpec.CoasterModel model) {
        float half = 1.5F;
        assertTrue(maxZ(car(model, true)) > half + 0.05F, model + ": the lead car's nose reaches ahead");
        assertTrue(maxZ(car(model, false)) <= half + 1.0e-4F, model + ": a trailing car ends at its length");
    }

    @org.junit.jupiter.api.Test
    void theCarTypesLookLikeWhatTheyAre() {
        // Over-the-shoulder seats carry headrests well above a sit-down car's seat backs.
        assertTrue(maxY(car(TrainSpec.CoasterModel.SHOULDER, false)) > 1.1F, "shoulder: tall seats");
        assertTrue(maxY(car(TrainSpec.CoasterModel.SIT_DOWN, false)) < 0.9F, "sit-down: low backs");
        // A wooden car's sides stand higher than a modern tub's.
        assertTrue(maxY(car(TrainSpec.CoasterModel.WOODEN, false)) > 0.7F
            && maxY(car(TrainSpec.CoasterModel.WOODEN, false)) < 0.8F, "wooden: high sides, low seats");
    }

    /** Whether some seat-coloured, level quad at height {@code y} covers {@code (x, z)}. */
    private static boolean onSeatTop(CarMesh mesh, double x, double y, double z) {
        for (CarMesh.Quad q : mesh.quads()) {
            if (q.colour != SEATS) {
                continue;
            }
            float minX = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE;
            float maxZ = -Float.MAX_VALUE;
            boolean level = true;
            for (int c = 0; c < 4; c++) {
                level &= Math.abs(q.y(c) - y) < 1.0e-3D;
                minX = Math.min(minX, q.x(c));
                maxX = Math.max(maxX, q.x(c));
                minZ = Math.min(minZ, q.z(c));
                maxZ = Math.max(maxZ, q.z(c));
            }
            if (level && x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                return true;
            }
        }
        return false;
    }

    private static float maxZ(CarMesh mesh) {
        float max = -Float.MAX_VALUE;
        for (CarMesh.Quad q : mesh.quads()) {
            for (int c = 0; c < 4; c++) {
                max = Math.max(max, q.z(c));
            }
        }
        return max;
    }

    private static float maxY(CarMesh mesh) {
        float max = -Float.MAX_VALUE;
        for (CarMesh.Quad q : mesh.quads()) {
            for (int c = 0; c < 4; c++) {
                max = Math.max(max, q.y(c));
            }
        }
        return max;
    }
}
