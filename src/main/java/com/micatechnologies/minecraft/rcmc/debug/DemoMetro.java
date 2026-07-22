package com.micatechnologies.minecraft.rcmc.debug;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.TrackStyleIds;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a complete demonstration metro line: a flat, gently curving alignment with three named
 * stations, styled with the wide transit gauge and pole catenary.
 *
 * <p>Deliberately its own demo rather than reusing {@link DemoCoaster}: a metro line and a
 * coaster share physics but not <em>shape</em>. A coaster is a hill circuit that trades height
 * for speed; a metro alignment is level, sweeps its curves at radii a standing passenger
 * tolerates, and exists to connect stations. Demonstrating metro hardware on a lift hill
 * misrepresents both. (Lesson learned by doing exactly that.)</p>
 *
 * <p>The alignment runs ~460 blocks out along +X with one broad S-sweep, at constant height —
 * an elevated viaduct when built above terrain, which the auto-generated supports carry.</p>
 */
public final class DemoMetro {

    /** A built demo line: the geometry plus where its stations sit. */
    public static final class Result {

        public final TrackSection section;
        public final String[] stationNames;
        public final double[] stationDistances;

        Result(TrackSection section, String[] stationNames, double[] stationDistances) {
            this.section = section;
            this.stationNames = stationNames;
            this.stationDistances = stationDistances;
        }
    }

    private DemoMetro() {
        throw new AssertionError("No instances.");
    }

    /**
     * @param origin where the first station's platform is — the player's position
     */
    public static Result build(int sectionId, Vec3 origin) {
        // A gentle S: straight out, a sweep to +Z, back through centre, a sweep to -Z, straight
        // in. Curve radii stay ~100 blocks — transit-scale, nothing a standing rider would feel.
        List<TrackNode> nodes = new ArrayList<>();
        double y = origin.y;
        double[][] xz = {
            {0, 0}, {50, 0}, {100, 6}, {150, 22}, {200, 34},
            {250, 34}, {300, 22}, {350, 6}, {400, 0}, {460, 0},
        };
        for (double[] p : xz) {
            nodes.add(new TrackNode(new Vec3(origin.x + p[0], y, origin.z + p[1])));
        }
        TrackSection section = new TrackSection(sectionId, nodes, false,
            TrackStyleIds.TRANSIT_CATENARY);

        double total = section.totalLength();
        // Stations at the near end, the middle of the S, and the far end — spaced like real
        // metro stops, with the termini pulled in from the dead ends by a train's length.
        String[] names = {"Westgate", "Midtown", "Eastvale"};
        double[] distances = {25.0D, total * 0.5D, total - 25.0D};
        return new Result(section, names, distances);
    }

    /**
     * Builds an underground <b>double-track loop</b> subway: a closed, flat, tunnel-styled oval with
     * two parallel straights joined by a turning loop at each end, so a train runs one way forever —
     * out along one track, round the end, back along the other — never reversing.
     *
     * <p>Modelled as a <b>loop line</b> (never reverses) with six distinct stations around the ring.
     * The metro model gives each station a single stop point and matches stations by name, so it has
     * no notion of "one station, two directional platforms" — the honest way to express a
     * double-track line here is six stops around the loop, exactly as a real loop/circle line has.
     * Where the two straights run parallel, an island platform between them serves both tracks.</p>
     *
     * <p>The geometry is a stadium oval: a north straight running +X, a semicircular turn at the east
     * end, a south straight running −X, a semicircular turn at the west end, closed. Straights are
     * {@code GAUGE_SEP} apart — room for an island platform between them. Station distances are found
     * by walking the built spline to the point nearest each platform, rather than hand-computed, so
     * they stay correct if the control points are ever tweaked.</p>
     */
    public static Result buildUndergroundLoop(int sectionId, Vec3 origin) {
        double y = origin.y;
        // Wide separation on purpose: the turning loop at each end is a semicircle of radius sep/2,
        // so a bigger separation buys a broader, more gradual turnaround (a metro turnback, not a
        // hairpin) — and leaves a generous island between the straights. Long straights so three
        // stations spaced well apart each get a platform long enough for a three-car train.
        final double sep = 20.0D;  // centreline separation of the two tracks
        final double r = sep / 2.0D;   // turning-loop radius = half the separation (=10 blocks)
        final double len = 360.0D; // straight length
        final double d = r * 0.70710678D; // 45° offset on the semicircles

        // Counter-clockwise from the west end of the north (z=0) track; points every 90 blocks keep
        // the long straights straight.
        double[][] xz = {
            {0, 0}, {90, 0}, {180, 0}, {270, 0}, {len, 0},
            {len + d, r - d}, {len + r, r}, {len + d, r + d},   // east turning loop (+X bulge)
            {len, sep}, {270, sep}, {180, sep}, {90, sep}, {0, sep},
            {-d, r + d}, {-r, r}, {-d, r - d},                  // west turning loop (−X bulge)
        };
        List<TrackNode> nodes = new ArrayList<>();
        for (double[] p : xz) {
            nodes.add(new TrackNode(new Vec3(origin.x + p[0], y, origin.z + p[1])));
        }
        TrackSection section = new TrackSection(sectionId, nodes, true,
            TrackStyleIds.TRANSIT_TUNNEL);

        // Six platforms, three per straight, spaced 120 blocks apart so a 64-block platform (three
        // cars) fits each with clear tunnel between. Named around the ring in service order.
        double[][] platforms = {
            {60, 0}, {180, 0}, {300, 0},          // outbound (north, running east)
            {300, sep}, {180, sep}, {60, sep},    // inbound (south, running west)
        };
        String[] names = {"Union", "Central", "Harbor", "Seaside", "Midway", "Parkside"};
        double[] distances = new double[platforms.length];
        for (int i = 0; i < platforms.length; i++) {
            distances[i] = nearestDistance(section,
                new Vec3(origin.x + platforms[i][0], y, origin.z + platforms[i][1]));
        }
        return new Result(section, names, distances);
    }

    /** Distance along the section whose frame position is closest to {@code target}. */
    private static double nearestDistance(TrackSection section, Vec3 target) {
        double total = section.totalLength();
        double best = 0.0D;
        double bestSq = Double.MAX_VALUE;
        // 1-block steps are ample: the nearest station point needs only to land a train berth on the
        // right straight, and the stop controller creeps in to the exact point from there.
        for (double s = 0.0D; s <= total; s += 1.0D) {
            Vec3 p = section.positionAtDistance(s);
            double dx = p.x - target.x;
            double dz = p.z - target.z;
            double sq = dx * dx + dz * dz;
            if (sq < bestSq) {
                bestSq = sq;
                best = s;
            }
        }
        return best;
    }
}
