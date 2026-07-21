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
}
