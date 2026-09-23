package com.micatechnologies.minecraft.rcmc.debug;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * A launched shuttle coaster: a station in the middle, a spike at each end, and a launch either
 * side of the platform — one forward, one backward.
 *
 * <p>The ride: the train leaves the station forward, the forward launch fires it up the front
 * spike, it falls back and runs backward through the station — the platform lets it pass once —
 * the backward launch fires it up the rear spike, and it falls back once more and is caught in the
 * station going forward. The two launches never fight the train on the way through, because a
 * launch does not fire against a train running the other way.</p>
 *
 * <p>Laid along +X from the player's feet, like {@link DemoCoaster}. Both spikes are far taller
 * than a 22 blocks/s launch can climb — about 25 blocks — so the ends of the track are never
 * reached.</p>
 */
public final class DemoShuttle {

    /** The built section, and where each piece of hardware goes on it. */
    public static final class Result {
        public final TrackSection section;
        public final double stationStart;
        public final double stationEnd;
        public final double stationStop;
        public final double launchStart;
        public final double launchEnd;
        public final double backLaunchStart;
        public final double backLaunchEnd;

        Result(TrackSection section, double stationStart, double stationEnd, double stationStop,
               double launchStart, double launchEnd, double backLaunchStart, double backLaunchEnd) {
            this.section = section;
            this.stationStart = stationStart;
            this.stationEnd = stationEnd;
            this.stationStop = stationStop;
            this.launchStart = launchStart;
            this.launchEnd = launchEnd;
            this.backLaunchStart = backLaunchStart;
            this.backLaunchEnd = backLaunchEnd;
        }
    }

    /** Height of each spike above the station, blocks. */
    static final double SPIKE_HEIGHT = 45.0D;

    private DemoShuttle() {
        throw new AssertionError("No instances.");
    }

    public static Result build(int sectionId, Vec3 origin) {
        double[][] alongAndUp = {
            // Rear spike, from its top down to the level.
            {-95, SPIKE_HEIGHT}, {-88, 30}, {-78, 10}, {-65, 1},
            // Backward launch, then the station, then the forward launch — all level.
            {-50, 0}, {-20, 0}, {20, 0}, {55, 0},
            // Front spike, up from the level to its top.
            {70, 1}, {82, 10}, {92, 30}, {99, SPIKE_HEIGHT},
        };
        List<TrackNode> nodes = new ArrayList<>();
        for (double[] p : alongAndUp) {
            nodes.add(new TrackNode(new Vec3(origin.x + p[0], origin.y + p[1], origin.z)));
        }
        TrackSection section = new TrackSection(sectionId, nodes, false, null);
        double backStart = section.nodeDistance(4);
        double stationStart = section.nodeDistance(5);
        double stationEnd = section.nodeDistance(6);
        double launchEnd = section.nodeDistance(7);
        return new Result(section, stationStart, stationEnd, stationEnd - 3.0D,
            stationEnd, launchEnd, backStart, stationStart);
    }
}
