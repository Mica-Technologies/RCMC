package com.micatechnologies.minecraft.rcmc.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link RideTelemetry} on a bare JVM. Like {@code physics.TrainTest}, this needs no
 * game instance: {@code Train}, {@code TrackNetwork} and everything under {@code track} are all
 * free of Minecraft types, and {@link RideTelemetry} is written to the same standard.
 */
class RideTelemetryTest {

    private static final double GRAVITY = 9.81D;

    private static PhysicsIntegrator frictionless() {
        return new PhysicsIntegrator(GRAVITY, 0.0D, 0.0D, 1000.0D);
    }

    private static TrackNode node(double x, double y, double z) {
        return new TrackNode(new Vec3(x, y, z));
    }

    /** Flat, straight, open track along +X, 200 blocks long. */
    private static TrackNetwork flatNetwork() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(100, 64, 0), node(200, 64, 0)), false, null));
        return n;
    }

    /** A flat, closed, roughly circular ring in the XZ plane, unbanked. */
    private static TrackNetwork ringNetwork(double radius, int nodeCount) {
        List<TrackNode> ring = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            double a = 2.0D * Math.PI * i / nodeCount;
            ring.add(node(Math.cos(a) * radius, 64.0D, Math.sin(a) * radius));
        }
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, ring, true, null));
        return n;
    }

    @Test
    @DisplayName("stationary on flat mid-section track reads as a plain 1g at rest")
    void restingOnFlatTrackIsOneG() {
        TrackNetwork n = flatNetwork();
        Train train = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 100.0D), 0.0D);

        RideTelemetry.Reading reading = RideTelemetry.compute(train, n, 0, 0.0D, 0.05D, GRAVITY);

        assertEquals(1.0D, reading.gForces.vertical, 1e-9);
        assertEquals(0.0D, reading.gForces.lateral, 1e-9);
        assertEquals(0.0D, reading.gForces.longitudinal, 1e-9);
        assertEquals(0.0D, reading.speedBlocksPerSecond, 1e-9);
        assertFalse(reading.curvatureUncertain, "well clear of both section ends");
    }

    @Test
    @DisplayName("curvature is flagged uncertain within half a step of an open section's end")
    void curvatureUncertainNearOpenEnd() {
        TrackNetwork n = flatNetwork();
        Train nearStart = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 0.1D), 5.0D);
        Train midSection = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 100.0D), 5.0D);

        RideTelemetry.Reading atStart = RideTelemetry.compute(nearStart, n, 0, 5.0D, 0.05D, GRAVITY);
        RideTelemetry.Reading atMiddle = RideTelemetry.compute(midSection, n, 0, 5.0D, 0.05D, GRAVITY);

        assertTrue(atStart.curvatureUncertain, "within half a step of the section start");
        assertFalse(atMiddle.curvatureUncertain, "far from either end");
    }

    @Test
    @DisplayName("a flat unbanked circular turn shows up almost entirely as lateral load")
    void circularTurnIsMostlyLateral() {
        // Mirrors GForcesTest's "an unbanked turn throws riders sideways", but exercised through
        // the whole finite-difference curvature estimate rather than an exact analytic value —
        // this is the test that would catch a sign error or a units mistake in that estimate.
        double radius = 50.0D;
        double speed = 15.0D;
        TrackNetwork n = ringNetwork(radius, 32);
        // A quarter-lap in, comfortably clear of the closed loop's start/finish seam (see the
        // class javadoc on RideTelemetry: the seam is where the clamp-based sampling degrades).
        double s = n.section(1).totalLength() * 0.25D;
        Train train = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, s), speed);

        RideTelemetry.Reading reading = RideTelemetry.compute(train, n, 0, speed, 0.05D, GRAVITY);

        // The ring is flat and unbanked, so its transported frame's `up` stays exactly world-up
        // and the (horizontal) centripetal term is exactly perpendicular to it — vertical load
        // should be untouched by the turn.
        assertEquals(1.0D, reading.gForces.vertical, 1e-6,
            "an unbanked horizontal turn should not load the vertical axis");

        double expectedLateralMagnitude = (speed * speed / radius) / GRAVITY;
        assertEquals(expectedLateralMagnitude, Math.abs(reading.gForces.lateral),
            expectedLateralMagnitude * 0.1D,
            "lateral load should match v^2/r within the curvature estimate's discretisation error");
    }

    @Test
    @DisplayName("curvature direction is consistent (not flipping sign) around a full lap")
    void curvatureDirectionIsConsistentAroundTheLoop() {
        // The direction estimate is a normalised finite difference of the tangent; if the sign
        // convention were wrong it would show up as lateral load flipping which side of the
        // frame's `right` axis it lands on as the car goes around, rather than staying consistently
        // toward the (fixed, single) centre of the ring.
        double radius = 50.0D;
        double speed = 15.0D;
        TrackNetwork n = ringNetwork(radius, 32);
        double total = n.section(1).totalLength();
        Train train = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 0.0D), speed);

        Boolean sign = null;
        for (double frac = 0.1D; frac < 1.0D; frac += 0.1D) {
            train.setState(new TrackRef(1, total * frac), speed);
            RideTelemetry.Reading reading = RideTelemetry.compute(train, n, 0, speed, 0.05D, GRAVITY);
            boolean positive = reading.gForces.lateral > 0.0D;
            if (sign == null) {
                sign = positive;
            }
            else {
                assertEquals(sign, positive,
                    "lateral load flipped sign at " + (frac * 100) + "% around a constant-curvature loop");
            }
        }
    }

    @Test
    @DisplayName("longitudinal G tracks dv/dt between samples")
    void longitudinalTracksVelocityChange() {
        TrackNetwork n = flatNetwork();
        double previousVelocity = 10.0D;
        double currentVelocity = 15.0D;
        double dt = 0.05D;
        Train train = new Train(TrainSpec.singleCar(), frictionless(),
            new TrackRef(1, 100.0D), currentVelocity);

        RideTelemetry.Reading reading = RideTelemetry.compute(train, n, 0, previousVelocity, dt, GRAVITY);

        double expectedAcceleration = (currentVelocity - previousVelocity) / dt;
        assertEquals(expectedAcceleration / GRAVITY, reading.gForces.longitudinal, 1e-9);
    }

    @Test
    @DisplayName("a missing section returns null rather than throwing")
    void missingSectionReturnsNull() {
        // Mirrors the transient EntityCoasterCar.onUpdate() tolerates: a train can reference a
        // section the local network does not (yet) have. RideMonitor relies on this returning
        // null rather than throwing so a client render frame never crashes on it.
        TrackNetwork empty = new TrackNetwork();
        Train train = new Train(TrainSpec.singleCar(), frictionless(), new TrackRef(1, 0.0D), 5.0D);

        assertNull(RideTelemetry.compute(train, empty, 0, 5.0D, 0.05D, GRAVITY));
    }
}
