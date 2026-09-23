package com.micatechnologies.minecraft.rcmc.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.transit.CurveSpeed;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The demo metro's turning loops are wide enough to run round at a sensible speed.
 *
 * <p>They were hand-placed balloons of radius 18, entered through S-bends tighter still: with trains
 * slowing for curves, every train crawled round each loop at 3 blocks/s.</p>
 */
class DemoUndergroundLoopTest {

    @Test
    @DisplayName("a loop leaves the outbound track and arrives on the inbound one, heading back")
    void loopCloses() {
        List<double[]> points = DemoUnderground.turningLoop();
        double[] last = points.get(points.size() - 1);
        // The last point stops short of the inbound track's first node; it must be headed for it.
        assertTrue(last[1] > 0.0D && last[1] < DemoUnderground.TRACK_OFFSET + 1.0D,
            "ends beside the inbound track, at z=" + last[1]);
        assertTrue(last[0] > 0.0D && last[0] < 12.0D, "just past the end of the straights, at x=" + last[0]);
        double widest = 0.0D;
        for (double[] p : points) {
            widest = Math.max(widest, Math.abs(p[1]));
        }
        assertEquals(DemoUnderground.LOOP_RADIUS, widest, 2.0D, "its big arc centred between the tracks");
    }

    @Test
    @DisplayName("no curve anywhere on the Circle Line holds a train much under the loops' design speed")
    void noCrawling() {
        DemoUnderground.Plan plan = DemoUnderground.build(1, 2, new Vec3(0, 64, 0));
        TrackSection ring = plan.sections.get(0);
        double slowest = Double.POSITIVE_INFINITY;
        double at = 0.0D;
        for (double s = 0.0D; s < ring.totalLength(); s += 1.0D) {
            double limit = CurveSpeed.limitAt(ring, s);
            if (limit < slowest) {
                slowest = limit;
                at = s;
            }
        }
        double design = Math.sqrt(CurveSpeed.LATERAL * DemoUnderground.LOOP_RADIUS);
        Vec3 where = ring.frameAtDistance(at).position;
        assertTrue(slowest > 0.85D * design, "the slowest curve allows " + slowest + " blocks/s, at x="
            + where.x + " z=" + where.z + "; the loops are designed for " + design);
    }
}
