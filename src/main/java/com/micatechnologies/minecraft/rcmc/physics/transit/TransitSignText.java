package com.micatechnologies.minecraft.rcmc.physics.transit;

/**
 * The words every piece of transit signage says, in one place.
 *
 * <p>Platform arrival boards, in-car destination signs and station announcement speakers all
 * describe the same thing — a service, its direction, its destination terminus, and how close it
 * is — and they must never disagree, because a board reading "2 stops away" while the speaker says
 * "now approaching" reads as a fault. So the phrasing lives here, pure and shared, driven off the
 * same {@link ArrivalEstimator} stop count, and unit-tested rather than trusted to three renderers
 * to keep in step by hand.</p>
 *
 * <p>Minecraft-free: it composes strings from the transit model and nothing else.</p>
 */
public final class TransitSignText {

    private TransitSignText() {
        throw new AssertionError("No instances.");
    }

    /**
     * The direction-and-destination label a board groups its rows under: {@code "OUTBOUND/Alewife"},
     * or just the direction label on a loop line that has no terminus.
     */
    public static String destinationLabel(TransitLine line, int serviceDirection) {
        String direction = line.labelFor(serviceDirection);
        String terminus = line.terminusName(serviceDirection);
        return terminus == null ? direction : direction + "/" + terminus;
    }

    /**
     * What an in-car sign shows beneath the next stop: the line and where this train is bound —
     * {@code "Red Line  OUTBOUND/Alewife"}.
     */
    public static String carDestination(TransitLine line, int serviceDirection) {
        return line.name() + "  " + destinationLabel(line, serviceDirection);
    }

    /**
     * The destination shown on a car's <em>exterior</em> side sign — the terminus, in the amber
     * dot-matrix a platform reads it in: {@code "FOREST HILLS"}. Uppercased because every real one
     * is. A loop line has no terminus, so it falls back to the line's own name.
     */
    public static String exteriorDestination(TransitLine line, int serviceDirection) {
        String terminus = line.terminusName(serviceDirection);
        return (terminus == null ? line.name() : terminus)
            .toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * The short arrival phrase a board row shows, from a raw {@link ArrivalEstimator} stop count
     * (0 = this station is the service's next stop) and whether the train is berthed here now:
     * {@code "Boarding"}, {@code "now approaching"}, {@code "1 stop away"}, {@code "3 stops away"},
     * or {@code null} if the service never reaches this station.
     *
     * <p>Counts are raw — a train whose next stop is this station reads "now approaching", one stop
     * before it "1 stop away". This is the same scale the announcement uses, which is the whole
     * point of routing both through one function.</p>
     */
    public static String stopsLabel(int rawStopsAway, boolean atPlatform) {
        if (rawStopsAway < 0) {
            return null;
        }
        if (rawStopsAway == 0) {
            return atPlatform ? "Boarding" : "now approaching";
        }
        return rawStopsAway == 1 ? "1 stop away" : rawStopsAway + " stops away";
    }

    /**
     * The full spoken announcement for a station speaker:
     * {@code "The next OUTBOUND Red Line train to Alewife is now approaching."} Returns {@code null}
     * when the service does not reach this station (nothing to announce).
     *
     * <p>Kept in lock-step with {@link #stopsLabel} by sharing the same {@code rawStopsAway}/
     * {@code atPlatform} thresholds: a board saying "now approaching" and a speaker saying
     * "is now approaching" are the same event described two ways.</p>
     */
    public static String announcement(TransitLine line, int serviceDirection, int rawStopsAway,
                                      boolean atPlatform) {
        if (rawStopsAway < 0) {
            return null;
        }
        StringBuilder sentence = new StringBuilder("The next ")
            .append(line.labelFor(serviceDirection))
            .append(' ').append(line.name()).append(" train");
        String terminus = line.terminusName(serviceDirection);
        if (terminus != null) {
            sentence.append(" to ").append(terminus);
        }
        if (rawStopsAway == 0) {
            sentence.append(atPlatform ? " is now arriving" : " is now approaching");
        } else if (rawStopsAway == 1) {
            sentence.append(" is one stop away");
        } else {
            sentence.append(" is ").append(rawStopsAway).append(" stops away");
        }
        return sentence.append('.').toString();
    }
}
