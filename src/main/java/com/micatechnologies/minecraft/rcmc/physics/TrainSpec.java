package com.micatechnologies.minecraft.rcmc.physics;

/**
 * The physical description of a train: how many cars, how long they are, and how they couple.
 *
 * <p>Immutable, and free of Minecraft types like everything else in this package, so a train's
 * behaviour can be tested without a game instance. Intended to be loaded from a data-driven car
 * definition (Phase 8.2) rather than constructed in code, once that exists.</p>
 */
public final class TrainSpec {

    private final int carCount;
    private final double carLength;
    private final double couplingGap;
    private final int seatsPerCar;

    /**
     * @param carCount    number of cars, at least 1
     * @param carLength   length of one car in blocks, measured between its bogie centres — that is
     *                    the distance that actually matters for how the train sits on a curve, and
     *                    it is shorter than the visible body
     * @param couplingGap gap between adjacent cars' reference points, in blocks
     * @param seatsPerCar riders per car
     */
    public TrainSpec(int carCount, double carLength, double couplingGap, int seatsPerCar) {
        if (carCount < 1) {
            throw new IllegalArgumentException("carCount must be >= 1, got " + carCount);
        }
        if (carLength <= 0.0D) {
            throw new IllegalArgumentException("carLength must be positive, got " + carLength);
        }
        if (couplingGap < 0.0D) {
            throw new IllegalArgumentException("couplingGap must be >= 0, got " + couplingGap);
        }
        this.carCount = carCount;
        this.carLength = carLength;
        this.couplingGap = couplingGap;
        this.seatsPerCar = seatsPerCar;
    }

    /** A single car, roughly the size of a standard coaster car. */
    public static TrainSpec singleCar() {
        return new TrainSpec(1, 3.0D, 0.0D, 4);
    }

    public int carCount() {
        return carCount;
    }

    public double carLength() {
        return carLength;
    }

    public double couplingGap() {
        return couplingGap;
    }

    public int seatsPerCar() {
        return seatsPerCar;
    }

    public int totalSeats() {
        return carCount * seatsPerCar;
    }

    /** Spacing between adjacent cars' reference points. */
    public double carPitch() {
        return carLength + couplingGap;
    }

    /**
     * Distance from the train's reference point (the lead car) back to car {@code index}.
     *
     * <p>Positive and increasing toward the rear. Callers subtract this from the reference
     * position, so car 0 sits exactly at the reference.</p>
     */
    public double offsetOfCar(int index) {
        if (index < 0 || index >= carCount) {
            throw new IndexOutOfBoundsException("car " + index + " of " + carCount);
        }
        return index * carPitch();
    }

    /** Distance from the lead car's reference point to the last car's. */
    public double totalLength() {
        return (carCount - 1) * carPitch() + carLength;
    }

    @Override
    public String toString() {
        return "TrainSpec{" + carCount + " cars, pitch=" + carPitch()
            + ", length=" + totalLength() + '}';
    }
}
