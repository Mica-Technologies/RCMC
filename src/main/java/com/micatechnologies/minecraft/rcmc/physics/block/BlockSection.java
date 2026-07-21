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
    }

    /** Whether {@code ref} falls within this block's span (inclusive at both ends). */
    public boolean contains(TrackRef ref) {
        return ref != null && ref.sectionId() == sectionId
            && ref.distance() >= startDistance && ref.distance() <= endDistance;
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

    public double length() {
        return endDistance - startDistance;
    }

    @Override
    public String toString() {
        return "BlockSection{" + id + ", section=" + sectionId
            + ", [" + startDistance + ", " + endDistance + "]}";
    }
}
