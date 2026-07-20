package com.micatechnologies.minecraft.rcmc.track.validation;

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
 * Tests for the vertical overshoot detector.
 *
 * <p>Written from a real screenshot: track running level, then a node raised half a block before a
 * steep climb, produced a pronounced sag that clipped into the terrain. The curve was correct —
 * Catmull-Rom derives each node's tangent from its neighbours, so a node between a level run and a
 * steep climb gets a sharply upward tangent, and the segment arriving at it must dip to finish with
 * that tangent while still passing through both endpoints.</p>
 */
class OvershootCheckTest {

    private static TrackNode at(double x, double y, double z) {
        return new TrackNode(new Vec3(x, y, z));
    }

    private static boolean reportsOvershoot(TrackSection section) {
        for (TrackIssue issue : new OvershootCheck().check(section)) {
            if (issue.code().contains("OVERSHOOT")) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("a level run followed by a sharp climb sags between its nodes")
    void levelThenClimbSags() {
        // The reported shape: flat, flat, flat, then up sharply.
        TrackSection section = new TrackSection(1, Arrays.asList(
            at(0, 64, 0), at(10, 64, 0), at(20, 64, 0), at(30, 90, 0)), false, null);
        assertTrue(reportsOvershoot(section),
            "expected the span before a sharp climb to be flagged as sagging");
    }

    @Test
    @DisplayName("a gently graded run is not flagged")
    void gentleGradeIsClean() {
        // A validator that fires on ordinary track is worse than none; this is the shape a builder
        // gets by raising the height a little at a time, which is the remedy being recommended.
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i <= 6; i++) {
            nodes.add(at(i * 10.0D, 64.0D + i * 3.0D, 0.0D));
        }
        assertTrue(!reportsOvershoot(new TrackSection(1, nodes, false, null)),
            "a steady climb should not be reported as overshooting");
    }

    @Test
    @DisplayName("dead level track is not flagged")
    void levelTrackIsClean() {
        List<TrackNode> nodes = new ArrayList<>();
        for (int i = 0; i <= 6; i++) {
            nodes.add(at(i * 10.0D, 64.0D, 0.0D));
        }
        assertTrue(!reportsOvershoot(new TrackSection(1, nodes, false, null)));
    }

    @Test
    @DisplayName("splitting the span with an extra node reduces the sag — the advertised remedy")
    void extraNodeReducesOvershoot() {
        // The message tells builders to place a node partway. That advice has to actually work.
        TrackSection harsh = new TrackSection(1, Arrays.asList(
            at(0, 64, 0), at(10, 64, 0), at(20, 64, 0), at(30, 90, 0)), false, null);
        TrackSection eased = new TrackSection(1, Arrays.asList(
            at(0, 64, 0), at(10, 64, 0), at(20, 64, 0),
            at(25, 72, 0), at(30, 90, 0)), false, null);

        double worstHarsh = worstOvershoot(harsh);
        double worstEased = worstOvershoot(eased);
        assertTrue(worstEased < worstHarsh,
            "adding an intermediate node should reduce the sag; " + worstHarsh + " -> " + worstEased);
    }

    private static double worstOvershoot(TrackSection section) {
        double worst = 0.0D;
        for (TrackIssue issue : new OvershootCheck(0.0D).check(section)) {
            worst = Math.max(worst, issue.value());
        }
        return worst;
    }
}
