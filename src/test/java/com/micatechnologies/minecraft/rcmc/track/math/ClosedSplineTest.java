package com.micatechnologies.minecraft.rcmc.track.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClosedSplineTest {

    private static List<Vec3> ring(int count, double radius) {
        List<Vec3> pts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double a = 2.0D * Math.PI * i / count;
            pts.add(new Vec3(Math.cos(a) * radius, 0.0D, Math.sin(a) * radius));
        }
        return pts;
    }

    @Test
    @DisplayName("a closed spline has one segment per node, including the closing span")
    void segmentCountMatchesNodeCount() {
        assertEquals(8, CatmullRomSpline.closed(ring(8, 30.0D)).segmentCount());
    }

    @Test
    @DisplayName("a closed spline passes through every node, with u=1 back at node 0")
    void interpolatesEveryNodeAndWraps() {
        List<Vec3> pts = ring(6, 30.0D);
        CatmullRomSpline s = CatmullRomSpline.closed(pts);

        for (int i = 0; i < pts.size(); i++) {
            Vec3 expected = pts.get(i);
            Vec3 actual = s.positionAt((double) i / pts.size());
            assertEquals(expected.x, actual.x, 1e-6, "node " + i + " x");
            assertEquals(expected.y, actual.y, 1e-6, "node " + i + " y");
            assertEquals(expected.z, actual.z, 1e-6, "node " + i + " z");
        }

        Vec3 start = s.positionAt(0.0D);
        Vec3 wrapped = s.positionAt(1.0D);
        assertEquals(start.x, wrapped.x, 1e-6);
        assertEquals(start.z, wrapped.z, 1e-6);
    }

    @Test
    @DisplayName("the closing join is tangent-continuous — no kink at the seam")
    void closingJoinHasNoKink() {
        // The reason closed() wraps control points instead of duplicating endpoints. A tangent
        // discontinuity here would hit the physics as an instantaneous direction change.
        CatmullRomSpline s = CatmullRomSpline.closed(ring(10, 40.0D));

        Vec3 justBefore = s.tangentAt(1.0D - 1e-6);
        Vec3 justAfter = s.tangentAt(1e-6);
        assertTrue(justBefore.dot(justAfter) > 0.9999D,
            "kink at the closing seam: " + justBefore + " vs " + justAfter);
    }

    @Test
    @DisplayName("a sampled circle is close to the true circle")
    void ringApproximatesACircle() {
        double radius = 40.0D;
        CatmullRomSpline s = CatmullRomSpline.closed(ring(16, radius));
        for (int i = 0; i <= 200; i++) {
            Vec3 p = s.positionAt(i / 200.0D);
            double r = Math.sqrt(p.x * p.x + p.z * p.z);
            assertEquals(radius, r, radius * 0.01D, "radius drifted at u=" + (i / 200.0D));
        }
    }

    @Test
    @DisplayName("rejects fewer than three loop points")
    void rejectsTooFewPoints() {
        assertThrows(IllegalArgumentException.class,
            () -> CatmullRomSpline.closed(Arrays.asList(new Vec3(0, 0, 0), new Vec3(10, 0, 0))));
    }

    @Test
    @DisplayName("distanceAtParam inverts paramAtDistance")
    void distanceAtParamRoundTrips() {
        ArcLengthTable table = new ArcLengthTable(CatmullRomSpline.withPhantomEndpoints(Arrays.asList(
            new Vec3(0, 0, 0), new Vec3(20, 10, 0), new Vec3(40, 0, 15), new Vec3(60, 12, 30))));

        for (int i = 0; i <= 50; i++) {
            double s = table.totalLength() * i / 50.0D;
            double u = table.paramAtDistance(s);
            assertEquals(s, table.distanceAtParam(u), table.totalLength() * 1e-4,
                "round trip failed at s=" + s);
        }

        assertEquals(0.0D, table.distanceAtParam(-1.0D), 0.0D);
        assertEquals(table.totalLength(), table.distanceAtParam(2.0D), 0.0D);
    }
}
