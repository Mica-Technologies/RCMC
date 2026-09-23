package com.micatechnologies.minecraft.rcmc.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.debug.DemoCoaster;
import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.element.BrakeRun;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.rating.RideRater;
import com.micatechnologies.minecraft.rcmc.rating.RideRating;
import com.micatechnologies.minecraft.rcmc.rating.RideStatistics;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /rcmc rate} on the demo coaster must produce a real rating.
 *
 * <p>Found in review: it answered 0.00 / 0.00 / 0.00, "0.0 blocks, 0.1 s", VALLEYED. It rated a single
 * car at rest at distance zero, through the park's <em>live</em> ride hardware — whose station was
 * stuck in DEPARTED — so the car had no hold and no push and latched as a stall on the first tick.
 * Rating a ride means running the ride: its own train, from its station, through a copy of its
 * hardware that the running park never sees.</p>
 */
class DemoCoasterRatingTest {

    private static final double TICK = RcmcConstants.SECONDS_PER_TICK;

    private static final class Demo {
        final DemoCoaster.Result demo = DemoCoaster.build(1, new Vec3(0, 64, 0), 1.0D, 34.0D);
        final TrackNetwork network = new TrackNetwork();
        final RideElementSet elements = new RideElementSet();
        final StationPlatform station;

        Demo() {
            network.addSection(demo.section);
            station = new StationPlatform(1, demo.stationStart, demo.stationEnd, demo.stationStop,
                6.0D, 60, 4.0D, 6.0D, TICK);
            elements.add(station);
            elements.add(new ChainLift(1, demo.liftStart, demo.liftEnd, 5.0D, 12.0D, TICK));
            elements.add(new BrakeRun(1, demo.brakeStart, demo.brakeEnd, 6.0D, 6.0D,
                BrakeRun.Mode.TRIM, TICK));
        }
    }

    private static RideRater rater() {
        return RideRater.standard(new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D), 9.81D);
    }

    @Test
    @DisplayName("the demo rates as a real ride: it completes its lap and scores above zero")
    void demoGetsARealRating() {
        Demo d = new Demo();
        RideStatistics stats = rater().simulateRide(d.network, d.elements, 1,
            new TrainSpec(5, 3.0D, 0.5D, 4));
        RideRating rating = RideRating.from(stats);

        assertEquals(Train.Status.RUNNING, stats.finalStatus, "the lap must complete, not stall");
        assertTrue(stats.totalLengthBlocks > d.demo.section.totalLength() * 0.95D,
            "a whole lap: " + stats.totalLengthBlocks);
        assertTrue(stats.maxSpeedBlocksPerSecond > 15.0D,
            "the drop is 34 blocks; top speed was " + stats.maxSpeedBlocksPerSecond);
        assertTrue(rating.excitement > 1.0D, "excitement " + rating.excitement);
        assertTrue(rating.intensity > 0.5D, "intensity " + rating.intensity);
    }

    @Test
    @DisplayName("rating a ride does not disturb the train running on it")
    void ratingLeavesTheLiveRideAlone() {
        Demo d = new Demo();
        TrainManager trains = new TrainManager();
        trains.add(1, new Train(new TrainSpec(5, 3.0D, 0.5D, 4),
            new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, d.demo.stationStop), 0.0D));
        for (int t = 0; t < 20; t++) {
            trains.tick(d.network, d.elements, 4, TICK);
        }
        StationPlatform.Phase before = d.station.phase();
        int serving = d.station.servingTrain();

        rater().simulateRide(d.network, d.elements, 1, new TrainSpec(5, 3.0D, 0.5D, 4));

        assertEquals(before, d.station.phase(), "the live station's cycle is untouched");
        assertEquals(serving, d.station.servingTrain(), "and it is still serving the real train");
    }

    @Test
    @DisplayName("a lap is the rated ride's own length, not every track in the park added up")
    void lapIsTheRatedSection() {
        Demo d = new Demo();
        List<TrackNode> far = new ArrayList<>();
        for (int x = 0; x <= 2000; x += 100) {
            far.add(new TrackNode(new Vec3(5000 + x, 64, 5000)));
        }
        d.network.addSection(new TrackSection(2, far, false, null));

        RideStatistics stats = rater().simulateRide(d.network, d.elements, 1,
            new TrainSpec(5, 3.0D, 0.5D, 4));
        assertTrue(stats.totalLengthBlocks < d.demo.section.totalLength() * 1.1D,
            "one lap of the demo, not the demo plus 2000 blocks elsewhere: " + stats.totalLengthBlocks);
    }
}
