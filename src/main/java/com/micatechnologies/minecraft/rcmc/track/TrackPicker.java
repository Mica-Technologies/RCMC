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

    /**
     * The first piece of track a look ray passes within {@code radius} of, or {@code null}.
     *
     * <p>This — not {@link #pick(TrackNetwork, Vec3, double)} — is what "I clicked on that track"
     * means. Track is rendered geometry with no block behind it, so a click aimed at it produces
     * either a block hit somewhere else entirely or no hit at all; a point query can only work when
     * the player happens to be standing next to what they are pointing at.</p>
     *
     * <p>Nearest along the ray wins rather than nearest to it, so track in front occludes track
     * behind it the way a solid object would. The hit is therefore where the ray first enters the
     * radius, which on a ray approaching the track at a shallow angle is a little short of the
     * point aimed at — within a span's length, which is the unit an edit applies to anyway.</p>
     *
     * @param origin    where the ray starts, normally the player's eyes
     * @param direction look direction; need not be normalised
     * @param maxRange  how far down the ray to look, in blocks
     * @param radius    how far off the ray track may be and still count as pointed at
     */
    public static Hit pickAlongRay(TrackNetwork network, Vec3 origin, Vec3 direction,
                                   double maxRange, double radius) {
        if (direction.lengthSquared() <= 0.0D) {
            return null;
        }
        Vec3 unit = direction.normalize();

        double bestAlong = Double.MAX_VALUE;
        Hit best = null;
        for (TrackSection section : network.sections()) {
            double total = section.totalLength();
            if (total <= 0.0D) {
                continue;
            }
            for (double s = 0.0D; s <= total; s += COARSE_STEP) {
                // A coarse sample may sit up to half a step from where the track actually passes
                // closest to the ray, so the shortlist is widened by that before refining.
                double off = offRay(origin, unit, section.positionAtDistance(s), maxRange);
                if (off > radius + COARSE_STEP) {
                    continue;
                }
                double at = s;
                double window = COARSE_STEP;
                for (int pass = 0; pass < REFINEMENTS; pass++) {
                    double from = Math.max(0.0D, at - window);
                    double to = Math.min(total, at + window);
                    double step = (to - from) / 10.0D;
                    if (step <= 0.0D) {
                        break;
                    }
                    for (double t = from; t <= to; t += step) {
                        double d = offRay(origin, unit, section.positionAtDistance(t), maxRange);
                        if (d < off) {
                            off = d;
                            at = t;
                        }
                    }
                    window = step;
                }
                if (off > radius) {
                    continue;
                }
                double along = section.positionAtDistance(at).subtract(origin).dot(unit);
                if (along >= 0.0D && along <= maxRange && along < bestAlong) {
                    bestAlong = along;
                    best = new Hit(new TrackRef(section.id(), at), off);
                }
            }
        }
        return best;
    }

    /**
     * How far a point lies off the ray, or {@link Double#MAX_VALUE} if it is behind the origin or
     * past the range — those are misses, not distant hits.
     */
    private static double offRay(Vec3 origin, Vec3 unit, Vec3 point, double maxRange) {
        Vec3 relative = point.subtract(origin);
        double along = relative.dot(unit);
        if (along < 0.0D || along > maxRange) {
            return Double.MAX_VALUE;
        }
        return relative.subtract(unit.scale(along)).length();
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
