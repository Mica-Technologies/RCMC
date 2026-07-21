package com.micatechnologies.minecraft.rcmc.physics.block;

/**
 * Two trains whose along-track extents overlap on the same section right now.
 *
 * <p>This is a physical fact, not a fault report and not an exception. The one-degree-of-freedom
 * model in {@code physics} has no rigid-body collision response — nothing stops two
 * {@code TrackRef}s from occupying the same distance, or one train's reference from numerically
 * passing another's. Left alone, that reads as a bug. Reported through this class instead, it is
 * the honest RCT-style feature {@link BlockSystem} exists to make optional: with block safety
 * enabled, a well-provisioned block system prevents this from ever happening; with it disabled,
 * trains are free to pile into each other exactly as they would on a real, unsignalled circuit,
 * and this is how a caller finds out that happened — to trigger a wreck animation, an operator
 * alert, or simply a statistic, rather than an uncaught exception three layers away.</p>
 *
 * <p>Immutable. Overlap is computed from each train's full length (see {@code TrainSpec}), not
 * just its lead reference — a car overtaking only the front of another train is still a
 * collision.</p>
 */
public final class Collision {

    private final int trainIdA;
    private final int trainIdB;
    private final int sectionId;
    private final double overlapDistance;

    /**
     * @param overlapDistance how far the two trains' occupied ranges overlap, in blocks — always
     *                        {@code > 0}; this constructor does not validate that, since it is
     *                        always called from {@link BlockSystem}'s own already-verified overlap
     *                        computation
     */
    Collision(int trainIdA, int trainIdB, int sectionId, double overlapDistance) {
        this.trainIdA = trainIdA;
        this.trainIdB = trainIdB;
        this.sectionId = sectionId;
        this.overlapDistance = overlapDistance;
    }

    public int trainIdA() {
        return trainIdA;
    }

    public int trainIdB() {
        return trainIdB;
    }

    public int sectionId() {
        return sectionId;
    }

    public double overlapDistance() {
        return overlapDistance;
    }

    /** Whether {@code trainId} is one of the two trains involved. */
    public boolean involves(int trainId) {
        return trainId == trainIdA || trainId == trainIdB;
    }

    @Override
    public String toString() {
        return "Collision{train" + trainIdA + " x train" + trainIdB + " on section " + sectionId
            + ", overlap=" + String.format("%.3f", overlapDistance) + '}';
    }
}
