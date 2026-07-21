package com.micatechnologies.minecraft.rcmc.physics;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import java.util.ArrayList;
import java.util.List;

/**
 * A train running on the track network: where it is, how fast, and how it moves.
 *
 * <p>This is where the geometry layer and the physics layer meet, and it is the single most
 * important class in the simulation.</p>
 *
 * <p><b>Whole-train gravity.</b> A train is not a point. The gravity term is the <em>average</em>
 * of {@code -g·sin(grade)} sampled at every car, not the value at the lead car. That distinction
 * is not a refinement — it is a real, felt part of coaster behaviour: a long train crests a hill
 * differently from a short one, because its rear is still being pulled forward by the climb while
 * its front has already tipped over. Sampling at one point loses the effect entirely, and with it
 * the reason train length is a design decision rather than a cosmetic one.</p>
 *
 * <p><b>Determinism is a hard requirement, not a nicety.</b> This class must produce bit-identical
 * results from identical inputs on client and server, because the netcode plan is to sync the four
 * numbers of {@link TrainState} and have the client run this same code. No randomness, no world
 * lookups, no wall-clock, no floats. Anything added here that breaks determinism silently breaks
 * client prediction, and the symptom will be rubber-banding that looks like a network problem.</p>
 *
 * <p>Mutable, single-threaded, server-authoritative (with a client-side mirror). Free of Minecraft
 * types, so a full circuit can be simulated in a unit test.</p>
 */
public final class Train {

    /** Why a train stopped moving under its own power. */
    public enum Status {
        /** Running normally. */
        RUNNING,
        /**
         * Ran into the end of unconnected track. An operational fault the ride controller should
         * surface, not absorb — see {@link TrackNetwork.Traversal#hitDeadEnd}.
         */
        DEAD_END,
        /**
         * Stopped mid-circuit with nowhere to go: a valleyed train. Real coasters need a recovery
         * crew for this; here it must at minimum be visible rather than presenting as a hang.
         */
        VALLEYED
    }

    /**
     * Below this speed, a train with no external drive and negligible grade is treated as stopped
     * rather than left to creep on rounding error. Chosen as roughly a millimetre per tick — well
     * under anything visible, but far enough above double-precision noise to actually terminate.
     */
    private static final double STOPPED_SPEED = 0.02D;

    private final TrainSpec spec;
    private final PhysicsIntegrator integrator;

    /** Network address of the lead car. Every other car is derived from this. */
    private TrackRef reference;

    /** Speed along the track in blocks/s. Signed: negative is running backwards. */
    private double velocity;

    private Status status = Status.RUNNING;

    /** True while a ride element is deliberately holding this train stationary. */
    private boolean held;

    public Train(TrainSpec spec, PhysicsIntegrator integrator, TrackRef start, double initialVelocity) {
        if (spec == null || integrator == null || start == null) {
            throw new IllegalArgumentException("spec, integrator and start are all required");
        }
        this.spec = spec;
        this.integrator = integrator;
        this.reference = start;
        this.velocity = initialVelocity;
    }

    /**
     * Advances the train by one game tick.
     *
     * <p>Sub-stepped: one 50 ms step is far too coarse at coaster speeds, where a train doing 30
     * blocks/s covers 1.5 blocks per tick — enough to cut the corner on a tight helix. Sub-steps
     * cost only CPU, never bandwidth, because only the resulting state is ever transmitted.</p>
     *
     * @param externalAcceleration along-track acceleration from ride hardware (chain lift, launch,
     *                             brakes), in blocks/s². Supplied by the caller so one integrator
     *                             serves every element type.
     * @param subSteps             physics iterations per tick; see {@code RcmcConfig.physicsSubSteps}
     * @param tickSeconds          length of a tick in seconds
     */
    public void tick(TrackNetwork network, double externalAcceleration, int subSteps, double tickSeconds) {
        if (subSteps < 1) {
            throw new IllegalArgumentException("subSteps must be >= 1, got " + subSteps);
        }
        double dt = tickSeconds / subSteps;
        for (int i = 0; i < subSteps && status == Status.RUNNING; i++) {
            step(network, externalAcceleration, dt);
        }
    }

    private void step(TrackNetwork network, double externalAcceleration, double dt) {
        double gravity = averageGravityAlongTrack(network);
        double newVelocity = integrator.advanceVelocity(velocity, gravity, externalAcceleration, dt);

        TrackNetwork.Traversal traversal = network.advance(reference, newVelocity * dt);
        reference = traversal.ref;

        if (traversal.reversed) {
            // Crossing an END-to-END join flips the direction of increasing distance. The train
            // is still travelling the same way through the world, so its velocity must flip to
            // stay consistent with the new section's axis. Miss this and the train appears to
            // bounce off the join.
            newVelocity = -newVelocity;
        }
        velocity = newVelocity;

        if (traversal.hitDeadEnd) {
            velocity = 0.0D;
            status = Status.DEAD_END;
            return;
        }

        // A train that has run out of speed on a grade too shallow to restart it is valleyed.
        // Checked against the gravity that would act NEXT step, not the one just used, so a train
        // momentarily at zero speed at the crest of a hill is not misdiagnosed.
        if (Math.abs(velocity) < STOPPED_SPEED && !held) {
            double gravityHere = averageGravityAlongTrack(network);
            if (Math.abs(gravityHere) < STOPPED_SPEED) {
                velocity = 0.0D;
                status = Status.VALLEYED;
            }
        }
    }

