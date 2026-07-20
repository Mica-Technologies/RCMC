package com.micatechnologies.minecraft.rcmc.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.builder.TrackBuildSession.SegmentType;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reproduces a builder's actual session — place, press G, place more, commit — end to end.
 *
 * <p>Written because segment types were reported as not working twice, and each previous fix
 * targeted a piece in isolation that turned out not to be the cause. Testing
 * {@code SegmentElements} alone proves the mapping is right given a correct type list; it proves
 * nothing about whether the session <em>produces</em> a correct type list, which is the other half
 * of the path and the half no test covered.</p>
 */
class BuildSessionFlowTest {

    private static final UUID PLAYER = UUID.nameUUIDFromBytes("flow-test".getBytes());

    private static TrackBuildSession freshSession() {
        TrackBuildSession.clear(PLAYER);
        return TrackBuildSession.of(PLAYER);
    }

    /** Places a node the way {@code ItemTrackTool.onItemUse} does. */
    private static void place(TrackBuildSession session, double x, double y, double z) {
        session.add(new TrackNode(new Vec3(x, y, z), session.bankDegrees(), null));
    }

    private static TrackSection sectionOf(TrackBuildSession session) {
        return new TrackSection(1, session.pending(), session.isClosing(), null);
    }

    @Test
    @DisplayName("the reported flow: plain, then lift for nodes 3-6, then plain")
    void liftSelectedPartwayThrough() {
        // Exactly what was described: lift selected between nodes 3 and 6 of a longer layout.
        TrackBuildSession session = freshSession();

        place(session, 0, 64, 0);
        place(session, 20, 64, 0);
        place(session, 40, 64, 0);

        assertEquals(SegmentType.LIFT, session.cycleType(), "one press should reach LIFT");
        place(session, 60, 70, 0);
        place(session, 80, 78, 0);
        place(session, 100, 86, 0);
        place(session, 120, 94, 0);

        // Back to plain for the rest.
        session.cycleType();
        session.cycleType();
        session.cycleType();
        assertEquals(SegmentType.PLAIN, session.currentType());
        place(session, 140, 90, 0);
        place(session, 160, 80, 0);

        List<SegmentType> types = session.pendingTypes();
        assertEquals(session.pending().size(), types.size(),
            "every placed node must have a recorded type");
        assertEquals(SegmentType.PLAIN, types.get(2));
        assertEquals(SegmentType.LIFT, types.get(3));
        assertEquals(SegmentType.LIFT, types.get(6));
        assertEquals(SegmentType.PLAIN, types.get(7));

        TrackSection section = sectionOf(session);
        List<RideElement> elements = SegmentElements.build(section, types);
        assertEquals(1, elements.size(), "expected exactly one chain lift");
        assertTrue(elements.get(0) instanceof ChainLift,
            "expected a ChainLift, got " + elements.get(0).getClass().getSimpleName());

        // THE assertion this whole file exists for. The builder selected lift, then drew the track
        // between nodes 2 and 6 — four clicks producing four spans. The element must cover exactly
        // that stretch.
        //
        // It used to be placed one span later, from node 3 to node 7, because a node's type was
        // taken to describe the track LEAVING it rather than the track arriving at it. The
        // observable symptom was precise and is worth recording: the stretch marked as lift came
        // out plain, and the plain stretch after it came out as lift, while the HUD read correctly
        // throughout. A type list can be entirely right and still produce wrong track.
        assertEquals(section.nodeDistance(2), elements.get(0).startDistance(), 1e-6,
            "the lift must start where the builder started drawing it — the node BEFORE the first "
                + "tagged one");
        assertEquals(section.nodeDistance(6), elements.get(0).endDistance(), 1e-6,
            "the lift must end at the last node drawn while it was selected");
    }

    @Test
    @DisplayName("cycling before placing any node still tags the first node")
    void cycleBeforeFirstPlacement() {
        // A builder who selects the type first and then starts building — arguably the more
        // natural order, and one where an off-by-one would tag nothing.
        TrackBuildSession session = freshSession();
        session.cycleType();
        place(session, 0, 64, 0);
        place(session, 20, 70, 0);
        place(session, 40, 78, 0);

        assertEquals(SegmentType.LIFT, session.pendingTypes().get(0));
        assertFalse(SegmentElements.build(sectionOf(session), session.pendingTypes()).isEmpty(),
            "a type selected before the first click must still take effect");
    }

    @Test
    @DisplayName("undo removes the node's type with the node")
    void undoKeepsListsAligned() {
        // If these ever drift out of step, every type after the undo is attributed to the wrong
        // node — which would look exactly like "the type I picked did nothing".
        TrackBuildSession session = freshSession();
        place(session, 0, 64, 0);
        session.cycleType();
        place(session, 20, 64, 0);
        place(session, 40, 64, 0);

        session.undo();
        assertEquals(session.pending().size(), session.pendingTypes().size(),
            "nodes and types must stay the same length through an undo");
        assertEquals(SegmentType.PLAIN, session.pendingTypes().get(0));
        assertEquals(SegmentType.LIFT, session.pendingTypes().get(1));
    }

    @Test
    @DisplayName("the selected type survives a commit, so consecutive sections keep it")
    void typeSurvivesReset() {
        TrackBuildSession session = freshSession();
        session.cycleType();
        assertEquals(SegmentType.LIFT, session.currentType());

        session.reset();
        assertEquals(SegmentType.LIFT, session.currentType(),
            "re-selecting the type after every commit would be tedious");
        assertTrue(session.pendingTypes().isEmpty(), "reset must still clear the pending nodes");
    }

    @Test
    @DisplayName("a whole-section lift on a closed circuit reaches all the way round")
    void closedCircuitLift() {
        TrackBuildSession session = freshSession();
        session.cycleType();
        session.setClosing(true);
        for (int i = 0; i < 8; i++) {
            double a = 2.0D * Math.PI * i / 8;
            place(session, Math.cos(a) * 40.0D, 64.0D, Math.sin(a) * 40.0D);
        }

        TrackSection section = sectionOf(session);
        List<RideElement> elements = SegmentElements.build(section, session.pendingTypes());
        assertEquals(1, elements.size());
        assertEquals(section.totalLength(), elements.get(0).endDistance(), 1e-6,
            "a lift covering every node of a circuit should span the whole lap");
    }
}
