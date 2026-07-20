package com.micatechnologies.minecraft.rcmc.track.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TrackFrame#rollDegreesFromLevel()}, which drives the rider camera.
 *
 * <p>Signed-angle code is exactly the kind that is easy to get wrong in a way nothing catches: an
 * unsigned result still looks plausible in every static screenshot and only shows up as riders
 * rolling the wrong way through half their turns.</p>
 */
class TrackFrameRollTest {

    private static TrackFrame level() {
        return new TrackFrame(Vec3.ZERO, new Vec3(1, 0, 0), Vec3.UP);
    }

    @Test
    @DisplayName("flat unbanked track reads as exactly zero roll")
    void levelTrackIsZero() {
        // Must be exactly zero, not merely small: a residue here would make a stationary camera
        // sit very slightly tilted, which is more noticeable than it sounds.
        assertEquals(0.0D, level().rollDegreesFromLevel(), 1e-9);
    }

    @Test
    @DisplayName("banking reports the angle it was banked by")
    void bankedTrackReportsItsAngle() {
        for (double bank : new double[] {5.0D, 15.0D, 30.0D, 45.0D, 60.0D, 89.0D}) {
            TrackFrame banked = level().withBank(Math.toRadians(bank));
            assertEquals(bank, Math.abs(banked.rollDegreesFromLevel()), 1e-6,
                "round trip failed at " + bank + " degrees");
        }
    }

    @Test
    @DisplayName("banking left and right roll in opposite directions")
    void rollIsSigned() {
        // The property that an unsigned implementation would silently fail.
        double right = level().withBank(Math.toRadians(35.0D)).rollDegreesFromLevel();
        double left = level().withBank(Math.toRadians(-35.0D)).rollDegreesFromLevel();

        assertTrue(right * left < 0.0D,
            "left and right banking gave the same sign: " + left + " and " + right);
        assertEquals(Math.abs(right), Math.abs(left), 1e-6, "asymmetric magnitude");
    }

    @Test
    @DisplayName("an inverted car reads as fully rolled over")
    void inversionReadsAsUpsideDown() {
        TrackFrame inverted = level().withBank(Math.PI);
        assertEquals(180.0D, Math.abs(inverted.rollDegreesFromLevel()), 1e-6);
    }

    @Test
    @DisplayName("roll is measured independently of heading")
    void rollDoesNotDependOnHeading() {
        // A 30-degree bank is 30 degrees whether the train is heading north or south-west. If the
        // level reference were computed in world axes rather than relative to travel, this drifts.
        for (double heading = 0.0D; heading < 2.0D * Math.PI; heading += Math.PI / 6.0D) {
            Vec3 forward = new Vec3(Math.cos(heading), 0.0D, Math.sin(heading));
            TrackFrame banked = new TrackFrame(Vec3.ZERO, forward, Vec3.UP)
                .withBank(Math.toRadians(30.0D));
            assertEquals(30.0D, Math.abs(banked.rollDegreesFromLevel()), 1e-6,
                "heading " + Math.toDegrees(heading) + " changed the measured roll");
        }
    }

    @Test
    @DisplayName("roll is measured independently of grade")
    void rollDoesNotDependOnGrade() {
        // Climbing or diving must not register as banking; only rotation about the direction of
        // travel counts. Getting this wrong would roll the camera on every hill.
        for (double grade = -60.0D; grade <= 60.0D; grade += 15.0D) {
            double r = Math.toRadians(grade);
            Vec3 forward = new Vec3(Math.cos(r), Math.sin(r), 0.0D).normalize();
            TrackFrame plain = new TrackFrame(Vec3.ZERO, forward, Vec3.UP);
            assertEquals(0.0D, plain.rollDegreesFromLevel(), 1e-6,
                "a " + grade + "-degree grade registered as roll");

            TrackFrame banked = plain.withBank(Math.toRadians(25.0D));
            assertEquals(25.0D, Math.abs(banked.rollDegreesFromLevel()), 1e-6,
                "bank measured wrongly on a " + grade + "-degree grade");
        }
    }

    @Test
    @DisplayName("vertical track has no level reference and reports zero rather than NaN")
    void verticalTrackIsZero() {
        // Straight up leaves nothing to project world-up onto. Zero is the honest answer; a NaN
        // here would propagate into the camera and black-screen the client.
        TrackFrame straightUp = new TrackFrame(Vec3.ZERO, Vec3.UP, new Vec3(1, 0, 0));
        double roll = straightUp.rollDegreesFromLevel();
        assertTrue(Double.isFinite(roll), "vertical track produced " + roll);
        assertEquals(0.0D, roll, 1e-9);
    }
}
