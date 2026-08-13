package com.micatechnologies.minecraft.rcmc.debug;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.TrackStyleIds;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A whole underground network: two lines, nine stations, one interchange — laid out to exercise
 * every part of RCMC's metro model at once rather than to be the smallest thing that runs.
 *
 * <h2>Why the Circle Line is shaped the way it is</h2>
 *
 * <p>A real metro does not stop inbound service while an outbound train runs. Inbound and outbound
 * are <em>separate tracks</em>, joined by a turning loop at each terminus, so the two directions run
 * at the same time and a train never reverses — it drives forward for ever, out along one track and
 * back along the other.</p>
 *
 * <p>So the track here is a <b>closed circuit</b> while the service is <b>out-and-back</b>: stations
 * are listed once, in service order, and each has a berth on each track. That is the combination
 * {@code TransitLine.turnsBackOnLoop} exists for, and it is what makes a two-platform station mean
 * anything — {@code TransitStation.platformFor} picks the berth on the track the train is actually
 * on, which differs by direction. Modelling the same circuit as a loop line instead would need each
 * place named twice, and a train would pass every second platform without stopping.</p>
 *
 * <h2>Layout</h2>
 *
 * <pre>
 *   OUTBOUND track (z = -4), running +X
 *   ┌── Kingsway ─ Guildhall ─ Riverside ─ Exchange ─ Foundry ─ Lakeshore ──┐
 *   │     side       ISLAND       side      ISLAND      side      ISLAND    │  east
 *   │                                        ╪                              │  turnback
 *   └── Kingsway ─ Guildhall ─ Riverside ─ Exchange ─ Foundry ─ Lakeshore ──┘
 *   INBOUND track (z = +4), running -X
 *          west turnback                     ╪ = interchange, one level down
 * </pre>
 *
 * <p>The two tracks run eight blocks apart — close enough that a four-block island fits between
 * them, wide enough that a car on each has clearance — and flare out at the ends into turning loops
 * broad enough to take at metro speed.</p>
 *
 * <p>The <b>Airport Line</b> is deliberately the other kind of line: a single-track out-and-back
 * with <em>stub</em> termini, where the train really does change ends. It runs a level below and
 * crosses under Exchange, which is what makes Exchange an interchange — one named place, three
 * berths, two lines, and a board that shows both.</p>
 *
 * <p>Pure Java: geometry and naming only. Placing the blocks is {@code MetroFabric}'s job, and
 * registering the stations is the command's.</p>
 */
public final class DemoUnderground {

    // --- Alignment, in blocks relative to the origin. -------------------------------------------

    /** Half the gap between the two running tracks: they sit at z = ±TRACK_OFFSET. */
    public static final int TRACK_OFFSET = 4;

    /** Where the straights end and the turnbacks begin. */
    private static final int STRAIGHT_END = 460;

    /** Deck top sits one block up, so its surface is level with the car floor. */
    public static final int DECK_RISE = 1;

    /**
     * Height of the tunnel's interior above rail level. The ceiling is the block above this,
     * so this is also the topmost block a board can hang in — put one any higher and it is
     * buried in the rock above the ceiling, which is exactly what happened the first time.
     *
     * <p>{@code MetroFabric} bores to this height, so the two cannot drift apart.</p>
     */
    public static final int INTERIOR_HEIGHT = 5;

    /** The Airport Line runs this far below the Circle Line — a deep-level interchange. */
    public static final int LOWER_LEVEL_DROP = 12;

    /** Half a platform's length. A three-car metro plus margin at both ends. */
    public static final int PLATFORM_HALF_LENGTH = 30;

    /** Where the Airport Line crosses under the Circle Line, and so where Exchange is. */
    private static final int INTERCHANGE_X = 270;

    /** How a station's decking relates to its track (or tracks). */
    public enum Kind {
        /** One deck between two tracks; the two berths open on opposite hands. */
        ISLAND,
        /** A deck outside each track, so both berths open outward. */
        SIDE,
        /** One track, decking on both sides — the doors open both ways. */
        SPANISH
    }

    /** One berth: a stop point on a section, and what the signage calls it. */
    public static final class Berth {

        public final int sectionId;
        public final double distance;
        public final String label;

        Berth(int sectionId, double distance, String label) {
            this.sectionId = sectionId;
            this.distance = distance;
            this.label = label;
        }
    }

