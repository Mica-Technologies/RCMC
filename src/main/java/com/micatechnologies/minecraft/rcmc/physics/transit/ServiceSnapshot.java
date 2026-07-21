package com.micatechnologies.minecraft.rcmc.physics.transit;

/**
 * The slice of one running {@link LineService} that signage needs: which line, which way, and
 * which stop it is running to next. This is what goes over the wire to clients — an arrival
 * board derives "N stops away" from it with {@link ArrivalEstimator}, so no controller state,
 * position, or timing ever needs syncing.
 *
 * <p>Immutable, pure Java.</p>
 */
public final class ServiceSnapshot {

    private final String lineName;
    private final int serviceDirection;
    private final int nextStopIndex;
    private final boolean atPlatform;

    public ServiceSnapshot(String lineName, int serviceDirection, int nextStopIndex,
                           boolean atPlatform) {
        if (lineName == null || lineName.isEmpty()) {
            throw new IllegalArgumentException("lineName is required");
        }
        if (serviceDirection != 1 && serviceDirection != -1) {
            throw new IllegalArgumentException("serviceDirection must be +1 or -1, got " + serviceDirection);
        }
        if (nextStopIndex < 0) {
            throw new IllegalArgumentException("nextStopIndex must be >= 0, got " + nextStopIndex);
        }
        this.lineName = lineName;
        this.serviceDirection = serviceDirection;
        this.nextStopIndex = nextStopIndex;
        this.atPlatform = atPlatform;
    }

    /** Snapshot of a live service. */
    public static ServiceSnapshot of(LineService service) {
        return new ServiceSnapshot(service.line().name(), service.serviceDirection(),
            service.currentStopIndex(),
            service.controller().phase() != TransitStopController.Phase.APPROACHING);
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

    @Override
    public String toString() {
        return "ServiceSnapshot{" + lineName + " dir=" + serviceDirection
            + " nextStop=" + nextStopIndex + '}';
    }
}
