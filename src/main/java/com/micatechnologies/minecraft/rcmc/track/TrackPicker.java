package com.micatechnologies.minecraft.rcmc.track;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;

/**
 * Finds the point on a track network nearest a position — "which bit of track did I click on".
 *
 * <p>Pure, so the search can be tested without a game instance. Picking is the kind of thing that
 * looks obviously right and is quietly off by a segment, and an editor that edits the wrong span is
 * worse than one that refuses to edit.</p>
 *
 * <p>Searches by coarse sampling then refines around the best sample. A closed-form nearest-point
 * on a cubic is a quartic root problem per segment; sampling plus refinement gets within a
 * fraction of a block for a fraction of the code, and a fraction of a block is well under the
 * precision of pointing at something in Minecraft.</p>
 */
public final class TrackPicker {

    /** Coarse sample spacing, in blocks. */
    private static final double COARSE_STEP = 1.0D;

    /** Refinement passes around the best coarse sample, each narrowing the window tenfold. */
    private static final int REFINEMENTS = 3;

    /** A point on the network, and how far the query was from it. */
    public static final class Hit {

        public final TrackRef ref;
        public final double distanceFromQuery;

        Hit(TrackRef ref, double distanceFromQuery) {
            this.ref = ref;
            this.distanceFromQuery = distanceFromQuery;
        }
    }

    private TrackPicker() {
        throw new AssertionError("No instances.");
    }

    /**
     * Nearest point on any section, or {@code null} if nothing is within {@code maxDistance}.
     *
     * @param maxDistance how far from track a query may be and still count as pointing at it
     */
    public static Hit pick(TrackNetwork network, Vec3 query, double maxDistance) {
        Hit best = null;
        for (TrackSection section : network.sections()) {
            Hit hit = pickOn(section, query);
            if (hit != null && (best == null || hit.distanceFromQuery < best.distanceFromQuery)) {
                best = hit;
            }
        }
        return best != null && best.distanceFromQuery <= maxDistance ? best : null;
    }

    /** Nearest point on one section. Never null for a section with length. */
    public static Hit pickOn(TrackSection section, Vec3 query) {
        double total = section.totalLength();
        if (total <= 0.0D) {
            return null;
        }

        double bestAt = 0.0D;
        double bestDistance = Double.MAX_VALUE;
        for (double s = 0.0D; s <= total; s += COARSE_STEP) {
            double d = section.positionAtDistance(s).distanceTo(query);
            if (d < bestDistance) {
                bestDistance = d;
                bestAt = s;
            }
        }

        // Refine within the coarse window. Curvature between two samples a block apart is small
        // enough that the true minimum is bracketed by them.
        double window = COARSE_STEP;
        for (int pass = 0; pass < REFINEMENTS; pass++) {
            double from = Math.max(0.0D, bestAt - window);
            double to = Math.min(total, bestAt + window);
            double step = (to - from) / 10.0D;
            if (step <= 0.0D) {
                break;
            }
            for (double s = from; s <= to; s += step) {
                double d = section.positionAtDistance(s).distanceTo(query);
                if (d < bestDistance) {
                    bestDistance = d;
                    bestAt = s;
                }
            }
            window = step;
        }

        return new Hit(new TrackRef(section.id(), bestAt), bestDistance);
    }

    /**
     * Index of the node-to-node span containing {@code distance} on {@code section}.
     *
     * <p>Editing works in spans rather than at points: a builder retyping "this bit of track" means
     * the stretch between two nodes they placed, not an infinitesimal position. Returns the index
     * of the span's <em>first</em> node.</p>
     */
    public static int spanIndexAt(TrackSection section, double distance) {
        int nodes = section.nodes().size();
        for (int i = 0; i + 1 < nodes; i++) {
            if (distance < section.nodeDistance(i + 1)) {
                return i;
            }
        }
        // Past the last node: on a circuit that is the wrap span, otherwise the final span.
        return Math.max(0, nodes - (section.isClosed() ? 1 : 2));
    }
}