    /** A rectangle of decking, in blocks relative to the origin. Inclusive bounds. */
    public static final class Deck {

        public final int minX;
        public final int maxX;
        public final int minZ;
        public final int maxZ;
        public final int y;

        Deck(int minX, int maxX, int minZ, int maxZ, int y) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.y = y;
        }
    }

    /** One named place: its berths, its decking, and where its signage hangs. */
    public static final class Stop {

        public final String name;
        public final Kind kind;
        public final List<Berth> berths;
        public final List<Deck> decks;
        /** Block position, relative to the origin, for the arrival board's master block. */
        public final int signX;
        public final int signZ;
        public final int signY;

        Stop(String name, Kind kind, List<Berth> berths, List<Deck> decks,
             int signX, int signY, int signZ) {
            this.name = name;
            this.kind = kind;
            this.berths = Collections.unmodifiableList(berths);
            this.decks = Collections.unmodifiableList(decks);
            this.signX = signX;
            this.signY = signY;
            this.signZ = signZ;
        }
    }

    /** A line, by the names of the stops it serves, in service order. */
    public static final class Line {

        public final String name;
        public final List<String> stops;
        public final boolean loop;
        public final boolean turnbackLoop;
        public final String inboundLabel;
        public final String outboundLabel;

        Line(String name, List<String> stops, boolean loop, boolean turnbackLoop,
             String inboundLabel, String outboundLabel) {
            this.name = name;
            this.stops = Collections.unmodifiableList(stops);
            this.loop = loop;
            this.turnbackLoop = turnbackLoop;
            this.inboundLabel = inboundLabel;
            this.outboundLabel = outboundLabel;
        }
    }

    /** Everything the command needs to build the network. */
    public static final class Plan {

        public final List<TrackSection> sections;
        public final List<Stop> stops;
        public final List<Line> lines;

        Plan(List<TrackSection> sections, List<Stop> stops, List<Line> lines) {
            this.sections = Collections.unmodifiableList(sections);
            this.stops = Collections.unmodifiableList(stops);
            this.lines = Collections.unmodifiableList(lines);
        }

        public Stop stop(String name) {
            for (Stop stop : stops) {
                if (stop.name.equalsIgnoreCase(name)) {
                    return stop;
                }
            }
            return null;
        }
    }

    private DemoUnderground() {
        throw new AssertionError("No instances.");
    }

    /**
     * @param ringId    section id for the Circle Line's closed circuit
     * @param airportId section id for the Airport Line's single track
     * @param origin    the west end of the outbound track, at rail level
     */
    public static Plan build(int ringId, int airportId, Vec3 origin) {
        TrackSection ring = ring(ringId, origin);
        TrackSection airport = airportLine(airportId, origin);

        List<Stop> stops = new ArrayList<>();

        // Circle Line, in service order. Outbound (+1) runs +X on the z = -4 track; inbound runs
        // -X on the z = +4 track. Every station therefore has one berth on each, at the same x —
        // which is the whole point, and what a single-track line could never show.
        int[] xs = {30, 110, 190, INTERCHANGE_X, 350, 430};
        String[] names = {"Kingsway", "Guildhall", "Riverside", "Exchange", "Foundry", "Lakeshore"};
        Kind[] kinds = {Kind.SIDE, Kind.ISLAND, Kind.SIDE, Kind.ISLAND, Kind.SIDE, Kind.ISLAND};

        for (int i = 0; i < names.length; i++) {
            int x = xs[i];
            // A terminus has ONE platform, on the track it is approached along, and the train loops
            // round from there. Giving it one per direction would strand the second: the service
            // stops once, flips, and runs on to the next station, so the other platform would never
            // be served by anything. The middle stations are the ones with a berth per direction.
            boolean firstTerminus = i == 0;
            boolean lastTerminus = i == names.length - 1;
            List<Berth> berths = new ArrayList<>();
            if (!firstTerminus) {
                berths.add(new Berth(ringId, nearestDistance(ring, origin, x, -TRACK_OFFSET), "1"));
            }
            if (!lastTerminus) {
                berths.add(new Berth(ringId, nearestDistance(ring, origin, x, TRACK_OFFSET),
                    firstTerminus ? "1" : "2"));
            }
            if ("Exchange".equals(names[i])) {
                // The interchange gets a third berth a level down, on the other line. Three
                // platforms at one named place is the case the whole multi-platform model is for.
                berths.add(new Berth(airportId,
                    nearestDistanceOnAirport(airport, origin, 0), "3"));
            }
            Kind kind = firstTerminus || lastTerminus ? Kind.SIDE : kinds[i];
            List<Deck> decks = new ArrayList<>(ringDecks(kind, x, firstTerminus, lastTerminus));
            if ("Exchange".equals(names[i])) {
                // The interchange's lower level needs decking of its own. Its Circle Line berths sit
                // on the island above; without this the Airport Line berth is a stop point beside
                // bare tunnel, and the door side detects as nothing at all.
                decks.addAll(airportDecks(Kind.SIDE, 0));
            }
            stops.add(new Stop(names[i], kind, berths, decks,
                x, INTERIOR_HEIGHT, signZFor(kind, firstTerminus, lastTerminus)));
        }

        // Airport Line: single track, stub termini, a level below. The other kind of terminus —
        // here the train genuinely changes ends, which is what turnbackLoop = false means.
        int[] zs = {-160, -80, 0, 80};
        String[] airportNames = {"Airfield", "Docklands", "Exchange", "Parkway"};
        Kind[] airportKinds = {Kind.SIDE, Kind.SPANISH, Kind.SIDE, Kind.SIDE};
        for (int i = 0; i < airportNames.length; i++) {
            if ("Exchange".equals(airportNames[i])) {
                continue;  // already built above, with its Circle Line berths
            }
            double distance = nearestDistanceOnAirport(airport, origin, zs[i]);
            List<Berth> berths = new ArrayList<>();
            berths.add(new Berth(airportId, distance, ""));
            stops.add(new Stop(airportNames[i], airportKinds[i], berths,
                airportDecks(airportKinds[i], zs[i]),
                INTERCHANGE_X + signXOffsetFor(airportKinds[i]),
                INTERIOR_HEIGHT - LOWER_LEVEL_DROP,
                zs[i]));
        }

        List<Line> lines = new ArrayList<>();
        // Single-word line names on purpose: Minecraft's command parser splits on spaces
        // and ignores quotes, and "/rcmc line start <name> <trainId>" ends with the train
        // id, so a name with a space in it could never be typed at the one command that
        // matters most. Signage renders whatever it is given, so the cost is only prose.
        lines.add(new Line("Circle", Arrays.asList(names), false, true,
            "INBOUND", "OUTBOUND"));
        lines.add(new Line("Airport", Arrays.asList(airportNames), false, false,
            "NORTHBOUND", "SOUTHBOUND"));

        return new Plan(Arrays.asList(ring, airport), stops, lines);
    }

    /**
     * The Circle Line's closed circuit: two straights eight blocks apart, joined at each end by a
     * turning loop broad enough to take at line speed.
     *
     * <p>The straights are close together because that is where the platforms are and an island has
     * to fit between them; the ends flare out because a U-turn at that spacing would be a hairpin.
     * Real turnbacks do exactly this.</p>
     */
    private static TrackSection ring(int sectionId, Vec3 origin) {
        double[][] xz = {
            // Outbound: +X at z = -4.
            {0, -TRACK_OFFSET}, {110, -TRACK_OFFSET}, {230, -TRACK_OFFSET},
            {350, -TRACK_OFFSET}, {STRAIGHT_END, -TRACK_OFFSET},
            // East turning loop: flare out, round, and back onto the other track.
            {492, -9}, {512, -18}, {524, 0}, {512, 18}, {492, 9},
            // Inbound: -X at z = +4.
            {STRAIGHT_END, TRACK_OFFSET}, {350, TRACK_OFFSET}, {230, TRACK_OFFSET},
            {110, TRACK_OFFSET}, {0, TRACK_OFFSET},
            // West turning loop.
            {-32, 9}, {-52, 18}, {-64, 0}, {-52, -18}, {-32, -9},
        };
        List<TrackNode> nodes = new ArrayList<>();
        for (double[] p : xz) {
            nodes.add(new TrackNode(new Vec3(origin.x + p[0], origin.y, origin.z + p[1])));
        }
        return new TrackSection(sectionId, nodes, true, TrackStyleIds.TRANSIT_TUNNEL);
    }

    /** The Airport Line: a straight single track along Z, one level down, crossing under Exchange. */
    private static TrackSection airportLine(int sectionId, Vec3 origin) {
        double y = origin.y - LOWER_LEVEL_DROP;
        List<TrackNode> nodes = new ArrayList<>();
        for (int z = -200; z <= 130; z += 55) {
            nodes.add(new TrackNode(new Vec3(origin.x + INTERCHANGE_X, y, origin.z + z)));
        }
        return new TrackSection(sectionId, nodes, false, TrackStyleIds.TRANSIT_TUNNEL);
    }

    /** Decking for a station on the ring, as block rectangles relative to the origin. */
    private static List<Deck> ringDecks(Kind kind, int x, boolean firstTerminus,
                                        boolean lastTerminus) {
        int from = x - PLATFORM_HALF_LENGTH;
        int to = x + PLATFORM_HALF_LENGTH;
        List<Deck> decks = new ArrayList<>();
        if (kind == Kind.ISLAND) {
            // Between the tracks. A car at z = ∓4 occupies blocks ∓6..∓3, leaving -2..1 free.
            decks.add(new Deck(from, to, -2, 1, DECK_RISE));
            return decks;
        }
        // Outside the track, clear of the car bodies by a block. A terminus is served on one track
        // only, so it gets the one deck that faces it; a through station gets both.
        if (!firstTerminus) {
            decks.add(new Deck(from, to, -9, -7, DECK_RISE));
        }
        if (!lastTerminus) {
            decks.add(new Deck(from, to, 7, 9, DECK_RISE));
        }
        return decks;
    }

    /** Decking for a station on the Airport Line's single track. */
    private static List<Deck> airportDecks(Kind kind, int z) {
        int from = z - PLATFORM_HALF_LENGTH;
        int to = z + PLATFORM_HALF_LENGTH;
        int y = DECK_RISE - LOWER_LEVEL_DROP;
        List<Deck> decks = new ArrayList<>();
        // The track runs along Z here, so the decking lies either side of it in X.
        decks.add(new Deck(INTERCHANGE_X + 3, INTERCHANGE_X + 5, from, to, y));
        if (kind == Kind.SPANISH) {
            // Decking on both sides of one track: the doors open both ways, which is a real
            // arrangement (and the only honest way to end up with DoorSide.BOTH).
            decks.add(new Deck(INTERCHANGE_X - 5, INTERCHANGE_X - 3, from, to, y));
        }
        return decks;
    }

    /** Where a ring station's board hangs: over the island, or over whichever side deck exists. */
    private static int signZFor(Kind kind, boolean firstTerminus, boolean lastTerminus) {
        if (kind == Kind.ISLAND) {
            return 0;
        }
        return firstTerminus ? 8 : -8;
    }

    private static int signXOffsetFor(Kind kind) {
        return 4;
    }

    /** Distance along the ring whose frame is nearest the given offset from the origin. */
    private static double nearestDistance(TrackSection section, Vec3 origin, double dx, double dz) {
        return nearestTo(section, origin.x + dx, origin.z + dz);
    }

    private static double nearestDistanceOnAirport(TrackSection section, Vec3 origin, double dz) {
        return nearestTo(section, origin.x + INTERCHANGE_X, origin.z + dz);
    }

    /**
     * Distance along {@code section} whose frame position is closest to a target, in the XZ plane.
     *
     * <p>Walked rather than computed: the alignment is a spline through control points, so its arc
     * length is not the polyline's, and hand-derived distances would drift the moment a control
     * point moved. Half-block steps are finer than a stop point needs — the controller creeps in to
     * the exact point from wherever this lands.</p>
     */
    private static double nearestTo(TrackSection section, double targetX, double targetZ) {
        double total = section.totalLength();
        double best = 0.0D;
        double bestSq = Double.MAX_VALUE;
        for (double s = 0.0D; s <= total; s += 0.5D) {
            Vec3 p = section.positionAtDistance(s);
            double dx = p.x - targetX;
            double dz = p.z - targetZ;
            double sq = dx * dx + dz * dz;
            if (sq < bestSq) {
                bestSq = sq;
                best = s;
            }
        }
        return best;
    }
}
