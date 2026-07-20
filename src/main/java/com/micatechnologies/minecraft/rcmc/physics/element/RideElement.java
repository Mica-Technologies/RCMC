package com.micatechnologies.minecraft.rcmc.physics.element;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;

/**
 * A piece of ride hardware occupying a span of one track section: a chain lift, a launch run, a
 * brake run, a station platform, station drive tyres.
 *
 * <p>This is the thing {@code PhysicsIntegrator} and {@code Train} deliberately know nothing
 * about — see {@code Train.tick}'s {@code externalAcceleration} parameter,
 * {@code TrainManager.ExternalAcceleration}, and the "External acceleration" section of
 * {@code docs/design/PHYSICS.md}. Keeping ride hardware out of the integrator is what lets one
 * integrator serve every element type, and lets each element be implemented and tested in
 * isolation from the others and from the rest of the simulation.</p>
 *
 * <p><b>Pure Java, and deterministic.</b> No Minecraft types, no wall-clock reads, no randomness.
 * An implementation's output must be a function only of the train's current state and (for
 * stateful elements such as {@link StationPlatform}) of how many times it has previously been
 * invoked — never of {@code System.currentTimeMillis()} or anything else that could differ
 * between two machines given the same tick history. That is what makes client-side prediction
 * possible: the client runs the identical element code and must reach the identical result. See
 * {@code Train}'s determinism note for why this matters in practice.</p>
 */
public interface RideElement {

    /** Track section this element sits on. */
    int sectionId();

    /** Distance along the section where the element's span begins, in blocks. */
    double startDistance();

    /** Distance along the section where the element's span ends, in blocks. Always {@code >= startDistance()}. */
    double endDistance();

    /** Whether {@code ref} falls within this element's span (inclusive at both ends). */
    boolean contains(TrackRef ref);

    /**
     * The along-track acceleration this element applies to {@code train} right now, in blocks/s².
     *
     * <p>Called once per game tick, and only while the train's current reference is
     * {@link #contains(TrackRef) contained} by this element — callers (see {@link RideElementSet})
     * are responsible for that gating. Implementations may assume it holds and do not need to
     * re-check it themselves.</p>
     */
    double accelerationFor(Train train);

    /**
     * Whether this element is deliberately holding a train stationary, rather than the train
     * having stalled there.
     *
     * <p>The physics cannot tell the difference on its own — a train stopped in a station on level
     * track has the same speed, grade and applied force as one stranded in a valley — so the
     * element, which is the only thing that knows its own intent, has to say. Most elements never
     * hold anything, hence the default.</p>
     */
    default boolean isHolding() {
        return false;
    }
}
