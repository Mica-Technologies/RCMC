package com.micatechnologies.minecraft.rcmc.track.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArcLengthTableTest {

    @Test
    @DisplayName("total length of a straight run equals its geometric length")
    void straightLineLength() {
        List<Vec3> pts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            pts.add(new Vec3(i * 10.0D, 0, 0));
        }
        ArcLengthTable table = new ArcLengthTable(CatmullRomSpline.withPhantomEndpoints(pts));
        assertEquals(40.0D, table.totalLength(), 1.0e-6D);
    }

    @Test
    @DisplayName("equal distance steps produce equal spatial steps — the whole point of the table")
    void constantDistanceMeansConstantSpeed() {
        // A deliberately lumpy curve: tight corner next to a long straight is exactly the
        // case where the raw spline parameter and arc length diverge most.
        List<Vec3> pts = Arrays.asList(
            new Vec3(0, 0, 0),
            new Vec3(40, 0, 0),
            new Vec3(44, 0, 4),
            new Vec3(44, 0, 40));
        ArcLengthTable table = new ArcLengthTable(CatmullRomSpline.withPhantomEndpoints(pts));

        double total = table.totalLength();
        int steps = 200;
        double stride = total / steps;

        Vec3 previous = table.positionAtDistance(0.0D);
        for (int i = 1; i <= steps; i++) {
            Vec3 current = table.positionAtDistance(i * stride);
            double actual = current.distanceTo(previous);
            // Chord vs arc over a stride this small differs by a fraction of a percent; a
            // 5% band catches a broken lookup while tolerating legitimate curvature error.
            assertEquals(stride, actual, stride * 0.05D,
                "uneven spatial step at s=" + (i * stride));
            previous = current;
        }
    }

    @Test
    @DisplayName("distance lookup is monotonic and clamps outside the section")
    void monotonicAndClamped() {
        List<Vec3> pts = Arrays.asList(
            new Vec3(0, 0, 0), new Vec3(10, 4, 0), new Vec3(20, 0, 8), new Vec3(30, 6, 12));
        ArcLengthTable table = new ArcLengthTable(CatmullRomSpline.withPhantomEndpoints(pts));

        double previous = -1.0D;
        for (int i = 0; i <= 100; i++) {
            double u = table.paramAtDistance(table.totalLength() * i / 100.0D);
            assertTrue(u >= previous, "parameter went backwards at step " + i);
            previous = u;
        }

        assertEquals(0.0D, table.paramAtDistance(-500.0D), 0.0D);
        assertEquals(1.0D, table.paramAtDistance(table.totalLength() + 500.0D), 0.0D);
    }
}
