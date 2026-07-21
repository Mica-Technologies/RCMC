package com.micatechnologies.minecraft.rcmc.track;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.element.ElementContext;
import com.micatechnologies.minecraft.rcmc.track.element.ElementResult;
import com.micatechnologies.minecraft.rcmc.track.element.VerticalLoop;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rule that stops support columns being drawn through track.
 *
 * <p>Reported from a screenshot: on a vertical loop and a corkscrew, the auto-generated supports ran
 * straight through the track. The cause is structural rather than cosmetic — a column dropped from
 * a point near the top of a loop has the bottom of the loop directly beneath it, so any rule of the
 * form "drop a line to the ground" is guaranteed to produce this on any inversion.</p>
 *
 * <p>These tests use a real loop rather than a contrived shape, because the whole question is
 * whether the rule holds on the geometry that actually broke.</p>
 */
class TrackClearanceTest {

    // The production policy, not a copy of it — a test that quietly disagreed with the shipped
    // thresholds would prove nothing about what actually gets built.
    private static final double CLEARANCE = TrackClearance.COLUMN_CLEARANCE;
    private static final double EXCLUSION = TrackClearance.ATTACH_EXCLUSION;

    /** A real vertical loop, entered from level track at y=64. */
    private static TrackSection loop() {
        TrackFrame entry = new TrackFrame(new Vec3(0, 64, 0), new Vec3(1, 0, 0), Vec3.UP);
        ElementResult result = new VerticalLoop(6.0D).generate(new ElementContext(entry, 0.0D));
        List<TrackNode> nodes = new ArrayList<>();
        nodes.add(new TrackNode(entry.position, 0.0D, null));
        nodes.addAll(result.nodes);
        return new TrackSection(1, nodes, false, null);
    }

    @Test
    @DisplayName("a column dropped from the crown of a loop is refused, because the loop is under it")
    void crownColumnIsRefused() {
        TrackSection loop = loop();

        // Find the highest point and the distance along the track where it occurs — the crown.
        double crownAt = 0.0D;
        double crownY = -Double.MAX_VALUE;
        for (double s = 0.0D; s < loop.totalLength(); s += 0.5D) {
            Vec3 at = loop.positionAtDistance(s);
            if (at.y > crownY) {
                crownY = at.y;
                crownAt = s;
            }
        }
        Vec3 crown = loop.positionAtDistance(crownAt);

        // The loop's two legs pass 1.14 blocks apart, so this column misses both sets of rails.
        // It is still refused, because it would stand through the middle of the loop and the
        // clearance that matters is the one around the train — see TrackClearance.COLUMN_CLEARANCE.
        assertTrue(TrackClearance.columnWouldClash(loop, crown.x, crown.z, 60.0D, crown.y,
                crownAt, CLEARANCE, EXCLUSION),
            "a column from the crown of a loop straight down to the ground passes through the "
                + "middle of that loop, and must not be placed");
    }

    @Test
    @DisplayName("a column well clear of the loop is allowed")
    void clearColumnIsAllowed() {
        TrackSection loop = loop();
        // Far to the side of the loop's plane, which lies along x with a small lateral offset.
        assertFalse(TrackClearance.columnWouldClash(loop, 9.0D, 40.0D, 60.0D, 80.0D,
                0.0D, CLEARANCE, EXCLUSION),
            "nothing is anywhere near this column; it should not be refused");
    }

    @Test
    @DisplayName("the track being held up is not counted as an obstruction")
    void attachmentItselfIsNotAClash() {
        TrackSection loop = loop();
        // A column attaching at the very start of the run, directly under its own attachment point.
        Vec3 start = loop.positionAtDistance(1.0D);
        assertFalse(TrackClearance.columnWouldClash(loop, start.x, start.z, 55.0D, start.y,
                1.0D, CLEARANCE, EXCLUSION),
            "a support must not reject itself: the track at its own attachment is what it holds");
    }

    @Test
    @DisplayName("only track at heights the column occupies can obstruct it")
    void trackAboveTheColumnIsIgnored() {
        TrackSection loop = loop();
        Vec3 crown = loop.positionAtDistance(loop.totalLength() * 0.5D);
        // A stub column ending well below the loop: the loop is above it, so nothing is in the way.
        assertFalse(TrackClearance.columnWouldClash(loop, crown.x, crown.z, 40.0D, 45.0D,
                0.0D, CLEARANCE, EXCLUSION),
            "track passing overhead is not in a column's path");
    }
}
