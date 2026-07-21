package com.micatechnologies.minecraft.rcmc.physics;

/**
 * The physical description of a train: how many cars, how long they are, and how they couple.
 *
 * <p>Immutable, and free of Minecraft types like everything else in this package, so a train's
 * behaviour can be tested without a game instance. Intended to be loaded from a data-driven car
 * definition (Phase 8.2) rather than constructed in code, once that exists.</p>
 */
public final class TrainSpec {

    /**
     * The parts of a car that can be coloured independently.
     *
     * <p>Mirrors {@code TrackPalette.Part} in intent but is deliberately its own enum: a train and a
     * track are painted separately in every park worth looking at, and sharing one enum would imply
     * a relationship that does not exist.</p>
     */
    public enum Part {
        BODY,
        TRIM,
        SEATS
    }

    /**
     * Which family of rolling stock this is. More than cosmetic: it selects both the model the
     * renderer emits and how the car body is placed on the track — see
     * {@code Train.bodyFrameOfCar}. A {@link #METRO} car is long enough that orienting its rigid
     * body from a single track point visibly cuts curves; its body is placed from its two bogies
     * instead. Coaster cars are short enough that the single-point placement they have always
     * used is indistinguishable, and keeping it avoids perturbing every existing ride.
     */
    public enum CarStyle {
        COASTER,
        METRO;

        /** Ordinal-indexed lookup for the wire format, clamped rather than trusted. */
        public static CarStyle byOrdinal(int ordinal) {
            CarStyle[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, ordinal))];
        }
    }

    private final int carCount;
    private final double carLength;
    private final double couplingGap;
    private final int seatsPerCar;
    private final CarStyle carStyle;

    /**
     * Paint, as ordinals into {@code TrackPalette.Colour}.
     *
     * <p>Stored as ordinals rather than the enum itself so this class stays in {@code physics},
     * which must not depend on {@code track}. That is a slightly awkward indirection and it is the
     * price of the layering: physics knows how a train moves, not what colour it is, and only the
     * renderer needs to resolve these back to actual colours.</p>
     */
    private final int bodyColour;
    private final int trimColour;
    private final int seatColour;

    /**
     * @param carCount    number of cars, at least 1
     * @param carLength   length of one car in blocks, measured between its bogie centres — that is
     *                    the distance that actually matters for how the train sits on a curve, and
     *                    it is shorter than the visible body
     * @param couplingGap gap between adjacent cars' reference points, in blocks
     * @param seatsPerCar riders per car
     */
    public TrainSpec(int carCount, double carLength, double couplingGap, int seatsPerCar) {
        this(carCount, carLength, couplingGap, seatsPerCar, 3, 4, 1);
    }

    /**
     * @param bodyColour ordinal into {@code TrackPalette.Colour}; see the field javadoc for why
     *                   this is an int rather than the enum
     */
    public TrainSpec(int carCount, double carLength, double couplingGap, int seatsPerCar,
                     int bodyColour, int trimColour, int seatColour) {
        this(carCount, carLength, couplingGap, seatsPerCar,
            bodyColour, trimColour, seatColour, CarStyle.COASTER);
    }

    public TrainSpec(int carCount, double carLength, double couplingGap, int seatsPerCar,
                     int bodyColour, int trimColour, int seatColour, CarStyle carStyle) {
        if (carCount < 1) {
            throw new IllegalArgumentException("carCount must be >= 1, got " + carCount);
        }
        if (carLength <= 0.0D) {
            throw new IllegalArgumentException("carLength must be positive, got " + carLength);
        }
        if (couplingGap < 0.0D) {
            throw new IllegalArgumentException("couplingGap must be >= 0, got " + couplingGap);
        }
        if (carStyle == null) {
            throw new IllegalArgumentException("carStyle is required");
        }
        this.carCount = carCount;
        this.carLength = carLength;
        this.couplingGap = couplingGap;
        this.seatsPerCar = seatsPerCar;
        this.bodyColour = bodyColour;
        this.trimColour = trimColour;
        this.seatColour = seatColour;
        this.carStyle = carStyle;
    }

    public int bodyColour() {
        return bodyColour;
    }

    public int trimColour() {
        return trimColour;
    }

    public int seatColour() {
        return seatColour;
    }

    public int colourOf(Part part) {
        switch (part) {
            case TRIM:
                return trimColour;
            case SEATS:
                return seatColour;
            case BODY:
            default:
                return bodyColour;
        }
    }

    /** A copy with one part repainted. Immutable like the rest of the spec. */
    public TrainSpec withColour(Part part, int colour) {
        return new TrainSpec(carCount, carLength, couplingGap, seatsPerCar,
            part == Part.BODY ? colour : bodyColour,
            part == Part.TRIM ? colour : trimColour,
            part == Part.SEATS ? colour : seatColour,
            carStyle);
    }

    /** A single car, roughly the size of a standard coaster car. */
    public static TrainSpec singleCar() {
        return new TrainSpec(1, 3.0D, 0.0D, 4);
    }

    /**
     * The metro presets below are proportioned from real North American rolling stock at the
     * mod's 1 block ≈ 1 metre scale. {@code carLength} is bogie-centre ("truck centre") spacing
     * per this class's convention; US stock places trucks at roughly 72% of body length, so the
     * renderer derives the visible body as {@code carLength / 0.72} and the coupling gap here is
     * sized to hold ~0.7 blocks of daylight between bodies. All three are ~3 blocks wide in
     * model terms — real heavy-rail bodies run 2.7–3.05 m.
     */

    /**
     * Compact metro car, proportioned like NYC Subway A-Division stock (R142: 15.65 m long,
     * 2.68 m wide) — the small profile for tight, twisty tunnels. Stainless body, dark trim.
     */
    public static TrainSpec metroTrainCompact(int carCount) {
        return new TrainSpec(carCount, 11.3D, 5.1D, 8, 0, 1, 5, CarStyle.METRO);
    }

    /**
     * Standard metro car, proportioned like MBTA Orange Line stock (CRRC, 65 ft class ≈ 19.8 m)
     * — silver body, orange trim band, the default metro consist.
     */
    public static TrainSpec metroTrain(int carCount) {
        return new TrainSpec(carCount, 14.3D, 6.2D, 10, 0, 4, 1, CarStyle.METRO);
    }

    /**
     * Long metro car, proportioned like LA Metro B/D Line stock (HR4000: 75 ft / 22.86 m long,
     * 10 ft / 3.05 m wide — NYC B-Division 75-footers share the length). White body, red trim.
     */
    public static TrainSpec metroTrainLong(int carCount) {
        return new TrainSpec(carCount, 16.5D, 7.1D, 12, 2, 3, 8, CarStyle.METRO);
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

    public CarStyle carStyle() {
        return carStyle;
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
