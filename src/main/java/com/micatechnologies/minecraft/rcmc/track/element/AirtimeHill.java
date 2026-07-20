package com.micatechnologies.minecraft.rcmc.track.element;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * A crest of constant curvature, radius-tuned so a car at the design speed feels a target fraction of a
 * G at the top — the "stomach drop" of a well-built hill.
 *
 * <p><b>The physics.</b> At the top of a convex vertical arc of radius {@code r}, gravity alone supplies
 * {@code v^2 / r} of the centripetal acceleration needed to keep a car on a circular path; whatever is
 * left over is what the seat pushes back with. Apparent weight, as a fraction of normal weight, is
 * {@code k = 1 - v^2 / (r*g)}. Solving for the radius that hits a chosen {@code k} at a chosen design
 * speed: {@code r = v^2 / (g*(1 - k))} — see {@link #forDesignSpeed}. {@code k = 0} is the classic
 * "floater" airtime hill (momentarily weightless, the free-fall radius); more negative {@code k} pulls
 * harder ("ejector" air) at a tighter radius.</p>
 *
 * <p><b>Why a single circular arc, not a clothoid like {@link VerticalLoop}.</b> A loop's problem is a
 * curvature <em>step</em> at its entry — arriving from straight (zero curvature) track directly onto a
 * circle (constant nonzero curvature) is the violent spike a clothoid is built to avoid. This element does
 * not have that problem by construction: it does not claim to be the whole hill, only its crest. A
 * complete hill is composed by a builder as {@link Slope} (ease up to some pitch), {@code AirtimeHill} (a
 * constant-curvature cap at that pitch), {@link Slope} again (ease back down) — the curvature step is
 * pushed to the {@code Slope}/{@code AirtimeHill} joins, which is exactly where {@code Slope}'s own
 * smoothstep pitch easing already guarantees continuity. Building the clothoid tapering directly into this
 * element too would solve a problem it does not have at the cost of the same bisection machinery
 * {@code Slope} already pays for.</p>
 *
 * <p><b>The path.</b> A circular arc in the vertical plane spanned by the entry frame's {@code forward}
 * and {@code up}, rotating about the entry {@code right} axis — the same "rotate about the fixed plane
 * normal" construction {@link Curve} uses horizontally, just turned 90 degrees. Because {@code right} is
 * the rotation axis, it passes through the whole arc unchanged; {@code up} and {@code forward} both rotate
 * with it, which is how the exit frame is computed with no numerical transport.</p>
 *
 * <p>Bank is held at {@code entryBankDegrees} throughout — hills are a vertical-plane maneuver and, for
 * the common case of a level entry, that means no bank at all. A banked airtime hill (cresting while
 * rolled) is a real, if advanced, coaster element; this class does not build one, it just does not
 * actively undo whatever bank arrives, consistent with {@link Straight}.</p>
 */
public final class AirtimeHill implements TrackElement {

    private final double radiusBlocks;
    private final double crestArcDegrees;

    public AirtimeHill(double radiusBlocks, double crestArcDegrees) {
        ElementGeometry.requirePositive(radiusBlocks, "radiusBlocks");
        ElementGeometry.requirePositive(crestArcDegrees, "crestArcDegrees");
        this.radiusBlocks = radiusBlocks;
        this.crestArcDegrees = crestArcDegrees;
    }

    /**
     * @param designSpeedBlocksPerSecond speed at which the target G is actually hit
     * @param targetVerticalGFactor      apparent weight at the crest as a fraction of normal, {@code < 1}.
     *                                    {@code 0} is a floater ("weightless"); negative is stronger
     *                                    ("ejector") air.
     */
    public static AirtimeHill forDesignSpeed(double designSpeedBlocksPerSecond, double targetVerticalGFactor,
                                              double crestArcDegrees, double gravity) {
        ElementGeometry.requirePositive(designSpeedBlocksPerSecond, "designSpeedBlocksPerSecond");
        ElementGeometry.requireFinite(targetVerticalGFactor, "targetVerticalGFactor");
        ElementGeometry.requirePositive(gravity, "gravity");
        if (targetVerticalGFactor >= 1.0D) {
            throw new IllegalArgumentException(
                "targetVerticalGFactor must be < 1 to produce any airtime, got " + targetVerticalGFactor);
        }
        double radius = (designSpeedBlocksPerSecond * designSpeedBlocksPerSecond)
            / (gravity * (1.0D - targetVerticalGFactor));
        return new AirtimeHill(radius, crestArcDegrees);
    }

    public static AirtimeHill forDesignSpeed(double designSpeedBlocksPerSecond, double targetVerticalGFactor,
                                              double crestArcDegrees) {
        return forDesignSpeed(designSpeedBlocksPerSecond, targetVerticalGFactor, crestArcDegrees,
            ElementGeometry.DEFAULT_GRAVITY);
    }

    @Override
    public String id() {
        return "airtime_hill";
    }

    @Override
    public String displayName() {
        return "Airtime Hill";
    }

    @Override
    public ElementResult generate(ElementContext context) {
        double arcRad = Math.toRadians(crestArcDegrees);
        Vec3 entryPos = context.entryFrame.position;
        Vec3 forward = context.entryFrame.forward;
        Vec3 up = context.entryFrame.up;
        Vec3 right = context.entryFrame.right;

        // See the class javadoc for the sign: rotating `forward` about `right` by a NEGATIVE angle
        // pitches it downward, which is the cresting-over behaviour a hill's top needs (constant negative
        // curvature the whole arc, hence constant airtime G at the design speed throughout, not just at
        // one instant).
        Vec3 v0 = up.scale(radiusBlocks);
        Vec3 center = entryPos.subtract(v0);

        int segments = ElementGeometry.segmentCount(radiusBlocks * arcRad, context.nodeSpacing, 4);

        List<TrackNode> nodes = new ArrayList<>(segments);
        for (int i = 1; i <= segments; i++) {
            double theta = arcRad * i / segments;
            Vec3 pos = center.add(ElementGeometry.rotate(v0, right, -theta));
            nodes.add(new TrackNode(pos, context.entryBankDegrees, null));
        }

        Vec3 exitForward = ElementGeometry.rotate(forward, right, -arcRad).normalize();
        Vec3 exitUp = ElementGeometry.rotate(up, right, -arcRad);
        Vec3 exitPos = nodes.get(nodes.size() - 1).position();
        TrackFrame exitFrame = new TrackFrame(exitPos, exitForward, exitUp);
        return new ElementResult(nodes, exitFrame, context.entryBankDegrees);
    }
}
