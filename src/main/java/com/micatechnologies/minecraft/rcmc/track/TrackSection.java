package com.micatechnologies.minecraft.rcmc.track;

import com.micatechnologies.minecraft.rcmc.track.math.ArcLengthTable;
import com.micatechnologies.minecraft.rcmc.track.math.CatmullRomSpline;
import com.micatechnologies.minecraft.rcmc.track.math.ParallelTransportFrames;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A continuous run of track: an ordered list of {@link TrackNode}s plus all the geometry derived
 * from them.
 *
 * <p>Immutable, and the derived geometry — spline, arc-length table, transported frames, node
 * distances, closed-circuit roll residual — is built <b>once</b> in the constructor. Building it
 * eagerly rather than lazily is deliberate: the alternative is a mutable cache read from both the
 * server tick thread and the client render thread, which is a data race for no benefit. Editing
 * produces a new section (see {@link #withNode}), so a cached table can never go stale.</p>
 *
 * <p><b>Construction is not cheap</b> — it samples the spline thousands of times. Build sections
 * when track is created or edited, never per tick.</p>
 *
 * <p>Pure Java by design: no Minecraft types anywhere in this class or its dependencies, so the
 * whole geometry pipeline is unit-testable on a bare JVM. Persistence lives in
 * {@code track.storage}, which is the only layer that knows about NBT.</p>
 */
public final class TrackSection {

    /** Frame samples per block of track. Half-block spacing is well inside rider perception. */
    private static final double FRAME_SAMPLES_PER_BLOCK = 2.0D;

    private static final int MIN_FRAME_SAMPLES = 16;
    private static final int MAX_FRAME_SAMPLES = 8192;

    private final int id;
    private final List<TrackNode> nodes;
    private final boolean closed;
    private final String styleId;

    private final ArcLengthTable arcLength;
    private final ParallelTransportFrames frames;

    /** Distance along the section at which each node sits. */
    private final double[] nodeDistances;

    /**
     * Roll, in radians, that parallel transport accumulates over a full lap of a closed circuit.
     * Zero for open sections. See {@link #rollCorrectionAt(double)}.
     */
    private final double rollResidual;

    public TrackSection(int id, List<TrackNode> nodes, boolean closed, String styleId) {
        if (nodes == null) {
            throw new IllegalArgumentException("nodes must not be null");
        }
        int minimum = closed ? 3 : 2;
        if (nodes.size() < minimum) {
            throw new IllegalArgumentException(
                (closed ? "A closed circuit" : "An open section") + " needs at least " + minimum
                    + " nodes, got " + nodes.size());
        }
        this.id = id;
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.closed = closed;
        this.styleId = styleId;

        List<Vec3> positions = new ArrayList<>(nodes.size());
        for (TrackNode node : this.nodes) {
            positions.add(node.position());
        }
        CatmullRomSpline spline = closed
            ? CatmullRomSpline.closed(positions)
            : CatmullRomSpline.withPhantomEndpoints(positions);
        this.arcLength = new ArcLengthTable(spline);

        int sampleCount = (int) Math.max(MIN_FRAME_SAMPLES,
            Math.min(MAX_FRAME_SAMPLES, arcLength.totalLength() * FRAME_SAMPLES_PER_BLOCK));
        this.frames = new ParallelTransportFrames(arcLength, sampleCount);

        // Node i sits at u = i / segmentCount. A closed spline has one segment per node (the
        // last spans node n-1 back to node 0); an open one has n-1.
        int segments = spline.segmentCount();
        this.nodeDistances = new double[this.nodes.size()];
        for (int i = 0; i < this.nodes.size(); i++) {
            nodeDistances[i] = arcLength.distanceAtParam((double) i / segments);
        }

        this.rollResidual = closed ? computeRollResidual(frames) : 0.0D;
    }

    /**
     * Roll that parallel transport picks up over one lap.
     *
     * <p>Transport is not periodic: carrying a frame around a closed curve and back to the start
     * generally returns it rotated about the tangent by the curve's total torsion — a real
     * geometric quantity, the same effect as a Foucault pendulum's precession. Untreated it shows
     * as a visible seam where a car crosses the start/finish line.</p>
     *
     * <p>Measured as the signed angle from the starting frame's {@code up} to the returning
     * frame's {@code up}, about their shared tangent.</p>
     */
    private static double computeRollResidual(ParallelTransportFrames frames) {
        TrackFrame start = frames.frameAtDistance(0.0D);
        TrackFrame end = frames.frameAtDistance(frames.totalLength());
        Vec3 axis = start.forward;
        double sin = start.up.cross(end.up).dot(axis);
        double cos = start.up.dot(end.up);
        return Math.atan2(sin, cos);
    }

    /**
     * Correction applied to cancel the closed-circuit roll residual, distributed linearly so the
     * frame arrives back at the start exactly as it left.
     *
     * <p>Linear distribution spreads the error evenly rather than dumping it at the seam. It is
     * not free — it introduces a small constant twist rate around the whole circuit — but that is
     * imperceptible spread over hundreds of blocks, whereas the alternative is a visible snap at
     * one point.</p>
     */
    private double rollCorrectionAt(double distance) {
        if (rollResidual == 0.0D) {
            return 0.0D;
        }
        double total = arcLength.totalLength();
        return total == 0.0D ? 0.0D : -rollResidual * (distance / total);
    }

    /**
     * Authored bank angle in radians at distance {@code s}, cubically eased between the
     * bracketing nodes.
     *
     * <p>Smoothstep ({@code t²(3−2t)}) rather than linear interpolation: its derivative is zero at
     * both ends, so bank rate is continuous <em>across</em> nodes. Linear interpolation would step
     * the roll rate at every node, which a rider feels as a jolt even though the bank angle itself
     * is continuous.</p>
     */
    public double bankRadiansAt(double s) {
        int count = nodes.size();
        double total = arcLength.totalLength();
        double distance = clampDistance(s);

        // Locate the span containing `distance`. Spans are node i -> node i+1, plus a wrap span
        // from the last node back to the first on a closed circuit.
        for (int i = 0; i < count - 1; i++) {
            if (distance <= nodeDistances[i + 1]) {
                return ease(nodes.get(i).bankRadians(), nodes.get(i + 1).bankRadians(),
                    nodeDistances[i], nodeDistances[i + 1], distance);
            }
        }
        if (closed) {
            return ease(nodes.get(count - 1).bankRadians(), nodes.get(0).bankRadians(),
                nodeDistances[count - 1], total, distance);
        }
        return nodes.get(count - 1).bankRadians();
    }

    private static double ease(double from, double to, double spanStart, double spanEnd, double at) {
        double span = spanEnd - spanStart;
        if (span <= 0.0D) {
            return to;
        }
        double t = Math.max(0.0D, Math.min(1.0D, (at - spanStart) / span));
        double smooth = t * t * (3.0D - 2.0D * t);
        return from + (to - from) * smooth;
    }

    /**
     * The full orientation of a car at distance {@code s}: transported frame, corrected for the
     * closed-circuit residual, then rolled by the authored bank.
     *
     * <p>This is the method everything downstream calls — renderer, rider camera, and the physics
     * layer projecting gravity onto {@code forward}.</p>
     */
    public TrackFrame frameAtDistance(double s) {
        double distance = clampDistance(s);
        TrackFrame base = frames.frameAtDistance(distance);
        double roll = rollCorrectionAt(distance) + bankRadiansAt(distance);
        return roll == 0.0D ? base : base.withBank(roll);
    }

    /** Centreline position at distance {@code s}. */
    public Vec3 positionAtDistance(double s) {
        return arcLength.positionAtDistance(clampDistance(s));
    }

    /** Unit direction of travel at distance {@code s}. */
    public Vec3 tangentAtDistance(double s) {
        return arcLength.tangentAtDistance(clampDistance(s));
    }

    /**
     * Wraps {@code s} into the section on a closed circuit; clamps it on an open one.
     *
     * <p>The difference is the whole point of the {@code closed} flag: a train running past the
     * end of a circuit should continue onto the next lap, whereas one running off the end of an
     * unconnected open section should stop there and be visible as a fault rather than silently
     * teleporting back to the start.</p>
     */
    public double clampDistance(double s) {
        double total = arcLength.totalLength();
        if (total <= 0.0D) {
            return 0.0D;
        }
        if (closed) {
            double wrapped = s % total;
            return wrapped < 0.0D ? wrapped + total : wrapped;
        }
        return Math.max(0.0D, Math.min(total, s));
    }

    /**
     * The endpoint of this section at {@code end}, in world coordinates.
     *
     * <p>Meaningless for a closed circuit, which has no ends — {@link TrackNetwork} refuses to
     * join one for that reason.</p>
     */
    public Vec3 endpointAt(TrackNetwork.End end) {
        return positionAtDistance(end == TrackNetwork.End.START ? 0.0D : totalLength());
    }

    /**
     * Unit vector pointing <em>out of</em> the section at {@code end} — the direction a train is
     * travelling as it leaves.
     *
     * <p>At {@link TrackNetwork.End#END} that is simply the tangent. At
     * {@link TrackNetwork.End#START} the train is travelling backwards relative to the distance
     * axis, so the outward direction is the negated tangent. Getting this sign wrong makes every
     * join at a section's start appear to be a 180° kink.</p>
     */
    public Vec3 exitDirectionAt(TrackNetwork.End end) {
        if (end == TrackNetwork.End.START) {
            return tangentAtDistance(0.0D).scale(-1.0D);
        }
        return tangentAtDistance(totalLength());
    }

    public int id() {
        return id;
    }

    public List<TrackNode> nodes() {
        return nodes;
    }

    public boolean isClosed() {
        return closed;
    }

    /** Default style for this section; individual nodes may override from their position onward. */
    public String styleId() {
        return styleId;
    }

    public double totalLength() {
        return arcLength.totalLength();
    }

    /** Distance along the section at which node {@code index} sits. */
    public double nodeDistance(int index) {
        return nodeDistances[index];
    }

    /** Roll accumulated by transport over one lap, in radians. Zero for open sections. */
    public double rollResidual() {
        return rollResidual;
    }

    /** Style in effect at distance {@code s}, honouring per-node overrides. */
    public String styleAtDistance(double s) {
        double distance = clampDistance(s);
        String effective = styleId;
        for (int i = 0; i < nodes.size(); i++) {
            if (nodeDistances[i] > distance) {
                break;
            }
            if (nodes.get(i).styleId() != null) {
                effective = nodes.get(i).styleId();
            }
        }
        return effective;
    }

    // ---- editing: each returns a new section, rebuilding derived geometry ----

    public TrackSection withNode(int index, TrackNode replacement) {
        List<TrackNode> edited = new ArrayList<>(nodes);
        edited.set(index, replacement);
        return new TrackSection(id, edited, closed, styleId);
    }

    public TrackSection withNodeInserted(int index, TrackNode inserted) {
        List<TrackNode> edited = new ArrayList<>(nodes);
        edited.add(index, inserted);
        return new TrackSection(id, edited, closed, styleId);
    }

    public TrackSection withNodeRemoved(int index) {
        List<TrackNode> edited = new ArrayList<>(nodes);
        edited.remove(index);
        return new TrackSection(id, edited, closed, styleId);
    }

    public TrackSection withNodeAppended(TrackNode appended) {
        List<TrackNode> edited = new ArrayList<>(nodes);
        edited.add(appended);
        return new TrackSection(id, edited, closed, styleId);
    }

    public TrackSection withClosed(boolean nowClosed) {
        return nowClosed == closed ? this : new TrackSection(id, nodes, nowClosed, styleId);
    }

    public TrackSection withStyle(String newStyleId) {
        return new TrackSection(id, nodes, closed, newStyleId);
    }

    /** Reverses the direction of travel. Bank angles negate, since right becomes left. */
    public TrackSection reversed() {
        List<TrackNode> flipped = new ArrayList<>(nodes.size());
        for (int i = nodes.size() - 1; i >= 0; i--) {
            TrackNode node = nodes.get(i);
            flipped.add(node.withBank(-node.bankDegrees()));
        }
        return new TrackSection(id, flipped, closed, styleId);
    }

    @Override
    public String toString() {
        return "TrackSection{id=" + id + ", nodes=" + nodes.size() + ", closed=" + closed
            + ", length=" + String.format("%.2f", totalLength()) + '}';
    }
}
