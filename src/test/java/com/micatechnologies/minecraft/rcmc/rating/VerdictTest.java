package com.micatechnologies.minecraft.rcmc.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VerdictTest {

    private static final double[] BANDS = {2.0D, 4.0D, 6.0D, 8.0D, 10.0D};

    @Test
    @DisplayName("values below the first threshold are LOW, at/above the last are ULTRA_EXTREME")
    void bandsCoverTheWholeRange() {
        assertEquals(Verdict.LOW, Verdict.bandFor(-5.0D, BANDS));
        assertEquals(Verdict.LOW, Verdict.bandFor(0.0D, BANDS));
        assertEquals(Verdict.MEDIUM, Verdict.bandFor(2.0D, BANDS));
        assertEquals(Verdict.MEDIUM, Verdict.bandFor(3.9D, BANDS));
        assertEquals(Verdict.HIGH, Verdict.bandFor(4.0D, BANDS));
        assertEquals(Verdict.VERY_HIGH, Verdict.bandFor(6.0D, BANDS));
        assertEquals(Verdict.EXTREME, Verdict.bandFor(8.0D, BANDS));
        assertEquals(Verdict.ULTRA_EXTREME, Verdict.bandFor(10.0D, BANDS));
        assertEquals(Verdict.ULTRA_EXTREME, Verdict.bandFor(1000.0D, BANDS));
    }

    @Test
    @DisplayName("a malformed band array is rejected rather than silently misbehaving")
    void wrongBandCountThrows() {
        assertThrows(IllegalArgumentException.class, () -> Verdict.bandFor(5.0D, new double[]{1.0D, 2.0D}));
        assertThrows(IllegalArgumentException.class, () -> Verdict.bandFor(5.0D, null));
    }
}
