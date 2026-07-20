package com.micatechnologies.minecraft.rcmc.track.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrackValidatorTest {

    private static TrackNode node(double x, double y, double z) {
        return new TrackNode(new Vec3(x, y, z));
    }

    private static TrackNode node(double x, double y, double z, double bankDegrees) {
        return new TrackNode(new Vec3(x, y, z), bankDegrees, null);
    }

    private static List<TrackIssue> issuesOf(TrackSection section) {
        return new TrackValidator().validate(section);
    }

    private static List<TrackIssue> withCode(List<TrackIssue> issues, String code) {
        List<TrackIssue> matches = new ArrayList<>();
        for (TrackIssue issue : issues) {
            if (issue.code().equals(code)) {
                matches.add(issue);
            }
        }
        return matches;
    }

    // ---- clean geometry: the validator must not cry wolf --------------------------------

    @Test
    @DisplayName("a wide, flat, gently-spaced ring produces no issues at all")
    void cleanFlatRingProducesNoIssues() {
        List<TrackNode> nodes = new ArrayList<>();
        int count = 24;
        double radius = 80.0D;
        for (int i = 0; i < count; i++) {
            double a = 2.0D * Math.PI * i / count;
            nodes.add(node(Math.cos(a) * radius, 64.0D, Math.sin(a) * radius));
        }
        TrackSection section = new TrackSection(1, nodes, true, null);

        List<TrackIssue> issues = issuesOf(section);
        assertTrue(issues.isEmpty(), "expected no issues, got " + issues);
    }

    @Test
    @DisplayName("a gentle climbing curve well inside every default limit produces no issues")
    void gentleHillProducesNoIssues() {
        List<TrackNode> nodes = new ArrayList<>();
        double radius = 100.0D;
        for (int i = 0; i <= 8; i++) {
            double a = (Math.PI / 2.0D) * i / 8.0D;
            double height = 64.0D + 20.0D * i / 8.0D;
            nodes.add(node(Math.sin(a) * radius, height, radius - Math.cos(a) * radius));
        }
        TrackSection section = new TrackSection(1, nodes, false, null);

        List<TrackIssue> issues = issuesOf(section);
        assertTrue(issues.isEmpty(), "expected no issues, got " + issues);
    }

    @Test
    @DisplayName("null section is rejected rather than silently producing no issues")
    void validateRejectsNullSection() {
        assertThrows(IllegalArgumentException.class, () -> new TrackValidator().validate(null));
    }

    // ---- excessive curvature / minimum radius --------------------------------------------

    @Test
    @DisplayName("a very tight turn implies lateral G above the default cap")
    void tightTurnFlagsExcessiveLateralG() {
        List<TrackNode> nodes = new ArrayList<>();
        double radius = 2.0D;
        for (int i = 0; i <= 8; i++) {
            double a = Math.PI * i / 8.0D;
            nodes.add(node(Math.cos(a) * radius, 64.0D, Math.sin(a) * radius));
        }
        TrackSection section = new TrackSection(1, nodes, false, null);

        List<TrackIssue> issues = issuesOf(section);
        List<TrackIssue> lateralG = withCode(issues, "TRACK_EXCESSIVE_LATERAL_G");
        assertFalse(lateralG.isEmpty(), "expected an excessive-lateral-G issue, got " + issues);

        TrackIssue issue = lateralG.get(0);
        assertTrue(issue.value() > ValidationLimits.DEFAULT.maxLateralGees(),
            "reported g-force should exceed the configured cap: " + issue);
        assertTrue(issue.distanceBlocks() >= 0.0D && issue.distanceBlocks() <= section.totalLength());
        assertEquals(TrackIssue.Severity.WARNING, issue.severity());
    }

    // ---- excessive grade --------------------------------------------------------------------

    @Test
    @DisplayName("a near-vertical drop is flagged as excessive grade, but only as a warning")
    void nearVerticalDropFlagsExcessiveGrade() {
        List<TrackNode> nodes = Arrays.asList(
            node(0, 100, 0), node(0, 80, 0), node(0, 60, 0), node(0, 40, 0));
        TrackSection section = new TrackSection(1, nodes, false, null);

        List<TrackIssue> issues = issuesOf(section);
        List<TrackIssue> grade = withCode(issues, "TRACK_EXCESSIVE_GRADE");
        assertFalse(grade.isEmpty(), "expected an excessive-grade issue, got " + issues);

        TrackIssue issue = grade.get(0);
        assertTrue(Math.abs(issue.value()) >= 85.0D, "expected a near-vertical grade reading: " + issue);
        assertEquals(TrackIssue.Severity.WARNING, issue.severity(),
            "steep track is legal on a coaster, so this must never be an ERROR");
    }

    @Test
    @DisplayName("a 45-degree ramp is not flagged at default limits, but is once the limit is tightened")
    void gradeLimitIsConfigurable() {
        List<TrackNode> nodes = Arrays.asList(
            node(0, 60, 0), node(10, 70, 0), node(20, 80, 0), node(30, 90, 0));
        TrackSection section = new TrackSection(1, nodes, false, null);

        List<TrackIssue> withDefaults = new TrackValidator(ValidationLimits.DEFAULT).validate(section);
        assertTrue(withCode(withDefaults, "TRACK_EXCESSIVE_GRADE").isEmpty(),
            "45 degrees should be well inside the default 80-degree limit: " + withDefaults);

        ValidationLimits tightened = ValidationLimits.DEFAULT.withMaxGradeDegrees(30.0D);
        List<TrackIssue> withTightLimit = new TrackValidator(tightened).validate(section);
        assertFalse(withCode(withTightLimit, "TRACK_EXCESSIVE_GRADE").isEmpty(),
            "45 degrees should trip a 30-degree limit: " + withTightLimit);
    }

    // ---- excessive bank rate ----------------------------------------------------------------

    @Test
    @DisplayName("banking 90 degrees over 5 blocks rolls far faster than the default comfort limit")
    void rapidBankChangeFlagsExcessiveBankRate() {
        List<TrackNode> nodes = Arrays.asList(
            node(0, 64, 0, 0.0D), node(5, 64, 0, 90.0D), node(40, 64, 0, 0.0D));
        TrackSection section = new TrackSection(1, nodes, false, null);

        List<TrackIssue> issues = issuesOf(section);
        List<TrackIssue> bankRate = withCode(issues, "TRACK_EXCESSIVE_BANK_RATE");
        assertFalse(bankRate.isEmpty(), "expected an excessive-bank-rate issue, got " + issues);

        TrackIssue issue = bankRate.get(0);
        assertTrue(Math.abs(issue.value()) > ValidationLimits.DEFAULT.maxBankRateDegreesPerBlock(),
            "reported rate should exceed the configured cap: " + issue);
        // The spike is in the first (short) span, node 0 -> node 1 at s=5.
        assertTrue(issue.distanceBlocks() >= 0.0D && issue.distanceBlocks() <= 5.5D,
            "expected the spike near the short span between s=0 and s=5: " + issue);
    }

    // ---- cusp / tangent reversal: the backstop that should never fire ----------------------

    @Test
    @DisplayName("even an aggressive hairpin turn does not trigger the cusp backstop")
    void hairpinTurnDoesNotFlagCusp() {
        // Deliberately about as tight as track geometry gets: an out-and-back leg only 1 block
        // apart. This is exactly the shape uniform Catmull-Rom would cusp on; centripetal is
        // proven not to (see TrackValidator#checkCusps), so this must stay silent on TRACK_CUSP
        // even though it is expected to (and does, separately) trip the lateral-G check hard.
        List<TrackNode> nodes = Arrays.asList(
            node(0, 64, 0), node(20, 64, 0), node(20, 64, 1), node(0, 64, 1));
        TrackSection section = new TrackSection(1, nodes, false, null);

        List<TrackIssue> issues = issuesOf(section);
        assertTrue(withCode(issues, "TRACK_CUSP").isEmpty(),
            "cusp backstop fired on geometry centripetal Catmull-Rom guarantees not to cusp: " + issues);
    }

    // ---- node spacing -----------------------------------------------------------------------

    @Test
    @DisplayName("coincident consecutive nodes are an ERROR, not a warning")
    void coincidentNodesFlaggedAsError() {
        List<TrackNode> nodes = Arrays.asList(
            node(0, 64, 0), node(0, 64, 0), node(40, 64, 0), node(80, 64, 0));
        TrackSection section = new TrackSection(1, nodes, false, null);

        List<TrackIssue> issues = issuesOf(section);
        List<TrackIssue> coincident = withCode(issues, "TRACK_NODE_COINCIDENT");
        assertFalse(coincident.isEmpty(), "expected a coincident-node issue, got " + issues);
        assertEquals(TrackIssue.Severity.ERROR, coincident.get(0).severity(),
            "coincident nodes are degenerate geometry, not merely uncomfortable");
        assertEquals(0.0D, coincident.get(0).distanceBlocks(), 1.0e-6D);
    }

    @Test
    @DisplayName("nodes placed suspiciously close together are a warning")
    void closeNodesFlaggedAsWarning() {
        List<TrackNode> nodes = Arrays.asList(
            node(0, 64, 0), node(0.3D, 64, 0), node(40, 64, 0), node(80, 64, 0));
        TrackSection section = new TrackSection(1, nodes, false, null);

        List<TrackIssue> issues = issuesOf(section);
        List<TrackIssue> tooClose = withCode(issues, "TRACK_NODE_TOO_CLOSE");
        assertFalse(tooClose.isEmpty(), "expected a nodes-too-close issue, got " + issues);
        assertEquals(TrackIssue.Severity.WARNING, tooClose.get(0).severity());
    }

    @Test
    @DisplayName("nodes placed implausibly far apart are a warning")
    void farNodesFlaggedAsWarning() {
        List<TrackNode> nodes = Arrays.asList(
            node(0, 64, 0), node(100, 64, 0), node(140, 64, 0), node(180, 64, 0));
        TrackSection section = new TrackSection(1, nodes, false, null);

        List<TrackIssue> issues = issuesOf(section);
        List<TrackIssue> tooFar = withCode(issues, "TRACK_NODE_TOO_FAR");
        assertFalse(tooFar.isEmpty(), "expected a nodes-too-far issue, got " + issues);
        assertEquals(TrackIssue.Severity.WARNING, tooFar.get(0).severity());
        assertEquals(0.0D, tooFar.get(0).distanceBlocks(), 1.0e-6D);
    }

    // ---- self-intersection / clearance -------------------------------------------------------

    @Test
    @DisplayName("track that loops back and passes close to an earlier, distant point is flagged")
    void loopingTrackFlagsSelfIntersection() {
        // Goes out in a big rectangle and comes back within 3 blocks of the start, well past
        // 100 blocks of arc length later — far apart along the track, close in space.
        List<TrackNode> nodes = Arrays.asList(
            node(0, 64, 0),
            node(40, 64, 0),
            node(40, 64, 40),
            node(0, 64, 40),
            node(0, 64, 3),
            node(-15, 64, -5));
        TrackSection section = new TrackSection(1, nodes, false, null);

        List<TrackIssue> issues = issuesOf(section);
        List<TrackIssue> selfIntersect = withCode(issues, "TRACK_SELF_INTERSECTION");
        assertFalse(selfIntersect.isEmpty(), "expected a self-intersection issue, got " + issues);

        // The near-miss is symmetric — it is found by walking forward from the start (which is
        // close to node 4, far away in arc length) and by walking forward from node 4 (which is
        // close to the start), so both anchors are legitimately reported. Just check that one of
        // the two anchors sits near each end, and that the measured clearance is under the limit.
        boolean anchoredNearStart = false;
        boolean anchoredNearLoopEnd = false;
        for (TrackIssue issue : selfIntersect) {
            assertTrue(issue.value() < ValidationLimits.DEFAULT.selfIntersectionClearanceBlocks(),
                "reported clearance should be below the configured minimum: " + issue);
            if (issue.distanceBlocks() < section.totalLength() * 0.1D) {
                anchoredNearStart = true;
            }
            if (issue.distanceBlocks() > section.totalLength() * 0.5D) {
                anchoredNearLoopEnd = true;
            }
        }
        assertTrue(anchoredNearStart || anchoredNearLoopEnd,
            "expected the self-intersection to be anchored near the start or near node 4: " + selfIntersect);
    }

    @Test
    @DisplayName("a closed circuit does not flag itself for being close to its own start/finish seam")
    void closedCircuitDoesNotFalselyFlagItsOwnSeam() {
        // The wrap point (s=0 and s=totalLength) is the same physical location on a closed
        // circuit; the arc-length wraparound handling must treat that as adjacent, not distant.
        List<TrackNode> nodes = new ArrayList<>();
        int count = 16;
        double radius = 60.0D;
        for (int i = 0; i < count; i++) {
            double a = 2.0D * Math.PI * i / count;
            nodes.add(node(Math.cos(a) * radius, 64.0D, Math.sin(a) * radius));
        }
        TrackSection section = new TrackSection(1, nodes, true, null);

        List<TrackIssue> issues = issuesOf(section);
        assertTrue(withCode(issues, "TRACK_SELF_INTERSECTION").isEmpty(),
            "closed ring falsely flagged its own seam: " + issues);
    }
}
