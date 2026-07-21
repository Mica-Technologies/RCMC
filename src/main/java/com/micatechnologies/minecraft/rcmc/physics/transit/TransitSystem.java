package com.micatechnologies.minecraft.rcmc.physics.transit;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackWalk;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One world's transit: the authored stations and lines, and the trains currently in service.
 *
 * <p>This is the transit analogue of {@code RideElementSet} + {@code BlockSystems} — the object
 * world state owns, commands mutate, and the tick loop consults. Stations and lines are the
 * <em>persistent</em> content (saved with the track, since they are meaningless without it);
 * services are runtime state, exactly as trains themselves are, and vanish with them on
 * restart.</p>
 *
 * <p><b>Tick contract</b>, mirroring {@code BlockSystem}'s two-phase rule and for the same
 * iteration-order reason: call {@link #beginTick} once per tick — it snapshots the network
 * reference, refreshes every line's signal occupancy, and prunes services whose trains are gone
 * — then hand {@link #composedWith} to {@code TrainManager.tick}. A train in service is driven
 * by its {@link LineService}; every other train falls through to the wrapped control (coaster
 * elements, block brakes), so metro service and coaster hardware coexist in one world without
 * fighting over the same train.</p>
 *
 * <p>Names are case-insensitive and unique per kind. Pure Java; the NBT codec and the commands
 * live at the storage/command layer, keeping this testable on a bare JVM.</p>
 */
public final class TransitSystem {

    private final Map<String, TransitStation> stations = new LinkedHashMap<>();
    private final Map<String, TransitLine> lines = new LinkedHashMap<>();
    private final Map<String, LineSignals> signalsByLine = new LinkedHashMap<>();
    private final Map<Integer, LineService> services = new LinkedHashMap<>();

    /** Network reference for the tick in flight — set by {@link #beginTick}, read by the control. */
    private TrackNetwork tickNetwork;

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // --- Stations. -----------------------------------------------------------------------------

    public void addStation(TransitStation station) {
        if (station == null) {
            throw new IllegalArgumentException("station is required");
        }
        stations.put(key(station.name()), station);
    }

    /** Removes a station. Lines already created keep their own snapshot — see {@link TransitLine}. */
    public TransitStation removeStation(String name) {
        return stations.remove(key(name));
    }

    public TransitStation station(String name) {
        return stations.get(key(name));
    }

    public Collection<TransitStation> stations() {
        return Collections.unmodifiableCollection(stations.values());
    }

    // --- Lines. --------------------------------------------------------------------------------

    public void addLine(TransitLine line) {
        if (line == null) {
            throw new IllegalArgumentException("line is required");
        }
        lines.put(key(line.name()), line);
    }

    /** Removes a line, its signals, and takes every train serving it out of service. */
    public TransitLine removeLine(String name) {
        TransitLine removed = lines.remove(key(name));
        if (removed != null) {
            signalsByLine.remove(key(name));
            services.values().removeIf(service -> service.line() == removed);
        }
        return removed;
    }

    public TransitLine line(String name) {
        return lines.get(key(name));
    }

    public Collection<TransitLine> lines() {
        return Collections.unmodifiableCollection(lines.values());
    }

    /** Installs (or clears, with {@code null}) block signalling for a line. */
    public void setSignals(String lineName, LineSignals signals) {
        if (line(lineName) == null) {
            throw new IllegalArgumentException("no line named " + lineName);
        }
        if (signals == null) {
            signalsByLine.remove(key(lineName));
        } else {
            signalsByLine.put(key(lineName), signals);
        }
    }

    public LineSignals signalsFor(String lineName) {
        return signalsByLine.get(key(lineName));
    }

    // --- Services. -----------------------------------------------------------------------------

    /**
     * Enters a train into service on a line, targeting whichever of the line's stations is
     * nearest along the track from where the train currently stands — in either direction, so an
     * operator can start a service from anywhere on the line without caring which way the spawn
     * command happened to point the train.
     *
     * @throws IllegalArgumentException if no station on the line is reachable from the train —
     *                                  which means the train simply is not on this line's track
     */
    public LineService enterService(int trainId, Train train, TrackNetwork network,
                                    String lineName, TransitStopController controller) {
        TransitLine line = line(lineName);
        if (line == null) {
            throw new IllegalArgumentException("no line named " + lineName);
        }
        int bestIndex = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestFacing = 1.0D;
        for (int i = 0; i < line.stationCount(); i++) {
            for (double facing : new double[] {1.0D, -1.0D}) {
                double d = TrackWalk.distanceTo(network, train.reference(), facing,
                    line.station(i).stopPoint(), 10_000.0D);
                if (d < bestDistance) {
                    bestDistance = d;
                    bestIndex = i;
                    bestFacing = facing;
                }
            }
        }
        if (bestIndex < 0) {
            throw new IllegalArgumentException("train " + trainId
                + " cannot reach any station of line " + line.name() + " — is it on this line's track?");
        }
        LineService service = new LineService(line, controller, bestIndex, 1, bestFacing);
        services.put(trainId, service);
        // A train sitting at rest before service has usually already latched VALLEYED (zero
        // force, zero speed, nothing claiming it) — and TrainManager skips faulted trains before
        // any control is consulted, so the service alone could never move it. Taking control IS
        // the recovery: setHeld(true) both marks the intent and clears the stall, per Train.
        train.setHeld(true);
        return service;
    }

    /** Takes a train out of service. The train keeps rolling under whatever else controls it. */
    public LineService exitService(int trainId) {
        return services.remove(trainId);
    }

    public LineService serviceFor(int trainId) {
        return services.get(trainId);
    }

    public Map<Integer, LineService> services() {
        return Collections.unmodifiableMap(services);
    }

    public boolean hasServices() {
        return !services.isEmpty();
    }

    public boolean isEmpty() {
        return stations.isEmpty() && lines.isEmpty() && services.isEmpty();
    }

    /**
     * Whether a rider may board this train right now: always, for a train not in metro service;
     * only while the doors are open, for one that is. The entity's interact handler asks this.
     */
    public boolean mayBoard(int trainId) {
        LineService service = services.get(trainId);
        return service == null || service.controller().doorsOpen();
    }

    // --- Tick. ---------------------------------------------------------------------------------

    /** Once per tick, before {@link #composedWith}'s result is used — see the class javadoc. */
    public void beginTick(TrainManager trains, TrackNetwork network) {
        this.tickNetwork = network;
        // A service whose train was removed must not linger and grab a recycled train id later.
        for (Iterator<Integer> it = services.keySet().iterator(); it.hasNext(); ) {
            if (trains.train(it.next()) == null) {
                it.remove();
            }
        }
        for (LineSignals signals : signalsByLine.values()) {
            signals.updateOccupancy(trains);
        }
    }

    /**
     * The control handed to {@code TrainManager.tick}: trains in service are driven by their
     * service (with their line's movement authority, when signalled); everything else falls
     * through to {@code fallback} — which may be {@code null} for "no other control".
     */
    public TrainManager.ExternalAcceleration composedWith(TrainManager.ExternalAcceleration fallback) {
        return new TrainManager.ExternalAcceleration() {
            @Override
            public double forTrain(int trainId, Train train) {
                LineService service = services.get(trainId);
                if (service == null) {
                    return fallback == null ? 0.0D : fallback.forTrain(trainId, train);
                }
                LineSignals signals = signalsByLine.get(key(service.line().name()));
                double authority = signals == null
                    ? TrainDriver.NO_STOP
                    : signals.authorityFor(trainId, train, tickNetwork, service.facing());
                return service.tick(train, tickNetwork, authority);
            }

            @Override
            public boolean isHolding(int trainId, Train train) {
                LineService service = services.get(trainId);
                if (service == null) {
                    return fallback != null && fallback.isHolding(trainId, train);
                }
                return service.isHolding(train);
            }
        };
    }

    @Override
    public String toString() {
        return "TransitSystem{" + stations.size() + " stations, " + lines.size()
            + " lines, " + services.size() + " in service}";
    }
}
