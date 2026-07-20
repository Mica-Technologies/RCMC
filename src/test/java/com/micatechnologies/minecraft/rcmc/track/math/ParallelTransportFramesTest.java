package com.micatechnologies.minecraft.rcmc.track.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParallelTransportFramesTest {

    private static ParallelTransportFrames framesFor(List<Vec3> pts) {
        return new ParallelTransportFrames(
            new ArcLengthTable(CatmullRomSpline.withPhantomEndpoints(pts)), 256);
    }

    @Test
    @DisplayName("frames stay orthonormal along a wildly twisting track")
    void framesStayOrthonormal() {
        List<Vec3> pts = Arrays.asList(
            new Vec3(0, 0, 0),
            new Vec3(20, 30, 0),
            new Vec3(40, 10, 20),
            new Vec3(30, 25, 45),
            new Vec3(0, 5, 50),
            new Vec3(-20, 20, 20));
        ParallelTransportFrames frames = framesFor(pts);

        for (int i = 0; i <= 200; i++) {
            TrackFrame f = frames.frameAtDistance(frames.totalLength() * i / 200.0D);
            assertEquals(1.0D, f.forward.length(), 1.0e-6D, "forward not unit");
            assertEquals(1.0D, f.up.length(), 1.0e-6D, "up not unit");
            assertEquals(1.0D, f.right.length(), 1.0e-6D, "right not unit");
            assertEquals(0.0D, f.forward.dot(f.up), 1.0e-6D, "forward/up not perpendicular");
            assertEquals(0.0D, f.forward.dot(f.right), 1.0e-6D, "forward/right not perpendicular");
            assertEquals(0.0D, f.up.dot(f.right), 1.0e-6D, "up/right not perpendicular");
        }
    }

    @Test
    @DisplayName("flat straight track keeps up pointing at world up — no spurious twist")
    void straightTrackAcquiresNoTwist() {
        // This is the property a Frenet frame fails outright (undefined normal at zero
        // curvature) and the reason parallel transport is used instead.
        List<Vec3> pts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            pts.add(new Vec3(i * 8.0D, 0, 0));
        }
        ParallelTransportFrames frames = framesFor(pts);

        for (int i = 0; i <= 100; i++) {
            TrackFrame f = frames.frameAtDistance(frames.totalLength() * i / 100.0D);
            assertTrue(f.up.dot(Vec3.UP) > 0.99999D, "up drifted to " + f.up);
        }
    }

    @Test
    @DisplayName("a flat left turn does not roll the frame")
    void planarCurveDoesNotRoll() {
        // A curve confined to the horizontal plane has zero torsion, so transport must
        // produce zero roll. If it rolls here, the transport step is picking up rotation
        // about the tangent that it should not.
        List<Vec3> pts = new ArrayList<>();
        for (int i = 0; i <= 12; i++) {
            double a = Math.PI * i / 12.0D;
            pts.add(new Vec3(Math.cos(a) * 30.0D, 0, Math.sin(a) * 30.0D));
        }
        ParallelTransportFrames frames = framesFor(pts);

        for (int i = 0; i <= 100; i++) {
            TrackFrame f = frames.frameAtDistance(frames.totalLength() * i / 100.0D);
            assertTrue(f.up.dot(Vec3.UP) > 0.9999D,
                "planar curve rolled the frame; up = " + f.up);
        }
    }

    @Test
    @DisplayName("banking rolls up about forward by the requested angle")
    void bankingRollsByRequestedAngle() {
        TrackFrame level = new TrackFrame(Vec3.ZERO, new Vec3(1, 0, 0), Vec3.UP);
        TrackFrame banked = level.withBank(Math.toRadians(45.0D));

        assertEquals(Math.toRadians(45.0D), Math.acos(banked.up.dot(level.up)), 1.0e-9D);
        // Rolling about forward must not change forward.
        assertEquals(1.0D, banked.forward.dot(level.forward), 1.0e-9D);
        // A full turn is the identity.
        TrackFrame full = level.withBank(Math.PI * 2.0D);
        assertTrue(full.up.dot(level.up) > 0.99999D, "360-degree bank was not the identity");
    }
}
