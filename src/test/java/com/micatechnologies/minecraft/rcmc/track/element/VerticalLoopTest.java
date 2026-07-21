package com.micatechnologies.minecraft.rcmc.track.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VerticalLoopTest {

    private static ElementContext levelContext() {
        TrackFrame entry = new TrackFrame(new Vec3(0.0D, 64.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), Vec3.UP);
        return new ElementContext(entry, 0.0D, 2.0D);
    }

    @Test
    @DisplayName("a full loop returns to (very nearly) its entry heading")
    void returnsToEntryHeading() {
        VerticalLoop loop = new VerticalLoop(8.0D);
        ElementResult result = loop.generate(levelContext());
        // The heading closure comes from an exact trig identity (phi(L) = 2*pi), independent of the
        // numerical position integration, so this should be very tight.
        assertEquals(1.0D, result.exitFrame.forward.dot(new Vec3(1.0D, 0.0D, 0.0D)), 1e-6);
    }

    @Test
    @DisplayName("a full loop restores the entry up vector - the rider ends right-side up again")
    void restoresEntryUp() {
        VerticalLoop loop = new VerticalLoop(8.0D);
        ElementResult result = loop.generate(levelContext());
        assertEquals(1.0D, result.exitFrame.up.dot(Vec3.UP), 1e-6);
    }

    @Test
    @DisplayName("the loop reaches a peak noticeably higher than its entry, on the order of the top radius")
    void reachesAPeak() {
        double topRadius = 10.0D;
        VerticalLoop loop = new VerticalLoop(topRadius);
        ElementResult result = loop.generate(levelContext());

        double entryY = 64.0D;
        double peakY = entryY;
        for (TrackNode node : result.nodes) {
            peakY = Math.max(peakY, node.position().y);
        }
        assertTrue(peakY > entryY + topRadius,
            "expected a peak clearly above entry height, got peak=" + peakY + " entry=" + entryY);
        // Generous sanity bound - a teardrop loop is not enormously taller than a handful of top radii.
        assertTrue(peakY < entryY + 8.0D * topRadius,
            "peak height looks implausibly large: " + peakY);
    }

    @Test
    @DisplayName("the peak lands roughly at the midpoint of the node sequence, not near either end")
    void peakIsNearTheMiddle() {
        VerticalLoop loop = new VerticalLoop(9.0D);
        ElementResult result = loop.generate(levelContext());

        int peakIndex = 0;
        double peakY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < result.nodes.size(); i++) {
            double y = result.nodes.get(i).position().y;
            if (y > peakY) {
                peakY = y;
                peakIndex = i;
            }
        }
        double fraction = (double) peakIndex / result.nodes.size();
        assertEquals(0.5D, fraction, 0.15D);
    }

    @Test
    @DisplayName("the generated path length matches pi^2 * topRadius, since it is walked at unit speed")
    void pathLengthMatchesDerivedFormula() {
        double topRadius = 6.0D;
        VerticalLoop loop = new VerticalLoop(topRadius);
        ElementResult result = loop.generate(levelContext());

        double total = 0.0D;
        Vec3 previous = new Vec3(0.0D, 64.0D, 0.0D);
        for (TrackNode node : result.nodes) {
            total += node.position().distanceTo(previous);
            previous = node.position();
        }
        // The in-plane path is exactly pi^2 * r, because the loop is integrated at unit speed. The
        // nodes are that path pushed sideways for self-clearance (see VerticalLoop's javadoc), and
        // that displacement adds a little length in quadrature — 0.068 blocks here, measured. So the
        // real invariant is no longer equality: the path may exceed the formula slightly and must
        // never fall short of it, since the offset can only add length.
        double inPlane = Math.PI * Math.PI * topRadius;
        assertTrue(total >= inPlane - 1e-6D,
            "the path cannot be shorter than the in-plane loop it is derived from: "
                + total + " < " + inPlane);
        assertTrue(total <= inPlane + 0.10D,
            "the clearance offset should add well under a tenth of a block, got "
                + (total - inPlane));
    }

    @Test
    @DisplayName("bank passes through a loop unchanged - inversion comes from transported up, not authored bank")
    void bankPassesThrough() {
        TrackFrame entry = new TrackFrame(new Vec3(0.0D, 64.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), Vec3.UP);
        ElementContext context = new ElementContext(entry, 3.0D, 2.0D);
        for (TrackNode node : new VerticalLoop(8.0D).generate(context).nodes) {
            assertEquals(3.0D, node.bankDegrees(), 1e-9);
        }
    }
}
