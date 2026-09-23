package com.micatechnologies.minecraft.rcmc.physics.block;

import com.micatechnologies.minecraft.rcmc.physics.Train;

/**
 * Whether the block system has a train stopped at a boundary, and so owns it outright.
 *
 * <p>Asked by {@link BlockSignaledElementSet} to decide whose acceleration a train gets. Not the
 * same question as "is the block system braking": a train standing at a boundary is not being
 * braked at all, and the station or chain under it would push it straight through if asked.</p>
 */
interface BoundaryHold {

    boolean isHeldAtBoundary(int trainId, Train train);
}
