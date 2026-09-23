package com.micatechnologies.minecraft.rcmc.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.rating.RideRater;
import com.micatechnologies.minecraft.rcmc.rating.RideStatistics;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.element.Corkscrew;
import com.micatechnologies.minecraft.rcmc.track.element.DiveLoop;
import com.micatechnologies.minecraft.rcmc.track.element.ElementContext;
import com.micatechnologies.minecraft.rcmc.track.element.ElementResult;
import com.micatechnologies.minecraft.rcmc.track.element.Immelmann;
import com.micatechnologies.minecraft.rcmc.track.element.RollDirection;
import com.micatechnologies.minecraft.rcmc.track.element.Straight;
import com.micatechnologies.minecraft.rcmc.track.element.TrackElement;
import com.micatechnologies.minecraft.rcmc.track.element.ZeroGRoll;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Every inversion rides as an inversion should: upside down, and never thrown sideways.
 *
 * <p>Found measuring the corkscrew the builder offered: it rolled the car about the rail, so a
 * rider's chest swung round a circle a block across — 3 to 9 g sideways, at every size and speed.
 * These pieces roll about the riders' hearts instead, each shaped for the speed it is entered at.
 * Ridden at that speed, by a real five-car train, what a rider feels is checked here.</p>
 */
class InversionRideTest {

    private static final double GRAVITY = 9.81D;

    @ParameterizedTest(name = "{0} at {1} blocks/s, rolling {2}")
    @CsvSource({
        "corkscrew, 14, POSITIVE", "corkscrew, 20, NEGATIVE", "corkscrew, 30, POSITIVE",
        "zero-g roll, 14, POSITIVE", "zero-g roll, 20, NEGATIVE", "zero-g roll, 30, POSITIVE",
        "immelmann, 22, POSITIVE", "immelmann, 28, NEGATIVE", "immelmann, 36, POSITIVE",
        "dive loop, 14, POSITIVE", "dive loop, 20, NEGATIVE", "dive loop, 30, POSITIVE"})
    void ridesLikeAnInversion(String kind, double speed, RollDirection roll) {
        TrackElement element = element(kind, speed, roll);
        TrackFrame start = new TrackFrame(new Vec3(0.0D, 100.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), Vec3.UP);
        List<TrackNode> nodes = new ArrayList<>();
        nodes.add(new TrackNode(start.position));
        ElementResult in = new Straight(24.0D).generate(new ElementContext(start, 0.0D));
        nodes.addAll(in.nodes);
        int from = nodes.size() - 1;
        ElementResult piece = element.generate(in.asNextContext(ElementContext.DEFAULT_NODE_SPACING));
        nodes.addAll(piece.nodes);
        int to = nodes.size() - 1;
        nodes.addAll(new Straight(40.0D).generate(piece.asNextContext(ElementContext.DEFAULT_NODE_SPACING)).nodes);
        TrackSection section = new TrackSection(1, nodes, false, null);
        TrackNetwork network = new TrackNetwork();
        network.addSection(section);

        // The builder's own validator has nothing to say about the element's nodes: found in game,
        // where half-block nodes drew a warning for every one of them.
        for (com.micatechnologies.minecraft.rcmc.track.validation.TrackIssue issue
            : new com.micatechnologies.minecraft.rcmc.track.validation.TrackValidator().validate(section)) {
            assertTrue(issue.severity() != com.micatechnologies.minecraft.rcmc.track.validation.TrackIssue.Severity.WARNING
                && issue.severity() != com.micatechnologies.minecraft.rcmc.track.validation.TrackIssue.Severity.ERROR,
                kind + ": " + issue.code() + " " + issue.message());
        }

        // It leaves level and upright, facing the way the element says.
        Vec3 riderUp = piece.exitFrame.withBank(Math.toRadians(piece.exitBankDegrees)).up;
        assertEquals(1.0D, riderUp.dot(Vec3.UP), 0.01D, kind + " leaves the rider tilted: " + riderUp);
        double heading = piece.exitFrame.forward.dot(start.forward);
        boolean reverses = kind.equals("immelmann") || kind.equals("dive loop");
        assertEquals(reverses ? -1.0D : 1.0D, heading, 0.01D, kind + " leaves on the wrong heading");

        double enter = section.nodeDistance(from);
        double leave = section.nodeDistance(to);
        double[] worst = {0.0D, 9.0D, -9.0D};
        double[] furthest = {0.0D};
        RideRater rater = RideRater.standard(new PhysicsIntegrator(GRAVITY, 0.01D, 0.0015D, 60.0D), GRAVITY);
        // The front car starts at the element's entry, at its design speed; the rest of the train is
        // on the run-in behind it.
        RideStatistics stats = rater.simulate(network, new RideElementSet(), new TrackRef(1, enter),
            new TrainSpec(5, 3.0D, 0.5D, 4), speed, (where, v, g) -> {
                furthest[0] = Math.max(furthest[0], where.distance());
                if (where.distance() >= enter && where.distance() <= leave) {
                    worst[0] = Math.max(worst[0], Math.abs(g.lateral));
                    worst[1] = Math.min(worst[1], g.vertical);
                    worst[2] = Math.max(worst[2], g.vertical);
                }
            });

        assertTrue(furthest[0] > leave, kind + " stalled at " + furthest[0] + " of " + leave);
        assertTrue(stats.inversionCount >= 1, kind + " never turned its riders over");
        assertTrue(worst[0] < 0.6D, kind + " throws riders sideways: " + worst[0] + " g");
        // Hanging lightly in the restraints over the top is what an inversion does — the front car of
        // a corkscrew most, since the train behind it is still climbing — but never near the ride
        // check's -2 g, and never more than a firm pull-out's worth of pressing in.
        assertTrue(worst[1] > -1.5D && worst[2] < 4.5D,
            kind + " vertical load out of range: " + worst[1] + " .. " + worst[2] + " g");
    }

    private static TrackElement element(String kind, double speed, RollDirection roll) {
        switch (kind) {
            case "corkscrew":
                return new Corkscrew(speed, roll);
            case "zero-g roll":
                return new ZeroGRoll(speed, roll);
            case "immelmann":
                return new Immelmann(speed, roll);
            default:
                return new DiveLoop(speed, roll);
        }
    }
}
