package com.micatechnologies.minecraft.rcmc.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.micatechnologies.minecraft.rcmc.physics.TrainSpec.Part;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for train paint.
 *
 * <p>Colours are stored as ordinals rather than an enum so {@code physics} need not depend on
 * {@code track} — the renderer resolves them. That indirection is the price of the layering, and it
 * is exactly the sort of thing that silently loses a value when a spec is copied.</p>
 */
class TrainSpecColourTest {

    @Test
    @DisplayName("repainting one part leaves the others and the geometry alone")
    void repaintIsolated() {
        TrainSpec original = new TrainSpec(5, 3.0D, 0.5D, 4);
        TrainSpec painted = original.withColour(Part.BODY, 9);

        assertEquals(9, painted.bodyColour());
        assertEquals(original.trimColour(), painted.trimColour());
        assertEquals(original.seatColour(), painted.seatColour());

        // The physical spec must survive a paint job untouched — a repaint that quietly resized
        // the cars would be a bizarre bug to track down.
        assertEquals(original.carCount(), painted.carCount());
        assertEquals(original.carLength(), painted.carLength(), 0.0D);
        assertEquals(original.couplingGap(), painted.couplingGap(), 0.0D);
        assertEquals(original.seatsPerCar(), painted.seatsPerCar());
        assertEquals(original.carPitch(), painted.carPitch(), 0.0D);
    }

    @Test
    @DisplayName("the original spec is not mutated by repainting")
    void repaintIsACopy() {
        TrainSpec original = new TrainSpec(3, 3.0D, 0.5D, 4);
        int before = original.bodyColour();
        original.withColour(Part.BODY, before + 1);
        assertEquals(before, original.bodyColour(), "specs are immutable");
    }

    @Test
    @DisplayName("colourOf agrees with the individual accessors")
    void colourOfMatchesAccessors() {
        TrainSpec spec = new TrainSpec(2, 3.0D, 0.5D, 4, 1, 2, 3);
        assertEquals(spec.bodyColour(), spec.colourOf(Part.BODY));
        assertEquals(spec.trimColour(), spec.colourOf(Part.TRIM));
        assertEquals(spec.seatColour(), spec.colourOf(Part.SEATS));
        assertNotEquals(spec.colourOf(Part.BODY), spec.colourOf(Part.TRIM));
    }

    @Test
    @DisplayName("the convenience constructor gives a sensible default paint")
    void defaultPaint() {
        // Not asserting particular colours — those will be retuned — only that the three parts are
        // not all identical, which would make the model read as one flat block.
        TrainSpec spec = new TrainSpec(4, 3.0D, 0.5D, 4);
        assertNotEquals(spec.bodyColour(), spec.trimColour());
        assertNotEquals(spec.bodyColour(), spec.seatColour());
    }
}
