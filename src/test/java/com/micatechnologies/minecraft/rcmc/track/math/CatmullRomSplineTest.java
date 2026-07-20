package com.micatechnologies.minecraft.rcmc.track.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatmullRomSplineTest {

    private static final double EPS = 1.0e-6D;

    private static void assertVecEquals(Vec3 expected, Vec3 actual, double eps) {
        assertEquals(expected.x, actual.x, eps, "x of " + actual);
        assertEquals(expected.y, actual.y, eps, "y of " + actual);
        assertEquals(expected.z, actual.z, eps, "z of " + actual);
    }

    @Test
    @DisplayName("passes exactly through every point when built with phantom endpoints")
    void interpolatesControlPoints() {
        List<Vec3> pts = Arrays.asList(
            new Vec3(0, 0, 0),
            new Vec3(10, 0, 0),
            new Vec3(20, 5, 5),
            new Vec3(30, 5, 15));
        CatmullRomSpline spline = CatmullRomSpline.withPhantomEndpoints(pts);

        // Global parameter is uniform in segment index, and there is one segment between
        // each adjacent pair, so point i sits at u = i / (n - 1).
        for (int i = 0; i < pts.size(); i++) {
            double u = (double) i / (pts.size() - 1);
            assertVecEquals(pts.get(i), spline.positionAt(u), EPS);
        }
    }

    @Test
    @DisplayName("a straight line of control points yields a straight line")
    void straightLineStaysStraight() {
        List<Vec3> pts = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            pts.add(new Vec3(i * 4.0D, 0, 0));
        }
        CatmullRomSpline spline = CatmullRomSpline.withPhantomEndpoints(pts);

        for (int i = 0; i <= 100; i++) {
            Vec3 p = spline.positionAt(i / 100.0D);
            assertEquals(0.0D, p.y, EPS, "y drifted off the line at u=" + (i / 100.0D));
            assertEquals(0.0D, p.z, EPS, "z drifted off the line at u=" + (i / 100.0D));
        }
    }

    @Test
    @DisplayName("tangent is unit length and points forward everywhere")
    void tangentIsUnitAndForward() {
        List<Vec3> pts = Arrays.asList(
            new Vec3(0, 0, 0),
            new Vec3(10, 8, 0),
            new Vec3(20, 2, 6),
            new Vec3(28, 0, 14),
            new Vec3(30, 6, 24));
        CatmullRomSpline spline = CatmullRomSpline.withPhantomEndpoints(pts);

        for (int i = 0; i <= 200; i++) {
            double u = i / 200.0D;
            Vec3 tangent = spline.tangentAt(u);
            assertEquals(1.0D, tangent.length(), 1.0e-9D, "tangent not unit at u=" + u);
        }

        // The analytic tangent must agree with a central finite difference of the position.
        // This is the test that actually catches an algebra error in the derivative
        // recurrence — unit length alone would still pass with a wrong direction.
        double h = 1.0e-5D;
        for (double u = 0.1D; u <= 0.9D; u += 0.1D) {
            Vec3 numeric = spline.positionAt(u + h).subtract(spline.positionAt(u - h)).normalize();
            Vec3 analytic = spline.tangentAt(u);
            assertTrue(numeric.dot(analytic) > 0.9999D,
                "analytic tangent " + analytic + " disagrees with numeric " + numeric + " at u=" + u);
        }
    }

    @Test
    @DisplayName("coincident control points degrade gracefully instead of producing NaN")
    void coincidentPointsDoNotProduceNaN() {
        // Players double-place nodes; a NaN here would silently teleport a train into oblivion.
        List<Vec3> pts = Arrays.asList(
            new Vec3(0, 0, 0),
            new Vec3(5, 0, 0),
            new Vec3(5, 0, 0),
            new Vec3(12, 0, 0));
        CatmullRomSpline spline = CatmullRomSpline.withPhantomEndpoints(pts);

        for (int i = 0; i <= 50; i++) {
            Vec3 p = spline.positionAt(i / 50.0D);
            assertTrue(Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z),
                "non-finite position " + p);
        }
    }

    @Test
    @DisplayName("rejects fewer than four control points")
    void rejectsTooFewPoints() {
        assertThrows(IllegalArgumentException.class,
            () -> new CatmullRomSpline(Arrays.asList(new Vec3(0, 0, 0), new Vec3(1, 0, 0))));
        assertThrows(IllegalArgumentException.class,
            () -> CatmullRomSpline.withPhantomEndpoints(Arrays.asList(new Vec3(0, 0, 0))));
    }
}
