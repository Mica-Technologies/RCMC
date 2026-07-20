package com.micatechnologies.minecraft.rcmc.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.element.Curve;
import com.micatechnologies.minecraft.rcmc.track.element.ElementContext;
import com.micatechnologies.minecraft.rcmc.track.element.ElementResult;
import com.micatechnologies.minecraft.rcmc.track.element.Slope;
import com.micatechnologies.minecraft.rcmc.track.element.Straight;
import com.micatechnologies.minecraft.rcmc.track.element.TrackElement;
import com.micatechnologies.minecraft.rcmc.track.element.TurnDirection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.track.storage.TrackCodec;
import com.micatechnologies.minecraft.rcmc.track.validation.TrackIssue;
import com.micatechnologies.minecraft.rcmc.track.validation.TrackValidator;
import com.micatechnologies.minecraft.rcmc.track.validation.ValidationLimits;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end checks across the whole stack: prefab elements → section geometry → validation →
 * physics → persistence.
 *
 * <p>Each package has thorough tests of its own, but every one of them checks its layer in
 * isolation. The failure mode this file exists to catch is the one where every piece is
 * individually correct and they still do not compose — an element that emits nodes too closely
 * spaced for the validator, a chained exit frame that does not line up with the next entry, a
 * generated circuit whose geometry a train cannot actually traverse.</p>
 */
class CoasterCompositionTest {

    private static final double TICK = 1.0D / 20.0D;

    /** Chains elements from a starting frame, collecting every node they emit. */
    private static List<TrackNode> chain(TrackFrame start, double startBank, TrackElement... elements) {
        List<TrackNode> nodes = new ArrayList<>();
        nodes.add(new TrackNode(start.position, startBank, null));

        ElementContext context = new ElementContext(start, startBank);
        for (TrackElement element : elements) {
            ElementResult result = element.generate(context);
            nodes.addAll(result.nodes);
            context = result.asNextContext(ElementContext.DEFAULT_NODE_SPACING);
        }
        return nodes;
    }

    private static TrackFrame levelStart() {
        return new TrackFrame(new Vec3(0, 64, 0), new Vec3(1, 0, 0), Vec3.UP);
    }

    @Test
    @DisplayName("chained elements produce a section a train can run end to end")
    void chainedElementsAreTraversable() {
        List<TrackNode> nodes = chain(levelStart(), 0.0D,
            new Straight(40.0D),
            new Slope(60.0D, -25.0D),
            new Curve(35.0D, 90.0D, TurnDirection.LEFT, 22.0D, 55.0D),
            new Straight(30.0D));

        TrackSection section = new TrackSection(1, nodes, false, null);
        TrackNetwork network = new TrackNetwork();
        network.addSection(section);

        Train train = new Train(TrainSpec.singleCar(),
            new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D),
            new TrackRef(1, 0.0D), 6.0D);

        for (int tick = 0; tick < 2000 && train.isRunning(); tick++) {
            train.tick(network, 0.0D, 4, TICK);
        }

