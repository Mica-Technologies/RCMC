package com.micatechnologies.minecraft.rcmc.debug;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a complete demonstration coaster: station, lift hill, first drop, airtime hill, banked
 * turnaround, and a brake run back into the station.
 *
 * <p>An earlier version was a parametric ellipse with a sinusoidal height profile. It exercised the
 * geometry, but it was not a <em>coaster</em> — there was no station to leave from, no lift to
 * climb, and a train started at the crest of a hill instead of at rest on a platform. A layout you
 * can point at and name the parts of is worth more as a demo than a mathematically tidy one.</p>
 *
 * <p>The build returns the element spans alongside the geometry, so the caller places the station,
 * lift and brakes exactly where the layout intends rather than guessing at fractions of the lap.
 * Guessing is what the previous version did, and it only appeared to work because the shape was
 * uniform.</p>
 */
public final class DemoCoaster {

    /** A built demo, and where its ride hardware belongs. */
    public static final class Result {

        public final TrackSection section;

        /** Distance along the section at which each element starts and ends, in blocks. */
        public final double stationStart;
        public final double stationEnd;
        public final double stationStop;
        public final double liftStart;
        public final double liftEnd;
        public final double brakeStart;
        public final double brakeEnd;

        Result(TrackSection section, double stationStart, double stationEnd, double stationStop,
               double liftStart, double liftEnd, double brakeStart, double brakeEnd) {
            this.section = section;
            this.stationStart = stationStart;
            this.stationEnd = stationEnd;
            this.stationStop = stationStop;
            this.liftStart = liftStart;
            this.liftEnd = liftEnd;
            this.brakeStart = brakeStart;
            this.brakeEnd = brakeEnd;
        }
    }

    private DemoCoaster() {
        throw new AssertionError("No instances.");
    }

    /**
     * @param origin     the station's position — where the player is standing
     * @param scale      overall size multiplier; 1.0 gives roughly a 120x70 block footprint
     * @param liftHeight height of the lift crest above the station, in blocks
     */
    public static Result build(int sectionId, Vec3 origin, double scale, double liftHeight) {
        List<TrackNode> nodes = new ArrayList<>();

        double baseY = origin.y;
        double topY = baseY + liftHeight;
        // The first drop bottoms out below the station so the train has surplus energy to carry it
        // through the rest of the circuit — the same reason real layouts dig the first valley in.
        double valleyY = baseY - liftHeight * 0.12D;

        double w = 60.0D * scale;   // half-width, across the layout
        double l = 55.0D * scale;   // half-length, along it

        // --- Station: level, straight, along +X ---
        int stationFirst = nodes.size();
        add(nodes, origin.x - l, baseY, origin.z - w, 0);
        add(nodes, origin.x - l * 0.55D, baseY, origin.z - w, 0);
        int stationLast = nodes.size();
        add(nodes, origin.x - l * 0.1D, baseY, origin.z - w, 0);

        // --- Lift hill: steady climb to the crest ---
        int liftFirst = nodes.size();
        add(nodes, origin.x + l * 0.25D, baseY + liftHeight * 0.25D, origin.z - w, 0);
        add(nodes, origin.x + l * 0.55D, baseY + liftHeight * 0.65D, origin.z - w, 0);
        add(nodes, origin.x + l * 0.8D, baseY + liftHeight * 0.93D, origin.z - w, 0);
        int liftLast = nodes.size();
        add(nodes, origin.x + l, topY, origin.z - w * 0.86D, 0);

        // --- First drop: over the crest and down, curving away ---
        add(nodes, origin.x + l * 1.05D, topY - liftHeight * 0.22D, origin.z - w * 0.55D, -12);
        add(nodes, origin.x + l * 0.98D, valleyY + liftHeight * 0.28D, origin.z - w * 0.18D, -22);
        add(nodes, origin.x + l * 0.78D, valleyY, origin.z + w * 0.12D, -10);

        // --- Airtime hill: a brisk crest, then straight down the far side ---
        add(nodes, origin.x + l * 0.4D, valleyY + liftHeight * 0.42D, origin.z + w * 0.35D, 0);
        add(nodes, origin.x + l * 0.05D, valleyY + liftHeight * 0.5D, origin.z + w * 0.5D, 8);
        add(nodes, origin.x - l * 0.3D, valleyY + liftHeight * 0.15D, origin.z + w * 0.62D, 18);

        // --- Banked turnaround: hard left, back toward the station ---
        add(nodes, origin.x - l * 0.75D, valleyY + liftHeight * 0.1D, origin.z + w * 0.72D, 42);
        add(nodes, origin.x - l * 1.08D, valleyY + liftHeight * 0.3D, origin.z + w * 0.45D, 48);
        add(nodes, origin.x - l * 1.12D, valleyY + liftHeight * 0.45D, origin.z + w * 0.05D, 40);

        // --- Second, smaller hill on the return leg ---
        add(nodes, origin.x - l * 0.9D, valleyY + liftHeight * 0.62D, origin.z - w * 0.3D, 14);
        add(nodes, origin.x - l * 0.6D, valleyY + liftHeight * 0.45D, origin.z - w * 0.58D, 0);

        // --- Brake run: level out at station height, aimed back into the platform ---
        int brakeFirst = nodes.size();
        add(nodes, origin.x - l * 1.25D, baseY, origin.z - w * 0.72D, 0);
        int brakeLast = nodes.size();
        add(nodes, origin.x - l * 1.35D, baseY, origin.z - w * 0.88D, 0);

        TrackSection section = new TrackSection(sectionId, nodes, true, null);

        // Element spans come from the node distances the section computed, so they track the
        // layout exactly even if the shape above is retuned.
        return new Result(section,
            section.nodeDistance(stationFirst), section.nodeDistance(stationLast + 1),
            section.nodeDistance(stationLast),
            section.nodeDistance(liftFirst), section.nodeDistance(liftLast),
            section.nodeDistance(brakeFirst), section.nodeDistance(brakeLast));
    }

    /** Sensible defaults: a mid-sized layout with a 34-block lift. */
    public static Result build(int sectionId, Vec3 origin) {
        return build(sectionId, origin, 1.0D, 34.0D);
    }

    private static void add(List<TrackNode> nodes, double x, double y, double z, double bankDegrees) {
        nodes.add(new TrackNode(new Vec3(x, y, z), bankDegrees, null));
    }
}
