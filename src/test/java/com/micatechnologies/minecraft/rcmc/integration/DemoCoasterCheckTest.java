package com.micatechnologies.minecraft.rcmc.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.debug.DemoCoaster;
import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.element.BrakeRun;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.rating.RideCheck;
import com.micatechnologies.minecraft.rcmc.rating.RideRater;
import com.micatechnologies.minecraft.rcmc.rating.RideRating;
import com.micatechnologies.minecraft.rcmc.rating.RideStatistics;
import com.micatechnologies.minecraft.rcmc.rating.RideWarning;
import com.micatechnologies.minecraft.rcmc.rating.SafetyLimits;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The demo coaster passes its own ride check, at every size {@code /rcmc demo} will build.
 *
 * <p>Found after the ride check landed: the demo failed it, with up to 6 g sideways where the
 * hand-set banks did not match the speed through a turn, and its safety rating said FAILED. A demo
 * is what a player learns from; it has to be a ride the mod itself would pass.</p>
 */
class DemoCoasterCheckTest {

    private static final double TICK = RcmcConstants.SECONDS_PER_TICK;

    @ParameterizedTest(name = "scale {0}, lift {1}")
    @CsvSource({"1.0, 34", "1.0, 16", "1.0, 20", "0.4, 16", "1.0, 24", "4.0, 34", "0.4, 60", "4.0, 60",
        "0.4, 90", "4.0, 90", "0.4, 120", "4.0, 120"})
    @DisplayName("the demo coaster has no ride-check warnings, and rates safe")
    void demoPassesItsCheck(double scale, double lift) {
        DemoCoaster.Result demo = DemoCoaster.build(1, new Vec3(0, 64, 0), scale, lift);
        TrackNetwork network = new TrackNetwork();
        network.addSection(demo.section);
        // The hardware /rcmc demo installs.
        RideElementSet elements = new RideElementSet();
        elements.add(new StationPlatform(1, demo.stationStart, demo.stationEnd, demo.stationStop,
            6.0D, 60, 4.0D, 6.0D, TICK));
        elements.add(new ChainLift(1, demo.liftStart, demo.liftEnd, 5.0D, 12.0D, TICK));
        elements.add(new BrakeRun(1, demo.brakeStart, demo.brakeEnd, 6.0D, 6.0D, BrakeRun.Mode.TRIM, TICK));
        RideRater rater = RideRater.standard(new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D), 9.81D);
        TrainSpec train = new TrainSpec(5, 3.0D, 0.5D, 4);

        List<RideWarning> warnings = RideCheck.check(rater, network, elements, 1, train, SafetyLimits.DEFAULT);
        assertTrue(warnings.isEmpty(), "the ride check found: " + warnings);

        RideStatistics stats = rater.simulateRide(network, elements, 1, train);
        RideRating rating = RideRating.from(stats);
        assertTrue(rating.safety.safe, "rated " + rating.safety);
        assertTrue(stats.peakNegativeVerticalG < 0.0D, "a camelback that gives no airtime: "
            + stats.peakNegativeVerticalG + " g at its lowest");
    }
}
