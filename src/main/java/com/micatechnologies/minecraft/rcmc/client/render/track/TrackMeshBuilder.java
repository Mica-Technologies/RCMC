package com.micatechnologies.minecraft.rcmc.client.render.track;

import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.TrackStyleIds;
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
    // Gauge, tie length, rail and spine sizes all come from the section's TrackStyles; only the
    // style-independent pieces remain constants here.

    private static final double SPINE_CENTER_U = -0.35D;

    // Ties span vertically from the underside of the rails down to the top of the spine,
    // bridging them into one structure — see tieProfile, which derives the extent from the
    // style's rail and spine geometry so the gap between them cannot reopen if either is
    // retuned. Real box-spine coaster track is built exactly this way.

    /** Chain link spacing along the lift, in blocks. Close enough to read as links, not a stripe. */
    private static final double CHAIN_LINK_SPACING = 0.5D;

    private static final double CHAIN_LINK_LENGTH = 0.30D;
    private static final double CHAIN_LINK_WIDTH = 0.15D;
    private static final double CHAIN_LINK_THICKNESS = 0.06D;

    /**
     * Chain height relative to the track centreline.
     *
     * <p>Sits between the rails at roughly rail level, which is where a real lift chain runs. It
     * must clear the ties: those now span from the underside of the rails down to the spine, and
     * an earlier value of -0.16 put the chain <em>inside</em> that volume, so the two fought for
     * the same pixels.</p>
     */
    private static final double CHAIN_HEIGHT = 0.0D;

    /** Dark oiled steel — deliberately darker than the rails so the lift reads at a distance. */
    private static final float[] CHAIN_COLOR = { 0.18F, 0.17F, 0.15F };

    /**
     * Support columns: slim enough to read as steelwork, not as a wall.
     *
     * <p>Taken from {@code TrackSupports} rather than declared here, so the column you can see and
     * the column you bump into are one number. Two constants that "must match" eventually will
     * not, and the symptom — colliding with air beside a post, or walking through its edge — is
     * maddening to diagnose from a bug report.</p>
     */
    private static final double SUPPORT_HALF_WIDTH =
        com.micatechnologies.minecraft.rcmc.world.TrackSupports.HALF_WIDTH;

    /**
     * Below this height above ground a support is skipped. Track running along the floor does not
     * need holding up, and a stub column at every sample there would be visual noise.
     */
    private static final double MIN_SUPPORT_HEIGHT = 2.0D;

    private static final float[] SUPPORT_COLOR = { 0.42F, 0.43F, 0.46F };
    private static final double TIE_HALF_THICKNESS_S = 0.08D;

    /**
     * Track-ballast bed, swept below the spine on heavy-rail (transit) styles so the track sits on
     * stone rather than floating a gap above whatever is under it — the classic problem of laying
     * spline track just above the ground. Flat-coloured a gravel grey (the mesh carries no texture,
     * so it is the colour of gravel rather than the gravel texture itself).
     */
    private static final float[] BALLAST_COLOR = { 0.30F, 0.29F, 0.29F };

    /** How far below the railhead the ballast bed reaches — a block, so a floor one block down meets it. */
    private static final double BALLAST_BOTTOM_U = -1.02D;

    /** Ballast shoulder half-width — wider than the gauge, like a real ballast bed spilling past the ties. */
    private static final double BALLAST_HALF_WIDTH = 1.55D;

    /** Arc-length spacing between cross-ties, in blocks. See class javadoc. */
    private static final double TIE_SPACING = 1.5D;

    // ---- material colors (flat, pre-shading — no track-style system exists yet to source
    // these from; TrackSection.styleAtDistance is not consulted here. Revisit once track styles
    // (docs/design/TRACK_GEOMETRY.md, "what is not here yet") land. ----

    /**
     * Colours come from the section's own palette rather than from constants, so a builder's paint
     * choice reaches the geometry.
     *
     * <p>Resolved once per build, not per quad, and folded into the vertex colours alongside the
     * per-face shade. That does mean recolouring rebuilds a section's mesh — the plan called for
     * resolving palette at draw time to avoid exactly that, and this is the simpler thing that
     * works: recolouring is a deliberate, occasional act, and the rebuild it triggers is the same
     * one any other edit causes. Revisit if painting ever becomes something done continuously,
     * such as a live colour picker.</p>
     */
    private static float[] colourOf(TrackSection section,
                                    com.micatechnologies.minecraft.rcmc.track.TrackPalette.Part part) {
        com.micatechnologies.minecraft.rcmc.track.TrackPalette.Colour colour =
            section.palette().of(part);
        return new float[] { colour.red(), colour.green(), colour.blue() };
    }

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
        TrackStyles style = TrackStyles.of(section.styleId());

        float[] railColour = colourOf(section,
            com.micatechnologies.minecraft.rcmc.track.TrackPalette.Part.RAIL);
        sweepTube(section, rings, railProfile(style, -1), railColour, capEnds, quads);
        sweepTube(section, rings, railProfile(style, 1), railColour, capEnds, quads);
        if (style.ballast) {
            // Under the spine, so the track reads as sitting on a stone bed rather than hovering a
            // gap above the floor. Swept like the spine — one continuous bed, capped on open runs.
            sweepTube(section, rings, ballastProfile(style), BALLAST_COLOR, capEnds, quads);
        }
        sweepTube(section, rings, spineProfile(style), colourOf(section,
            com.micatechnologies.minecraft.rcmc.track.TrackPalette.Part.SPINE), capEnds, quads);
        buildTies(section, total, style, quads);
        buildLiftChains(section, total, spans, quads);
        buildSupports(section, supports, quads);
        buildCatenary(section, total, rings, style, quads);

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
        // Each entry is {x, z, bottomY, topY, attachX, attachZ}, computed by TrackSupports so the
        // rendered column and the collision box are the same object described once. They used to be
        // computed here from a distance and a ground height, which meant the geometry existed only
        // on the client — fine while supports were decorative, impossible once they had to be solid.
        float[] colour =
            colourOf(section, com.micatechnologies.minecraft.rcmc.track.TrackPalette.Part.SUPPORT);
        for (double[] column : supports) {
            buildColumn(column[0], column[1], column[2], column[3], colour, quads);
            // Banked and inverted track is met by a column standing beside it, so the arm is what
            // actually reaches the rails. Without it the support stops short and reads as floating.
            if (column.length >= 6
                && Math.hypot(column[4] - column[0], column[5] - column[1]) > 1.0e-3D) {
                buildArm(column[0], column[3], column[1], column[4], column[5], colour, quads);
            }
        }
    }

    /**
     * A square beam at height {@code y} running horizontally from the column to the track.
     *
     * <p>Drawn as a box rather than two quads because it is seen from every side once the track it
     * holds is upside down — the same lesson the chain lift taught, where coplanar quads at
     * identical coordinates shimmered against each other.</p>
     */
    private static void buildArm(double fromX, double y, double fromZ, double toX, double toZ,
                                 float[] supportColour, List<MeshQuad> quads) {
        double h = SUPPORT_HALF_WIDTH;
        double minX = Math.min(fromX, toX) - h;
        double maxX = Math.max(fromX, toX) + h;
        double minZ = Math.min(fromZ, toZ) - h;
        double maxZ = Math.max(fromZ, toZ) + h;
        double minY = y - h;
        double maxY = y + h;

        float r = supportColour[0];
        float g = supportColour[1];
        float b = supportColour[2];
        Vec3 a1 = new Vec3(minX, minY, minZ);
        Vec3 b1 = new Vec3(maxX, minY, minZ);
        Vec3 c1 = new Vec3(maxX, minY, maxZ);
        Vec3 d1 = new Vec3(minX, minY, maxZ);
        Vec3 a2 = new Vec3(minX, maxY, minZ);
        Vec3 b2 = new Vec3(maxX, maxY, minZ);
        Vec3 c2 = new Vec3(maxX, maxY, maxZ);
        Vec3 d2 = new Vec3(minX, maxY, maxZ);
        quads.add(new MeshQuad(a1, a2, b2, b1, r, g, b));
        quads.add(new MeshQuad(b1, b2, c2, c1, r, g, b));
        quads.add(new MeshQuad(c1, c2, d2, d1, r, g, b));
        quads.add(new MeshQuad(d1, d2, a2, a1, r, g, b));
        quads.add(new MeshQuad(d2, c2, b2, a2, r, g, b));
        quads.add(new MeshQuad(a1, b1, c1, d1, r, g, b));
    }

    /** A square column between two heights at a fixed x/z. */
    private static void buildColumn(double x, double z, double bottomY, double topY,
                                    float[] supportColour, List<MeshQuad> quads) {
        double h = SUPPORT_HALF_WIDTH;
        Vec3 a1 = new Vec3(x - h, bottomY, z - h);
        Vec3 b1 = new Vec3(x + h, bottomY, z - h);
        Vec3 c1 = new Vec3(x + h, bottomY, z + h);
        Vec3 d1 = new Vec3(x - h, bottomY, z + h);
        Vec3 a2 = new Vec3(x - h, topY, z - h);
        Vec3 b2 = new Vec3(x + h, topY, z - h);
        Vec3 c2 = new Vec3(x + h, topY, z + h);
        Vec3 d2 = new Vec3(x - h, topY, z + h);

        float r = supportColour[0];
        float g = supportColour[1];
        float b = supportColour[2];
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

    /**
     * One chain link: a small solid box, canted alternately left and right.
     *
     * <p>Solid rather than a flat plate, and emitted once rather than as a front face plus a
     * reversed backface at the same coordinates. Two coplanar quads in identical positions
     * z-fight against each other, which is what made the first version of this shimmer.</p>
     */
    private static void buildChainLink(TrackSection section, double distance, List<MeshQuad> quads) {
        TrackFrame frame = section.frameAtDistance(distance);
        // Alternating tilt costs nothing and is what stops a row of identical links reading as a
        // continuous stripe.
        boolean flat = ((int) Math.round(distance / CHAIN_LINK_SPACING)) % 2 == 0;

        Vec3 centre = frame.position.add(frame.up.scale(CHAIN_HEIGHT));
        Vec3 along = frame.forward.scale(CHAIN_LINK_LENGTH * 0.5D);
        // A real chain alternates flat and upright links; swapping which axis is wide is a cheap
        // way to read as that rather than as a row of identical plates.
        Vec3 across = (flat ? frame.right : frame.up).scale(CHAIN_LINK_WIDTH * 0.5D);
        Vec3 through = (flat ? frame.up : frame.right).scale(CHAIN_LINK_THICKNESS * 0.5D);

        box(centre, along, across, through, CHAIN_COLOR, quads);
    }

    /**
     * An oriented solid box from three half-extent vectors. Faces are wound outward, so
     * back-face culling keeps only the ones actually visible.
     */
    private static void box(Vec3 centre, Vec3 along, Vec3 across, Vec3 through,
                            float[] color, List<MeshQuad> quads) {
        Vec3 a = centre.subtract(along).subtract(across).subtract(through);
        Vec3 b = centre.subtract(along).add(across).subtract(through);
        Vec3 c = centre.add(along).add(across).subtract(through);
        Vec3 d = centre.add(along).subtract(across).subtract(through);
        Vec3 e = centre.subtract(along).subtract(across).add(through);
        Vec3 f = centre.subtract(along).add(across).add(through);
        Vec3 g = centre.add(along).add(across).add(through);
        Vec3 h = centre.add(along).subtract(across).add(through);

        float r = color[0];
        float gg = color[1];
        float bb = color[2];
        quads.add(new MeshQuad(a, b, c, d, r, gg, bb));
        quads.add(new MeshQuad(h, g, f, e, r, gg, bb));
        quads.add(new MeshQuad(e, f, b, a, r, gg, bb));
        quads.add(new MeshQuad(d, c, g, h, r, gg, bb));
        quads.add(new MeshQuad(b, f, g, c, r, gg, bb));
        quads.add(new MeshQuad(e, a, d, h, r, gg, bb));
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

    private static ProfileEdge[] railProfile(TrackStyles style, int side) {
        return rectangle(side * style.halfGauge, 0.0D, style.railHalfWidth, style.railHalfHeight);
    }

    private static ProfileEdge[] spineProfile(TrackStyles style) {
        return rectangle(0.0D, SPINE_CENTER_U, style.spineHalfWidth, style.spineHalfHeight);
    }

    /**
     * The ballast bed: a wide, shallow box from just under the spine down to {@link #BALLAST_BOTTOM_U}.
     * Its top overlaps the spine bottom so the two read as one structure, and its bottom sits a block
     * below the railhead so a floor laid one block down meets it flush.
     */
    private static ProfileEdge[] ballastProfile(TrackStyles style) {
        double top = SPINE_CENTER_U - style.spineHalfHeight * 0.5D;
        double centre = (top + BALLAST_BOTTOM_U) * 0.5D;
        double halfHeight = Math.abs(top - BALLAST_BOTTOM_U) * 0.5D;
        return rectangle(0.0D, centre, BALLAST_HALF_WIDTH, halfHeight);
    }

    private static ProfileEdge[] tieProfile(TrackStyles style) {
        double tieTop = -style.railHalfHeight;
        double tieBottom = SPINE_CENTER_U + style.spineHalfHeight;
        double centre = (tieTop + tieBottom) * 0.5D;
        double halfHeight = Math.abs(tieTop - tieBottom) * 0.5D;
        return rectangle(0.0D, centre, style.tieHalfLength, halfHeight);
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
    private static void buildTies(TrackSection section, double total, TrackStyles style,
                                  List<MeshQuad> out) {
        ProfileEdge[] profile = tieProfile(style);
        int tieCount = section.isClosed()
            ? (int) Math.floor(total / TIE_SPACING)
            : (int) Math.floor(total / TIE_SPACING) + 1;

        for (int i = 0; i < tieCount; i++) {
            double center = i * TIE_SPACING;
            double[] tieRings = {center - TIE_HALF_THICKNESS_S, center + TIE_HALF_THICKNESS_S};
            sweepTube(section, tieRings, profile, colourOf(section,
                com.micatechnologies.minecraft.rcmc.track.TrackPalette.Part.TIE), true, out);
        }
    }

    // ---- catenary ----
    // Heights are frame-local u above the railheads, and every one of them derives from the
    // section's own contact-wire height (default 10, authored per section — see TrackStyleIds).
    // The metro car's pantograph derives its reach from the same number rather than carrying a
    // constant of its own, so the wire and the thing that touches it can no longer drift apart.
    // All of this is dressing: nothing reads it back, and the "requires power" gameplay hook
    // remains deliberately unbuilt.

    private static final double CONTACT_WIRE_HALF = 0.03D;

    /** Messenger height above the contact wire: at the mast, and at midspan (the sag). */
    private static final double MESSENGER_AT_MAST = 1.0D;
    private static final double MESSENGER_AT_MIDSPAN = 0.15D;

    private static final double MAST_SPACING = 24.0D;
    private static final double MAST_BOTTOM_U = -0.45D;

    /** How far a mast stands proud of the carrier it holds. */
    private static final double MAST_ABOVE_CARRIER = 0.4D;
    private static final double MAST_HALF_WIDTH = 0.09D;

    /** How far outside the gauge a mast stands. */
    private static final double MAST_CLEARANCE = 0.9D;

    private static final double MESSENGER_SAMPLE_STEP = 1.5D;
    private static final double DROPPER_SPACING = 6.0D;

    /**
     * Every catenary height derives from the section's own contact-wire height — masts, messenger,
     * registration drops and the tunnel conductor alike — so raising the wire raises the hardware
     * with it. The height itself is authored per section on the style id
     * ({@code transit-catenary-12}); see {@link TrackStyleIds#contactWireHeight}.
     */
    private static double wireHeightOf(TrackSection section) {
        return TrackStyleIds.contactWireHeight(section.styleId());
    }

    private static final float[] MAST_COLOR = { 0.36F, 0.37F, 0.40F };
    private static final float[] CONTACT_COLOR = { 0.48F, 0.33F, 0.24F };
    private static final float[] MESSENGER_COLOR = { 0.20F, 0.21F, 0.23F };
    private static final float[] RIGID_COLOR = { 0.30F, 0.31F, 0.34F };

    /**
     * Overhead electrification, per the section's style. Three looks:
     *
     * <ul>
     *   <li><b>POLES</b> — a mast beside the track every {@link #MAST_SPACING} blocks with a
     *       bracket arm reaching over the centreline; contact wire swept at constant height;
     *       messenger wire sagging between masts with droppers. The classic light-rail span.</li>
     *   <li><b>PORTALS</b> — the same wires carried by two-legged gantry frames spanning the
     *       track instead of single masts.</li>
     *   <li><b>TUNNEL</b> — a rigid conductor bar swept like a third rail overhead, with clamp
     *       stubs instead of masts; nothing sags because nothing hangs.</li>
     * </ul>
     *
     * <p>Masts hang off the track's own frames (like the support columns do), so elevated and
     * banked transit track carries its own electrification with it.</p>
     */
    private static void buildCatenary(TrackSection section, double total, double[] rings,
                                      TrackStyles style, List<MeshQuad> out) {
        if (style.catenary == TrackStyles.Catenary.NONE) {
            return;
        }
        boolean capEnds = !section.isClosed();
        double wireU = wireHeightOf(section);
        if (wireU <= 0.0D) {
            return;
        }

        if (style.catenary == TrackStyles.Catenary.TUNNEL) {
            sweepTube(section, rings, rectangle(0.0D, wireU, 0.07D, 0.10D),
                RIGID_COLOR, capEnds, out);
            for (double s = 0.0D; s < total; s += DROPPER_SPACING) {
                TrackFrame frame = section.frameAtDistance(s);
                box(worldPoint(frame, 0.0D, wireU + 0.16D),
                    frame.forward.scale(0.05D), frame.right.scale(0.05D),
                    frame.up.scale(0.10D), RIGID_COLOR, out);
            }
            return;
        }

        sweepTube(section, rings, rectangle(0.0D, wireU,
            CONTACT_WIRE_HALF, CONTACT_WIRE_HALF), CONTACT_COLOR, capEnds, out);

        // Mast positions: every MAST_SPACING, plus an anchor at the far end of an open run so
        // the wire never dangles unsupported past the last mast. On a closed circuit s = total
        // IS the s = 0 mast, so the wrap span closes itself.
        List<Double> masts = new ArrayList<>();
        for (double s = 0.0D; s < total - 1.0e-6D; s += MAST_SPACING) {
            masts.add(s);
        }
        masts.add(total);

        for (int i = 0; i < masts.size() - (section.isClosed() ? 1 : 0); i++) {
            buildMast(section, masts.get(i), style, wireU, out);
        }
        for (int i = 0; i < masts.size() - 1; i++) {
            buildMessengerSpan(section, masts.get(i), masts.get(i + 1), wireU, out);
        }
    }

    private static void buildMast(TrackSection section, double s, TrackStyles style,
                                  double wireU, List<MeshQuad> out) {
        TrackFrame frame = section.frameAtDistance(s);
        double offset = style.halfGauge + MAST_CLEARANCE;
        double mastTopU = wireU + MESSENGER_AT_MAST + MAST_ABOVE_CARRIER;
        double postCentreU = (mastTopU + MAST_BOTTOM_U) * 0.5D;
        double postHalfU = (mastTopU - MAST_BOTTOM_U) * 0.5D;

        // Height of the member that actually carries the wires at this mast: the bracket arm
        // sits exactly at the messenger's mast-end height, so the sag span visibly lands ON it
        // rather than peaking in mid-air beside the mast — the first cut floated the arm halfway
        // between the two wires, touching neither, which read as disconnected hardware from any
        // distance.
        double carrierU = wireU + MESSENGER_AT_MAST;

        if (style.catenary == TrackStyles.Catenary.PORTALS) {
            for (int side = -1; side <= 1; side += 2) {
                box(worldPoint(frame, side * offset, postCentreU),
                    frame.forward.scale(MAST_HALF_WIDTH), frame.right.scale(MAST_HALF_WIDTH),
                    frame.up.scale(postHalfU), MAST_COLOR, out);
            }
            // The portal beam spans post to post, carrying the messenger.
            box(worldPoint(frame, 0.0D, carrierU),
                frame.forward.scale(MAST_HALF_WIDTH), frame.right.scale(offset + MAST_HALF_WIDTH),
                frame.up.scale(MAST_HALF_WIDTH), MAST_COLOR, out);
        } else {
            box(worldPoint(frame, offset, postCentreU),
                frame.forward.scale(MAST_HALF_WIDTH), frame.right.scale(MAST_HALF_WIDTH),
                frame.up.scale(postHalfU), MAST_COLOR, out);
            // Bracket arm from the mast out over the centreline, at messenger height.
            box(worldPoint(frame, offset * 0.5D, carrierU),
                frame.forward.scale(0.06D), frame.right.scale(offset * 0.5D + MAST_HALF_WIDTH),
                frame.up.scale(0.06D), MAST_COLOR, out);
        }

        // Registration drop: the vertical link tying the contact wire up to its carrier, so the
        // wire visibly hangs from hardware at every mast.
        double dropHalf = (carrierU - wireU) * 0.5D;
        box(worldPoint(frame, 0.0D, wireU + dropHalf),
            frame.forward.scale(0.04D), frame.right.scale(0.04D),
            frame.up.scale(dropHalf), MAST_COLOR, out);
    }

    /**
     * The messenger wire between two masts: sampled along the arc with a parabolic sag —
     * highest at each mast, {@link #MESSENGER_AT_MIDSPAN} above the contact wire at midspan —
     * plus vertical droppers tying the two wires together. Segments are thin oriented boxes,
     * like the chain links and for the same z-fighting reason.
     */
    private static void buildMessengerSpan(TrackSection section, double s0, double s1,
                                           double wireU, List<MeshQuad> out) {
        double span = s1 - s0;
        if (span < 1.0e-3D) {
            return;
        }
        Vec3 previous = null;
        TrackFrame previousFrame = null;
        for (double s = s0; ; s = Math.min(s1, s + MESSENGER_SAMPLE_STEP)) {
            double t = (s - s0) / span;
            double rise = MESSENGER_AT_MIDSPAN
                + (MESSENGER_AT_MAST - MESSENGER_AT_MIDSPAN) * (2.0D * t - 1.0D) * (2.0D * t - 1.0D);
            TrackFrame frame = section.frameAtDistance(s);
            Vec3 point = worldPoint(frame, 0.0D, wireU + rise);
            if (previous != null) {
                Vec3 half = point.subtract(previous).scale(0.5D);
                box(previous.add(half), half, previousFrame.right.scale(0.025D),
                    previousFrame.up.scale(0.025D), MESSENGER_COLOR, out);
            }
            previous = point;
            previousFrame = frame;
            if (s >= s1 - 1.0e-9D) {
                break;
            }
        }
        for (double s = s0 + DROPPER_SPACING * 0.5D; s < s1; s += DROPPER_SPACING) {
            double t = (s - s0) / span;
            double rise = MESSENGER_AT_MIDSPAN
                + (MESSENGER_AT_MAST - MESSENGER_AT_MIDSPAN) * (2.0D * t - 1.0D) * (2.0D * t - 1.0D);
            TrackFrame frame = section.frameAtDistance(s);
            box(worldPoint(frame, 0.0D, wireU + rise * 0.5D),
                frame.forward.scale(0.02D), frame.right.scale(0.02D),
                frame.up.scale(rise * 0.5D), MESSENGER_COLOR, out);
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
