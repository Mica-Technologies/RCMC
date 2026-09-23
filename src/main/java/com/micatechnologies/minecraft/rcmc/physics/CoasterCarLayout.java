package com.micatechnologies.minecraft.rcmc.physics;

/**
 * Where the seats are in a coaster car: rows two abreast, spread along the car behind its nose.
 *
 * <p>The one place this is decided. The car model draws its seats here and a rider is seated here,
 * and those were two separate guesses before — the model drew two rows while riders were stacked on
 * the car's centreline, one to a car, because nothing said where a second rider would go.</p>
 *
 * <p>Distances are blocks in the car's own frame: across positive to the car's right, along positive
 * toward its front, both from the car's centre on the track centreline.</p>
 */
public final class CoasterCarLayout {

    /** Riders per row. */
    public static final int ABREAST = 2;

    /** Half the distance between the two riders of a row, centre to centre. */
    public static final double SEAT_HALF_SPACING = 0.29D;

    /** Clear length at the front of the car before the first row: the nose or front cowl. */
    public static final double FRONT_CLEARANCE = 0.30D;

    /** Clear length behind the last row: the rear bulkhead. */
    public static final double REAR_CLEARANCE = 0.12D;

    /**
     * Height of a seated rider above the track centreline — the cushion top every model builds its
     * seats to, and where a rider is placed.
     */
    public static final double SEAT_HEIGHT = 0.31D;

    private CoasterCarLayout() {
    }

    /** Rows in a car of {@code spec}: half its seats, at least one. */
    public static int rows(TrainSpec spec) {
        return Math.max(1, spec.seatsPerCar() / ABREAST);
    }

    /** Riders a car of {@code spec} carries: every seat of every row. */
    public static int capacity(TrainSpec spec) {
        return rows(spec) * ABREAST;
    }

    /** Distance between the centres of adjacent rows. */
    public static double rowPitch(TrainSpec spec) {
        return usableLength(spec) / rows(spec);
    }

    /** Where row {@code row} (0 at the front) is centred along the car. */
    public static double rowCentre(TrainSpec spec, int row) {
        double front = spec.carLength() * 0.5D - FRONT_CLEARANCE;
        return front - rowPitch(spec) * (row + 0.5D);
    }

    /** Across offset of seat {@code index}: even seats on the right of their row, odd on the left. */
    public static double across(int index) {
        return (index % 2 == 0 ? 1.0D : -1.0D) * SEAT_HALF_SPACING;
    }

    /** Along offset of seat {@code index}: its row's centre, filled front row first. */
    public static double along(TrainSpec spec, int index) {
        int row = Math.min(Math.max(0, index) / ABREAST, rows(spec) - 1);
        return rowCentre(spec, row);
    }

    private static double usableLength(TrainSpec spec) {
        return Math.max(0.5D, spec.carLength() - FRONT_CLEARANCE - REAR_CLEARANCE);
    }
}
