package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.block.BlockLineDesk;
import com.micatechnologies.minecraft.rcmc.net.LineView;
import com.micatechnologies.minecraft.rcmc.net.PacketLineAction.Action;
import com.micatechnologies.minecraft.rcmc.net.PacketLineView;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.physics.TrainType;
import com.micatechnologies.minecraft.rcmc.physics.TrainTypes;
import com.micatechnologies.minecraft.rcmc.physics.transit.LineDepot;
import com.micatechnologies.minecraft.rcmc.physics.transit.LineOperations;
import com.micatechnologies.minecraft.rcmc.physics.transit.LineService;
import com.micatechnologies.minecraft.rcmc.physics.transit.LineSignals;
import com.micatechnologies.minecraft.rcmc.physics.transit.ServiceStatus;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitDrives;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitPlatform;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The server side of the line control desk: which line each operator is looking at, and what each
 * press does to it.
 *
 * <p>A desk is a control room, not a platform console: it runs any line. It opens on the line that
 * serves the station nearest it, and the operator steps through the rest from there. Everything it
 * does already had a command — {@code /rcmc line set}, {@code line start}, {@code train remove} —
 * and goes through the same calls, so the desk and the commands cannot drift apart.</p>
 */
public final class LineControl {

    /** How far from the desk an operator may stand and still work it, blocks. */
    private static final double REACH = 10.0D;

    /** Dwell and headway move in steps of this many seconds. */
    private static final int STEP_SECONDS = 5;

    /** Line speed a train added from the desk runs at, blocks/s: the same as {@code /rcmc line start}. */
    private static final double CRUISE = 15.0D;

    private static final String TRAIN_TYPE = "metro";

    private static final class Session {
        final int dimension;
        final BlockPos desk;
        String line;
        int cars;

