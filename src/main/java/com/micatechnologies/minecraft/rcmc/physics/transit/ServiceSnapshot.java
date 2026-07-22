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

    public ServiceSnapshot(int trainId, String lineName, int serviceDirection, int nextStopIndex,
                           boolean atPlatform, boolean doorsOpen, double doorFraction,
                           double distanceToNextStop) {
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
    }

    /** Snapshot of a live service. */
    public static ServiceSnapshot of(int trainId, LineService service) {
        return new ServiceSnapshot(trainId, service.line().name(), service.serviceDirection(),
            service.currentStopIndex(),
            service.controller().phase() != TransitStopController.Phase.APPROACHING,
            service.controller().doorsOpen(), service.controller().doorFraction(),
            service.distanceToNextStop());
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
    public double distanceToNextStop() {
        return distanceToNextStop;
    }

    @Override
    public String toString() {
        return "ServiceSnapshot{train " + trainId + " on " + lineName + " dir=" + serviceDirection
            + " nextStop=" + nextStopIndex + '}';
    }
}
