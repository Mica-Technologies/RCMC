package com.micatechnologies.minecraft.rcmc.physics.transit;

/**
 * Which side of a train its doors open on at a given station.
 *
 * <h2>Which "left" this is</h2>
 *
 * <p><b>Stored against the track, not against the train.</b> {@link #LEFT} and {@link #RIGHT} are
 * relative to the track's own axis — what a train travelling in the direction of increasing
 * distance would call left and right. That is the only frame in which the answer is a property of
 * the <em>station</em>: a shuttle serves the same platform in both directions, and a loop can be
 * run either way round, so a side stored as the train sees it would be wrong half the time.</p>
 *
 * <p>{@link #asSeenFrom(double)} converts to the rider's left and right, which is what an
 * announcement says and what the model animates. Every use that faces a person goes through it.
 * This is exactly the class of sign error the geometry layer keeps warning about, so the
 * conversion lives in one method rather than at each call site.</p>
 *
 * <p>Pure Java, like the rest of {@code physics.transit}.</p>
 */
public enum DoorSide {

    /** Platform on the track's left — the negative side of the frame's right axis. */
    LEFT,

    /** Platform on the track's right — the positive side of the frame's right axis. */
    RIGHT,

    /**
     * Platforms both sides, so everything opens.
     *
     * <p>The default for a station nobody has told otherwise, and deliberately so: it is the
     * behaviour every station had before sides existed, and a door that opens onto nothing is a
     * cosmetic oddity where a door that refuses to open strands a passenger.</p>
     */
    BOTH;

    /** Ordinal-indexed lookup for the wire and save formats, clamped rather than trusted. */
    public static DoorSide byOrdinal(int ordinal) {
        DoorSide[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, ordinal))];
    }

    /**
     * This side as a train travelling with {@code facing} would describe it.
     *
     * @param facing {@code +1} travelling along increasing distance, {@code -1} against it
     */
    public DoorSide asSeenFrom(double facing) {
        if (this == BOTH || facing >= 0.0D) {
            return this;
        }
        return this == LEFT ? RIGHT : LEFT;
    }

    public boolean opensLeft() {
        return this == LEFT || this == BOTH;
    }

    public boolean opensRight() {
        return this == RIGHT || this == BOTH;
    }

    /** How an announcement names this side: "left", "right", or "both sides". */
    public String spokenLabel() {
        switch (this) {
            case LEFT:
                return "left";
            case RIGHT:
                return "right";
            case BOTH:
            default:
                return "both sides";
        }
    }

    /** Parses a command argument, or {@code null} if it names no side. */
    public static DoorSide parse(String argument) {
        if (argument == null) {
            return null;
        }
        switch (argument.toLowerCase(java.util.Locale.ROOT)) {
            case "left":
                return LEFT;
            case "right":
                return RIGHT;
            case "both":
                return BOTH;
            default:
                return null;
        }
    }
}
