package com.micatechnologies.minecraft.rcmc.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.math.CatmullRomSpline;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GForcesTest {

    private static final double G = 9.81D;

    private static TrackFrame level() {
        return new TrackFrame(Vec3.ZERO, new Vec3(1, 0, 0), Vec3.UP);
    }

    @Test
    @DisplayName("sitting still on level track is exactly 1g down and nothing else")
    void restingIsOneG() {
        // The anchor for the whole sign convention. If this drifts, every rating built on top of
        // these numbers is offset.
        GForces g = GForces.at(level(), 0.0D, 0.0D, null, 0.0D, G);
        assertEquals(1.0D, g.vertical, 1e-9);
        assertEquals(0.0D, g.lateral, 1e-9);
        assertEquals(0.0D, g.longitudinal, 1e-9);
        assertFalse(g.isAirtime());
    }

    @Test
    @DisplayName("a valley presses riders into the seat above 1g")
    void valleyIncreasesVerticalLoad() {
        // Curving upward (centre of curvature above) at speed: v^2*kappa adds to gravity.
        double speed = 25.0D;
        double radius = 40.0D;
        GForces g = GForces.at(level(), speed, 1.0D / radius, Vec3.UP, 0.0D, G);

        double expected = 1.0D + (speed * speed / radius) / G;
        assertEquals(expected, g.vertical, 1e-6);
        assertTrue(g.vertical > 1.0D);
    }

    @Test
    @DisplayName("a crest taken fast enough produces airtime")
    void crestProducesAirtime() {
        // The sensation the whole hobby is built around: over a hill the required centripetal
        // acceleration points DOWN, and once it exceeds gravity the seat stops pushing back.
        double radius = 30.0D;
        double justEnough = Math.sqrt(G * radius);

        GForces gentle = GForces.at(level(), justEnough * 0.5D, 1.0D / radius,
            new Vec3(0, -1, 0), 0.0D, G);
        assertFalse(gentle.isAirtime(), "should still be seated at half the critical speed");

        GForces brisk = GForces.at(level(), justEnough * 1.4D, 1.0D / radius,
            new Vec3(0, -1, 0), 0.0D, G);
        assertTrue(brisk.isAirtime(), "expected airtime, got " + brisk);

        // At exactly the critical speed the rider is weightless.
        GForces critical = GForces.at(level(), justEnough, 1.0D / radius,
            new Vec3(0, -1, 0), 0.0D, G);
        assertEquals(0.0D, critical.vertical, 1e-6);
    }

    @Test
    @DisplayName("an unbanked turn throws riders sideways")
    void unbankedTurnHasLateralLoad() {
        double speed = 20.0D;
        double radius = 30.0D;
        // Turning left: centre of curvature is to the rider's left.
        Vec3 toCentre = new Vec3(0, 0, -1);
        TrackFrame frame = new TrackFrame(Vec3.ZERO, new Vec3(1, 0, 0), Vec3.UP);

        GForces g = GForces.at(frame, speed, 1.0D / radius, toCentre, 0.0D, G);
        assertEquals(1.0D, g.vertical, 1e-6, "an unbanked turn should not change vertical load");
        assertTrue(Math.abs(g.lateral) > 1.0D, "expected significant lateral load, got " + g);
    }

    @Test
    @DisplayName("banking a turn to the ideal angle cancels lateral load entirely")
    void perfectBankRemovesLateralLoad() {
        // The entire point of banking, and the property the nausea rating keys off. Note the bank
        // angle appears nowhere in the calculation — it is already carried in the frame's axes.
        double speed = 20.0D;
        double radius = 30.0D;
        double ideal = Math.atan((speed * speed) / (radius * G));

        Vec3 toCentre = new Vec3(0, 0, -1);
        TrackFrame banked = new TrackFrame(Vec3.ZERO, new Vec3(1, 0, 0), Vec3.UP)
            .withBank(-ideal);

        GForces g = GForces.at(banked, speed, 1.0D / radius, toCentre, 0.0D, G);
        assertEquals(0.0D, g.lateral, 1e-6,
            "ideal bank should cancel lateral load; got " + g);
        // The load has not vanished — it moved into the vertical axis, which is why a banked turn
        // presses you into the seat rather than throwing you sideways.
        assertTrue(g.vertical > 1.0D, "load should have moved to vertical, got " + g);
    }

    @Test
    @DisplayName("acceleration and braking show up as longitudinal load")
    void longitudinalTracksAcceleration() {
        assertEquals(1.0D, GForces.at(level(), 10.0D, 0.0D, null, G, G).longitudinal, 1e-9);
        assertEquals(-0.5D, GForces.at(level(), 10.0D, 0.0D, null, -G / 2.0D, G).longitudinal, 1e-9);
    }

    @Test
    @DisplayName("spline curvature matches a circle's known 1/R")
    void splineCurvatureMatchesCircle() {
        // Validates the analytic-first-derivative + one-central-difference approach against the
        // one shape whose curvature is exactly known. A parameterisation mistake shows up here as
        // a constant factor — this test caught exactly that: derivativeAt() originally returned
        // the per-segment derivative while curvature differenced it against the global parameter,
        // inflating every reading by segmentCount.
        //
        // The residual few percent is real and not a bug: a Catmull-Rom through points sampled ON
        // a circle is not itself a circle, it bulges slightly between control points. See the
        // convergence test below, which is what distinguishes discretisation from a formula error.
        double radius = 40.0D;
        CatmullRomSpline spline = ringSpline(radius, 48);

        for (double u = 0.1D; u <= 0.9D; u += 0.1D) {
            assertEquals(1.0D / radius, spline.curvatureAt(u), (1.0D / radius) * 0.02D,
                "curvature wrong at u=" + u);
        }
    }

    @Test
    @DisplayName("curvature error shrinks as the circle is sampled more finely")
    void curvatureConvergesWithSampleDensity() {
        // The check that makes the tolerance above honest: discretisation error shrinks with
        // density, a formula mistake would not.
        //
        // Averaged over the whole lap rather than read at one parameter, because the error is
        // OSCILLATORY, not monotonic in position — a Catmull-Rom through points sampled on a
        // circle bulges between control points, so curvature ripples above and below 1/R within
        // every span. Reading a single fixed u samples a different phase of that ripple at each
        // density and can easily show the error "growing". That is a property of where you looked,
        // not of the approximation, and an earlier version of this test was fooled by it.
        double radius = 40.0D;
        double exact = 1.0D / radius;

        double coarse = meanCurvatureError(ringSpline(radius, 12), exact);
        double medium = meanCurvatureError(ringSpline(radius, 24), exact);
        double fine = meanCurvatureError(ringSpline(radius, 48), exact);

        assertTrue(medium < coarse,
            "mean error grew from 12 to 24 points: " + coarse + " -> " + medium);
        assertTrue(fine < medium,
            "mean error grew from 24 to 48 points: " + medium + " -> " + fine);
        assertTrue(fine < exact * 0.01D,
            "48 samples should average within 1%; was " + (fine / exact * 100.0D) + "%");
    }

    /** Mean absolute curvature error over a full lap, sampled densely enough to cover the ripple. */
    private static double meanCurvatureError(CatmullRomSpline spline, double exact) {
        int samples = 400;
        double total = 0.0D;
        for (int i = 0; i < samples; i++) {
            total += Math.abs(spline.curvatureAt((double) i / samples) - exact);
        }
        return total / samples;
    }

    private static CatmullRomSpline ringSpline(double radius, int nodeCount) {
        List<Vec3> ring = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            double a = 2.0D * Math.PI * i / nodeCount;
            ring.add(new Vec3(Math.cos(a) * radius, 0.0D, Math.sin(a) * radius));
        }
        return CatmullRomSpline.closed(ring);
    }

    @Test
    @DisplayName("a straight spline has zero curvature")
    void straightSplineHasNoCurvature() {
        List<Vec3> line = Arrays.asList(
            new Vec3(0, 0, 0), new Vec3(20, 0, 0), new Vec3(40, 0, 0),
            new Vec3(60, 0, 0), new Vec3(80, 0, 0));
        CatmullRomSpline spline = CatmullRomSpline.withPhantomEndpoints(line);

        for (double u = 0.1D; u <= 0.9D; u += 0.1D) {
            assertEquals(0.0D, spline.curvatureAt(u), 1e-6, "phantom curvature at u=" + u);
        }
    }

    @Test
    @DisplayName("tighter curves report proportionally higher curvature")
    void curvatureScalesInverselyWithRadius() {
        assertEquals(2.0D, curvatureOfRing(20.0D) / curvatureOfRing(40.0D), 0.05D);
    }

    private static double curvatureOfRing(double radius) {
        List<Vec3> ring = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            double a = 2.0D * Math.PI * i / 24;
            ring.add(new Vec3(Math.cos(a) * radius, 0.0D, Math.sin(a) * radius));
        }
        return CatmullRomSpline.closed(ring).curvatureAt(0.35D);
    }
}
