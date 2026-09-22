package com.micatechnologies.minecraft.rcmc.physics.transit;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
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

    /**
     * Every line's installed signalling, keyed by the same normalised name {@link #line} uses —
     * what the codec writes. Keyed rather than a bare collection because a {@link LineSignals}
     * does not know which line it belongs to; the association lives here and nowhere else.
     */
    public Map<String, LineSignals> signals() {
        return Collections.unmodifiableMap(signalsByLine);
    }

    /** Every line that stops at a station of this name — what a sign linked to it renders. */
    public java.util.List<TransitLine> linesServing(String stationName) {
        java.util.List<TransitLine> serving = new java.util.ArrayList<>();
        for (TransitLine line : lines.values()) {
            if (line.indexOfStation(stationName) >= 0) {
                serving.add(line);
            }
        }
        return serving;
    }

    /**
     * Replaces the authored content (stations, lines, signals) wholesale — the client-side sync
     * path, mirroring how the track network itself is cleared and rebuilt from a full packet.
     * Services are untouched; a client has none.
     */
    public void replaceAuthoredFrom(TransitSystem incoming) {
        stations.clear();
        lines.clear();
        signalsByLine.clear();
        for (TransitStation station : incoming.stations()) {
            addStation(station);
        }
        for (TransitLine line : incoming.lines()) {
            addLine(line);
        }
        // Signals come across too: a client never simulates them, but this method's contract is
        // "the authored content, wholesale", and a copy that silently dropped a field would be a
        // trap for the next caller — the save path already round-trips them.
        signalsByLine.putAll(incoming.signals());
    }

    /** Wire-format snapshots of every running service, for {@code PacketServiceSync}. */
    public java.util.List<ServiceSnapshot> serviceSnapshots() {
        java.util.List<ServiceSnapshot> snapshots = new java.util.ArrayList<>(services.size());
        for (Map.Entry<Integer, LineService> entry : services.entrySet()) {
            TransitPlatform berth = berthFor(entry.getValue());
            snapshots.add(ServiceSnapshot.of(entry.getKey(), entry.getValue(),
                berth == null ? DoorSide.BOTH : berth.doorSide(),
                berth == null ? "" : berth.label()));
        }
        return snapshots;
    }

    /**
     * Which side of the <b>track</b> {@code service}'s doors open at its current stop.
     *
     * <p>Deliberately NOT converted to the train's own left and right. The car model is drawn in
     * the track's axes — {@code RenderCoasterCar} loads {@code (right, up, forward)} straight onto
     * the matrix stack, so model {@code +x} is {@code frame.right} whichever way the train is
     * running — and metro stock is double-ended, so it is not flipped when a service reverses.
     * Rendering therefore wants this answer as-is. Only the things said to a <em>person</em> want
     * it converted, and those call {@link DoorSide#asSeenFrom} at the point of use.</p>
     *
     * <p>The side belongs to the berth this train is pulling into, not to the station — see
     * {@link #berthFor}, which is where that is worked out.</p>
     */
    public DoorSide doorSideFor(LineService service) {
        TransitPlatform berth = berthFor(service);
        return berth == null ? DoorSide.BOTH : berth.doorSide();
    }

    /**
     * The berth {@code service} is running to.
     *
     * <p>Everything that is a property of the <em>track</em> rather than of the station — which
     * side the doors open, what the signage calls the platform — is read off this one answer, so
     * two of them can never end up describing different platforms of the same station.</p>
     *
     * <p><b>Resolved against the station registry, not against the line's own copy.</b> A
     * {@link TransitLine} snapshots its stations by value — which is what lets a line survive its
     * stations being renamed or deleted — so a berth authored, relabelled or re-sided after the
     * line was created lives only in the registry, and reading the line's copy would quietly serve
     * the old answer forever. The service's own berth is matched back in by stop point; the line's
     * copy is the fallback for a station since removed from the registry altogether, and the
     * primary platform the fallback for a service that has not resolved a berth yet.</p>
     *
     * <p><b>This is the berth at the service's current stop, and nowhere else.</b> A train four
     * stops out has resolved a berth at the station it is running to, which says nothing about
     * which platform it will use anywhere further along — see {@code RenderArrivalBoard}, which
     * shows the label only on rows whose service is due at the board's own station.</p>
     *
     * @return {@code null} only for a {@code null} service
     */
    public TransitPlatform berthFor(LineService service) {
        if (service == null) {
            return null;
        }
        TransitStation stop = service.line().station(service.currentStopIndex());
        TransitStation authoritative = station(stop.name());
        TransitStation resolved = authoritative == null ? stop : authoritative;
        // Reading the station's own first platform would answer for whichever berth happens to be
        // listed first, which at an island is a coin flip that opens the doors at the tunnel wall.
        TransitPlatform berth = service.currentBerth();
        if (berth == null) {
            return resolved.primary();
        }
        TransitPlatform current = resolved.platformAt(berth.stopPoint());
        return current == null ? berth : current;
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
        return enterService(trainId, train, network, lineName, controller, 0.0D);
    }

    /**
     * {@link #enterService(int, Train, TrackNetwork, String, TransitStopController)}, for a train
     * whose direction of travel is already known — a service being resumed from a save.
     *
     * <p><b>Why a resume needs this.</b> A train standing on a stop point is zero blocks from that
     * station in <em>both</em> directions, so a free choice of facing is decided by a rounding tie —
     * and the wrong side of it sends the train back down the track it arrived on. In play that ran
     * a Circle Line train backwards on the wrong track and an Airport Line train off the end of its
     * line, after an ordinary save and reload. The facing is a fact about how the train was moving,
     * not about the line, so unlike a saved stop index it cannot go stale when stations change.</p>
     *
     * @param preferredFacing {@code +1} or {@code -1} to consider only that direction of travel,
     *                        or {@code 0} to consider both; if the preferred direction reaches no
     *                        station at all, both are tried rather than refusing the service
     */
    public LineService enterService(int trainId, Train train, TrackNetwork network,
                                    String lineName, TransitStopController controller,
                                    double preferredFacing) {
        TransitLine line = line(lineName);
        if (line == null) {
            throw new IllegalArgumentException("no line named " + lineName);
        }
        if (preferredFacing != 0.0D) {
            double facing = preferredFacing > 0.0D ? 1.0D : -1.0D;
            LineService service = enterFacing(trainId, train, network, line, controller,
                new double[] {facing});
            if (service != null) {
                return service;
            }
        }
        LineService service = enterFacing(trainId, train, network, line, controller,
            candidateFacings(train));
        if (service == null) {
            throw new IllegalArgumentException("train " + trainId
                + " cannot reach any station of line " + line.name() + " — is it on this line's track?");
        }
        return service;
    }

    /** Enters service choosing among {@code facings}; {@code null} if none reaches a station. */
    private LineService enterFacing(int trainId, Train train, TrackNetwork network,
                                    TransitLine line, TransitStopController controller,
                                    double[] facings) {
        int bestIndex = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestFacing = 1.0D;
        for (int i = 0; i < line.stationCount(); i++) {
            for (double facing : facings) {
                // Nearest berth of this station, not the primary's: an island's two platforms are
                // on different tracks, and only one of them is the one this train can pull into.
                double d = live(line.station(i)).distanceToNearestPlatform(
                    network, train.reference(), facing, 10_000.0D);
                if (d < bestDistance) {
                    bestDistance = d;
                    bestIndex = i;
                    bestFacing = facing;
                }
            }
        }
        if (bestIndex < 0) {
            return null;
        }
        LineService service = new LineService(line, controller, bestIndex,
            serviceDirectionFrom(line, network, train.reference(), bestFacing, bestIndex), bestFacing,
            this::station);
        services.put(trainId, service);
        // A train sitting at rest before service has usually already latched VALLEYED (zero
        // force, zero speed, nothing claiming it) — and TrainManager skips faulted trains before
        // any control is consulted, so the service alone could never move it. Taking control IS
        // the recovery: setHeld(true) both marks the intent and clears the stall, per Train.
        train.setHeld(true);
        return service;
    }

    /**
     * The facings {@link #enterService} is allowed to consider for this train.
     *
     * <p>A train at rest may be sent either way, so both are open and the nearest station wins.
     * <b>A train already running may not.</b> {@link LineService#tick} resyncs facing from the
     * velocity sign on its very first tick, so a facing chosen against the direction of travel is
     * discarded immediately — while the service direction derived from it survives, leaving the
     * schedule counting one way round the line and the train driving the other. On a loop that is
     * the "metro skips stations" report: it serves one station per lap and drives past the rest,
     * and nothing ever corrects it.</p>
     *
     * <p>Searching only the direction the train is genuinely going costs nothing an operator would
     * want: a train doing line speed was never going to stop and reverse for a station behind it.
     * Trains spawn at rest, so this narrows the choice only for a service entered on a moving
     * train — which is what resuming a save made mid-run does.</p>
     */
    private static double[] candidateFacings(Train train) {
        double velocity = train.velocity();
        if (Math.abs(velocity) > LineService.FACING_SPEED) {
            return new double[] {velocity >= 0.0D ? 1.0D : -1.0D};
        }
        return new double[] {1.0D, -1.0D};
    }

    /**
     * Which way along the line's station order the train is actually pointing.
     *
     * <p><b>This used to be hardcoded to {@code +1}, and that was a real bug.</b> The first stop is
     * chosen as the nearest station <em>in either direction</em>, so the train may well be facing
     * against the order its stations are listed in. Serving stop {@code k} and then targeting
     * {@code k+1} regardless meant the next stop could be a whole lap away in the direction of
     * travel — so the train drove straight past every station between here and there. On a
     * <b>loop</b> that presents exactly as "the metro skips stations", and it became much easier to
     * hit once services resumed from a save, because a train reloading at a platform can pick either
     * facing depending on which side of the stop point it came to rest.</p>
     *
     * <p>Measured from the <em>train</em>, not from the target station: whichever neighbour of the
     * target the train would reach sooner, driving the way it is pointed, is the stop that comes
     * after it. Doing it from the station instead would need the facing translated onto that
     * station's own section axis, which is exactly the kind of sign bookkeeping
     * {@code TrackWalk} exists to keep out of callers.</p>
     *
     * <p>A shuttle whose nearest station is a terminus gets the only direction that exists, which is
     * also a small improvement: it used to start toward {@code +1} from the far end and rely on the
     * turnback to correct itself on arrival.</p>
     */
    /**
     * A line's frozen copy of a station, refreshed from the registry when it still exists there.
     *
     * <p>A line snapshots its stations by value so it survives them being renamed or deleted; the
     * registry is where a station's <em>current</em> shape lives. A berth added after the line was
     * created exists only in the registry, and routing that read the frozen copy would never see
     * the second platform of an island.</p>
     */
    private TransitStation live(TransitStation snapshot) {
        TransitStation authoritative = station(snapshot.name());
        return authoritative == null ? snapshot : authoritative;
    }

    private int serviceDirectionFrom(TransitLine line, TrackNetwork network, TrackRef from,
                                     double facing, int index) {
        if (line.stationCount() < 2) {
            return 1;
        }
        double toNext = neighbourDistance(line, network, from, facing, index + 1);
        double toPrevious = neighbourDistance(line, network, from, facing, index - 1);
        if (Double.isInfinite(toNext) && Double.isInfinite(toPrevious)) {
            // Neither neighbour is ahead: the train is running into the end of a stub line — the
            // buffer stop is ahead, the rest of the line behind. The service direction is the one
            // it arrived in, which is the way AWAY from the neighbour behind it; the terminus
            // turnback in LineService then reverses it on the way out. Defaulting to +1 here was
            // what sent a train reloaded at its first terminus straight on into the buffers.
            double behindNext = neighbourDistance(line, network, from, -facing, index + 1);
            double behindPrevious = neighbourDistance(line, network, from, -facing, index - 1);
            if (Double.isInfinite(behindNext) && Double.isInfinite(behindPrevious)) {
                return 1;
            }
            return behindNext <= behindPrevious ? -1 : 1;
        }
        return toNext <= toPrevious ? 1 : -1;
    }

    /** Distance to the station at {@code index}, wrapping on a loop; infinite if there is none. */
    private double neighbourDistance(TransitLine line, TrackNetwork network, TrackRef from,
                                     double facing, int index) {
        int resolved = index;
        if (line.isLoop()) {
            resolved = Math.floorMod(index, line.stationCount());
        }
        else if (index < 0 || index >= line.stationCount()) {
            return Double.POSITIVE_INFINITY;
        }
        return live(line.station(resolved))
            .distanceToNearestPlatform(network, from, facing, 10_000.0D);
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
