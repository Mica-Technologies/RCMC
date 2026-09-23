package com.micatechnologies.minecraft.rcmc.physics.block;

import com.micatechnologies.minecraft.rcmc.track.TrackRef;

/**
 * A named stretch of one track section that at most one train may occupy at a time.
 *
 * <p>This is the unit {@link BlockSystem} reasons about — the same real-world concept as a
 * railway or coaster "block": authored by whoever builds the ride, given an identity a park
 * operator would recognise ("brake block 2"), and bounded to a span of a single
 * {@code TrackSection} exactly like {@code RideElementSpan} in the sibling {@code element}
 * package. That boundary test (inclusive at both ends, exact section match) is deliberately
 * copied from there rather than shared, since {@code RideElementSpan} is package-private to
 * {@code physics.element} and this package must not reach into it.</p>
 *
 * <p><b>A block does not have to span an entire section, and adjacent blocks do not have to be
 * contiguous.</b> Leaving a gap between two blocks — track that belongs to no block at all — is a
 * legitimate, even useful, authoring choice: a train in the gap is not tracked by the block system
 * and cannot hold anyone up. {@link BlockSystem}'s javadoc explains why this matters for avoiding
 * gridlock on a circuit where the number of trains equals the number of blocks.</p>
 *
 * <p>Immutable and free of Minecraft types, matching every other class this deep in the physics
 * layer.</p>
 */
public final class BlockSection {

    private final String id;
    private final int sectionId;
    private final double startDistance;
    private final double endDistance;

    /**
     * Whether this block runs from {@link #startDistance} through a closed circuit's seam to
     * {@link #endDistance}. A block whose boundaries are placed hardware — a block brake, the
     * station, the lift — almost always has one such block, since the seam falls wherever the
     * builder happened to start laying track, not at a brake.
     */
    private final boolean wraps;

    /**
     * @param id            a stable, human-meaningful identifier ("brake-block-2"), not required
     *                      to be unique but expected to be by convention — this class does not
     *                      police uniqueness, {@link BlockSystem} treats blocks purely by list
     *                      position
     * @param sectionId     track section this block sits on
     * @param startDistance distance along the section where the block begins, in blocks
     * @param endDistance   distance along the section where the block ends, in blocks; must be
     *                      {@code >= startDistance}
     */
    public BlockSection(String id, int sectionId, double startDistance, double endDistance) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id must not be null or empty");
        }
        if (endDistance < startDistance) {
            throw new IllegalArgumentException(
                "endDistance (" + endDistance + ") must be >= startDistance (" + startDistance + ")");
        }
        this.id = id;
        this.sectionId = sectionId;
        this.startDistance = startDistance;
        this.endDistance = endDistance;
        this.wraps = false;
    }

    private BlockSection(String id, int sectionId, double startDistance, double endDistance,
                         boolean wraps) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id must not be null or empty");
        }
        this.id = id;
        this.sectionId = sectionId;
        this.startDistance = startDistance;
        this.endDistance = endDistance;
        this.wraps = wraps;
    }

    /**
     * A block on a closed circuit that runs from {@code startDistance} on through the seam and
     * ends at {@code endDistance}, which is nearer the start of the section than the block's own
     * start is.
     */
    public static BlockSection wrapping(String id, int sectionId, double startDistance,
                                        double endDistance) {
        if (endDistance > startDistance) {
            throw new IllegalArgumentException("a wrapping block ends before it starts: "
                + startDistance + " -> " + endDistance);
        }
        return new BlockSection(id, sectionId, startDistance, endDistance, true);
    }

    public boolean wraps() {
        return wraps;
    }

    /** Whether {@code ref} falls within this block's span (inclusive at both ends). */
    public boolean contains(TrackRef ref) {
        if (ref == null || ref.sectionId() != sectionId) {
            return false;
        }
        return wraps
            ? ref.distance() >= startDistance || ref.distance() <= endDistance
            : ref.distance() >= startDistance && ref.distance() <= endDistance;
    }

    public String id() {
        return id;
    }

    public int sectionId() {
        return sectionId;
    }

    public double startDistance() {
        return startDistance;
    }

    public double endDistance() {
        return endDistance;
    }

    /** Length along the track; for a {@link #wraps wrapping} block, excluding the section's length. */
    public double length() {
        return wraps ? endDistance : endDistance - startDistance;
    }

    @Override
    public String toString() {
        return "BlockSection{" + id + ", section=" + sectionId
            + ", [" + startDistance + ", " + endDistance + "]" + (wraps ? " wrapping" : "") + "}";
    }
}
