package com.micatechnologies.minecraft.rcmc.physics;

/**
 * A kind of train: which body it is drawn with, how its cars are proportioned and seated, how many
 * cars it runs, and its colours. Built in, or defined by a server in a JSON file.
 *
 * <p>A type only ever picks one of the bodies the mod draws — sit-down, over-the-shoulder, wooden
 * or metro — and sets numbers and colours for it, so a custom type needs nothing installed on a
 * client. The trains it builds carry their whole {@link TrainSpec}, so a type removed from the
 * server's files leaves the trains already built from it running exactly as they were.</p>
 */
public final class TrainType {

    /** Which drawn body a type uses. */
    public enum Body {
        SIT_DOWN("sit_down", TrainSpec.CarStyle.COASTER, TrainSpec.CoasterModel.SIT_DOWN),
        SHOULDER("shoulder", TrainSpec.CarStyle.COASTER, TrainSpec.CoasterModel.SHOULDER),
        WOODEN("wooden", TrainSpec.CarStyle.COASTER, TrainSpec.CoasterModel.WOODEN),
        METRO("metro", TrainSpec.CarStyle.METRO, TrainSpec.CoasterModel.SIT_DOWN);

        /** How a JSON file names it. */
        public final String word;
        public final TrainSpec.CarStyle style;
        public final TrainSpec.CoasterModel model;

        Body(String word, TrainSpec.CarStyle style, TrainSpec.CoasterModel model) {
            this.word = word;
            this.style = style;
            this.model = model;
        }

        /** By {@link #word}, case-insensitively; {@code null} for anything else. */
        public static Body byWord(String word) {
            for (Body body : values()) {
                if (body.word.equalsIgnoreCase(word)) {
                    return body;
                }
            }
            return null;
        }
    }

    public final String id;
    public final String name;
    public final Body body;
    public final double carLength;
    public final double couplingGap;
    public final int seatsPerCar;
    public final int defaultCars;
    public final int maxCars;
    /** Ordinals into {@code TrackPalette.Colour}, as {@link TrainSpec} stores them. */
    public final int bodyColour;
    public final int trimColour;
    public final int seatColour;
    /** Whether it came from a file rather than the mod. */
    public final boolean custom;

    public TrainType(String id, String name, Body body, double carLength, double couplingGap, int seatsPerCar,
                     int defaultCars, int maxCars, int bodyColour, int trimColour, int seatColour, boolean custom) {
        this.id = id;
        this.name = name;
        this.body = body;
        this.carLength = carLength;
        this.couplingGap = couplingGap;
        this.seatsPerCar = seatsPerCar;
        this.defaultCars = defaultCars;
        this.maxCars = maxCars;
        this.bodyColour = bodyColour;
        this.trimColour = trimColour;
        this.seatColour = seatColour;
        this.custom = custom;
    }

    /** Whether it runs as a coaster rather than a metro. */
    public boolean isCoaster() {
        return body.style == TrainSpec.CarStyle.COASTER;
    }

    /** A train of this type, {@code cars} long — held to the type's own limits. */
    public TrainSpec spec(int cars) {
        int count = Math.max(1, Math.min(maxCars, cars));
        return new TrainSpec(count, carLength, couplingGap, seatsPerCar, bodyColour, trimColour, seatColour,
            body.style, body.model);
    }

    @Override
    public String toString() {
        return id + " (" + name + ")";
    }
}
