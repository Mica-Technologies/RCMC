package com.micatechnologies.minecraft.rcmc.physics.transit;

/**
 * The slice of one running {@link LineService} that signage needs: which line, which way, and
 * which stop it is running to next. This is what goes over the wire to clients — an arrival
 * board derives "N stops away" from it with {@link ArrivalEstimator}, so no controller state,
 * position, or timing ever needs syncing.
 *
 * <p>The train id rides along so a snapshot can be matched to a specific train rather than only to
 * a line: an arrival board on a platform cares which <em>line</em> is coming, but the destination
 * sign inside a car has to find its own service among several running on the same line.</p>
 *
 * <p>Immutable, pure Java.</p>
 */
public final class ServiceSnapshot {

    private final int trainId;
    private final String lineName;
    private final int serviceDirection;
    private final int nextStopIndex;
    private final boolean atPlatform;
    private final boolean doorsOpen;
    private final double doorFraction;
    private final double distanceToNextStop;

    /**
     * Which side of the <b>track</b> the doors open at the current stop.
     *
     * <p>Track-relative, not rider-relative: the car model is drawn in the track's own axes, so
     * this is the form the renderer wants. Anything spoken to a passenger converts it through
     * {@link DoorSide#asSeenFrom} first. Sent rather than derived because a client has no station
     * registry to look it up in, and a line's own copy of a station can be stale — see
     * {@code TransitSystem.doorSideFor}.</p>
     */
    private final DoorSide doorSide;

    /**
     * What the signage calls the berth this train is running to, or empty when the berth has no
     * label — which is every station with a single platform, where there is nothing to
     * disambiguate.
     *
     * <p><b>It names the berth at {@link #nextStopIndex}, and only there.</b> A train four stops
     * out has a berth resolved at the station it is heading for, which says nothing about which
     * platform it will pull into further down the line. A board may therefore only show this
     * against a service that is due at that board's own station — everywhere else it would be
     * naming somebody else's platform.</p>
     */
    private final String platformLabel;

    public ServiceSnapshot(int trainId, String lineName, int serviceDirection, int nextStopIndex,
                           boolean atPlatform, boolean doorsOpen, double doorFraction,
                           double distanceToNextStop) {
        this(trainId, lineName, serviceDirection, nextStopIndex, atPlatform, doorsOpen,
            doorFraction, distanceToNextStop, DoorSide.BOTH, "");
    }

    public ServiceSnapshot(int trainId, String lineName, int serviceDirection, int nextStopIndex,
                           boolean atPlatform, boolean doorsOpen, double doorFraction,
                           double distanceToNextStop, DoorSide doorSide) {
        this(trainId, lineName, serviceDirection, nextStopIndex, atPlatform, doorsOpen,
            doorFraction, distanceToNextStop, doorSide, "");
    }

    public ServiceSnapshot(int trainId, String lineName, int serviceDirection, int nextStopIndex,
                           boolean atPlatform, boolean doorsOpen, double doorFraction,
                           double distanceToNextStop, DoorSide doorSide, String platformLabel) {
        if (lineName == null || lineName.isEmpty()) {
            throw new IllegalArgumentException("lineName is required");
        }
        if (serviceDirection != 1 && serviceDirection != -1) {
            throw new IllegalArgumentException("serviceDirection must be +1 or -1, got " + serviceDirection);
        }
        if (nextStopIndex < 0) {
            throw new IllegalArgumentException("nextStopIndex must be >= 0, got " + nextStopIndex);
        }
        this.trainId = trainId;
        this.lineName = lineName;
        this.serviceDirection = serviceDirection;
        this.nextStopIndex = nextStopIndex;
        this.atPlatform = atPlatform;
        this.doorsOpen = doorsOpen;
        this.doorFraction = Math.max(0.0D, Math.min(1.0D, doorFraction));
        this.distanceToNextStop = distanceToNextStop;
        this.doorSide = doorSide == null ? DoorSide.BOTH : doorSide;
        this.platformLabel = platformLabel == null ? "" : platformLabel;
    }

    /**
     * Snapshot of a live service.
     *
     * @param doorSide the current stop's TRACK-relative door side — resolved by the caller, which
     *                 is the only layer holding the station registry needed to look it up
     */
    public static ServiceSnapshot of(int trainId, LineService service, DoorSide doorSide) {
        return of(trainId, service, doorSide, "");
    }

    /**
     * Snapshot of a live service, naming the berth it is running to.
     *
     * @param doorSide      the current stop's TRACK-relative door side — resolved by the caller,
     *                      which is the only layer holding the station registry needed to look it up
     * @param platformLabel that berth's label, from the same lookup — see {@link #platformLabel()}
     *                      for the one place a board may show it
     */
    public static ServiceSnapshot of(int trainId, LineService service, DoorSide doorSide,
                                     String platformLabel) {
        return new ServiceSnapshot(trainId, service.line().name(), service.serviceDirection(),
            service.currentStopIndex(),
            service.controller().phase() != TransitStopController.Phase.APPROACHING,
            service.controller().doorsOpen(), service.controller().doorFraction(),
            service.distanceToNextStop(), doorSide, platformLabel);
    }

    /** The train running this service — how an in-car sign finds its own. */
    public int trainId() {
        return trainId;
    }

    public String lineName() {
        return lineName;
    }

    public int serviceDirection() {
        return serviceDirection;
    }

    public int nextStopIndex() {
        return nextStopIndex;
    }

    /** True while the train is berthed with its door cycle running at its next stop. */
    public boolean atPlatform() {
        return atPlatform;
    }

    /**
     * True only while the doors are actually open — the narrower window inside {@link #atPlatform},
     * which also covers the doors opening and closing. This is what the car model draws and what
     * decides whether a rider may walk aboard, so the two can never disagree.
     */
    public boolean doorsOpen() {
        return doorsOpen;
    }

    /** How far the leaves have travelled, 0 shut to 1 open — what the car model animates. */
    public double doorFraction() {
        return doorFraction;
    }

    /**
     * Remaining track distance to the train's next stop. Lets a station speaker hold the "now
     * approaching" announcement until the train is genuinely close, instead of the moment this
     * station becomes its next stop.
     */
    /** Which side of the track the doors open at the current stop. Never null. */
    public DoorSide doorSide() {
        return doorSide;
    }

    /**
     * What the signage calls the berth at {@link #nextStopIndex}, or empty when it has none.
     *
     * <p>Only meaningful to a board at that very station. See the field's note: it is the berth
     * this train is running <em>to</em>, not the berth it will use wherever you happen to be
     * standing.</p>
     */
    public String platformLabel() {
        return platformLabel;
    }

    public double distanceToNextStop() {
        return distanceToNextStop;
    }

    @Override
    public String toString() {
        return "ServiceSnapshot{train " + trainId + " on " + lineName + " dir=" + serviceDirection
            + " nextStop=" + nextStopIndex + '}';
    }
}
