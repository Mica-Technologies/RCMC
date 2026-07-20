package com.micatechnologies.minecraft.rcmc.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.builder.TrackBuildSession.SegmentType;
import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the mapping from a builder's segment-type selection to working ride hardware.
 *
 * <p>Written after track marked as chain lift behaved as plain track in a real build. The logic was
 * inline in {@code ItemTrackTool}, so the only way to exercise it was to construct a coaster by
 * hand and ride it — which is exactly why it shipped broken. Pulling it into
 * {@link SegmentElements} made it testable, and these tests go as far as putting a train on the
 * result: "an element was created" is not the claim that matters, "the train actually climbs" is.</p>
 */
class SegmentElementsTest {

    private static final double TICK = 1.0D / 20.0D;

    /** A steady climb, the shape a lift hill actually is. */
    private static TrackSection climbingSection() {
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i <= 4; i++) {
            nodes.add(new TrackNode(new Vec3(i * 15.0D, 64.0D + i * 9.0D, 0.0D)));
        }
        return new TrackSection(1, nodes, false, null);
    }

    private static List<SegmentType> allOf(SegmentType type, int count) {
        List<SegmentType> types = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            types.add(type);
        }
        return types;
    }

    @Test
    @DisplayName("tagging every node as lift produces one lift covering the whole section")
    void wholeSectionLift() {
        TrackSection section = climbingSection();
        List<RideElement> elements =
            SegmentElements.build(section, allOf(SegmentType.LIFT, section.nodes().size()));

        assertEquals(1, elements.size(), "a single run should make a single element");
        RideElement lift = elements.get(0);
        assertTrue(lift instanceof ChainLift, "expected a ChainLift, got " + lift.getClass());
        assertEquals(0.0D, lift.startDistance(), 1e-6);
        assertEquals(section.totalLength(), lift.endDistance(), 1e-6,
            "the lift must reach the end of the section, not stop at the last tagged node");
    }

    @Test
    @DisplayName("a lift actually carries a train up the hill")
    void liftCarriesATrain() {
        // The assertion that matters. An element that exists but never contains the train, or
        // spans zero length, satisfies every structural check and does nothing.
        TrackSection section = climbingSection();
        TrackNetwork network = new TrackNetwork();
        network.addSection(section);

        RideElementSet elements = new RideElementSet();
        for (RideElement element : SegmentElements.build(section,
            allOf(SegmentType.LIFT, section.nodes().size()))) {
            elements.add(element);
        }

        Train train = new Train(TrainSpec.singleCar(),
            new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D), new TrackRef(1, 1.0D), 1.0D);
        TrainManager manager = new TrainManager();
        manager.add(1, train);

        double startHeight = network.frameAt(train.reference()).position.y;
        for (int tick = 0; tick < 1200 && train.isRunning(); tick++) {
            manager.tick(network, elements, 4, TICK);
        }
        double endHeight = network.frameAt(train.reference()).position.y;

        assertTrue(endHeight > startHeight + 20.0D,
            "lift failed to carry the train up; " + startHeight + " -> " + endHeight);
    }

    @Test
    @DisplayName("a single tagged node still produces a usable element")
    void singleTaggedNode() {
        // The degenerate case that produced a zero-length element and therefore did nothing at all.
        TrackSection section = climbingSection();
        List<SegmentType> types = new ArrayList<>(
            Arrays.asList(SegmentType.PLAIN, SegmentType.LIFT, SegmentType.PLAIN,
                SegmentType.PLAIN, SegmentType.PLAIN));

        List<RideElement> elements = SegmentElements.build(section, types);
        assertEquals(1, elements.size());
        assertTrue(elements.get(0).endDistance() > elements.get(0).startDistance(),
            "a single tagged node must still span real track, not zero length");
    }

    @Test
    @DisplayName("adjacent runs of different types become separate elements")
    void separateRuns() {
        TrackSection section = climbingSection();
        List<SegmentType> types = Arrays.asList(
            SegmentType.LIFT, SegmentType.LIFT, SegmentType.PLAIN,
            SegmentType.BRAKE, SegmentType.BRAKE);

        List<RideElement> elements = SegmentElements.build(section, types);
        assertEquals(2, elements.size(), "expected one lift and one brake run");
        assertTrue(elements.get(0).endDistance() <= elements.get(1).startDistance(),
            "runs should not overlap");
    }

    @Test
    @DisplayName("all-plain track produces no elements at all")
    void plainProducesNothing() {
        TrackSection section = climbingSection();
        assertTrue(SegmentElements.build(section,
            allOf(SegmentType.PLAIN, section.nodes().size())).isEmpty(),
            "plain is the absence of hardware, not a kind of it");
    }
}
