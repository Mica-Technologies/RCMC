package com.micatechnologies.minecraft.rcmc.physics.transit;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackWalk;

/**
 * One train's service on a {@link TransitLine}: which station it is running to, which way, and
 * the per-tick glue between the route and the {@link TransitStopController}.
 *
 * <p>This is the layer the M2 controller deliberately left to "the caller": each tick it walks
 * the track ({@link TrackWalk}) to measure the distance to the current target station, hands
 * that — plus whatever movement authority signalling grants ({@link LineSignals}, or unlimited
 * when unsignalled) — to the controller, and watches {@code stopsServed} to know when to move on
 * to the next station. At the end of a non-loop line it reverses: service direction flips,
 * facing flips, and the previous station becomes the next one — a terminus turnback, using the
 * reverse running the driver already supports.</p>
 *
 * <p><b>Facing.</b> The one genuinely subtle piece of state. The controller needs the direction
 * of travel expressed along the <em>current section's</em> distance axis, and that axis flips
 * whenever the train crosses an END-to-END join — {@code Train} flips its velocity sign to
 * match, which is exactly why facing is resynced from the velocity sign whenever the train is
 * meaningfully moving. While it is stopped (berthed, or held at a signal) the last facing is
 * retained, and a terminus reversal flips it explicitly while stationary. The result is that
 * facing is always "the sign of the velocity the train has, or is about to be commanded to
 * have".</p>
 *
 * <p>Pure Java, deterministic, one instance per train in service. The wiring that maps a world's
 * trains to their services (and implements {@code TrainManager.ExternalAcceleration} across all
 * of them) is world-state integration, deliberately outside this class.</p>
 */
public final class LineService {

    /**
     * Above this speed the velocity sign is trusted as the current facing.
     *
     * <p>Package-visible because {@link TransitSystem#enterService} has to apply the same
     * threshold: it is choosing a facing that {@link #tick} will overwrite from the velocity sign
     * on the very first tick if the train is moving faster than this.</p>
     */
    static final double FACING_SPEED = 0.1D;

    /** How far ahead the route walk will look for the target station before giving up. */
    private static final double ROUTE_HORIZON = 10_000.0D;

    /**
     * How far behind the train the target station is still recognised as "just overshot" rather
     * than lost. Load-bearing, not cosmetic: {@code TrackWalk} only measures ahead, so without
     * this a train that slides a centimetre past its stop point would see the station as
     * unreachable, read "no stop", and cruise off down the line — or, on a loop, lap forever,
     * overshooting again each time round. Probing a short window behind first turns that into a
     * small negative remaining, which the controller already handles as "berth here".
     */
    private static final double OVERSHOOT_WINDOW = 16.0D;

    /**
     * Resolves a station name against the live registry.
     *
     * <p>A {@link TransitLine} snapshots its stations <em>by value</em>, which is exactly what lets
     * a line survive its stations being renamed or deleted — but it also means the line's copy of a
     * station is frozen at the moment the line was created. A berth added afterwards exists only in
     * the registry, so a service reading its own line's copy would never see the second platform of
     * an island and would keep berthing on the wrong track forever.</p>
     *
     * <p>Same reasoning {@code TransitSystem.doorSideFor} already applies to the door side: the
     * line owns the <em>route</em>, the registry owns each station's <em>current shape</em>.</p>
     */
    public interface StationLookup {
        TransitStation station(String name);
    }

    private final TransitLine line;
    private final TransitStopController controller;
    private final StationLookup stations;

    private int stopIndex;
    private int serviceDirection;
    private double facing;
    private int servedSeen;

    /**
     * Which berth of the current target station this train is running to.
     *
     * <p>Resolved once when the target is set and held until it changes, rather than re-derived
     * each tick. See {@link TransitStation#platformFor}: the question "which berth would this train
     * reach" only has an unambiguous answer while the train is still approaching. Once it is
     * berthed it has passed the stop point, and measuring forward again would pick the berth across
     * the island — flipping the door side at the worst possible moment.</p>
     */
    private TransitPlatform berth;

