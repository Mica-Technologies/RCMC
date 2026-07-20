package com.micatechnologies.minecraft.rcmc.track.element;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * A barrel roll: one full 360-degree roll about the direction of travel, spread over a path that also
 * nudges sideways — a corkscrew displaces the rider laterally, it does not just spin them in place.
 *
 * <p><b>Two independent things, deliberately kept independent.</b> A corkscrew's <em>roll</em> (how the
 * car is oriented) and its <em>centerline path</em> (where the car is) are modelled completely separately
 * here:</p>
 * <ul>
 *   <li>The roll is authored bank, sweeping from {@code entryBankDegrees} to
 *       {@code entryBankDegrees +/- 360} via {@link ElementGeometry#smoothstep} of the element's parameter
 *       {@code t}. Because {@code TrackNode.bankDegrees} is an unbounded scalar, not wrapped to
 *       {@code [0, 360)} anywhere in {@code TrackNode}/{@code TrackSection}, feeding it values that sweep
 *       past 360 works with the existing bank machinery unmodified — no special "this is a roll, not a
 *       bank" case is needed downstream, and two corkscrews chained back to back keep spinning the same
 *       way instead of snapping back through zero at the join.</li>
 *   <li>The path is a gentle sideways S-curve: {@code lateralOffsetBlocks * smoothstep(t)} along the entry
 *       {@code right} direction, added to uniform forward travel. Zero lateral velocity at both ends
 *       (smoothstep's derivative vanishes there) blends cleanly into straight track before and after.</li>
 * </ul>
 *
 * <p><b>Why not a true 3D spiral (a helix with its axis along {@code forward} instead of {@code up}).</b>
 * That is a more literal reading of "barrel roll" and was considered, but it reintroduces exactly the
 * curvature-step problem {@link VerticalLoop} exists to solve — a spiral path curving away from straight
 * track has the same zero-to-nonzero curvature jump at the join a circular loop does, and fixing it
 * properly means clothoid-tapering the lateral path too. The roll is what makes a corkscrew a corkscrew;
 * the lateral kink is a secondary, cosmetic part of the shape. Decoupling them gets a corkscrew that rolls
 * correctly and blends smoothly, without paying for machinery whose main job (in {@code VerticalLoop}) is
 * solving a problem this element does not really have — a mild sideways wiggle, unlike a full loop, does
 * not need to fight a large curvature step to begin with.</p>
 *
 * <p><b>The path stays in one plane</b> — spanned by the entry {@code forward} and {@code right}, i.e.
 * normal to entry {@code up} — exactly like {@link Curve}. So, just as in {@code Curve}, {@code up}
 * transports through the centerline unchanged; the roll the rider actually feels comes entirely from the
 * authored bank sweep, not from any change in the transported frame. {@link ElementResult#exitFrame}
 * therefore reports the un-banked exit frame with {@code up == entryUp}, and the full {@code +/-360}
 * degrees lives in {@link ElementResult#exitBankDegrees}, consistent with how every other element in this
 * package splits geometry from authored bank.</p>
 */
public final class Corkscrew implements TrackElement {

    private static final int SEGMENTS_MIN = 8;

    private final double lengthBlocks;
    private final double lateralOffsetBlocks;
    private final RollDirection rollDirection;

    public Corkscrew(double lengthBlocks, double lateralOffsetBlocks, RollDirection rollDirection) {
        ElementGeometry.requirePositive(lengthBlocks, "lengthBlocks");
        ElementGeometry.requireFinite(lateralOffsetBlocks, "lateralOffsetBlocks");
        if (rollDirection == null) {
            throw new IllegalArgumentException("rollDirection must not be null");
        }
        this.lengthBlocks = lengthBlocks;
        this.lateralOffsetBlocks = lateralOffsetBlocks;
        this.rollDirection = rollDirection;
    }

    @Override
    public String id() {
        return "corkscrew";
    }

    @Override
    public String displayName() {
        return "Corkscrew";
    }

    @Override
    public ElementResult generate(ElementContext context) {
        Vec3 entryPos = context.entryFrame.position;
        Vec3 forward = context.entryFrame.forward;
        Vec3 up = context.entryFrame.up;
        Vec3 right = context.entryFrame.right;
        double rollSign = rollDirection == RollDirection.POSITIVE ? 1.0D : -1.0D;

        // `length` is treated as forward travel distance, not true arc length — the lateral wiggle adds a
        // small amount of extra path length beyond it, exactly the same documented approximation Slope
        // makes treating its `length` as horizontal run rather than solving for exact arc length.
        int segments = ElementGeometry.segmentCount(lengthBlocks, context.nodeSpacing, SEGMENTS_MIN);

        List<TrackNode> nodes = new ArrayList<>(segments);
        for (int i = 1; i <= segments; i++) {
            double t = (double) i / segments;
            Vec3 pos = entryPos
                .add(forward.scale(lengthBlocks * t))
                .add(right.scale(lateralOffsetBlocks * ElementGeometry.smoothstep(t)));
            double bank = context.entryBankDegrees + rollSign * 360.0D * ElementGeometry.smoothstep(t);
            nodes.add(new TrackNode(pos, bank, null));
        }

        // The centerline never leaves the plane spanned by (forward, right), i.e. it is normal to `up`
        // throughout, so — exactly as in Curve — `up` is a fixed point of the path's own rotation and
        // passes through unchanged. The roll the rider feels is carried entirely by the bank sweep above.
        TrackFrame exitFrame = new TrackFrame(nodes.get(nodes.size() - 1).position(), forward, up);
        double exitBank = context.entryBankDegrees + rollSign * 360.0D;
        return new ElementResult(nodes, exitFrame, exitBank);
    }
}
