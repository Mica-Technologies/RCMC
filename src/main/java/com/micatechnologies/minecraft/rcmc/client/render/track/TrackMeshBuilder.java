package com.micatechnologies.minecraft.rcmc.client.render.track;

import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Sweeps a fixed 2D cross-section (two rails, a support spine, and cross-ties) along a
 * {@link TrackSection}'s frames to build a {@link TrackMesh}.
 *
 * <p><b>Pure by design.</b> Like {@code track.math}, this class touches no Minecraft type. That
 * is not required by the mod's side-discipline rule — everything under
 * {@code client.render.track} is already client-only and may import freely — but it is what makes
 * {@link #build} unit-testable without a game instance, which the geometry (adaptive tessellation
 * especially) badly needs: getting the curvature-based sampling wrong would silently under- or
 * over-tessellate rather than throw. World-light baking, which does need a {@code World}, is
 * deliberately kept out of this class and done by {@link TrackRenderer} as a second pass over the
 * quads returned here.</p>
 *
 * <p><b>Adaptive tessellation.</b> {@link #ringDistances} walks the section by arc length,
 * estimating curvature from the angle between the tangent at the current ring and the tangent at
 * a trial next ring: a wide angle over the trial step means tight curvature, so the step is
 * halved and retried; a narrow angle means the step can grow for next time. This is deliberately
 * not uniform-in-{@code s} sampling — a tight helix needs rings every few tenths of a block to
 * read as round, and uniform sampling would have to use that spacing over an entire circuit's
 * straight runs too, wasting orders of magnitude more geometry than the straights need.</p>
 *
 * <p><b>Cross-ties sit at fixed arc-length intervals</b> ({@link #TIE_SPACING}), independent of
 * the (non-uniform) ring list above: ties spaced by ring index would bunch up through curves and
 * spread out on straights, backwards from how a real sleeper-spaced track reads.</p>
 *
 * <p><b>Cross-section and banking.</b> Rails sit at {@code ±HALF_GAUGE} along the frame's
 * {@code right}, at the frame's own height along {@code up}. Sweeping through {@code right}/
 * {@code up} rather than through fixed world axes is what makes banking show up in the mesh at
 * all: a banked frame's {@code right} is not horizontal, so the two rails come out at different
 * heights automatically, with no curve-specific code here at all — the bank is entirely baked into
 * the frame by {@link TrackSection#frameAtDistance}.</p>
 *
 * <p><b>Closed circuits close for free.</b> {@link #ringDistances} always includes both
 * {@code s = 0} and {@code s = totalLength()}. For a closed section those are the same point with
 * the same frame — {@code TrackSection} already distributes the parallel-transport roll residual
 * so the frame returning to {@code s = totalLength()} matches the one that left {@code s = 0} (see
 * {@code TrackSection.rollCorrectionAt}) — so the last ring-to-ring quad row sweeps seamlessly back
 * onto the first ring's corners without this class doing anything wrap-specific.</p>
 */
public final class TrackMeshBuilder {

    // ---- adaptive ring sampling ----

    /** Largest gap between rings on straight track, in blocks of arc length. */
    private static final double MAX_RING_SPACING = 3.0D;

    /**
     * Smallest gap between rings, in blocks — a floor so a pathologically tight curve cannot
     * blow the ring count up without bound.
     */
    private static final double MIN_RING_SPACING = 0.25D;

    /**
     * Largest tangent-direction change tolerated between consecutive rings before the step is
     * halved and retried. Smaller means smoother-looking banking through tight curves at the cost
     * of more geometry.
     */
    private static final double MAX_RING_ANGLE = Math.toRadians(5.0D);

    /**
     * Safety bound on ring count, mirroring {@code TrackSection.MAX_FRAME_SAMPLES}: protects
     * against runaway tessellation on a degenerate section rather than hanging the client.
     */
    private static final int MAX_RINGS = 8000;

    // ---- cross-section, in blocks, in the frame's local (right, up) plane ----

    private static final double HALF_GAUGE = 0.55D;
    private static final double RAIL_HALF_WIDTH = 0.05D;
    private static final double RAIL_HALF_HEIGHT = 0.05D;

    private static final double SPINE_CENTER_U = -0.35D;
    private static final double SPINE_HALF_WIDTH = 0.08D;
    private static final double SPINE_HALF_HEIGHT = 0.12D;

    private static final double TIE_HALF_LENGTH_R = HALF_GAUGE + 0.15D;

    /**
     * Ties span vertically from the underside of the rails down to the top of the spine, bridging
     * them into one structure.
     *
     * <p>They used to be thin plates floating at {@code u = -0.05}, leaving a visible gap of open
     * air between the rails and the spine below — the track read as two unrelated ribbons rather
     * than as a single piece of steelwork. Deriving the extent from the rail and spine geometry
     * rather than hard-coding it means the gap cannot reopen if either is retuned.</p>
     *
     * <p>Real box-spine coaster track is built exactly this way: the running rails are carried on
     * webbing off a central spine, and the webbing is what you see between the ties.</p>
     */
    private static final double TIE_TOP_U = -RAIL_HALF_HEIGHT;
    private static final double TIE_BOTTOM_U = SPINE_CENTER_U + SPINE_HALF_HEIGHT;
    private static final double TIE_CENTER_U = (TIE_TOP_U + TIE_BOTTOM_U) * 0.5D;
    private static final double TIE_HALF_HEIGHT = Math.abs(TIE_TOP_U - TIE_BOTTOM_U) * 0.5D;

    /** Chain link spacing along the lift, in blocks. Close enough to read as links, not a stripe. */
    private static final double CHAIN_LINK_SPACING = 0.5D;

    private static final double CHAIN_LINK_LENGTH = 0.34D;
    private static final double CHAIN_LINK_WIDTH = 0.16D;

    /** Just above the spine and below the rail tops, so cars pass over without z-fighting. */
    private static final double CHAIN_HEIGHT = -0.16D;

    /** Dark oiled steel — deliberately darker than the rails so the lift reads at a distance. */
    private static final float[] CHAIN_COLOR = { 0.18F, 0.17F, 0.15F };

    /** Support columns: slim enough to read as steelwork, not as a wall. */
    private static final double SUPPORT_HALF_WIDTH = 0.16D;

    /**
     * Below this height above ground a support is skipped. Track running along the floor does not
     * need holding up, and a stub column at every sample there would be visual noise.
     */
    private static final double MIN_SUPPORT_HEIGHT = 2.0D;

    private static final float[] SUPPORT_COLOR = { 0.42F, 0.43F, 0.46F };
    private static final double TIE_HALF_THICKNESS_S = 0.08D;

    /** Arc-length spacing between cross-ties, in blocks. See class javadoc. */
    private static final double TIE_SPACING = 1.5D;

    // ---- material colors (flat, pre-shading — no track-style system exists yet to source
    // these from; TrackSection.styleAtDistance is not consulted here. Revisit once track styles
    // (docs/design/TRACK_GEOMETRY.md, "what is not here yet") land. ----

    private static final float[] RAIL_COLOR = {0.62F, 0.62F, 0.66F};
    private static final float[] SPINE_COLOR = {0.32F, 0.29F, 0.27F};
    private static final float[] TIE_COLOR = {0.42F, 0.31F, 0.20F};

    private TrackMeshBuilder() {
        throw new AssertionError("No instances.");
    }

    /**
     * Builds the mesh for {@code section}. Expensive — sample count scales with curvature and
     * length — so callers must cache the result and rebuild only when the section changes; see
     * {@link TrackRenderer}.
     */
    public static TrackMesh build(TrackSection section) {
        return build(section, java.util.Collections.<com.micatechnologies.minecraft.rcmc.track.ElementSpan>emptyList());
    }

    /**
     * Builds a section's mesh, adding hardware detail for any ride elements on it.
     *
     * <p>Currently that means the lift chain. A lift hill that looks identical to plain track is a
     * real usability problem — a builder cannot see where the chain ends, which is exactly the
     * point a train either crests or rolls back.</p>
     */
    public static TrackMesh build(TrackSection section,
                                  java.util.List<com.micatechnologies.minecraft.rcmc.track.ElementSpan> spans) {
        return build(section, spans, java.util.Collections.<double[]>emptyList());
    }

    /**
     * Builds a section's mesh, including support columns down to the ground.
     *
     * @param supports each entry is {@code {distanceAlongSection, groundY}}. Ground height has to
     *                 come from the caller because this class deliberately cannot see the world —
     *                 that is what keeps the geometry unit-testable.
     */
    public static TrackMesh build(TrackSection section,
                                  java.util.List<com.micatechnologies.minecraft.rcmc.track.ElementSpan> spans,
                                  java.util.List<double[]> supports) {
        List<MeshQuad> quads = new ArrayList<>();
        double total = section.totalLength();
        if (total <= 0.0D) {
            return new TrackMesh(quads, 0, 0, 0, 0, 0, 0);
        }

        double[] rings = ringDistances(section, total);
        boolean capEnds = !section.isClosed();

        sweepTube(section, rings, leftRailProfile(), RAIL_COLOR, capEnds, quads);
        sweepTube(section, rings, rightRailProfile(), RAIL_COLOR, capEnds, quads);
        sweepTube(section, rings, spineProfile(), SPINE_COLOR, capEnds, quads);
        buildTies(section, total, quads);
        buildLiftChains(section, total, spans, quads);
        buildSupports(section, supports, quads);

        double[] bounds = boundsOf(quads);
        return new TrackMesh(quads, bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
    }

    /**
     * Drops a support column from the track down to the ground at each requested point.
     *
     * <p>Columns hang from the track's own position rather than from a vertical directly below a
     * node, so a banked or overhanging section is still held up by something that meets it. They
     * are drawn as a plain square section — a real bent or A-frame is Phase 3.1 work — but even a
     * plain column changes how the layout reads enormously: unsupported track floating in the air
     * looks like a bug, not a design choice.</p>
     */
    private static void buildSupports(TrackSection section, java.util.List<double[]> supports,
                                      List<MeshQuad> quads) {
        if (supports == null || supports.isEmpty()) {
            return;
        }
        for (double[] support : supports) {
            double distance = support[0];
            double groundY = support[1];
            TrackFrame frame = section.frameAtDistance(distance);
            double topY = frame.position.y + SPINE_CENTER_U;
            if (topY - groundY < MIN_SUPPORT_HEIGHT) {
                continue;
            }
            buildColumn(frame.position.x, frame.position.z, groundY, topY, quads);
        }
    }

    /** A square column between two heights at a fixed x/z. */
    private static void buildColumn(double x, double z, double bottomY, double topY,
                                    List<MeshQuad> quads) {
        double h = SUPPORT_HALF_WIDTH;
        Vec3 a1 = new Vec3(x - h, bottomY, z - h);
        Vec3 b1 = new Vec3(x + h, bottomY, z - h);
        Vec3 c1 = new Vec3(x + h, bottomY, z + h);
        Vec3 d1 = new Vec3(x - h, bottomY, z + h);
        Vec3 a2 = new Vec3(x - h, topY, z - h);
        Vec3 b2 = new Vec3(x + h, topY, z - h);
        Vec3 c2 = new Vec3(x + h, topY, z + h);
        Vec3 d2 = new Vec3(x - h, topY, z + h);

        float r = SUPPORT_COLOR[0];
        float g = SUPPORT_COLOR[1];
        float b = SUPPORT_COLOR[2];
        quads.add(new MeshQuad(a1, a2, b2, b1, r, g, b));
        quads.add(new MeshQuad(b1, b2, c2, c1, r, g, b));
        quads.add(new MeshQuad(c1, c2, d2, d1, r, g, b));
        quads.add(new MeshQuad(d1, d2, a2, a1, r, g, b));
    }

    /**
     * Lays a chain of dark links down the centre of every chain-lift span on this section.
     *
     * <p>Links are spaced by arc length and alternate slightly left and right of centre, which is
     * what reads as a chain rather than a painted stripe at the distance a coaster is normally
     * viewed from. They sit just below the rail tops so a car passing over does not z-fight with
     * them.</p>
     *
     * <p>Only the geometry is here; whether a span <em>is</em> a lift comes from the server, since
     * ride hardware is not part of the track's own data.</p>
     */
    private static void buildLiftChains(TrackSection section, double total,
                                        java.util.List<com.micatechnologies.minecraft.rcmc.track.ElementSpan> spans,
                                        List<MeshQuad> quads) {
        if (spans == null || spans.isEmpty()) {
            return;
        }
        for (com.micatechnologies.minecraft.rcmc.track.ElementSpan span : spans) {
            if (span.sectionId != section.id() || !span.isLift()) {
                continue;
            }
            double from = Math.max(0.0D, Math.min(total, span.startDistance));
            double to = Math.max(0.0D, Math.min(total, span.endDistance));
            for (double s = from; s <= to; s += CHAIN_LINK_SPACING) {
                buildChainLink(section, s, quads);
            }
        }
    }

    /** One chain link: a short flat plate across the centreline, canted alternately side to side. */
    private static void buildChainLink(TrackSection section, double distance, List<MeshQuad> quads) {
        TrackFrame frame = section.frameAtDistance(distance);
        // Alternating tilt costs nothing and is what stops a row of identical plates reading as a
        // continuous stripe.
        double lean = (((int) Math.round(distance / CHAIN_LINK_SPACING)) % 2 == 0) ? 1.0D : -1.0D;

        Vec3 along = frame.forward.scale(CHAIN_LINK_LENGTH * 0.5D);
        Vec3 across = frame.right.scale(CHAIN_LINK_WIDTH * 0.5D * lean);
        Vec3 centre = frame.position.add(frame.up.scale(CHAIN_HEIGHT));

        Vec3 a = centre.subtract(along).subtract(across);
        Vec3 b = centre.subtract(along).add(across);
        Vec3 c = centre.add(along).add(across);
        Vec3 d = centre.add(along).subtract(across);
        quads.add(new MeshQuad(a, b, c, d, CHAIN_COLOR[0], CHAIN_COLOR[1], CHAIN_COLOR[2]));
        // Backface, so the chain is visible from below a lift hill as well as above it.
        quads.add(new MeshQuad(d, c, b, a, CHAIN_COLOR[0], CHAIN_COLOR[1], CHAIN_COLOR[2]));
    }

    /**
     * Curvature-adaptive arc-length samples from {@code 0} to {@code total}, always including
     * both endpoints. Package-private (rather than private) so the tessellation density itself is
     * directly unit-testable without reverse-engineering it from a quad count. See class javadoc
     * for the algorithm.
     */
    static double[] ringDistances(TrackSection section, double total) {
        List<Double> distances = new ArrayList<>();
        distances.add(0.0D);

        double s = 0.0D;
        double step = MAX_RING_SPACING;
        Vec3 tangent = section.tangentAtDistance(0.0D);
        int guard = 0;

        while (s < total - 1.0e-6D && guard++ < MAX_RINGS) {
            double trial = Math.min(step, total - s);
            Vec3 nextTangent = section.tangentAtDistance(s + trial);
            double angle = angleBetween(tangent, nextTangent);

            if (angle > MAX_RING_ANGLE && trial > MIN_RING_SPACING + 1.0e-9D) {
                // Too much direction change for this step: shrink and retry from the same `s`
                // rather than accepting a faceted-looking ring.
                step = Math.max(MIN_RING_SPACING, step * 0.5D);
                continue;
            }

            s += trial;
            distances.add(Math.min(s, total));
            tangent = nextTangent;

            // Comfortably under the angle budget: the curve is straightening out, so widen the
            // step for next time rather than staying at whatever a tight curve just forced.
            if (angle < MAX_RING_ANGLE * 0.25D && step < MAX_RING_SPACING) {
                step = Math.min(MAX_RING_SPACING, step * 1.5D);
            }
        }

        if (distances.get(distances.size() - 1) < total - 1.0e-6D) {
            distances.add(total);
        }

        double[] result = new double[distances.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = distances.get(i);
        }
        return result;
    }

    private static double angleBetween(Vec3 a, Vec3 b) {
        // atan2(|a x b|, a . b) rather than acos(a . b): stays numerically well-conditioned near
        // zero angle, which is the common case for straight track and is evaluated every ring.
        return Math.atan2(a.cross(b).length(), a.dot(b));
    }

    // ---- cross-section profiles ----

    /** One edge of a closed 2D cross-section polygon, in the frame's local (right, up) plane. */
    private static final class ProfileEdge {
        final double r;
        final double u;
        final double normalR;
        final double normalU;

        ProfileEdge(double r, double u, double normalR, double normalU) {
            this.r = r;
            this.u = u;
            this.normalR = normalR;
            this.normalU = normalU;
        }
    }

    /**
     * A closed rectangle in the (right, up) plane: one {@link ProfileEdge} per corner, whose
     * normal is the outward normal of the edge starting at that corner.
     */
    private static ProfileEdge[] rectangle(double centerR, double centerU, double halfR, double halfU) {
        double r0 = centerR - halfR;
        double r1 = centerR + halfR;
        double u0 = centerU - halfU;
        double u1 = centerU + halfU;
        return new ProfileEdge[] {
            new ProfileEdge(r0, u0, 0.0D, -1.0D),
            new ProfileEdge(r1, u0, 1.0D, 0.0D),
            new ProfileEdge(r1, u1, 0.0D, 1.0D),
            new ProfileEdge(r0, u1, -1.0D, 0.0D),
        };
    }

    private static ProfileEdge[] leftRailProfile() {
        return rectangle(-HALF_GAUGE, 0.0D, RAIL_HALF_WIDTH, RAIL_HALF_HEIGHT);
    }

    private static ProfileEdge[] rightRailProfile() {
        return rectangle(HALF_GAUGE, 0.0D, RAIL_HALF_WIDTH, RAIL_HALF_HEIGHT);
    }

    private static ProfileEdge[] spineProfile() {
        return rectangle(0.0D, SPINE_CENTER_U, SPINE_HALF_WIDTH, SPINE_HALF_HEIGHT);
    }

    private static ProfileEdge[] tieProfile() {
        return rectangle(0.0D, TIE_CENTER_U, TIE_HALF_LENGTH_R, TIE_HALF_HEIGHT);
    }

    // ---- sweeping ----

    /**
     * Sweeps a closed rectangular profile along every ring, emitting one quad per profile edge
     * per ring-to-ring step, plus end caps on an open run (a tie is also an "open run" of just two
     * rings, which is how it gets capped on both ends).
     */
    private static void sweepTube(TrackSection section, double[] rings, ProfileEdge[] profile,
                                   float[] color, boolean capEnds, List<MeshQuad> out) {
        int ringCount = rings.length;
        int cornerCount = profile.length;
        TrackFrame[] frames = new TrackFrame[ringCount];
        Vec3[][] corners = new Vec3[ringCount][cornerCount];

        for (int ri = 0; ri < ringCount; ri++) {
            TrackFrame frame = section.frameAtDistance(rings[ri]);
            frames[ri] = frame;
            for (int ci = 0; ci < cornerCount; ci++) {
                ProfileEdge edge = profile[ci];
                corners[ri][ci] = worldPoint(frame, edge.r, edge.u);
            }
        }

        for (int ri = 0; ri < ringCount - 1; ri++) {
            for (int ci = 0; ci < cornerCount; ci++) {
                int next = (ci + 1) % cornerCount;
                ProfileEdge edge = profile[ci];
                Vec3 normal = frames[ri].right.scale(edge.normalR).add(frames[ri].up.scale(edge.normalU));
                float shade = shadeFor(normal);
                out.add(new MeshQuad(
                    corners[ri][ci], corners[ri][next], corners[ri + 1][next], corners[ri + 1][ci],
                    color[0] * shade, color[1] * shade, color[2] * shade));
            }
        }

        if (capEnds) {
            out.add(cap(frames[0], corners[0], false, color));
            out.add(cap(frames[ringCount - 1], corners[ringCount - 1], true, color));
        }
    }

    private static MeshQuad cap(TrackFrame frame, Vec3[] corners, boolean outward, float[] color) {
        Vec3 normal = outward ? frame.forward : frame.forward.scale(-1.0D);
        float shade = shadeFor(normal);
        return outward
            ? new MeshQuad(corners[3], corners[2], corners[1], corners[0],
                color[0] * shade, color[1] * shade, color[2] * shade)
            : new MeshQuad(corners[0], corners[1], corners[2], corners[3],
                color[0] * shade, color[1] * shade, color[2] * shade);
    }

    /** Package-private (not private) so the frame-driven banking behaviour is directly testable
     *  without reaching into a whole built mesh to find the right quad. */
    static Vec3 worldPoint(TrackFrame frame, double r, double u) {
        return frame.position.add(frame.right.scale(r)).add(frame.up.scale(u));
    }

    /**
     * Cheap static directional shade — the same idea vanilla uses for fixed per-face block
     * lighting (top brighter than sides, sides brighter than bottom) — independent of any real
     * light source, just enough that the mesh reads as three-dimensional under flat baked color.
     * Genuine dynamic brightness is layered on top of this by {@link TrackRenderer}.
     */
    static float shadeFor(Vec3 worldNormal) {
        return (float) (0.55D + 0.45D * Math.max(0.0D, worldNormal.y));
    }

    // ---- cross-ties ----

    /**
     * Cross-ties at fixed arc-length spacing — see class javadoc for why fixed-{@code s} rather
     * than fixed ring index. Each tie is its own short independent sweep between two rings a fixed
     * small arc-length apart, capped on both ends since a tie is a short isolated piece rather
     * than a continuous run.
     *
     * <p>Deliberately does not clamp {@code s0}/{@code s1} into {@code [0, total]} itself: on a
     * closed circuit a tie near the seam legitimately needs to sample past {@code total} or before
     * {@code 0}, and {@link TrackSection#frameAtDistance} already wraps that correctly (it clamps
     * instead, for an open section, which only slightly shortens the first/last tie — an
     * acceptable edge effect at a track's dead end).</p>
     */
    private static void buildTies(TrackSection section, double total, List<MeshQuad> out) {
        ProfileEdge[] profile = tieProfile();
        int tieCount = section.isClosed()
            ? (int) Math.floor(total / TIE_SPACING)
            : (int) Math.floor(total / TIE_SPACING) + 1;

        for (int i = 0; i < tieCount; i++) {
            double center = i * TIE_SPACING;
            double[] tieRings = {center - TIE_HALF_THICKNESS_S, center + TIE_HALF_THICKNESS_S};
            sweepTube(section, tieRings, profile, TIE_COLOR, true, out);
        }
    }

    // ---- bounds ----

    private static double[] boundsOf(List<MeshQuad> quads) {
        if (quads.isEmpty()) {
            return new double[] {0, 0, 0, 0, 0, 0};
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (MeshQuad q : quads) {
            Vec3[] corners = {q.a, q.b, q.c, q.d};
            for (Vec3 v : corners) {
                minX = Math.min(minX, v.x);
                maxX = Math.max(maxX, v.x);
                minY = Math.min(minY, v.y);
                maxY = Math.max(maxY, v.y);
                minZ = Math.min(minZ, v.z);
                maxZ = Math.max(maxZ, v.z);
            }
        }
        return new double[] {minX, minY, minZ, maxX, maxY, maxZ};
    }
}