    /** Remaining distance to the current target station, from the last {@link #tick}. */
    private double stationRemaining = Double.POSITIVE_INFINITY;

    /**
     * @param line             the line being served
     * @param controller       the M2 stop controller doing the driving
     * @param initialStopIndex index of the first station to run to
     * @param serviceDirection {@code +1} to serve toward higher station indices, {@code -1} lower
     * @param initialFacing    travel direction along the train's <em>current</em> section axis
     *                         toward that first station — see {@link #facingToward} for a helper
     */
    public LineService(TransitLine line, TransitStopController controller,
                       int initialStopIndex, int serviceDirection, double initialFacing) {
        this(line, controller, initialStopIndex, serviceDirection, initialFacing, null);
    }

    /**
     * @param stations resolves stations against the live registry, or {@code null} to use the
     *                 line's own frozen copies — see {@link StationLookup}
     */
    public LineService(TransitLine line, TransitStopController controller,
                       int initialStopIndex, int serviceDirection, double initialFacing,
                       StationLookup stations) {
        this.stations = stations;
        if (line == null || controller == null) {
            throw new IllegalArgumentException("line and controller are required");
        }
        if (initialStopIndex < 0 || initialStopIndex >= line.stationCount()) {
            throw new IllegalArgumentException("initialStopIndex " + initialStopIndex
                + " out of range for " + line);
        }
        if (serviceDirection != 1 && serviceDirection != -1) {
            throw new IllegalArgumentException("serviceDirection must be +1 or -1, got " + serviceDirection);
        }
        if (initialFacing == 0.0D) {
            throw new IllegalArgumentException("initialFacing must be nonzero");
        }
        this.line = line;
        this.controller = controller;
        this.stopIndex = initialStopIndex;
        this.serviceDirection = serviceDirection;
        this.facing = initialFacing >= 0.0D ? 1.0D : -1.0D;
        this.servedSeen = controller.stopsServed();
    }

    /**
     * The facing (direction along {@code from}'s section axis) that reaches {@code station}
     * sooner — the sensible initialisation for a freshly entered service. Throws if the station
     * is unreachable both ways, which means the train simply is not on this line's track.
     */
    public static double facingToward(TrackNetwork network, TrackRef from, TransitStation station) {
        // Across every berth, not just the primary: the nearest way to "the station" is the nearest
        // way to any of its platforms, and on an island the primary may be the far one.
        double forward = station.distanceToNearestPlatform(network, from, 1.0D, ROUTE_HORIZON);
        double backward = station.distanceToNearestPlatform(network, from, -1.0D, ROUTE_HORIZON);
        if (Double.isInfinite(forward) && Double.isInfinite(backward)) {
            throw new IllegalArgumentException(
                "station " + station.name() + " is unreachable from " + from + " in either direction");
        }
        return forward <= backward ? 1.0D : -1.0D;
    }

    /**
     * The along-track acceleration to command this tick. Call exactly once per tick, exactly as
     * the controller demands.
     *
     * @param authorityRemaining movement authority from signalling, measured along the current
     *                           facing — {@link TrainDriver#NO_STOP} when unsignalled
     */
    public double tick(Train train, TrackNetwork network, double authorityRemaining) {
        double velocity = train.velocity();
        if (Math.abs(velocity) > FACING_SPEED) {
            facing = velocity >= 0.0D ? 1.0D : -1.0D;
        }

        TransitStation target = currentStation();
        if (berth == null) {
            berth = target.platformFor(network, train.reference(), facing, ROUTE_HORIZON);
        }
        TrackRef stop = berth.stopPoint();
        // Overshoot first — see OVERSHOOT_WINDOW. On a ring the two probes find the same point
        // from both sides, and the behind reading is the honest one.
        double behind = TrackWalk.distanceTo(
            network, train.reference(), -facing, stop, OVERSHOOT_WINDOW);
        double stationRemaining = Double.isInfinite(behind)
            ? TrackWalk.distanceTo(network, train.reference(), facing, stop, ROUTE_HORIZON)
            : -behind;
        this.stationRemaining = stationRemaining;

        double acceleration = controller.acceleration(
            velocity, facing, stationRemaining, authorityRemaining);

        if (controller.stopsServed() != servedSeen) {
            servedSeen = controller.stopsServed();
            advanceToNextStop();
        }
        return acceleration;
    }