        // It should reach the far end (a dead end on an open section), not valley on the way.
        assertEquals(Train.Status.DEAD_END, train.status(),
            "train did not make it around; ended " + train.status()
                + " at " + train.reference());
    }

    @Test
    @DisplayName("a gently-built element chain raises no validator warnings")
    void wellBuiltTrackValidatesClean() {
        // If sensible prefabs trip the validator's defaults, one of the two is miscalibrated —
        // and a validator that cries wolf on good track is worse than no validator.
        List<TrackNode> nodes = chain(levelStart(), 0.0D,
            new Straight(40.0D),
            new Curve(60.0D, 90.0D, TurnDirection.RIGHT, 20.0D, 55.0D),
            new Straight(40.0D));

        TrackSection section = new TrackSection(1, nodes, false, null);
        List<TrackIssue> issues = new TrackValidator(ValidationLimits.DEFAULT).validate(section);

        List<TrackIssue> serious = new ArrayList<>();
        for (TrackIssue issue : issues) {
            if (issue.severity() != TrackIssue.Severity.INFO) {
                serious.add(issue);
            }
        }
        assertTrue(serious.isEmpty(), "well-built track produced warnings: " + serious);
    }

    @Test
    @DisplayName("element-built track survives a save/load round trip identically")
    void generatedTrackPersists() {
        List<TrackNode> nodes = chain(levelStart(), 0.0D,
            new Straight(30.0D),
            new Curve(40.0D, 120.0D, TurnDirection.LEFT, 18.0D, 55.0D));

        TrackNetwork original = new TrackNetwork();
        original.addSection(new TrackSection(7, nodes, false, null));

        TrackNetwork restored = TrackCodec.readNetwork(TrackCodec.writeNetwork(original));
        TrackSection before = original.section(7);
        TrackSection after = restored.section(7);

        assertEquals(before.nodes(), after.nodes());
        assertEquals(before.totalLength(), after.totalLength(), 1e-9);

        // Geometry, not just node data, must survive — sample the frame around the section.
        for (int i = 0; i <= 20; i++) {
            double s = before.totalLength() * i / 20.0D;
            assertEquals(before.frameAtDistance(s).position.x, after.frameAtDistance(s).position.x, 1e-9);
            assertEquals(Math.toDegrees(before.bankRadiansAt(s)),
                Math.toDegrees(after.bankRadiansAt(s)), 1e-9);
        }
    }

    @Test
    @DisplayName("an element chain's exit frame lines up with the next element's entry")
    void chainingIsContinuous() {
        // The seam between two prefabs is the one place a builder cannot fix by hand, so the
        // handoff has to be exact rather than approximately right.
        ElementContext context = new ElementContext(levelStart(), 0.0D);

        ElementResult first = new Straight(40.0D).generate(context);
        TrackNode lastOfFirst = first.nodes.get(first.nodes.size() - 1);
        assertEquals(0.0D, lastOfFirst.position().distanceTo(first.exitFrame.position), 1e-6,
            "exit frame should sit on the element's final node");

        ElementResult second = new Curve(50.0D, 45.0D, TurnDirection.LEFT, 20.0D, 55.0D)
            .generate(first.asNextContext(ElementContext.DEFAULT_NODE_SPACING));
        TrackNode firstOfSecond = second.nodes.get(0);

        // The next element must start moving away from where the last one stopped, in the
        // direction it was heading — not jump.
        Vec3 step = firstOfSecond.position().subtract(first.exitFrame.position);
        assertTrue(step.length() < ElementContext.DEFAULT_NODE_SPACING * 2.0D,
            "gap of " + step.length() + " blocks at the element seam");
        assertTrue(step.normalize().dot(first.exitFrame.forward) > 0.9D,
            "next element does not continue in the previous element's direction");
    }

    @Test
    @DisplayName("a full circuit of elements closes and laps")
    void generatedCircuitLaps() {
        // Four 90-degree curves of equal radius separated by equal straights close a rectangle.
        List<TrackNode> nodes = chain(levelStart(), 0.0D,
            new Straight(40.0D), new Curve(30.0D, 90.0D, TurnDirection.LEFT, 18.0D, 55.0D),
            new Straight(40.0D), new Curve(30.0D, 90.0D, TurnDirection.LEFT, 18.0D, 55.0D),
            new Straight(40.0D), new Curve(30.0D, 90.0D, TurnDirection.LEFT, 18.0D, 55.0D),
            new Straight(40.0D), new Curve(30.0D, 90.0D, TurnDirection.LEFT, 18.0D, 55.0D));

        // Drop the duplicated closing node so the wrap is implicit, as closed sections require.
        nodes.remove(nodes.size() - 1);
        TrackSection circuit = new TrackSection(1, nodes, true, null);
        TrackNetwork network = new TrackNetwork();
        network.addSection(circuit);

        Train train = new Train(TrainSpec.singleCar(),
            new PhysicsIntegrator(9.81D, 0.0D, 0.0D, 60.0D), new TrackRef(1, 0.0D), 18.0D);
        for (int tick = 0; tick < 1200; tick++) {
            train.tick(network, 0.0D, 4, TICK);
        }

        assertTrue(train.isRunning(), "train stopped on a closed circuit: " + train.status());
        assertEquals(18.0D, train.speed(), 1.0D,
            "a level frictionless circuit should hold its speed");
    }

    @Test
    @DisplayName("the geometry pipeline stays free of Minecraft types")
    void pipelineRemainsPure() {
        // A guard on the project's load-bearing rule, phrased as something that runs rather than
        // as a line in CLAUDE.md nobody re-reads. If a Minecraft type ever leaks into these
        // packages, this test still compiles — but the classes could no longer be loaded on a
        // bare JVM without the game, which is exactly what the whole suite depends on.
        List<String> pureClasses = Arrays.asList(
            "com.micatechnologies.minecraft.rcmc.track.math.CatmullRomSpline",
            "com.micatechnologies.minecraft.rcmc.track.TrackSection",
            "com.micatechnologies.minecraft.rcmc.track.element.VerticalLoop",
            "com.micatechnologies.minecraft.rcmc.track.validation.TrackValidator",
            "com.micatechnologies.minecraft.rcmc.physics.Train");

        for (String name : pureClasses) {
            try {
                Class<?> loaded = Class.forName(name);
                assertTrue(loaded.getName().equals(name), name);
            }
            catch (ClassNotFoundException e) {
                throw new AssertionError("could not load " + name, e);
            }
        }
    }
}
