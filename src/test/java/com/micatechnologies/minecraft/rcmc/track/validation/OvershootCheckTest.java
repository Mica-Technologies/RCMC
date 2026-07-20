package com.micatechnologies.minecraft.rcmc.track.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for vertical overshoot — track sagging below the nodes it runs between.
 *
 * <p>Written from a real screenshot: level track, a node raised half a block, then a steep climb,
 * and the curve sagged hard enough to clip into terrain before swinging up. The cause was that
 * Catmull-Rom derives each node's tangent from its neighbours, so a node between a level run and a
 * climb gets a sharply upward tangent — and the segment arriving at it had to dip to finish with
 * that tangent while still passing through both endpoints.</p>
 *
 * <p>{@code CatmullRomSpline} now clamps tangents for vertical monotonicity, so these shapes no
 * longer overshoot at all. These tests assert that <em>absence</em>. They were originally written
 * to prove the sag existed; they now exist to catch it coming back.</p>
 *
 * <p>{@link OvershootCheck} is kept as a safety net rather than deleted. It should never fire on a
 * curve this spline produces, which is exactly why it is worth leaving in — the same rationale as
 * the validator's cusp check.</p>
 */
class OvershootCheckTest {

    private static TrackNode at(double x, double y, double z) {
        return new TrackNode(new Vec3(x, y, z));
    }

    /** Worst vertical excursion past a span's own endpoints, measured with no tolerance. */
    private static double worstOvershoot(TrackSection section) {
        double worst = 0.0D;
        for (TrackIssue issue : new OvershootCheck(0.0D).check(section)) {
            worst = Math.max(worst, issue.value());
        }
        return worst;
    }

    @Test
    @DisplayName("the reported shape — level, then a sharp climb — no longer sags")
    void levelThenClimbDoesNotSag() {
        // The geometry from the screenshot that prompted the fix.
        TrackSection section = new TrackSection(1, Arrays.asList(
            at(0, 64, 0), at(10, 64, 0), at(20, 64, 0), at(30, 90, 0)), false, null);
        assertEquals(0.0D, worstOvershoot(section), 0.01D,
            "the span before a sharp climb should no longer dip below its endpoints");
    }

    @Test
    @DisplayName("a half-block step before a climb does not dip into the ground")
    void smallStepDoesNotDip() {
        // Closer still to what was reported: a +0.5 height offset, which is where it was noticed.
        TrackSection section = new TrackSection(1, Arrays.asList(
            at(0, 64, 0), at(10, 64, 0), at(20, 64.5D, 0), at(30, 80, 0)), false, null);
        assertEquals(0.0D, worstOvershoot(section), 0.01D);
    }

    @Test
    @DisplayName("a crest does not bulge above the node that defines it")
    void peakDoesNotBulge() {
        // The mirror case. An airtime hill floating higher than the node a builder placed means the
        // hill is not the height they drew, and the G-forces will not be the ones they designed.
        TrackSection section = new TrackSection(1, Arrays.asList(
            at(0, 64, 0), at(20, 80, 0), at(40, 64, 0), at(60, 64, 0)), false, null);
        assertEquals(0.0D, worstOvershoot(section), 0.01D);
    }

    @Test
    @DisplayName("a steady climb is unaffected")
    void gentleGradeIsClean() {
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i <= 6; i++) {
            nodes.add(at(i * 10.0D, 64.0D + i * 3.0D, 0.0D));
        }
        assertEquals(0.0D, worstOvershoot(new TrackSection(1, nodes, false, null)), 0.01D);
    }

    @Test
    @DisplayName("dead level track is unaffected")
    void levelTrackIsClean() {
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i <= 6; i++) {
            nodes.add(at(i * 10.0D, 64.0D, 0.0D));
        }
        assertEquals(0.0D, worstOvershoot(new TrackSection(1, nodes, false, null)), 0.01D);
    }

    @Test
    @DisplayName("a closed circuit with varied elevation does not overshoot anywhere")
    void circuitDoesNotOvershoot() {
        // Clamping has to hold across the wrap too, where a node's neighbours come from both ends
        // of the control list.
        List<TrackNode> ring = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double a = 2.0D * Math.PI * i / 12;
            ring.add(at(Math.cos(a) * 50.0D, 64.0D + Math.sin(a * 2.0D) * 14.0D, Math.sin(a) * 50.0D));
        }
        assertTrue(worstOvershoot(new TrackSection(1, ring, true, null)) < 0.5D,
            "a closed circuit should not sag or bulge past its nodes");
    }
}