    /** Steps the target station after a completed stop cycle, reversing at a terminus. */
    private void advanceToNextStop() {
        // The new target's berth is resolved on the next tick, from where the train stands as it
        // pulls away — far from the station it is now running to, which is the only position from
        // which "which berth would it reach" has one answer.
        berth = null;
        int next = stopIndex + serviceDirection;
        if (line.isLoop()) {
            stopIndex = Math.floorMod(next, line.stationCount());
            return;
        }
        if (next < 0 || next >= line.stationCount()) {
            serviceDirection = -serviceDirection;
            if (!line.turnsBackOnLoop()) {
                // Stub terminus: a dead end, so the train changes ends and leaves the way it came,
                // same track and same platform. The facing flip happens here, while the train is
                // stationary with its doors just closed — by the time it moves, the flipped facing
                // is what the controller commands and the velocity sign follows.
                facing = -facing;
            }
            // On a turnback loop the facing deliberately does NOT flip. The train keeps driving
            // forward, round the loop, and comes back on the other track — which is the whole
            // reason a station has a berth per direction, and why inbound and outbound can run at
            // the same time instead of taking turns down one rail. Only the service direction
            // reverses; the wheels never do.
            next = stopIndex + serviceDirection;
        }
        stopIndex = next;
    }

    /** Forward to {@code Train.setHeld} wiring, exactly like the controller's own method. */
    public boolean isHolding(Train train) {
        return controller.isHolding(train.velocity());
    }

    public TransitLine line() {
        return line;
    }

    public TransitStopController controller() {
        return controller;
    }

    /**
     * The current target as the <em>registry</em> has it, falling back to the line's frozen copy.
     *
     * <p>Matched by name, which is the same handle the line stores. A station renamed out from
     * under a running service simply is not found, and the line's copy carries the service to the
     * end of its route rather than stranding it — which is the whole point of storing by value.</p>
     */
    private TransitStation currentStation() {
        TransitStation snapshot = line.station(stopIndex);
        if (stations == null) {
            return snapshot;
        }
        TransitStation live = stations.station(snapshot.name());
        return live == null ? snapshot : live;
    }

    /** Index of the station currently being run to — what an M7 arrival board counts against. */
    public int currentStopIndex() {
        return stopIndex;
    }

    /**
     * The berth of the current target this train is running to, or {@code null} before the first
     * tick has resolved one. What the doors and the signage must read: at an island platform the
     * station's own answer is only right for whichever track happens to be listed first.
     */
    public TransitPlatform currentBerth() {
        return berth;
    }

    /** {@code +1} toward higher station indices, {@code -1} toward lower — see {@link TransitLine#labelFor}. */
    public int serviceDirection() {
        return serviceDirection;
    }

    /** Current travel direction along the train's current section axis. */
    public double facing() {
        return facing;
    }

    /**
     * Remaining track distance to the current target station as of the last tick — what a station
     * speaker uses to hold the "now approaching" call until the train is genuinely close, rather
     * than firing it the moment the station becomes the next stop.
     */
    public double distanceToNextStop() {
        return stationRemaining;
    }

    @Override
    public String toString() {
        return "LineService{" + line.name() + " -> " + line.station(stopIndex).name()
            + ", " + line.labelFor(serviceDirection) + '}';
    }
}
