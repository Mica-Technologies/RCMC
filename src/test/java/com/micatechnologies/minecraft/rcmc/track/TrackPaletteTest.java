package com.micatechnologies.minecraft.rcmc.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackPalette.Colour;
import com.micatechnologies.minecraft.rcmc.track.TrackPalette.Part;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrackPaletteTest {

    private static TrackSection section(TrackPalette palette) {
        return new TrackSection(1, Arrays.asList(
            new TrackNode(new Vec3(0, 64, 0)),
            new TrackNode(new Vec3(50, 64, 0)),
            new TrackNode(new Vec3(100, 64, 0))), false, null, palette);
    }

    @Test
    @DisplayName("recolouring one part leaves the others alone")
    void withChangesOnlyOnePart() {
        TrackPalette painted = TrackPalette.DEFAULT.with(Part.RAIL, Colour.BLUE);
        assertEquals(Colour.BLUE, painted.of(Part.RAIL));
        assertEquals(TrackPalette.DEFAULT.of(Part.SPINE), painted.of(Part.SPINE));
        assertEquals(TrackPalette.DEFAULT.of(Part.TIE), painted.of(Part.TIE));
        assertEquals(TrackPalette.DEFAULT.of(Part.SUPPORT), painted.of(Part.SUPPORT));
    }

    @Test
    @DisplayName("cycling a colour eventually returns to where it started")
    void colourCycleWraps() {
        Colour start = Colour.STEEL;
        Colour current = start;
        for (int i = 0; i < Colour.values().length; i++) {
            current = current.next();
        }
        assertEquals(start, current, "a full cycle should come back around");
    }

    @Test
    @DisplayName("an unknown colour name falls back instead of failing the load")
    void unknownColourFallsBack() {
        // A colour from a newer version, or one since removed. Refusing to load a park over a
        // paint job would be a poor trade.
        assertEquals(Colour.RED, Colour.byName("CHARTREUSE_SPARKLE", Colour.RED));
        assertEquals(Colour.RED, Colour.byName(null, Colour.RED));
        assertEquals(Colour.BLUE, Colour.byName("blue", Colour.RED), "lookup should ignore case");
    }

    @Test
    @DisplayName("the default palette knows it is default, so saves stay small")
    void defaultIsRecognised() {
        assertTrue(TrackPalette.DEFAULT.isDefault());
        assertFalse(TrackPalette.DEFAULT.with(Part.TIE, Colour.PINK).isDefault());
    }

    @Test
    @DisplayName("a section keeps its palette through edits")
    void editsPreservePalette() {
        // Every editing method returns a NEW section, so each one is a chance to drop the palette
        // by forgetting to pass it along — and a coaster silently reverting to grey after an edit
        // is exactly the kind of bug nobody reports precisely.
        TrackPalette painted = TrackPalette.DEFAULT.with(Part.RAIL, Colour.PURPLE);
        TrackSection original = section(painted);

        assertEquals(painted, original.withNode(1, new TrackNode(new Vec3(60, 70, 0))).palette());
        assertEquals(painted, original.withNodeAppended(new TrackNode(new Vec3(150, 64, 0))).palette());
        assertEquals(painted, original.withStyle("wooden").palette());
        assertEquals(painted, original.reversed().palette());
        assertEquals(painted, original.withNodeRemoved(2).palette());
    }

    @Test
    @DisplayName("withPalette produces a differently painted copy without touching the original")
    void withPaletteIsACopy() {
        TrackSection original = section(TrackPalette.DEFAULT);
        TrackSection painted = original.withPalette(
            TrackPalette.DEFAULT.with(Part.SUPPORT, Colour.GREEN));

        assertEquals(Colour.GREEN, painted.palette().of(Part.SUPPORT));
        assertTrue(original.palette().isDefault(), "the original must not have been mutated");
        assertNotEquals(original.palette(), painted.palette());
        assertEquals(original.totalLength(), painted.totalLength(), 1e-9);
    }
}