    /**
     * Whether something is deliberately holding this train stationary — a station brake, a
     * dispatch hold — as opposed to it having stalled.
     *
     * <p>Set by the ride-control layer before {@link #tick}. Without it the two situations are
     * indistinguishable from inside the physics: a train stopped in a station on level track has
     * exactly the same speed, grade and applied acceleration as one stranded in a valley, so
     * valleying detection would fire on every normal dwell.</p>
     *
     * <p>This replaces an earlier rule that keyed on {@code externalAcceleration == 0}, which was
     * wrong twice over. It misread a station holding a train on level track — where zero really is
     * the physically correct force — as a fault. And it made "apply an imperceptible fake force"
     * the only way for an element to hold a train, which is the kind of workaround that survives
     * long after anyone remembers why it is there.</p>
     */
    public void setHeld(boolean value) {
        this.held = value;
        if (value && status == Status.VALLEYED) {
            // Being taken under control clears a stall: an operator recovering a stuck train
            // should not have to delete and respawn it.
            status = Status.RUNNING;
        }
    }

    public boolean isHeld() {
        return held;
    }

    /**
     * Gravity's along-track component averaged over every car.
     *
     * <p>See the class javadoc: this is the whole reason train length affects how a coaster runs.</p>
     */
    public double averageGravityAlongTrack(TrackNetwork network) {
        if (spec.carCount() == 1) {
            return integrator.gravityAlong(network.frameAt(reference).forward);
        }
        double sum = 0.0D;
        for (int i = 0; i < spec.carCount(); i++) {
            sum += integrator.gravityAlong(frameOfCar(network, i).forward);
        }
        return sum / spec.carCount();
    }

    /** Network address of car {@code index}, walking back from the lead car along the track. */
    public TrackRef refOfCar(TrackNetwork network, int index) {
        double offset = spec.offsetOfCar(index);
        if (offset == 0.0D) {
            return reference;
        }
        // Cars trail the reference, so walk backwards. Traversal handles crossing joins, which a
        // long train straddling a section boundary genuinely does.
        return network.advance(reference, -offset).ref;
    }

    /** Full orientation of car {@code index} — what the renderer and rider camera consume. */
    public TrackFrame frameOfCar(TrackNetwork network, int index) {
        return network.frameAt(refOfCar(network, index));
    }

    /**
     * The frame the car <em>body</em> of car {@code index} should be placed from.
     *
     * <p>For coaster stock this is simply {@link #frameOfCar}: the cars are short enough that a
     * rigid body oriented from one track point is indistinguishable from anything fancier. A
     * metro car is not — ten blocks of rigid body oriented by one point's tangent swings its ends
     * wide off the track on every curve. Real long stock rests on two bogies, each following the
     * rails, with the body spanning them; this reproduces exactly that. Each bogie is a plain
     * point at a fixed along-track offset (the one-degree-of-freedom rule extends untouched —
     * {@code spec.carLength()} is <em>defined</em> as the bogie-centre spacing), and the body
     * frame is derived: position at the bogies' midpoint — the chord, which on a curve is
     * correctly pulled toward its inside — forward along the bogie-to-bogie chord, up blended
     * from the two bogie frames and re-orthogonalised by {@link TrackFrame}'s constructor.</p>
     *
     * <p>Physics never reads this. Gravity sampling, occupancy and stopping distances all use the
     * per-car track references, same as always; this exists for the entity, the renderer and the
     * seat placement.</p>
     */
    public TrackFrame bodyFrameOfCar(TrackNetwork network, int index) {
        if (spec.carStyle() != TrainSpec.CarStyle.METRO) {
            return frameOfCar(network, index);
        }
        double half = spec.carLength() / 2.0D;
        double centerOffset = spec.offsetOfCar(index);
        TrackFrame front = network.frameAt(network.advance(reference, -(centerOffset - half)).ref);
        TrackFrame rear = network.frameAt(network.advance(reference, -(centerOffset + half)).ref);

        com.micatechnologies.minecraft.rcmc.track.math.Vec3 chord =
            front.position.subtract(rear.position);
        if (chord.lengthSquared() < 1.0e-9D) {
            // Degenerate (both bogies clamped onto the same dead end); fall back to the point frame.
            return frameOfCar(network, index);
        }
        com.micatechnologies.minecraft.rcmc.track.math.Vec3 mid =
            front.position.add(rear.position).scale(0.5D);
        com.micatechnologies.minecraft.rcmc.track.math.Vec3 up = front.up.add(rear.up);
        if (up.lengthSquared() < 1.0e-9D) {
            up = front.up;
        }
        return new TrackFrame(mid, chord, up);
    }

    /** Frames of every car, front to back. */
    public List<TrackFrame> carFrames(TrackNetwork network) {
        List<TrackFrame> result = new ArrayList<>(spec.carCount());
        for (int i = 0; i < spec.carCount(); i++) {
            result.add(frameOfCar(network, i));
        }
        return result;
    }

    /**
     * The train's state as the four numbers the network protocol carries.
     *
     * <p>Note what is <em>not</em> here: any car position. The whole point of the sync design is
     * that a client reconstructs every car from this plus the track geometry it already has.</p>
     */
    public TrainState state() {
        return new TrainState(reference.distance(), velocity);
    }

    public TrackRef reference() {
        return reference;
    }

    public double velocity() {
        return velocity;
    }

    public double speed() {
        return Math.abs(velocity);
    }

    public TrainSpec spec() {
        return spec;
    }

    public Status status() {
        return status;
    }

    public boolean isRunning() {
        return status == Status.RUNNING;
    }

    /**
     * Overwrites position and velocity, e.g. from a server correction packet or an operator
     * repositioning a valleyed train. Clears any fault status.
     */
    public void setState(TrackRef newReference, double newVelocity) {
        this.reference = newReference;
        this.velocity = newVelocity;
        this.status = Status.RUNNING;
    }

    @Override
    public String toString() {
        return "Train{" + spec.carCount() + " cars @ " + reference
            + ", v=" + String.format("%.2f", velocity) + ", " + status + '}';
    }
}
