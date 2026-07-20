package com.micatechnologies.minecraft.rcmc.track.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlopeTest {

    private static ElementContext levelContext() {
        TrackFrame entry = new TrackFrame(new Vec3(0.0D, 64.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), Vec3.UP);
        return new ElementContext(entry, 0.0D, 2.0D);
    }

    @Test
    @DisplayName("net height change over the slope matches the requested parameter")
    void heightChangeMatchesParameter() {
        Slope slope = new Slope(40.0D, 12.0D);
        ElementResult result = slope.generate(levelContext());
        TrackNode last = result.nodes.get(result.nodes.size() - 1);
        assertEquals(64.0D + 12.0D, last.position().y, 0.02D);
    }

    @Test
    @DisplayName("a descending slope loses height by the requested amount")
    void negativeHeightChangeDescends() {
        Slope slope = new Slope(40.0D, -15.0D);
        ElementResult result = slope.generate(levelContext());
        TrackNode last = result.nodes.get(result.nodes.size() - 1);
        assertEquals(64.0D - 15.0D, last.position().y, 0.02D);
    }

    @Test
    @DisplayName("the generated path's own length matches the requested length, since it is walked at unit speed")
    void pathLengthMatchesParameter() {
        Slope slope = new Slope(50.0D, 20.0D);
        ElementResult result = slope.generate(levelContext());

        double total = 0.0D;
        Vec3 previous = new Vec3(0.0D, 64.0D, 0.0D);
        for (TrackNode node : result.nodes) {
            total += node.position().distanceTo(previous);
            previous = node.position();
        }
        assertEquals(50.0D, total, 0.01D);
    }

    @Test
    @DisplayName("bank passes through a slope unchanged, since a grade change is not a turn")
    void bankPassesThrough() {
        TrackFrame entry = new TrackFrame(new Vec3(0.0D, 64.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), Vec3.UP);
        ElementContext context = new ElementContext(entry, 10.0D, 2.0D);
        List<TrackNode> nodes = new Slope(30.0D, 5.0D).generate(context).nodes;
        for (TrackNode node : nodes) {
            assertEquals(10.0D, node.bankDegrees(), 1e-9);
        }
    }

    @Test
    @DisplayName("a slope starting from level track ends with a shallower grade than a short, steep rise")
    void gentlerOverLongerRun() {
        // Same rise, a much longer run: the resulting exit pitch should be noticeably shallower.
        Slope gentle = new Slope(100.0D, 20.0D);
        Slope steep = new Slope(30.0D, 20.0D);

        double gentleExitPitch = Math.asin(gentle.generate(levelContext()).exitFrame.forward.y);
        double steepExitPitch = Math.asin(steep.generate(levelContext()).exitFrame.forward.y);

        assertTrue(gentleExitPitch < steepExitPitch,
            "longer run should produce a shallower exit grade: gentle=" + Math.toDegrees(gentleExitPitch)
                + " steep=" + Math.toDegrees(steepExitPitch));
    }

    @Test
    @DisplayName("rejects a height change that would require the path to be vertical or steeper")
    void rejectsImpossibleHeightChange() {
        assertThrows(IllegalArgumentException.class, () -> new Slope(10.0D, 15.0D));
        assertThrows(IllegalArgumentException.class, () -> new Slope(10.0D, -10.0D));
    }
}
