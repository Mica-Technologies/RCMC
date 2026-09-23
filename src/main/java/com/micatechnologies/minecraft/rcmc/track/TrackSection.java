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

    /** Authored colours. Immutable like the rest of the section; editing returns a new one. */
    private final TrackPalette palette;

    private final ArcLengthTable arcLength;
    private final ParallelTransportFrames frames;

    /** Distance along the section at which each node sits. */
    private final double[] nodeDistances;
    /** Roll rate at each node, radians per block — see {@link #bankRadiansAt}. */
    private final double[] bankSlopes;

    /**
     * Roll, in radians, that parallel transport accumulates over a full lap of a closed circuit.
     * Zero for open sections. See {@link #rollCorrectionAt(double)}.
     */
    private final double rollResidual;

    /**
     * An open section's end handles: the control points just beyond its first and last nodes, and
     * the up vector its frames start from. {@code null} for the defaults — reflected handles and
     * world-up — which is what every section built node by node has.
     *
     * <p>Set when a section is split from a longer one, so each half keeps the curve, and the roll,
     * it had as part of the whole: the halves meet with no bend and no twist. Always {@code null}
     * on a circuit, whose ends are each other.</p>
     */
    private final Vec3 leadIn;
    private final Vec3 leadOut;
    private final Vec3 startUp;

    public TrackSection(int id, List<TrackNode> nodes, boolean closed, String styleId) {
        this(id, nodes, closed, styleId, TrackPalette.DEFAULT);
    }

    public TrackSection(int id, List<TrackNode> nodes, boolean closed, String styleId,
                        TrackPalette palette) {
        this(id, nodes, closed, styleId, palette, null, null, null);
    }

    /** With end handles; see {@link #leadIn()}. Ignored on a circuit. */
    public TrackSection(int id, List<TrackNode> nodes, boolean closed, String styleId,
                        TrackPalette palette, Vec3 leadIn, Vec3 leadOut, Vec3 startUp) {
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
        this.palette = palette == null ? TrackPalette.DEFAULT : palette;
        this.leadIn = closed ? null : leadIn;
        this.leadOut = closed ? null : leadOut;
        this.startUp = closed ? null : startUp;

        List<Vec3> positions = new ArrayList<>(nodes.size());
        for (TrackNode node : this.nodes) {
            positions.add(node.position());
        }
        CatmullRomSpline spline = closed
            ? CatmullRomSpline.closed(positions)
            : CatmullRomSpline.withEndHandles(positions, this.leadIn, this.leadOut);
        this.arcLength = new ArcLengthTable(spline);

        int sampleCount = (int) Math.max(MIN_FRAME_SAMPLES,
            Math.min(MAX_FRAME_SAMPLES, arcLength.totalLength() * FRAME_SAMPLES_PER_BLOCK));
        this.frames = new ParallelTransportFrames(arcLength, sampleCount, this.startUp);

        // Node i sits at u = i / segmentCount. A closed spline has one segment per node (the
        // last spans node n-1 back to node 0); an open one has n-1.
        int segments = spline.segmentCount();
        this.nodeDistances = new double[this.nodes.size()];
        for (int i = 0; i < this.nodes.size(); i++) {
            nodeDistances[i] = arcLength.distanceAtParam((double) i / segments);
        }
        this.bankSlopes = bankSlopes(this.nodes, nodeDistances, arcLength.totalLength(), closed);

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
     * Authored bank angle in radians at distance {@code s}: a monotone cubic through the nodes.
     *
     * <p>Not linear: that steps the roll rate at every node, a jolt a rider feels though the angle
     * itself is continuous. And not a smoothstep per span, which is what this was: its rate is zero
     * at both ends of every span, so a roll spread over several nodes stops and starts again at each
     * one. On the rail that is invisible. At the rider's heart, most of a block above it, every stop
     * is a sideways jerk — measured at ±2 g on an ordinary banked curve's roll-in once G was taken
     * at the heart (see {@code physics.Heartline}), and fatal to a heartline roll.</p>
     *
     * <p>So each node carries a roll rate ({@link #bankSlopes}) and each span is a cubic Hermite
     * between them: rate as well as angle is continuous, and the roll runs straight through the
     * nodes. The rates are Fritsch–Carlson's, which keep the curve monotone between nodes — it
     * never overshoots a node's bank, and where the bank holds steady the rate is zero, so a
     * constant-bank stretch stays exactly constant.</p>
     */
    public double bankRadiansAt(double s) {
        int count = nodes.size();
        double total = arcLength.totalLength();
        double distance = clampDistance(s);

        // Locate the span containing `distance`. Spans are node i -> node i+1, plus a wrap span
        // from the last node back to the first on a closed circuit.
        for (int i = 0; i < count - 1; i++) {
            if (distance <= nodeDistances[i + 1]) {
                return hermite(nodes.get(i).bankRadians(), nodes.get(i + 1).bankRadians(),
                    bankSlopes[i], bankSlopes[i + 1], nodeDistances[i], nodeDistances[i + 1], distance);
            }
        }
        if (closed) {
            return hermite(nodes.get(count - 1).bankRadians(), nodes.get(0).bankRadians(),
                bankSlopes[count - 1], bankSlopes[0], nodeDistances[count - 1], total, distance);
        }
        return nodes.get(count - 1).bankRadians();
    }

    private static double hermite(double from, double to, double fromSlope, double toSlope,
                                  double spanStart, double spanEnd, double at) {
        double span = spanEnd - spanStart;
        if (span <= 0.0D) {
            return to;
        }
        double t = Math.max(0.0D, Math.min(1.0D, (at - spanStart) / span));
        double t2 = t * t;
        double t3 = t2 * t;
        return (2.0D * t3 - 3.0D * t2 + 1.0D) * from + (t3 - 2.0D * t2 + t) * span * fromSlope
            + (-2.0D * t3 + 3.0D * t2) * to + (t3 - t2) * span * toSlope;
    }

    /**
     * The roll rate at each node, radians per block: Fritsch–Carlson (Fritsch–Butland form), which
     * keeps the interpolation monotone. Zero where the bank peaks, dips or holds steady either side
     * of a node, and at an open section's ends, so the track rolls in from rest and out to rest.
     */
    private static double[] bankSlopes(List<TrackNode> nodes, double[] distances, double total, boolean closed) {
        int count = nodes.size();
        double[] slopes = new double[count];
        for (int i = 0; i < count; i++) {
            boolean first = i == 0;
            boolean last = i == count - 1;
            if (!closed && (first || last)) {
                continue;
            }
            int before = first ? count - 1 : i - 1;
            int after = last ? 0 : i + 1;
            double gapBefore = first ? total - distances[before] + distances[i] : distances[i] - distances[before];
            double gapAfter = last ? total - distances[i] + distances[after] : distances[after] - distances[i];
            if (gapBefore <= 0.0D || gapAfter <= 0.0D) {
                continue;
            }
            double into = (nodes.get(i).bankRadians() - nodes.get(before).bankRadians()) / gapBefore;
            double outOf = (nodes.get(after).bankRadians() - nodes.get(i).bankRadians()) / gapAfter;
            if (into * outOf <= 0.0D) {
                continue;
            }
            slopes[i] = 3.0D * (gapBefore + gapAfter)
                / ((2.0D * gapAfter + gapBefore) / into + (gapAfter + 2.0D * gapBefore) / outOf);
        }
        return slopes;
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

    /**
     * The frame at {@code s} before the authored bank is applied: transported, and corrected for a
     * circuit's residual. What a section continuing this one must start its own frames from.
     */
    public TrackFrame unbankedFrameAtDistance(double s) {
        double distance = clampDistance(s);
        TrackFrame base = frames.frameAtDistance(distance);
        double roll = rollCorrectionAt(distance);
        return roll == 0.0D ? base : base.withBank(roll);
    }

    /** The control point before the first node, or {@code null} for a reflected one. */
    public Vec3 leadIn() {
        return leadIn;
    }

    /** The control point after the last node, or {@code null} for a reflected one. */
    public Vec3 leadOut() {
        return leadOut;
    }

    /** The up vector the frames start from, or {@code null} for world-up. */
    public Vec3 startUp() {
        return startUp;
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

    public TrackPalette palette() {
        return palette;
    }

    /** A copy painted differently. Geometry is rebuilt, which is wasteful but keeps sections
     *  genuinely immutable; recolouring is rare next to anything that reads them. */
    public TrackSection withPalette(TrackPalette newPalette) {
        return new TrackSection(id, nodes, closed, styleId, newPalette, leadIn, leadOut, startUp);
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

    // An end handle belongs to the end node it was taken beside: an edit that replaces that node
    // with a new end (a node added past it, or the end node removed) drops the handle, and the new
    // end gets the usual reflected one.

    public TrackSection withNode(int index, TrackNode replacement) {
        List<TrackNode> edited = new ArrayList<>(nodes);
        edited.set(index, replacement);
        return new TrackSection(id, edited, closed, styleId, palette, leadIn, leadOut, startUp);
    }

    public TrackSection withNodeInserted(int index, TrackNode inserted) {
        List<TrackNode> edited = new ArrayList<>(nodes);
        edited.add(index, inserted);
        boolean newFirst = index == 0;
        boolean newLast = index == nodes.size();
        return new TrackSection(id, edited, closed, styleId, palette, newFirst ? null : leadIn,
            newLast ? null : leadOut, newFirst ? null : startUp);
    }

    public TrackSection withNodeRemoved(int index) {
        List<TrackNode> edited = new ArrayList<>(nodes);
        edited.remove(index);
        boolean first = index == 0;
        boolean last = index == nodes.size() - 1;
        return new TrackSection(id, edited, closed, styleId, palette, first ? null : leadIn,
            last ? null : leadOut, first ? null : startUp);
    }

    public TrackSection withNodeAppended(TrackNode appended) {
        List<TrackNode> edited = new ArrayList<>(nodes);
        edited.add(appended);
        return new TrackSection(id, edited, closed, styleId, palette, leadIn, null, startUp);
    }

    public TrackSection withClosed(boolean nowClosed) {
        return nowClosed == closed ? this : new TrackSection(id, nodes, nowClosed, styleId, palette);
    }

    public TrackSection withStyle(String newStyleId) {
        return new TrackSection(id, nodes, closed, newStyleId, palette, leadIn, leadOut, startUp);
    }

    /** Reverses the direction of travel. Bank angles negate, since right becomes left. */
    public TrackSection reversed() {
        List<TrackNode> flipped = new ArrayList<>(nodes.size());
        for (int i = nodes.size() - 1; i >= 0; i--) {
            TrackNode node = nodes.get(i);
            flipped.add(node.withBank(-node.bankDegrees()));
        }
        if (closed) {
            return new TrackSection(id, flipped, true, styleId, palette);
        }
        // The same curve run the other way: the handles swap ends, and the frames start from the
        // one this section's had reached at its far end, so a car's roll is unchanged anywhere.
        return new TrackSection(id, flipped, false, styleId, palette, leadOut, leadIn,
            unbankedFrameAtDistance(totalLength()).up);
    }

    @Override
    public String toString() {
        return "TrackSection{id=" + id + ", nodes=" + nodes.size() + ", closed=" + closed
            + ", length=" + String.format("%.2f", totalLength()) + '}';
    }
}
