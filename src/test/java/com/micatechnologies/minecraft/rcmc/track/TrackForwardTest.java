package com.micatechnologies.minecraft.rcmc.track;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code forwardAt} is the cheap path the physics takes for gravity, and it must give exactly the
 * direction a whole frame would: the integrator's energy conservation is asserted against frames.
 */
class TrackForwardTest {

    private static void assertMatchesFrames(TrackSection section) {
        double length = section.totalLength();
        int samples = 997;
        for (int i = -3; i <= samples + 3; i++) {
            // Past both ends too, where the frame path clamps or wraps.
            double s = length * i / samples;
            TrackFrame frame = section.frameAtDistance(s);
            Vec3 forward = section.forwardAtDistance(s);
            assertEquals(frame.forward.x, forward.x, 1.0E-12D, "x at " + s);
            assertEquals(frame.forward.y, forward.y, 1.0E-12D, "y at " + s);
            assertEquals(frame.forward.z, forward.z, 1.0E-12D, "z at " + s);
            assertEquals(0.0D, frame.position.subtract(section.framePositionAtDistance(s)).length(), 1.0E-12D,
                "position at " + s);
        }
    }

    @Test
    @DisplayName("forward matches the frame's on a banked, climbing open section, ends included")
    void openBankedSection() {
        assertMatchesFrames(new TrackSection(1, Arrays.asList(
            new TrackNode(new Vec3(0, 64, 0), 0.0D, null),
            new TrackNode(new Vec3(30, 70, 10), 35.0D, null),
            new TrackNode(new Vec3(50, 62, 40), -60.0D, null),
            new TrackNode(new Vec3(20, 80, 70), 180.0D, null)), false, null));
    }

    @Test
    @DisplayName("forward matches the frame's on a closed circuit")
    void closedSection() {
        assertMatchesFrames(new TrackSection(1, Arrays.asList(
            new TrackNode(new Vec3(0, 64, 0), 10.0D, null),
            new TrackNode(new Vec3(40, 66, 0), 0.0D, null),
            new TrackNode(new Vec3(40, 70, 40), -20.0D, null),
            new TrackNode(new Vec3(0, 64, 40), 0.0D, null)), true, null));
    }
}
