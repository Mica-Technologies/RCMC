package com.micatechnologies.minecraft.rcmc.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork.End;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.track.storage.TrackCodec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A builder marks a walkway once. What these hold the code to is that the mark is still there, in
 * the same place in the world, after anything else is done to the track above it: an edit that
 * quietly dropped it would put columns back in the walkway with nothing to say why.
 */
class SupportExclusionTest {

    private static final SupportExclusion WALKWAY = new SupportExclusion(790, 168, 810, 181);
    private static final SupportExclusion PLAZA = new SupportExclusion(-5, -5, 5, 5);

    private static TrackSection run(int id, double x0, int nodes) {
        List<TrackNode> list = new ArrayList<>();
        for (int i = 0; i < nodes; i++) {
            list.add(new TrackNode(new Vec3(x0 + i * 10.0D, 64.0D + (i % 2) * 2.0D, i * i * 0.8D)));
        }
        return new TrackSection(id, list, false, null);
    }

    private static TrackSection marked(int id, double x0, int nodes) {
        return run(id, x0, nodes).withSupportExclusions(Arrays.asList(WALKWAY, PLAZA));
    }

    private static void assertStillMarked(String edit, TrackSection section) {
        assertEquals(Arrays.asList(WALKWAY, PLAZA), section.supportExclusions(), edit + " lost the exclusions");
    }

    @Test
    @DisplayName("a box covers whole blocks, both corners included, whichever way it was given")
    void boxCoversWholeBlocks() {
        SupportExclusion box = new SupportExclusion(810, 181, 790, 168);
        assertTrue(box.contains(790.0D, 168.0D), "the low corner block");
        assertTrue(box.contains(810.99D, 181.99D), "the far edge of the high corner block");
        assertFalse(box.contains(811.0D, 175.0D), "the block past the high corner");
        assertFalse(box.contains(789.99D, 175.0D), "the block before the low corner");
        // Negative coordinates floor away from zero: -0.5 is in block -1, not block 0.
        assertTrue(new SupportExclusion(-1, -1, -1, -1).contains(-0.5D, -0.5D));
        assertFalse(new SupportExclusion(0, 0, 0, 0).contains(-0.5D, 0.5D));
    }

    @Test
    @DisplayName("a section says no to a column only inside one of its boxes")
    void sectionExcludesInsideItsBoxes() {
        TrackSection section = marked(1, 0.0D, 6);
        assertTrue(section.excludesSupportAt(800.5D, 170.5D));
        assertTrue(section.excludesSupportAt(0.5D, 0.5D));
        assertFalse(section.excludesSupportAt(30.5D, 30.5D));
        assertFalse(run(1, 0.0D, 6).excludesSupportAt(800.5D, 170.5D), "an unmarked section excludes nothing");
    }

    @Test
    @DisplayName("changing the exclusions makes a new section, so caches keyed on it see an edit")
    void changingExclusionsIsAnEdit() {
        TrackSection plain = run(1, 0.0D, 6);
        TrackSection one = plain.withSupportExclusions(Collections.singletonList(WALKWAY));
        assertTrue(one != plain, "adding a box must not hand back the same instance");
        assertSame(one, one.withSupportExclusions(Collections.singletonList(WALKWAY)), "no change, no new section");
        assertTrue(one.withSupportExclusions(Collections.<SupportExclusion>emptyList()) != one);
        // The track itself is untouched by it.
        assertEquals(plain.totalLength(), one.totalLength(), 0.0D);
        assertEquals(plain.nodes(), one.nodes());
    }

    @Test
    @DisplayName("every edit of a section keeps its exclusions")
    void editsKeepExclusions() {
        TrackSection s = marked(1, 0.0D, 6);
        TrackNode extra = new TrackNode(new Vec3(25.0D, 66.0D, 3.0D));
        assertStillMarked("moving a node", s.withNode(2, extra));
        assertStillMarked("inserting a node", s.withNodeInserted(3, extra));
        assertStillMarked("removing a node", s.withNodeRemoved(2));
        assertStillMarked("appending a node", s.withNodeAppended(new TrackNode(new Vec3(70.0D, 64.0D, 30.0D))));
        assertStillMarked("painting", s.withPalette(TrackPalette.DEFAULT.with(TrackPalette.Part.RAIL,
            TrackPalette.Colour.values()[2])));
        assertStillMarked("restyling", s.withStyle("transit"));
        assertStillMarked("closing", s.withClosed(true));
        assertStillMarked("reversing", s.reversed());
        assertStillMarked("extending", TrackAttachment.extend(s, End.END,
            Arrays.asList(new TrackNode(new Vec3(60.0D, 64.0D, 25.0D)), new TrackNode(new Vec3(70.0D, 64.0D, 30.0D))),
            false));
    }

    @Test
    @DisplayName("splitting keeps the exclusions on both halves, and merging keeps both sections'")
    void surgeryKeepsExclusions() {
        SectionSurgery.Result split = SectionSurgery.split(marked(1, 0.0D, 6), 3, 2);
        assertEquals(2, split.sections().size());
        for (TrackSection half : split.sections()) {
            assertStillMarked("splitting (half #" + half.id() + ")", half);
        }

        TrackSection a = run(1, 0.0D, 4).withSupportExclusions(Collections.singletonList(WALKWAY));
        TrackSection b = new TrackSection(2, Arrays.asList(a.nodes().get(3),
            new TrackNode(new Vec3(40.0D, 64.0D, 12.0D)), new TrackNode(new Vec3(50.0D, 64.0D, 20.0D))), false, null)
            .withSupportExclusions(Arrays.asList(PLAZA, WALKWAY));
        SectionSurgery.Result merged = SectionSurgery.merge(a, End.END, b, End.START);
        assertEquals(Arrays.asList(WALKWAY, PLAZA), merged.sections().get(0).supportExclusions(),
            "a merge keeps each box once");

        TrackSection closed = SectionSurgery.close(marked(1, 0.0D, 6)).sections().get(0);
        assertStillMarked("closing into a circuit", closed);
        assertStillMarked("opening a circuit", SectionSurgery.split(closed, 2, 9).sections().get(0));
    }

    @Test
    @DisplayName("exclusions survive a save and a sync, and an unmarked section stays unmarked")
    void codecRoundTrip() {
        TrackNetwork network = new TrackNetwork();
        network.addSection(marked(1, 0.0D, 6));
        network.addSection(run(2, 200.0D, 4));
        TrackNetwork restored = TrackCodec.readNetwork(TrackCodec.writeNetwork(network));
        assertStillMarked("a save and load", restored.section(1));
        assertTrue(restored.section(2).supportExclusions().isEmpty());
        assertStillMarked("a section tag", TrackCodec.readSection(TrackCodec.writeSection(network.section(1))));
    }
}
