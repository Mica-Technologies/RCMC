package com.micatechnologies.minecraft.rcmc.track.element;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * No element may run through itself.
 *
 * <p>Written after a builder's screenshot showed a vertical loop's entry and exit legs passing
 * straight through one another. The geometry was not "wrong" in any way the existing tests could
 * see — the element's endpoints were right, its exit frame was right, and the ideal path had no
 * formal self-intersection when the check was written carelessly enough. What it had was two legs
 * grazing within 0.016 blocks, which renders as track embedded in track.</p>
 *
 * <p>The lesson that shaped this file: an element's correctness is not only a property of its
 * endpoints and its curvature profile. It is also a property of the volume it sweeps, and nothing
 * else in the suite looked at that.</p>
 *
 * <p>So this measures clearance on the <em>spline the game actually draws</em>, not on the ideal
 * path the element integrates. Those differ — the spline interpolates a finite set of nodes — and
 * it is the drawn one a rider hits.</p>
 */
class ElementSelfClearanceTest {

    /**
     * Rendered track width in blocks: {@code 2 * TIE_HALF_LENGTH_R} in {@code TrackMeshBuilder},
     * where {@code TIE_HALF_LENGTH_R = HALF_GAUGE (0.55) + 0.15}. Duplicated rather than imported
     * because that constant is client-side and this package must stay free of client code; if the
     * cross-section is ever widened, this test should fail and be updated deliberately.
     */
    private static final double TRACK_WIDTH = 1.40D;

    /**
     * Samples closer together than this along the track are neighbours, not a clash. Generous —
     * well beyond the width of the track itself — so that a genuinely tight but legal curve is not
     * reported as running through itself.
     */
    private static final double NEIGHBOUR_ARC = 6.0D;

    private static final double SAMPLE_STEP = 0.25D;

    private static TrackSection sectionOf(TrackElement element) {
        TrackFrame entry = new TrackFrame(new Vec3(0, 64, 0), new Vec3(1, 0, 0), Vec3.UP);
        ElementResult result = element.generate(new ElementContext(entry, 0.0D));
        List<TrackNode> nodes = new ArrayList<>();
        nodes.add(new TrackNode(entry.position, 0.0D, null));
        nodes.addAll(result.nodes);
        return new TrackSection(1, nodes, false, null);
    }

    /** Closest approach between two points of the section more than {@link #NEIGHBOUR_ARC} apart. */
    private static double[] closestApproach(TrackSection section) {
        double length = section.totalLength();
        int count = (int) Math.ceil(length / SAMPLE_STEP);
        Vec3[] points = new Vec3[count + 1];
        for (int i = 0; i <= count; i++) {
            points[i] = section.frameAtDistance(Math.min(length, i * SAMPLE_STEP)).position;
        }
        int skip = (int) Math.ceil(NEIGHBOUR_ARC / SAMPLE_STEP);
        double best = Double.MAX_VALUE;
        double atA = 0.0D;
        double atB = 0.0D;
        for (int i = 0; i <= count; i++) {
            for (int j = i + skip; j <= count; j++) {
                double d = points[i].distanceTo(points[j]);
                if (d < best) {
                    best = d;
                    atA = i * SAMPLE_STEP;
                    atB = j * SAMPLE_STEP;
                }
            }
        }
        return new double[] {best, atA, atB};
    }

    private static void assertClears(TrackElement element, String what) {
        TrackSection section = sectionOf(element);
        double[] result = closestApproach(section);
        assertTrue(result[0] >= TRACK_WIDTH,
            what + " runs through itself: the track at " + String.format("%.1f", result[1])
                + " blocks comes within " + String.format("%.3f", result[0]) + " blocks of the track at "
                + String.format("%.1f", result[2]) + " (needs " + TRACK_WIDTH
                + ", the rendered width). Two legs of one element are occupying the same space.");
    }

    @Test
    @DisplayName("a vertical loop's entry and exit legs pass beside each other, not through")
    void verticalLoopClearsItself() {
        // The reported case was a top radius of 6. Larger and smaller are checked too, because the
        // clearance offset is absolute while the loop scales — so a big loop is the case where a
        // fixed offset is proportionally smallest, and a small one is where the legs are tightest.
        for (double radius : new double[] {4.0D, 6.0D, 10.0D, 16.0D}) {
            assertClears(new VerticalLoop(radius), "vertical loop (top radius " + radius + ")");
        }
    }

    @Test
    @DisplayName("a corkscrew clears itself in both roll directions")
    void corkscrewClearsItself() {
        for (RollDirection roll : RollDirection.values()) {
            assertClears(new Corkscrew(8.0D, 12.0D, roll), "corkscrew (" + roll + ")");
        }
    }

    @Test
    @DisplayName("a helix clears itself between revolutions")
    void helixClearsItself() {
        // A helix stacks its own turns vertically, so its clearance is set by the rise per
        // revolution. Two revolutions is the case where a too-shallow rise would show up.
        assertClears(new Helix(20.0D, 720.0D, 10.0D, TurnDirection.LEFT, 14.0D, 45.0D),
            "helix (2 revolutions, 10-block rise)");
    }
}
