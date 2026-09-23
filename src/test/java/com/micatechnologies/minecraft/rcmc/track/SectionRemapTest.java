package com.micatechnologies.minecraft.rcmc.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.debug.DemoCoaster;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElements;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Editing a node keeps everything on the section where the builder put it: between the same nodes.
 */
class SectionRemapTest {

    private static final double TICK = 1.0D / 20.0D;

    private final DemoCoaster.Result demo = DemoCoaster.build(1, new Vec3(0, 64, 0), 1.0D, 34.0D);
    private final TrackSection section = demo.section;

    /** The node at the top of the demo's lift: where its chain ends. */
    private int crestNode() {
        for (int i = 0; i < section.nodes().size(); i++) {
            if (Math.abs(section.nodeDistance(i) - demo.liftEnd) < 1.0e-6D) {
                return i;
            }
        }
        throw new AssertionError("no node at the lift's end");
    }

    @Test
    @DisplayName("raising the crest node, the chain still ends at the crest")
    void chainFollowsTheCrest() {
        int crest = crestNode();
        TrackNode old = section.nodes().get(crest);
        TrackSection raised = section.withNode(crest,
            new TrackNode(old.position().add(new Vec3(0, 6, 0)), old.bankDegrees(), old.styleId()));
        SectionRemap remap = SectionRemap.nodeChanged(section, raised);

        RideElement lift = new ChainLift(1, demo.liftStart, demo.liftEnd, 5.0D, 12.0D, TICK);
        RideElement moved = RideElements.moved(lift, 1, remap, TICK);
        assertEquals(raised.nodeDistance(crest), moved.endDistance(), 1.0e-6D,
            "the chain ends at the crest node, wherever it now is");
        assertTrue(raised.nodeDistance(crest) > section.nodeDistance(crest),
            "and that is further along, the climb being longer");
    }

    @Test
    @DisplayName("a point keeps its place between the same two nodes")
    void keepsItsFraction() {
        int crest = crestNode();
        TrackNode old = section.nodes().get(crest);
        TrackSection raised = section.withNode(crest,
            new TrackNode(old.position().add(new Vec3(0, 6, 0)), old.bankDegrees(), old.styleId()));
        SectionRemap remap = SectionRemap.nodeChanged(section, raised);
        for (int i = 0; i + 1 < section.nodes().size(); i++) {
            double a = section.nodeDistance(i);
            double b = section.nodeDistance(i + 1);
            double mid = a + (b - a) * 0.3D;
            double na = raised.nodeDistance(i);
            double nb = raised.nodeDistance(i + 1);
            assertEquals(na + (nb - na) * 0.3D, remap.applyAsDouble(mid), 1.0e-6D, "span " + i);
        }
    }

    @Test
    @DisplayName("inserting a node leaves every node's own position where it was")
    void insertKeepsNodes() {
        int at = 3;
        Vec3 mid = section.positionAtDistance((section.nodeDistance(at - 1) + section.nodeDistance(at)) * 0.5D);
        TrackSection inserted = section.withNodeInserted(at, new TrackNode(mid));
        SectionRemap remap = SectionRemap.nodeInserted(section, inserted, at);
        for (int i = 0; i < section.nodes().size(); i++) {
            int j = i < at ? i : i + 1;
            assertEquals(inserted.nodeDistance(j), remap.applyAsDouble(section.nodeDistance(i)), 1.0e-6D,
                "old node " + i + " is new node " + j);
        }
    }

    @Test
    @DisplayName("removing a node merges its two spans; the nodes either side stay put")
    void removeMerges() {
        int at = 4;
        TrackSection removed = section.withNodeRemoved(at);
        SectionRemap remap = SectionRemap.nodeRemoved(section, removed, at);
        for (int i = 0; i < section.nodes().size(); i++) {
            if (i == at) {
                continue;
            }
            int j = i < at ? i : i - 1;
            assertEquals(removed.nodeDistance(j), remap.applyAsDouble(section.nodeDistance(i)), 1.0e-6D,
                "old node " + i + " is new node " + j);
        }
        double where = remap.applyAsDouble(section.nodeDistance(at));
        assertTrue(where > removed.nodeDistance(at - 1) && where < removed.nodeDistance(at),
            "the removed node's own spot lands inside the merged span");
    }

    @Test
    @DisplayName("removing the first node of a circuit merges the wrap span, and nothing else moves")
    void removeFirstOfCircuit() {
        TrackSection removed = section.withNodeRemoved(0);
        SectionRemap remap = SectionRemap.nodeRemoved(section, removed, 0);
        for (int i = 1; i < section.nodes().size(); i++) {
            assertEquals(removed.nodeDistance(i - 1), remap.applyAsDouble(section.nodeDistance(i)), 1.0e-6D,
                "old node " + i);
        }
    }

    @Test
    @DisplayName("a station's stop point moves with its platform and stays on it")
    void stationStopStaysOnPlatform() {
        TrackNode first = section.nodes().get(1);
        TrackSection moved = section.withNode(1,
            new TrackNode(first.position().add(new Vec3(4, 0, 0)), first.bankDegrees(), first.styleId()));
        RideElement station = new StationPlatform(1, demo.stationStart, demo.stationEnd, demo.stationStop,
            6.0D, 60, 4.0D, 6.0D, TICK);
        StationPlatform after = (StationPlatform) RideElements.moved(station, 1,
            SectionRemap.nodeChanged(section, moved), TICK);
        assertTrue(after.stopDistance() >= after.startDistance() && after.stopDistance() <= after.endDistance());
    }
}
