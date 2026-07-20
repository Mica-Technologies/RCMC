package com.micatechnologies.minecraft.rcmc.track.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every element in this package is built on {@link ElementGeometry}'s handful of primitives, so bugs here
 * would silently corrupt every element's geometry at once. These tests check the primitives in isolation,
 * against invariants that must hold regardless of the specific vectors involved (length preservation,
 * fixed points, orthogonality) rather than one-off hand-computed numbers, which is a more reliable way to
 * catch a sign error than eyeballing a single worked example.
 */
class ElementGeometryTest {

    private static final double EPS = 1.0e-9D;

    @Test
    @DisplayName("rotate preserves vector length")
    void rotatePreservesLength() {
        Vec3 v = new Vec3(3.0D, 1.0D, -2.0D);
        Vec3 axis = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 rotated = ElementGeometry.rotate(v, axis, Math.toRadians(37.0D));
        assertEquals(v.length(), rotated.length(), 1e-9);
    }

    @Test
    @DisplayName("rotating by a full turn returns the original vector")
    void rotateFullTurnIsIdentity() {
        Vec3 v = new Vec3(2.0D, 0.5D, -1.0D);
        Vec3 axis = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 rotated = ElementGeometry.rotate(v, axis, 2.0D * Math.PI);
        assertEquals(v.x, rotated.x, 1e-6);
        assertEquals(v.y, rotated.y, 1e-6);
        assertEquals(v.z, rotated.z, 1e-6);
    }

    @Test
    @DisplayName("the rotation axis is a fixed point of its own rotation")
    void axisIsInvariant() {
        Vec3 axis = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 rotated = ElementGeometry.rotate(axis, axis, Math.toRadians(123.0D));
        assertEquals(0.0D, axis.distanceTo(rotated), 1e-9);
    }

    @Test
    @DisplayName("rotating forward 90 degrees about up turns it into right, for an orthonormal frame")
    void rotateNinetyDegreesMatchesRightAngle() {
        Vec3 forward = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        // TrackFrame's own convention: right = forward x up.
        Vec3 right = forward.cross(up);
        Vec3 rotatedNegative = ElementGeometry.rotate(forward, up, -Math.PI / 2.0D);
        assertEquals(right.x, rotatedNegative.x, 1e-9);
        assertEquals(right.y, rotatedNegative.y, 1e-9);
        assertEquals(right.z, rotatedNegative.z, 1e-9);
    }

    @Test
    @DisplayName("smoothstep hits its endpoints exactly and its midpoint at one half")
    void smoothstepEndpoints() {
        assertEquals(0.0D, ElementGeometry.smoothstep(0.0D), EPS);
        assertEquals(1.0D, ElementGeometry.smoothstep(1.0D), EPS);
        assertEquals(0.5D, ElementGeometry.smoothstep(0.5D), EPS);
    }

    @Test
    @DisplayName("smoothstep is monotonically increasing on [0, 1]")
    void smoothstepMonotonic() {
        double previous = -1.0D;
        for (int i = 0; i <= 50; i++) {
            double v = ElementGeometry.smoothstep(i / 50.0D);
            assertTrue(v >= previous - EPS, "smoothstep went backwards at i=" + i);
            previous = v;
        }
    }

    @Test
    @DisplayName("eased bank hits entry bank at t=0 and exit bank at t=1")
    void easedBankHitsEndpoints() {
        assertEquals(5.0D, ElementGeometry.easedBankDegrees(0.0D, 5.0D, 40.0D, 0.0D), EPS);
        assertEquals(0.0D, ElementGeometry.easedBankDegrees(1.0D, 5.0D, 40.0D, 0.0D), EPS);
    }

    @Test
    @DisplayName("eased bank holds the target value through the middle of the span")
    void easedBankHoldsMiddle() {
        assertEquals(40.0D, ElementGeometry.easedBankDegrees(0.5D, 5.0D, 40.0D, 0.0D), EPS);
    }

    @Test
    @DisplayName("balanced bank matches the closed-form atan(v^2/(r*g)) below the clamp")
    void balancedBankMatchesFormula() {
        double speed = 10.0D;
        double radius = 50.0D;
        double gravity = 9.81D;
        double expectedDegrees = Math.toDegrees(Math.atan((speed * speed) / (radius * gravity)));
        assertEquals(expectedDegrees,
            ElementGeometry.balancedBankDegrees(speed, radius, gravity, 90.0D), 1e-9);
    }

    @Test
    @DisplayName("balanced bank is clamped at high speed rather than approaching 90 degrees")
    void balancedBankIsClamped() {
        double clamped = ElementGeometry.balancedBankDegrees(200.0D, 5.0D, 9.81D, 60.0D);
        assertEquals(60.0D, clamped, 1e-9);
    }

    @Test
    @DisplayName("transported up stays perpendicular to the new tangent")
    void transportUpStaysOrthogonal() {
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 fromTangent = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 toTangent = new Vec3(0.0D, 0.0D, 1.0D).normalize();
        Vec3 transported = ElementGeometry.transportUp(up, fromTangent, toTangent);
        assertEquals(0.0D, transported.dot(toTangent), 1e-9);
        assertEquals(up.length(), transported.length(), 1e-9);
    }

    @Test
    @DisplayName("transporting up along parallel tangents leaves it unchanged")
    void transportUpNoOpOnStraightTrack() {
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 tangent = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 transported = ElementGeometry.transportUp(up, tangent, tangent);
        assertEquals(0.0D, up.distanceTo(transported), 1e-9);
    }
}
