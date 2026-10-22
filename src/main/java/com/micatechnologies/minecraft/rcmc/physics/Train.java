package com.micatechnologies.minecraft.rcmc.physics;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
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

    /**
     * The along-track acceleration ride hardware applied on the last tick, in blocks/s². Sent to
     * clients with each correction, so they predict a lift, a brake or a launch rather than a train
     * coasting through it.
     */
    private double externalAcceleration;

    /**
     * On a client: the server's {@link #externalAcceleration} and {@link #held} from the last
     * correction, which {@link PredictedControl} applies until the next one. Unused on the server.
     */
    private double syncedExternalAcceleration;
    private boolean syncedHeld;

    /**
     * On a client: blocks along the track still to be applied from the last correction. See
     * {@link #correctToward}. Always zero on the server.
     */
    private double correction;

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
        this.externalAcceleration = externalAcceleration;
        double dt = tickSeconds / subSteps;
        for (int i = 0; i < subSteps && status == Status.RUNNING; i++) {
            step(network, externalAcceleration, dt);
        }
    }

    /** The share of the configured air drag a metro train feels. */
    static final double METRO_AIR_DRAG_FRACTION = 0.1D;

    private void step(TrackNetwork network, double externalAcceleration, double dt) {
        double gravity = averageGravityAlongTrack(network);
        if (spec.carStyle() == TrainSpec.CarStyle.METRO) {
            // The configured air drag is a coaster's: an open car, riders in the wind. A metro is a
            // long closed tube that real drag barely touches at line speed, and at the coaster's
            // figure no traction could get one much past 25 blocks/s. Most of it is handed back.
            externalAcceleration += (1.0D - METRO_AIR_DRAG_FRACTION)
                * integrator.airDrag() * velocity * Math.abs(velocity);
        }
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
        this.correction = 0.0D;
    }

    /** The along-track acceleration ride hardware applied on the last tick. */
    public double externalAcceleration() {
        return externalAcceleration;
    }

    /** Records the server's control values from a correction; see {@link PredictedControl}. */
    public void setSyncedControl(double acceleration, boolean holding) {
        this.syncedExternalAcceleration = acceleration;
        this.syncedHeld = holding;
    }

    public double syncedExternalAcceleration() {
        return syncedExternalAcceleration;
    }

    public boolean syncedHeld() {
        return syncedHeld;
    }

    /**
     * Furthest a correction is eased rather than applied at once, in blocks.
     *
     * <p>Ordinary prediction error is well under this: a tick of phase difference between the two
     * sides, or a few ticks of a lift the client mispredicted. An error bigger than this means the
     * two sides disagree about what happened, not about timing, and easing across it would show a
     * train sliding along the track for no reason.</p>
     */
    static final double MAX_EASED_CORRECTION = 8.0D;

    /**
     * Applies a server correction gradually, so a rider's view does not jump.
     *
     * <p>The two sides tick on their own clocks, so a correction regularly lands a tick early or
     * late relative to the client's prediction. At coaster speeds a tick is a block of track, and
     * setting the position outright jumps the train and the rider's camera by that much four times
     * a second. Instead the velocity is taken at once, since it is the input the client's own
     * prediction continues from and changing it moves nothing, and the position error is kept and
     * worked off over the next few ticks by {@link #easeCorrection}.</p>
     *
     * <p>Falls back to {@link #setState} when the error cannot be measured along one section, or
     * is too large to be timing (see {@link #MAX_EASED_CORRECTION}).</p>
     *
     * @return whether the correction is being eased rather than applied outright
     */
    public boolean correctToward(TrackNetwork network, TrackRef target, double targetVelocity) {
        double error = alongTrackError(network, reference, target);
        if (Double.isNaN(error) || Math.abs(error) > MAX_EASED_CORRECTION) {
            setState(target, targetVelocity);
            return false;
        }
        this.velocity = targetVelocity;
        this.status = Status.RUNNING;
        this.correction = error;
        return true;
    }

    /**
     * Works off part of the remaining correction: {@code fraction} of it, or all of it once it is
     * too small to see.
     */
    public void easeCorrection(TrackNetwork network, double fraction) {
        if (correction == 0.0D || !network.hasSection(reference.sectionId())) {
            return;
        }
        double step = Math.abs(correction) < 1.0e-3D ? correction : correction * fraction;
        TrackNetwork.Traversal traversal = network.advance(reference, step);
        reference = traversal.ref;
        correction -= step;
        if (traversal.reversed) {
            // As in step(): an END-to-END join flips the axis, so what is left of the correction
            // and the velocity are both now measured the other way.
            velocity = -velocity;
            correction = -correction;
        }
        if (traversal.hitDeadEnd) {
            correction = 0.0D;
        }
    }

    /** What is left of the last correction, in blocks along the track. */
    public double pendingCorrection() {
        return correction;
    }

    /**
     * Signed distance along the track from {@code from} to {@code to}, or NaN when they are on
     * different sections. A circuit is measured the short way round, so a correction across the
     * start line is a small step rather than nearly a lap backwards.
     */
    static double alongTrackError(TrackNetwork network, TrackRef from, TrackRef to) {
        if (from.sectionId() != to.sectionId() || !network.hasSection(to.sectionId())) {
            return Double.NaN;
        }
        double error = to.distance() - from.distance();
        TrackSection section = network.section(to.sectionId());
        if (section.isClosed()) {
            double lap = section.totalLength();
            error -= lap * Math.rint(error / lap);
        }
        return error;
    }

    @Override
    public String toString() {
        return "Train{" + spec.carCount() + " cars @ " + reference
            + ", v=" + String.format("%.2f", velocity) + ", " + status + '}';
    }
}
