package com.micatechnologies.minecraft.rcmc.client.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which faces of a metro car are its saloon, lit by the car's own lights, and which are its
 * outside, lit by the world. Get it wrong one way and the train glows in a dark tunnel; the other
 * way, and the saloon goes dark with its lights on.
 */
class MetroSaloonFacesTest {

    private static final float HALF = 10.0F;

    private static boolean inside(float x, float y, float z, int axis, float sign) {
        return MetroCarModel.interior(x, y, z, axis, sign, HALF);
    }

    @Test
    @DisplayName("the inner skin of a side wall is the saloon, the outer skin the outside")
    void sideWall() {
        assertTrue(inside(1.80F, 3.0F, 0.0F, 0, -1.0F), "inner face, facing in");
        assertFalse(inside(1.90F, 3.0F, 0.0F, 0, 1.0F), "outer face, facing out");
        assertTrue(inside(-1.80F, 3.0F, 0.0F, 0, 1.0F), "the other side's inner face");
        assertFalse(inside(-1.90F, 3.0F, 0.0F, 0, -1.0F), "the other side's outer face");
    }

    @Test
    @DisplayName("a door leaf proud of the wall is lit inside, world-lit outside")
    void doorLeaf() {
        assertTrue(inside(1.87F, 3.0F, 2.0F, 0, -1.0F));
        assertFalse(inside(1.93F, 3.0F, 2.0F, 0, 1.0F));
    }

    @Test
    @DisplayName("ceiling and floor are inside; roof, underframe and trucks outside")
    void floorAndRoof() {
        assertTrue(inside(0.0F, 5.75F, 0.0F, 1, -1.0F), "the ceiling: the roof's underside");
        assertFalse(inside(0.0F, 5.95F, 0.0F, 1, 1.0F), "the roof top");
        assertTrue(inside(0.0F, 2.0F, 0.0F, 1, 1.0F), "the floor");
        assertFalse(inside(0.0F, 1.65F, 0.0F, 1, -1.0F), "the floor slab's underside");
        assertFalse(inside(1.1F, 0.4F, 5.0F, 0, 1.0F), "a truck");
    }

    @Test
    @DisplayName("fittings standing in the saloon are inside whichever way they face")
    void fittings() {
        assertTrue(inside(1.0F, 3.5F, 1.0F, 0, 1.0F), "a stanchion's outward face");
        assertTrue(inside(1.0F, 3.5F, 1.0F, 2, 1.0F), "a stanchion's end face");
    }

    @Test
    @DisplayName("an end wall's inner face is inside, its outer face outside")
    void endWall() {
        assertTrue(inside(0.0F, 3.0F, HALF - 0.1F, 2, -1.0F));
        assertFalse(inside(0.0F, 3.0F, HALF, 2, 1.0F));
        assertFalse(inside(0.0F, 3.0F, -HALF, 2, -1.0F));
    }
}
