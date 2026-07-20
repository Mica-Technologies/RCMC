package com.micatechnologies.minecraft.rcmc.track.element;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * A banked spiral: constant-radius turning about the entry {@code up} axis, climbing or descending at a
 * constant rate per revolution.
 *
 * <p><b>The path.</b> This is {@link Curve}'s construction with one addition: as the horizontal offset
 * from the axis rotates through angle {@code theta}, the whole point also rises {@code k*theta} along the
 * axis, where {@code k = heightChangePerRevolutionBlocks / (2*pi)}. Differentiating that position gives an
 * unnormalized tangent of exactly {@code radius * rotate(forward, axis, turnSign*theta) + k * axis} — see
 * the private {@link #rawTangent} — which has constant magnitude {@code sqrt(radius^2 + k^2)} for every
 * {@code theta}, because the rotated-forward term is always perpendicular to the axis term. That constant
 * speed is not a coincidence to route around; it means a fixed angular step already gives uniform
 * arc-length spacing, so unlike {@link VerticalLoop} this element needs no numerical arc-length solving at
 * all — a genuine helix, unlike a clothoid loop, has a closed form for everything except its own twist
 * (see below).</p>
 *
 * <p><b>Why the exit "up" needs real (sampled) parallel transport, unlike every planar element in this
 * package.</b> {@link Curve} and {@link AirtimeHill} both turn about a single fixed axis that coincides
 * with one of the frame's own basis vectors, so the whole frame's rotation reduces to one rigid rotation
 * about that axis and the exit frame is a closed-form read-off. A helix does not have that luxury: its
 * path has genuine torsion (curvature *and* climb, interacting), so there is no fixed axis the whole frame
 * rotates about — a real, physical helix (a spiral staircase handrail is the everyday example) twists as
 * you climb it even though its cross-section radius never changes. So the exit {@code up} is obtained by
 * sampling {@link #rawTangent} finely along the turn and running the same incremental Rodrigues transport
 * {@code ParallelTransportFrames} itself uses (re-derived locally as
 * {@link ElementGeometry#transportUp}), rather than guessed at from the endpoints alone.</p>
 *
 * <p><b>Bank</b> targets the same {@code atan(v^2/(r*g))} balance as {@link Curve}, eased in and back to
 * level at the ends — but eased over a fixed <em>angle</em> (default up to a quarter turn) rather than a
 * fixed fraction of the whole element. A multi-revolution helix easing over 25% of its <em>total</em> turn
 * would spend most of a three-turn helix just ramping bank up and down; capping the ease to a bounded
 * angle keeps the transition length sane regardless of how many revolutions follow.</p>
 */
public final class Helix implements TrackElement {

    /** Upper bound on how much of the helix's start/end is spent easing bank, regardless of how long the
     * helix is overall. For a short helix (less than double this) the two ease regions simply meet in the
     * middle with no flat hold, exactly like {@link Curve} on a short arc. */
    private static final double MAX_EASE_ANGLE_RADIANS = Math.toRadians(90.0D);

    /** How much finer than the output node spacing the transport-accumulation walk samples at. Node
     * spacing controls how many *authored* nodes come out; the accumulated exit roll converges
     * independently of that and benefits from finer sampling without bloating the node list. */
    private static final int TRANSPORT_OVERSAMPLE = 4;
    private static final int MAX_TRANSPORT_SAMPLES = 4000;

    private final double radiusBlocks;
    private final double totalTurnDegrees;
    private final double heightChangePerRevolutionBlocks;
    private final TurnDirection direction;
    private final double designSpeedBlocksPerSecond;
    private final double maxBankDegrees;
    private final double gravity;

    public Helix(double radiusBlocks, double totalTurnDegrees, double heightChangePerRevolutionBlocks,
                 TurnDirection direction, double designSpeedBlocksPerSecond, double maxBankDegrees,
                 double gravity) {
        ElementGeometry.requirePositive(radiusBlocks, "radiusBlocks");
        ElementGeometry.requirePositive(totalTurnDegrees, "totalTurnDegrees");
        ElementGeometry.requireFinite(heightChangePerRevolutionBlocks, "heightChangePerRevolutionBlocks");
        ElementGeometry.requirePositive(designSpeedBlocksPerSecond, "designSpeedBlocksPerSecond");
        ElementGeometry.requirePositive(maxBankDegrees, "maxBankDegrees");
        ElementGeometry.requirePositive(gravity, "gravity");
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }
        this.radiusBlocks = radiusBlocks;
        this.totalTurnDegrees = totalTurnDegrees;
        this.heightChangePerRevolutionBlocks = heightChangePerRevolutionBlocks;
        this.direction = direction;
        this.designSpeedBlocksPerSecond = designSpeedBlocksPerSecond;
        this.maxBankDegrees = maxBankDegrees;
        this.gravity = gravity;
    }

    public Helix(double radiusBlocks, double totalTurnDegrees, double heightChangePerRevolutionBlocks,
                 TurnDirection direction, double designSpeedBlocksPerSecond, double maxBankDegrees) {
        this(radiusBlocks, totalTurnDegrees, heightChangePerRevolutionBlocks, direction,
            designSpeedBlocksPerSecond, maxBankDegrees, ElementGeometry.DEFAULT_GRAVITY);
    }

    @Override
    public String id() {
        return "helix_" + direction.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String displayName() {
        return (direction == TurnDirection.LEFT ? "Left" : "Right") + " Helix";
    }

    @Override
    public ElementResult generate(ElementContext context) {
        double totalTurnRad = Math.toRadians(totalTurnDegrees);
        double turnSign = direction == TurnDirection.LEFT ? 1.0D : -1.0D;
        double k = heightChangePerRevolutionBlocks / (2.0D * Math.PI);

        Vec3 entryPos = context.entryFrame.position;
        Vec3 forward = context.entryFrame.forward;
        Vec3 axis = context.entryFrame.up;
        Vec3 right = context.entryFrame.right;

        Vec3 v0 = right.scale(radiusBlocks * turnSign);
        Vec3 axisBase = entryPos.subtract(v0);

        double speedPerRadian = Math.sqrt(radiusBlocks * radiusBlocks + k * k);
        int segments = ElementGeometry.segmentCount(speedPerRadian * totalTurnRad, context.nodeSpacing, 8);

        double targetBank = ElementGeometry.balancedBankDegrees(
            designSpeedBlocksPerSecond, radiusBlocks, gravity, maxBankDegrees) * turnSign;
        double easeAngle = Math.min(MAX_EASE_ANGLE_RADIANS, totalTurnRad / 2.0D);

        List<TrackNode> nodes = new ArrayList<>(segments);
        for (int i = 1; i <= segments; i++) {
            double theta = totalTurnRad * i / segments;
            Vec3 pos = axisBase.add(axis.scale(k * theta)).add(ElementGeometry.rotate(v0, axis, turnSign * theta));
            double bank = bankAt(theta, totalTurnRad, easeAngle, context.entryBankDegrees, targetBank);
            nodes.add(new TrackNode(pos, bank, null));
        }

        Vec3 exitTangent = rawTangent(forward, axis, turnSign, radiusBlocks, k, totalTurnRad).normalize();
        Vec3 exitUp = accumulateExitUp(forward, axis, turnSign, k, totalTurnRad);
        Vec3 exitPos = nodes.get(nodes.size() - 1).position();

        TrackFrame exitFrame = new TrackFrame(exitPos, exitTangent, exitUp);
        return new ElementResult(nodes, exitFrame, 0.0D);
    }

    /** Unnormalized tangent at turn angle {@code theta} — see the class javadoc for the derivation. */
    private static Vec3 rawTangent(Vec3 forward, Vec3 axis, double turnSign, double radius, double k,
                                    double theta) {
        Vec3 rotatedForward = ElementGeometry.rotate(forward, axis, turnSign * theta);
        return rotatedForward.scale(radius).add(axis.scale(k));
    }

    private static double bankAt(double theta, double totalTurnRad, double easeAngle,
                                  double entryBank, double targetBank) {
        if (theta < easeAngle) {
            return entryBank + (targetBank - entryBank) * ElementGeometry.smoothstep(theta / easeAngle);
        }
        double remaining = totalTurnRad - theta;
        if (remaining < easeAngle) {
            double local = 1.0D - remaining / easeAngle;
            return targetBank + (0.0D - targetBank) * ElementGeometry.smoothstep(local);
        }
        return targetBank;
    }

    /** Walks the helix at a finer resolution than the output nodes, accumulating parallel transport of
     * {@code up} exactly as {@code ParallelTransportFrames} would from the real generated geometry. */
    private Vec3 accumulateExitUp(Vec3 forward, Vec3 axis, double turnSign, double k,
                                   double totalTurnRad) {
        int samples = Math.min(MAX_TRANSPORT_SAMPLES,
            Math.max(8, (int) Math.ceil(totalTurnRad / Math.toRadians(90.0D)) * TRANSPORT_OVERSAMPLE * 8));

        Vec3 transportedUp = axis;
        Vec3 previousTangent = rawTangent(forward, axis, turnSign, radiusBlocks, k, 0.0D).normalize();
        for (int i = 1; i <= samples; i++) {
            double theta = totalTurnRad * i / samples;
            Vec3 tangent = rawTangent(forward, axis, turnSign, radiusBlocks, k, theta).normalize();
            transportedUp = ElementGeometry.transportUp(transportedUp, previousTangent, tangent);
            previousTangent = tangent;
        }
        return transportedUp;
    }
}