        Session(int dimension, BlockPos desk, String line, int cars) {
            this.dimension = dimension;
            this.desk = desk;
            this.line = line;
            this.cars = cars;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private LineControl() {
    }

    /** A player used the desk at {@code desk}: open it on the line serving the nearest station. */
    public static void open(EntityPlayerMP player, BlockPos desk) {
        RcmcWorldState state = RcmcWorldState.of(player.world);
        if (state == null) {
            return;
        }
        TransitSystem transit = state.transit();
        String line = nearestLine(state, desk);
        TrainType type = TrainTypes.get(TRAIN_TYPE);
        Session session = new Session(player.dimension, desk.toImmutable(), line,
            type == null ? 3 : type.defaultCars);
        SESSIONS.put(player.getUniqueID(), session);
        String message = transit.lines().isEmpty()
            ? "No metro lines yet: build one with the transit tool." : "";
        RcmcNetwork.sendTo(new PacketLineView(viewOf(state, session, message), true), player);
    }

    public static void handle(EntityPlayerMP player, Action action, int train) {
        Session session = SESSIONS.get(player.getUniqueID());
        if (session == null || session.dimension != player.dimension
            || player.getDistanceSq(session.desk) > REACH * REACH
            || !(player.world.getBlockState(session.desk).getBlock() instanceof BlockLineDesk)) {
            return;
        }
        RcmcWorldState state = RcmcWorldState.of(player.world);
        if (state == null) {
            return;
        }
        String message = apply(player.world, state, session, action, train);
        RcmcNetwork.sendTo(new PacketLineView(viewOf(state, session, message), false), player);
    }

    private static String apply(World world, RcmcWorldState state, Session session, Action action, int train) {
        TransitSystem transit = state.transit();
        List<TransitLine> lines = new ArrayList<>(transit.lines());
        if (lines.isEmpty()) {
            return "No metro lines yet: build one with the transit tool.";
        }
        TransitLine line = transit.line(session.line);
        if (line == null) {
            line = lines.get(0);
            session.line = line.name();
        }
        LineOperations operations = transit.operationsFor(line.name());
        switch (action) {
            case PREVIOUS_LINE:
            case NEXT_LINE: {
                int at = lines.indexOf(line);
                int step = action == Action.NEXT_LINE ? 1 : -1;
                session.line = lines.get(Math.floorMod(at + step, lines.size())).name();
                return "";
            }
            case DWELL_DOWN:
            case DWELL_UP: {
                int seconds = Math.max(0, Math.min(LineOperations.MAX_TICKS / 20,
                    operations.dwellTicks() / 20 + (action == Action.DWELL_UP ? STEP_SECONDS : -STEP_SECONDS)));
                transit.setOperations(line.name(), operations.withDwellTicks(seconds * 20));
                state.markTrackDirty(world);
                return "Dwell " + seconds + " s, from each train's next stop.";
            }
            case HEADWAY_DOWN:
            case HEADWAY_UP: {
                int seconds = Math.max(0, Math.min(LineOperations.MAX_TICKS / 20,
                    operations.headwayTicks() / 20 + (action == Action.HEADWAY_UP ? STEP_SECONDS * 6 : -STEP_SECONDS * 6)));
                transit.setOperations(line.name(), operations.withHeadwayTicks(seconds * 20));
                state.markTrackDirty(world);
                return seconds == 0 ? "Headway off." : "Headway " + seconds + " s between departures.";
            }
            case CARS_DOWN:
            case CARS_UP: {
                TrainType type = TrainTypes.get(TRAIN_TYPE);
                int most = type == null ? 8 : type.maxCars;
                session.cars = Math.max(1, Math.min(most, session.cars + (action == Action.CARS_UP ? 1 : -1)));
                return "";
            }
            case ADD_TRAIN:
                return addTrain(world, state, line, session.cars);
            case REMOVE_TRAIN:
                if (!serves(transit, train, line)) {
                    return "Train #" + train + " is not on " + line.name() + ".";
                }
                TrainSpawner.remove(world, state, train);
                return "Removed train #" + train + ".";
            case TOGGLE_HOLD: {
                if (!serves(transit, train, line)) {
                    return "Train #" + train + " is not on " + line.name() + ".";
                }
                boolean hold = !transit.isHeld(train);
                transit.setHeld(train, hold);
                return hold ? "Holding #" + train + " at its next platform." : "Released #" + train + ".";
            }
            case REFRESH:
            default:
                return "";
        }
    }

    private static boolean serves(TransitSystem transit, int train, TransitLine line) {
        LineService service = transit.serviceFor(train);
        return service != null && service.line().name().equalsIgnoreCase(line.name());
    }

    /** Puts a new train in service at the first clear platform of the line. */
    private static String addTrain(World world, RcmcWorldState state, TransitLine line, int cars) {
        TrainType type = TrainTypes.get(TRAIN_TYPE);
        if (type == null) {
            return "No '" + TRAIN_TYPE + "' train type is loaded.";
        }
        com.micatechnologies.minecraft.rcmc.physics.TrainSpec spec = type.spec(cars);
        TransitPlatform berth = LineDepot.freeBerth(state.transit(), line, state.network(), state.trains(),
            spec.totalLength());
        if (berth == null) {
            return "Every platform of " + line.name() + " has a train at or near it.";
        }
        int trainId = TrainSpawner.spawn(world, state, berth.stopPoint().sectionId(), spec,
            berth.stopPoint().distance(), 0.0D);
        try {
            state.transit().enterService(trainId, state.trains().train(trainId), state.network(), line.name(),
                TransitDrives.metro(CRUISE, RcmcConstants.SECONDS_PER_TICK));
        }
        catch (IllegalArgumentException e) {
            TrainSpawner.remove(world, state, trainId);
            return "Could not start a train there: " + e.getMessage();
        }
        return "Train #" + trainId + " added at " + stationOf(state.transit(), line, berth) + ".";
    }

    private static String stationOf(TransitSystem transit, TransitLine line, TransitPlatform berth) {
        for (TransitStation stop : line.stations()) {
            TransitStation live = transit.station(stop.name());
            if ((live == null ? stop : live).platforms().contains(berth)) {
                return stop.name();
            }
        }
        return "a platform";
    }

    /** The line serving the station nearest the desk, else the first line, else empty. */
    private static String nearestLine(RcmcWorldState state, BlockPos desk) {
        TransitSystem transit = state.transit();
        TransitStation nearest = null;
        double best = Double.MAX_VALUE;
        for (TransitStation station : transit.stations()) {
            for (TransitPlatform platform : station.platforms()) {
                if (!state.network().hasSection(platform.stopPoint().sectionId())) {
                    continue;
                }
                com.micatechnologies.minecraft.rcmc.track.math.Vec3 p =
                    state.network().frameAt(platform.stopPoint()).position;
                double d = desk.distanceSq(p.x, p.y, p.z);
                if (d < best) {
                    best = d;
                    nearest = station;
                }
            }
        }
        if (nearest != null) {
            List<TransitLine> serving = transit.linesServing(nearest.name());
            if (!serving.isEmpty()) {
                return serving.get(0).name();
            }
        }
        return transit.lines().isEmpty() ? "" : transit.lines().iterator().next().name();
    }

    private static LineView viewOf(RcmcWorldState state, Session session, String message) {
        TransitSystem transit = state.transit();
        List<TransitLine> lines = new ArrayList<>(transit.lines());
        TransitLine line = transit.line(session.line);
        if (line == null && !lines.isEmpty()) {
            line = lines.get(0);
            session.line = line.name();
        }
        if (line == null) {
            return new LineView("", 0, 0, "", "", 0, 0, 0, session.cars, new ArrayList<>(), message);
        }
        List<LineView.TrainRow> rows = new ArrayList<>();
        for (Map.Entry<Integer, LineService> entry : transit.services().entrySet()) {
            if (!entry.getValue().line().name().equalsIgnoreCase(line.name())) {
                continue;
            }
            ServiceStatus status = ServiceStatus.of(transit, entry.getKey(), entry.getValue(),
                state.trains(), state.network());
            rows.add(new LineView.TrainRow(entry.getKey(), status.direction, status.doing, status.speed,
                status.held, status.why(), status.headOnWith >= 0));
        }
        List<String> names = new ArrayList<>();
        for (TransitStation station : line.stations()) {
            names.add(station.name());
        }
        LineOperations operations = transit.operationsFor(line.name());
        LineSignals signals = transit.signalsFor(line.name());
        return new LineView(line.name(), lines.indexOf(line) + 1, lines.size(), line.kind().label(),
            String.join(" - ", names), operations.dwellTicks() / 20, operations.headwayTicks() / 20,
            signals == null ? 0 : signals.blocks().size(), session.cars, rows, message);
    }
}
