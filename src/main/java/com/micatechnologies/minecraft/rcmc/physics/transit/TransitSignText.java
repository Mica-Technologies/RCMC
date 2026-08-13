package com.micatechnologies.minecraft.rcmc.physics.transit;

/**
 * The words every piece of transit signage says, in one place.
 *
 * <p>Platform arrival boards, in-car destination signs and station announcement speakers all
 * describe the same thing — a service, its direction, its destination terminus, and how close it
 * is — and they must never disagree, because a board reading "2 stops away" while the speaker says
 * "Approaching" reads as a fault. So the phrasing lives here, pure and shared, driven off the
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
     * The direction-and-destination label a board groups its rows under: {@code "OUT/Alewife"}, or
     * just the direction on a loop line that has no terminus.
     *
     * <p><b>Where a train is going matters more than which way it is pointed</b>, which is why the
     * terminus gets the room and the direction is cut to its stem. A real platform board drops the
     * direction entirely and shows only {@code "Alewife"} — it can, because the platform you are
     * standing on already answers it. One board here serves a whole station, several lines and both
     * directions at once, so the direction has to survive in some form; it does not have to survive
     * at full length.</p>
     */
    public static String destinationLabel(TransitLine line, int serviceDirection) {
        String direction = shortDirection(line.labelFor(serviceDirection));
        String terminus = line.terminusName(serviceDirection);
        return terminus == null ? direction : direction + "/" + terminus;
    }

    /**
     * A direction label with its {@code BOUND} dropped: {@code OUTBOUND} to {@code OUT},
     * {@code NORTHBOUND} to {@code NORTH}.
     *
     * <p>The stem is what carries the meaning and the suffix is the same on every one of them, so
     * cutting it costs a reader nothing and buys five characters of destination on every row. Any
     * label that is not built that way — an operator's {@code "toward Airport"} — is left exactly
     * as authored, because there is no rule that shortens it without guessing.</p>
     */
    private static String shortDirection(String label) {
        int suffix = "BOUND".length();
        if (label.length() > suffix
            && label.regionMatches(true, label.length() - suffix, "BOUND", 0, suffix)) {
            return label.substring(0, label.length() - suffix);
        }
        return label;
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
     * {@code "Boarding"}, {@code "Approaching"}, {@code "1 stop"}, {@code "3 stops"}, or
     * {@code null} if the service never reaches this station.
     *
     * <p>Counts are raw — a train whose next stop is this station reads "Approaching", one stop
     * before it "1 stop". This is the same scale {@link #announcement} uses, which is the whole
     * point of routing both through one function.</p>
     *
     * <p><b>Terse on purpose, and only here.</b> A board row already carries a direction and a
     * destination before this is appended, and every word costs panel width that the destination
     * needs more. The spoken announcement says "is two stops away" in full, because a sentence read
     * aloud has no width to run out of — same event, same thresholds, phrased for its medium.</p>
     */
    public static String stopsLabel(int rawStopsAway, boolean atPlatform) {
        if (rawStopsAway < 0) {
            return null;
        }
        if (rawStopsAway == 0) {
            return atPlatform ? "Boarding" : "Approaching";
        }
        return rawStopsAway == 1 ? "1 stop" : rawStopsAway + " stops";
    }

    /**
     * {@link #stopsLabel(int, boolean)} with the berth the train is pulling into named after it —
     * {@code "Boarding (2)"} — for a board at an island platform, where "which side of the island"
     * is the question a rider actually needs answered.
     *
     * <p>The berth is appended only when {@code rawStopsAway} is zero, because that is the only
     * count for which it is <em>this</em> station's berth: a service's platform is resolved at the
     * stop it is running to, so for a train further out the label names a platform somewhere else
     * on the line. Showing it anyway would send riders across the concourse on the strength of a
     * number about a different station.</p>
     *
     * <p>It is also dropped when it only repeats the direction the row is already grouped under —
     * a berth labelled {@code Outbound} beneath an {@code OUTBOUND/Alewife} heading is a word of
     * screen width bought for nothing.</p>
     *
     * @param platformLabel the berth's label, or empty/{@code null} when it has none
     * @param directionLabel what the row is already grouped under, from
     *                       {@link TransitLine#labelFor(int)}
     */
    public static String stopsLabel(int rawStopsAway, boolean atPlatform, String platformLabel,
                                    String directionLabel) {
        String phrase = stopsLabel(rawStopsAway, atPlatform);
        if (phrase == null || rawStopsAway != 0
            || platformLabel == null || platformLabel.isEmpty()
            || platformLabel.equalsIgnoreCase(directionLabel)) {
            return phrase;
        }
        return phrase + " (" + platformLabel + ")";
    }

    /**
     * The in-car announcement made shortly after departure, naming the station the train is now
     * running to: {@code "Next stop: Alewife."} The station name is the one the service is bound
     * for next, resolved by the caller.
     */
    public static String nextStopAnnouncement(String stationName) {
        return "Next stop: " + stationName + ".";
    }

    /**
     * The in-car announcement made as the doors open on arrival, naming the station just reached:
     * {@code "This is Alewife."}
     */
    public static String arrivalAnnouncement(String stationName) {
        return "This is " + stationName + ".";
    }

    /**
     * The in-car announcement made as a train runs into a station, naming it and the side the doors
     * will open: {@code "Entering Alewife. The doors will open on the left."}
     *
     * <p>Said <em>before</em> arrival, unlike {@link #arrivalAnnouncement}, because the point of it
     * is to give a standing passenger time to move to the right side of the car — an announcement
     * telling you which door to use as it opens is too late to be useful.</p>
     *
     * @param side which side opens <b>as this train sees it</b> — already converted through
     *             {@link DoorSide#asSeenFrom}, because a platform on the track's left is on the
     *             rider's right when the train runs the other way
     */
    public static String enteringAnnouncement(String stationName, DoorSide side) {
        String doors = side == DoorSide.BOTH
            ? "The doors will open on both sides."
            : "The doors will open on the " + side.spokenLabel() + ".";
        return "Entering " + stationName + ". " + doors;
    }

    /**
     * The full spoken announcement for a station speaker:
     * {@code "The next OUTBOUND Red Line train to Alewife is now approaching."} Returns {@code null}
     * when the service does not reach this station (nothing to announce).
     *
     * <p>Kept in lock-step with {@link #stopsLabel} by sharing the same {@code rawStopsAway}/
     * {@code atPlatform} thresholds: a board reading "Approaching" and a speaker saying
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
