package com.micatechnologies.minecraft.rcmc.debug;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a complete demonstration circuit procedurally.
 *
 * <p>Exists for the high-speed motion spike — the project's largest open risk is whether a train
 * moving at coaster speed can be made to look smooth on this platform, and that question can only
 * be answered by a human watching a real client. This produces something worth watching without
 * a track editor existing yet.</p>
 *
 * <p>Also doubles as an integration fixture: the circuit exercises a drop, a banked turn, a
 * climb and a crest, so a regression in banking, transport framing or the residual correction
 * shows up as something visibly wrong rather than as a number in a test.</p>
 */
public final class DemoCoaster {

    private DemoCoaster() {
        throw new AssertionError("No instances.");
    }

    /**
     * A closed circuit centred on {@code origin}: a drop into a banked 180° turn, a climb, a crest
     * with airtime, and a banked return.
     *
     * @param origin  centre of the circuit, typically the player's position
     * @param radius  half-width of the layout in blocks
     * @param dropHeight height of the lift crest above the low point
     */
    public static TrackSection build(int sectionId, Vec3 origin, double radius, double dropHeight) {
        List<TrackNode> nodes = new ArrayList<>();
        int steps = 32;

        for (int i = 0; i < steps; i++) {
            double t = (double) i / steps;
            double angle = 2.0D * Math.PI * t;

            double x = origin.x + Math.cos(angle) * radius;
            double z = origin.z + Math.sin(angle) * radius * 0.6D;

            // One full up-down cycle around the lap: a lift to the crest, then a drop. Raised to
            // a power so the crest is narrower than the valley, which is what produces a sharp
            // airtime moment rather than a long floaty one.
            double climb = (Math.cos(angle) + 1.0D) / 2.0D;
            double y = origin.y + dropHeight * Math.pow(climb, 1.6D);

            // Bank into the turn. The layout is an ellipse, so curvature — and therefore the
            // bank that balances it — peaks at the ends of the major axis.
            double bank = Math.sin(angle * 2.0D) * 38.0D;

            nodes.add(new TrackNode(new Vec3(x, y, z), bank, null));
        }

        return new TrackSection(sectionId, nodes, true, null);
    }

    /** Sensible defaults: a 60-block-wide circuit with a 28-block drop. */
    public static TrackSection build(int sectionId, Vec3 origin) {
        return build(sectionId, origin, 60.0D, 28.0D);
    }
}
