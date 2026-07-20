package com.micatechnologies.minecraft.rcmc.track.element;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * A straight run of track: constant direction, constant bank.
 *
 * <p>Deliberately does not level itself out to zero bank. A straight is neutral — it neither introduces
 * curvature nor removes bank — so it simply carries {@code entryBankDegrees} through unchanged on every
 * node. That matters for composition: a {@link Curve} eases its own exit bank back to level by design (see
 * its javadoc), so {@code Curve} then {@code Straight} arrives level for free. But a straight used
 * mid-helix, say, should not silently un-bank the track underneath it; that decision belongs to whichever
 * element comes next, not to this one.</p>
 */
public final class Straight implements TrackElement {

    private final double lengthBlocks;

    public Straight(double lengthBlocks) {
        ElementGeometry.requirePositive(lengthBlocks, "lengthBlocks");
        this.lengthBlocks = lengthBlocks;
    }

    @Override
    public String id() {
        return "straight";
    }

    @Override
    public String displayName() {
        return "Straight";
    }

    @Override
    public ElementResult generate(ElementContext context) {
        int segments = ElementGeometry.segmentCount(lengthBlocks, context.nodeSpacing, 1);
        Vec3 entryPos = context.entryFrame.position;
        Vec3 forward = context.entryFrame.forward;

        List<TrackNode> nodes = new ArrayList<>(segments + 1);
        for (int i = 1; i <= segments; i++) {
            double d = lengthBlocks * i / segments;
            nodes.add(new TrackNode(entryPos.add(forward.scale(d)), context.entryBankDegrees, null));
        }

        // A straight line accumulates zero parallel-transport rotation by construction (this is the
        // headline example in ParallelTransportFrames' own javadoc: "straight track produces literally
        // zero rotation") — forward and up both pass through unchanged.
        TrackFrame exitFrame = new TrackFrame(
            entryPos.add(forward.scale(lengthBlocks)), forward, context.entryFrame.up);
        return new ElementResult(nodes, exitFrame, context.entryBankDegrees);
    }
}
