package com.micatechnologies.minecraft.rcmc.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.micatechnologies.minecraft.rcmc.rating.RideWarning;
import com.micatechnologies.minecraft.rcmc.rating.SafetyLimits;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The ride check has to say what is wrong with a ride, and where, from how it actually runs. */
class RideCheckTest {

    private static final double TICK = RcmcConstants.SECONDS_PER_TICK;
    private static final TrainSpec TRAIN = new TrainSpec(5, 3.0D, 0.5D, 4);

    private static RideRater rater() {
        return RideRater.standard(new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D), 9.81D);
    }

    /** The demo coaster, with its section as given. */
    private static List<RideWarning> checkDemo(TrackSection section, DemoCoaster.Result demo) {
        TrackNetwork network = new TrackNetwork();
        network.addSection(section);
        RideElementSet elements = new RideElementSet();
        elements.add(new StationPlatform(1, demo.stationStart, demo.stationEnd, demo.stationStop,
            6.0D, 60, 4.0D, 6.0D, TICK));
        elements.add(new ChainLift(1, demo.liftStart, demo.liftEnd, 5.0D, 12.0D, TICK));
        elements.add(new BrakeRun(1, demo.brakeStart, demo.brakeEnd, 6.0D, 6.0D, BrakeRun.Mode.TRIM, TICK));
        return RideCheck.check(rater(), network, elements, 1, TRAIN, SafetyLimits.DEFAULT);
    }

    @Test
    @DisplayName("the demo coaster runs its lap: nothing stalls it")
    void demoRunsClean() {
        DemoCoaster.Result demo = DemoCoaster.build(1, new Vec3(0, 64, 0), 1.0D, 34.0D);
        for (RideWarning warning : checkDemo(demo.section, demo)) {
            assertFalse(warning.kind == RideWarning.Kind.STALL, "the demo stalls: " + warning);
        }
    }

    @Test
    @DisplayName("a hill too high to climb is reported as a stall, at the hill")
    void hillTooHighIsFound() {
        DemoCoaster.Result demo = DemoCoaster.build(1, new Vec3(0, 64, 0), 1.0D, 34.0D);
        // Raise the first hill after the drop well above the lift: no train can get over it.
        List<TrackNode> nodes = new java.util.ArrayList<>(demo.section.nodes());
        int hill = 10;
        TrackNode old = nodes.get(hill);
        nodes.set(hill, new TrackNode(old.position().add(new Vec3(0, 40, 0)), old.bankDegrees(), null));
        TrackSection raised = new TrackSection(1, nodes, true, null);

        RideWarning stall = null;
        for (RideWarning warning : checkDemo(raised, demo)) {
            if (warning.kind == RideWarning.Kind.STALL) {
                stall = warning;
            }
        }
        assertNotNull(stall, "a hill 40 blocks above the lift must stop the train");
        double hillAt = raised.nodeDistance(hill);
        assertTrue(Math.abs(stall.from - hillAt) < 40.0D,
            "the stall is at " + stall.from + ", but the hill is at " + hillAt);
    }

    @Test
    @DisplayName("a flat turn taken fast is flagged for lateral G, on the turn")
    void tightTurnAtSpeedIsFound() {
        // A long straight drop into a tight, flat hairpin, then back up: the only hard sideways
        // G is in the hairpin.
        List<TrackNode> nodes = java.util.Arrays.asList(
            new TrackNode(new Vec3(0, 100, 0)), new TrackNode(new Vec3(40, 70, 0)),
            new TrackNode(new Vec3(80, 64, 0)), new TrackNode(new Vec3(92, 64, 6)),
            new TrackNode(new Vec3(92, 64, 18)), new TrackNode(new Vec3(80, 64, 24)),
            new TrackNode(new Vec3(40, 64, 24)));
        TrackSection section = new TrackSection(1, nodes, false, null);
        TrackNetwork network = new TrackNetwork();
        network.addSection(section);
        List<RideWarning> warnings = RideCheck.check(rater(), network, new RideElementSet(), 1,
            new TrainSpec(1, 3.0D, 0.5D, 4), SafetyLimits.DEFAULT);

        RideWarning lateral = null;
        for (RideWarning warning : warnings) {
            if (warning.kind == RideWarning.Kind.LATERAL_G) {
                lateral = warning;
            }
        }
        assertNotNull(lateral, "a flat hairpin at the bottom of a 36-block drop: " + warnings);
        assertTrue(lateral.to >= section.nodeDistance(3) - 5.0D && lateral.from <= section.nodeDistance(5) + 5.0D,
            "flagged at " + lateral.from + ".." + lateral.to + ", not on the hairpin");
    }
}
