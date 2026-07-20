package com.micatechnologies.minecraft.rcmc.track.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ties the validator's numerically-estimated curvature to a closed-form answer.
 *
 * <p>{@link TrackValidator} estimates curvature by finite-differencing sampled tangents, because
 * no shared class exposes an analytic second derivative yet. Finite differences are easy to get
 * subtly wrong — an off-by-one in the stencil, or dividing by the wrong arc-length span, produces
 * values that are plausible, self-consistent, and wrong by a constant factor. The other tests in
 * this package assert only that a tight curve trips a threshold, which such an error would still
 * satisfy.</p>
 *
 * <p>A circle is the one shape whose curvature is exactly known: {@code kappa = 1/R} everywhere.
 * Reported lateral load at design speed {@code v} is therefore {@code v² / (R·g)}, and comparing
 * against that pins the estimator to physics rather than to its own previous output.</p>
 */
class CurvatureAccuracyTest {

    private static final double GRAVITY = 9.81D;

    private static TrackSection circle(double radius, int nodeCount) {
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            double a = 2.0D * Math.PI * i / nodeCount;
            nodes.add(new TrackNode(new Vec3(Math.cos(a) * radius, 64.0D, Math.sin(a) * radius)));
        }
        return new TrackSection(1, nodes, true, null);
    }

    /**
     * Highest lateral-G the validator reports for {@code section}, or 0 if it reports none.
     * Keys on the issue code rather than the message, which is what the code exists for.
     */
    private static double peakLateralG(TrackSection section, double designSpeed) {
        ValidationLimits limits = ValidationLimits.DEFAULT
            .withDesignSpeedBlocksPerSecond(designSpeed)
            // Floor the threshold so every sample is reported, not just the ones over a comfort
            // limit — this test is measuring the estimator, not the policy.
            .withMaxLateralGees(0.0D);
        double peak = 0.0D;
        for (TrackIssue issue : new TrackValidator(limits).validate(section)) {
            if (issue.code().contains("LATERAL")) {
                peak = Math.max(peak, issue.value());
            }
        }
        return peak;
    }

    @Test
    @DisplayName("a circle's reported lateral load matches v^2/(R*g)")
    void circleMatchesClosedForm() {
        double radius = 40.0D;
        double speed = 20.0D;
        double expected = (speed * speed) / (radius * GRAVITY);

        double reported = peakLateralG(circle(radius, 24), speed);
        assertEquals(expected, reported, expected * 0.10D,
            "estimated curvature disagrees with 1/R by more than 10%");
    }

    @Test
    @DisplayName("halving the radius doubles the lateral load")
    void lateralLoadScalesInverselyWithRadius() {
        // Catches a constant-factor error that the absolute check above could conceivably absorb:
        // a scale bug preserves ratios only if it is genuinely constant, and this pins the shape
        // of the relationship independently of its magnitude.
        double speed = 20.0D;
        double wide = peakLateralG(circle(60.0D, 32), speed);
        double tight = peakLateralG(circle(30.0D, 32), speed);

        assertTrue(wide > 0.0D && tight > 0.0D, "expected a reading for both circles");
        assertEquals(2.0D, tight / wide, 0.2D, "lateral load should scale as 1/R");
    }

    @Test
    @DisplayName("doubling speed quadruples the lateral load")
    void lateralLoadScalesWithSpeedSquared() {
        TrackSection section = circle(40.0D, 32);
        double slow = peakLateralG(section, 10.0D);
        double fast = peakLateralG(section, 20.0D);

        assertTrue(slow > 0.0D, "expected a reading at the lower speed");
        assertEquals(4.0D, fast / slow, 0.05D, "lateral load should scale as v^2");
    }

    @Test
    @DisplayName("straight track reports essentially zero curvature")
    void straightTrackIsFlat() {
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            nodes.add(new TrackNode(new Vec3(i * 20.0D, 64.0D, 0.0D)));
        }
        double reported = peakLateralG(new TrackSection(1, nodes, false, null), 30.0D);
        assertEquals(0.0D, reported, 0.01D, "a straight line should have no lateral load");
    }
}
