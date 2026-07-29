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

    /** Flat and long, which is what a launch run and a station approach both actually are. */
    private static TrackSection flatSection() {
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i <= 4; i++) {
            nodes.add(new TrackNode(new Vec3(i * 20.0D, 64.0D, 0.0D)));
        }
        return new TrackSection(1, nodes, false, null);
    }

    /** Runs a train over a whole section tagged as one type, and returns it. */
    private static Train runTagged(TrackSection section, SegmentType type,
                                   double startSpeed, int ticks) {
        TrackNetwork network = new TrackNetwork();
        network.addSection(section);

        RideElementSet elements = new RideElementSet();
        for (RideElement element : SegmentElements.build(section,
            allOf(type, section.nodes().size()))) {
            elements.add(element);
        }

        Train train = new Train(TrainSpec.singleCar(),
            new PhysicsIntegrator(9.81D, 0.01D, 0.0015D, 60.0D), new TrackRef(1, 1.0D), startSpeed);
        TrainManager manager = new TrainManager();
        manager.add(1, train);
        for (int tick = 0; tick < ticks && train.isRunning(); tick++) {
            manager.tick(network, elements, 4, TICK);
        }
        return train;
    }

    @Test
    @DisplayName("a launch actually launches: it accelerates a train to its target speed")
    void launchAcceleratesATrain() {
        // LaunchTrack was complete, tested and persisted for weeks with no way to place one. This
        // asserts the authoring path, not the element — the element's own physics is covered in
        // LaunchTrackTest. What was missing was the sentence connecting the two.
        TrackSection section = flatSection();
        Train train = runTagged(section, SegmentType.LAUNCH, 1.0D, 60);

        assertTrue(train.velocity() > 20.0D,
            "launch failed to bring the train up to speed; reached " + train.velocity());
    }

    @Test
    @DisplayName("a launch stops pushing once the train is at target speed")
    void launchDoesNotPushForever() {
        // The motors switch off at target rather than accelerating indefinitely — the property that
        // makes this a launch and not a rocket. A long run at full push would sail past it.
        TrackSection section = flatSection();
        Train train = runTagged(section, SegmentType.LAUNCH, 1.0D, 200);

        assertTrue(train.velocity() < 24.0D,
            "launch kept pushing past its target; reached " + train.velocity());
    }

    @Test
    @DisplayName("drive tyres bring a train down to a creep speed and hold it there")
    void driveTyresCreep() {
        // DriveTyres was the other element with no authoring path, and it was marked DONE in the
        // plan. Approaching from ABOVE the creep speed is the case that matters: the tyres are a
        // speed servo, so they must brake a fast train as well as nudge a stopped one.
        TrackSection section = flatSection();
        Train train = runTagged(section, SegmentType.TYRES, 10.0D, 200);

        assertEquals(2.0D, train.velocity(), 0.5D,
            "drive tyres should hold the creep speed, not the speed the train arrived at");
    }

    @Test
    @DisplayName("every segment type maps back to itself through segmentTypeOf")
    void typesRoundTrip() {
        // The track editor cycles a span by asking what is there and taking .next(). A type missing
        // from that inverse mapping is silently converted into something else on the next G press.
        TrackSection section = flatSection();
        for (SegmentType type : SegmentType.values()) {
            List<RideElement> built =
                SegmentElements.build(section, allOf(type, section.nodes().size()));
            if (type == SegmentType.PLAIN) {
                continue;
            }
            assertEquals(type, SegmentElements.segmentTypeOf(built.get(0)),
                type + " did not survive the round trip through segmentTypeOf");
        }
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
