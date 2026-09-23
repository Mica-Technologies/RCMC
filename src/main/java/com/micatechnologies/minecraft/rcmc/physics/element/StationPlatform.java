package com.micatechnologies.minecraft.rcmc.physics.element;

import com.micatechnologies.minecraft.rcmc.physics.Train;

/**
 * A station platform: brings an arriving train to rest at a defined point, holds it there for
 * boarding, then dispatches it back up to speed.
 *
 * <p>A small state machine, driven entirely by ticks — never wall-clock time, matching every other
 * element in this package (see {@link RideElement}'s determinism note):</p>
 *
 * <pre>
 *   ARRIVING --(at rest at stopDistance)--&gt; DWELLING --(dwellTicks ticks later)--&gt; DISPATCHING
 *       --(reaches dispatchSpeed)--&gt; DEPARTED
 * </pre>
 *
 * <p><b>ARRIVING</b> brakes the train toward {@code stopDistance} using the classic
 * constant-deceleration stopping profile — target speed {@code sqrt(2 · brakeDeceleration ·
 * remaining distance)} — fed through the same {@link VelocityServo} used by {@link ChainLift} and
 * {@link DriveTyres}. That profile decays to zero exactly as the remaining distance does, so the
 * train coasts to rest near the stop point rather than needing a separate final "did we arrive"
 * heuristic. The servo's output is then clamped to never push in the train's direction of travel
 * (see {@link BrakeRun} for the identical reasoning) — a platform only ever removes energy, so a
 * train already slower than the ideal curve simply coasts, unbraked, until the curve catches up to
 * it. The transition to {@code DWELLING} is triggered by speed alone (see
 * {@link #STOP_SPEED_EPSILON}'s javadoc for why), not by also demanding the remaining distance be
 * within some tolerance — discretisation at game-tick granularity means the train generally settles
 * a little short of, or past, {@code stopDistance} rather than exactly on it, and chasing that last
 * sliver of position error is not worth the added complexity for what is, in the end, a Minecraft
 * block grid. One consequence worth knowing: a train sitting at rest short of {@code stopDistance}
 * (for instance, released too gently by whatever fed it in) has no force applied and will not creep
 * the rest of the way in on its own — this element assumes an arriving train still has some
 * residual speed, as it would coming off a lift, launch or brake run in a real layout. If {@code
 * brakeDeceleration} is very low relative to the entry speed and the distance available, the train
 * settles well short of or past {@code stopDistance} — the honest physical outcome of underpowered
 * brakes, not a bug.</p>
 *
 * <p><b>DWELLING</b> holds the train at rest with the platform brakes closed — an active hold
 * toward zero speed, not zero force — for exactly {@code dwellTicks} ticks. Zero force let a train
 * on a platform with the slightest grade creep during the dwell.</p>
 *
 * <p><b>DISPATCHING</b> pushes the train with {@code dispatchAcceleration} (signed — its sign is
 * the dispatch direction) until the train reaches {@code dispatchSpeed}, then goes idle
 * ({@code DEPARTED}), handing off to whatever comes next (a lift, a launch, or just gravity).</p>
 *
 * <p><b>One train at a time.</b> The phase belongs to the train being served.
 * {@link RideElementSet} calls {@link #claim} for every train found on the platform — a train the
 * platform is not already serving starts a fresh arrival — and {@link #release} when that train
 * leaves, so the next train is met at {@link Phase#ARRIVING}. The platform is in control (and says so
 * through {@link #isHolding}) from arrival until the train has departed.</p>
 */
public final class StationPlatform extends RideElementSpan {

    /** Where the platform is in its arrival/dwell/dispatch cycle. */
    public enum Phase {
        /** Braking toward {@code stopDistance}. */
        ARRIVING,
        /** At rest, holding for the dwell. */
        DWELLING,
        /** Being driven back up to speed. */
        DISPATCHING,
        /** Idle — released, waiting for {@link #reset()}. */
        DEPARTED
    }

    /**
     * Speed below which an arriving train is considered at rest and ready to dwell.
     *
     * <p>Deliberately set above {@code Train}'s own internal "stopped" threshold (0.02 blocks/s),
     * and that gap is load-bearing, not cosmetic. {@code Train} independently flags a train
     * {@code VALLEYED} — a fault status meant for a train stuck with nowhere to go — the moment its
     * speed drops under its own threshold while it happens to receive exactly zero external
     * acceleration. If this element kept braking with the tight position-based stopping criterion
     * it used to use, the train's speed could cross under {@code Train}'s threshold on a tick where
     * this element also returned exactly zero (either from the "never push forward" guard below, or
     * because braking had genuinely finished slightly early), latching a spurious fault a moment
     * before this element itself had decided the train had arrived. Because this element checks
     * only speed, and checks it strictly before {@code Train} would on the very same data, crossing
     * this (higher) threshold always transitions to {@link Phase#DWELLING} first — which then always
     * returns zero deliberately, so a false {@code VALLEYED} read alongside it is harmless.</p>
     */
    private static final double STOP_SPEED_EPSILON = 0.05D;

    /**
     * A vanishingly small, but strictly nonzero, acceleration used whenever this element means
     * "hold the train exactly where it is" — the final tick of {@link Phase#ARRIVING} and every
     * tick of {@link Phase#DWELLING}.
     *
     * <p>This is a workaround for a real interaction, not decoration. {@code Train} independently
     * flags a train {@code VALLEYED} — a fault meaning "stuck with nowhere to go" — the moment its
     * speed is near zero <em>and</em> the external acceleration it was given is exactly
     * {@code 0.0D}. Once that fires, {@code Train.tick} becomes a permanent no-op: even a later,
     * perfectly good dispatch push would never move the train again, because {@code Train} simply
     * stops advancing its own state. A literal {@code 0.0D} here is indistinguishable, from
     * {@code Train}'s point of view, from "nothing is holding this train, it just happened to coast
     * to a stop" — exactly the case {@code VALLEYED} exists to catch. Returning this instead signals
     * "something is actively controlling this train" without perceptibly affecting its physics: at
     * this magnitude, a full second of dwelling changes velocity by about {@code 1e-9} blocks/s,
     * far below anything a player or any assertion in this package's tests could observe.</p>
     */

    private final double stopDistance;
    private final double brakeDeceleration;
    private final int dwellTicks;
    private final double dispatchAcceleration;
    private final double dispatchSpeed;
    private final double tickSeconds;

    /**
     * How many times a dispatched train may run back through this platform before it is caught.
     *
     * <p>Zero for an ordinary circuit, where the next time a train reaches the station it has come
     * round the lap. A shuttle coaster comes back through its station between launches — out to one
     * spike, back through the platform, out to the other — and a platform that caught it the first
     * time would end every ride halfway. With one pass-through the train runs back through once,
     * and is caught the time after.</p>
     */
    private final int passThroughs;

    /** Pass-throughs still owed to the train most recently dispatched. */
    private int passesLeft;

    /** Whether the train now on the platform is running through it rather than being served. */
    private boolean passing;

    private Phase phase = Phase.ARRIVING;
    private int dwellRemaining;

    /**
     * @param stopDistance         distance along the section, within {@code [startDistance,
     *                              endDistance]}, where the train should come to rest
     * @param brakeDeceleration    magnitude of braking acceleration used while arriving, blocks/s²
     * @param dwellTicks            how many ticks the train is held at rest before dispatch — a tick
     *                              count, not seconds, so it needs no wall-clock or {@code
     *                              tickSeconds} conversion to stay deterministic
     * @param dispatchAcceleration signed acceleration applied while dispatching, blocks/s²; its
     *                              sign is the dispatch direction
     * @param dispatchSpeed         speed magnitude, blocks/s, at which dispatch is considered
     *                              complete and the platform goes idle
     * @param tickSeconds           length of the game tick this platform is evaluated at, used by
     *                              the arrival braking servo — see {@link VelocityServo}
     */
    public StationPlatform(int sectionId, double startDistance, double endDistance,
                            double stopDistance, double brakeDeceleration, int dwellTicks,
                            double dispatchAcceleration, double dispatchSpeed, double tickSeconds) {
        this(sectionId, startDistance, endDistance, stopDistance, brakeDeceleration, dwellTicks,
            dispatchAcceleration, dispatchSpeed, tickSeconds, 0);
    }

    /**
     * @param passThroughs how many times a dispatched train runs back through before it is caught;
     *                     see the field
     */
    public StationPlatform(int sectionId, double startDistance, double endDistance,
                            double stopDistance, double brakeDeceleration, int dwellTicks,
                            double dispatchAcceleration, double dispatchSpeed, double tickSeconds,
                            int passThroughs) {
        super(sectionId, startDistance, endDistance);
        if (passThroughs < 0) {
            throw new IllegalArgumentException("passThroughs must be >= 0, got " + passThroughs);
        }
        this.passThroughs = passThroughs;
        if (stopDistance < startDistance || stopDistance > endDistance) {
            throw new IllegalArgumentException("stopDistance " + stopDistance
                + " must lie within [" + startDistance + ", " + endDistance + "]");
        }
        if (brakeDeceleration <= 0.0D) {
            throw new IllegalArgumentException("brakeDeceleration must be positive, got " + brakeDeceleration);
        }
        if (dwellTicks < 0) {
            throw new IllegalArgumentException("dwellTicks must be >= 0, got " + dwellTicks);
        }
        if (dispatchAcceleration == 0.0D) {
            throw new IllegalArgumentException("dispatchAcceleration must not be zero");
        }
        if (dispatchSpeed <= 0.0D) {
            throw new IllegalArgumentException("dispatchSpeed must be positive, got " + dispatchSpeed);
        }
        if (tickSeconds <= 0.0D) {
            throw new IllegalArgumentException("tickSeconds must be positive, got " + tickSeconds);
        }
        this.stopDistance = stopDistance;
        this.brakeDeceleration = brakeDeceleration;
        this.dwellTicks = dwellTicks;
        this.dispatchAcceleration = dispatchAcceleration;
        this.dispatchSpeed = dispatchSpeed;
        this.tickSeconds = tickSeconds;
        this.dwellRemaining = dwellTicks;
    }

    @Override
    public double accelerationFor(Train train) {
        switch (phase) {
            case ARRIVING:
                return arriving(train);
            case DWELLING:
                return dwelling(train);
            case DISPATCHING:
                return dispatching(train);
            case DEPARTED:
            default:
                return 0.0D;
        }
    }

    private double arriving(Train train) {
        double v = train.velocity();
        double remaining = stopDistance - train.reference().distance();
        double direction = remaining >= 0.0D ? 1.0D : -1.0D;
        // Constant-deceleration stopping profile: the speed the train would need to be travelling
        // right now to reach zero exactly at the stop point, given brakeDeceleration. Feeding that
        // through the servo makes the applied braking taper smoothly to zero as the train nears the
        // stop, rather than needing a separate "close enough, stop braking" heuristic.
        double targetSpeed = direction * Math.sqrt(2.0D * brakeDeceleration * Math.abs(remaining));
        double acceleration = VelocityServo.accelerationToHold(v, targetSpeed, brakeDeceleration, tickSeconds);

        // A platform only ever brakes — it must never speed the train up to "catch" the ideal
        // stopping curve from below, which would mean powering the train. A train already slower
        // than the curve at this distance simply coasts (acceleration 0) until the curve catches up
        // to it, exactly like a real block-brake approach: no force is applied until braking is
        // actually needed. Same reasoning as the guard in BrakeRun.accelerationFor.
        if (v > 0.0D) {
            acceleration = Math.min(acceleration, 0.0D);
        } else if (v < 0.0D) {
            acceleration = Math.max(acceleration, 0.0D);
        } else {
            acceleration = 0.0D;
        }

        if (Math.abs(v) <= STOP_SPEED_EPSILON) {
            // At rest — see STOP_SPEED_EPSILON's javadoc for why this is a speed-only check rather
            // than also requiring `remaining` to be small: requiring both let a train that stopped
            // slightly short (or, from discretisation, slightly past) the mark get permanently
            // stuck fighting to correct a few centimetres of position error it should simply accept.
            phase = Phase.DWELLING;
            dwellRemaining = dwellTicks;
            return 0.0D;
        }
        return acceleration;
    }

    private double dwelling(Train train) {
        if (dwellRemaining > 0) {
            dwellRemaining--;
            // The platform brakes stay closed through the dwell. Applying nothing let a train on a
            // platform with the faintest grade creep — backwards, on the demo — and the dispatch that
            // followed then had to reverse it through zero.
            return VelocityServo.accelerationToHold(train.velocity(), 0.0D, brakeDeceleration,
                tickSeconds);
        }
        if (!gate.mayDispatch()) {
            // Dwell served, but the operator has not released it: a closed ride, a manual ride
            // waiting for DISPATCH, an emergency stop. Keep holding.
            return VelocityServo.accelerationToHold(train.velocity(), 0.0D, brakeDeceleration,
                tickSeconds);
        }
        phase = Phase.DISPATCHING;
        return dispatchAcceleration;
    }

    private double dispatching(Train train) {
        if (Math.abs(train.velocity()) < dispatchSpeed) {
            return dispatchAcceleration;
        }
        phase = Phase.DEPARTED;
        return 0.0D;
    }

    /**
     * Arms the platform for another arrival. See the class javadoc — deciding <em>when</em> to
     * call this is a ride-control responsibility this element deliberately does not take on
     * itself.
     */
    public void reset() {
        phase = Phase.ARRIVING;
        dwellRemaining = dwellTicks;
    }

    /** No train is being served. */
    public static final int NO_TRAIN = -1;

    private int servingTrain = NO_TRAIN;

    private DispatchGate gate = DispatchGate.ALWAYS;

    /** Who decides whether a train that has served its dwell may leave. See {@link DispatchGate}. */
    public void setGate(DispatchGate gate) {
        this.gate = gate == null ? DispatchGate.ALWAYS : gate;
    }

    /** Ticks of dwell still to serve before the train could be dispatched; 0 once it could go. */
    public int dwellRemaining() {
        return dwellRemaining;
    }

    /**
     * Takes charge of {@code trainId}, starting a fresh arrival if it was serving any other train (or
     * none). Called by {@link RideElementSet} for every train found on this platform.
     *
     * <p>Without this the phase was one-shot: a station dispatched its first train, went to DEPARTED,
     * and stayed there — every later train rolled through at speed, and one that reached it slowly
     * stopped with nothing holding it and latched VALLEYED for good. A platform's phase belongs to
     * the train it is serving, so a new train gets a new cycle.</p>
     */
    public void claim(int trainId) {
        if (servingTrain != trainId) {
            servingTrain = trainId;
            reset();
            if (passesLeft > 0) {
                // A pass-through: the platform stands aside and the train keeps going.
                passesLeft--;
                passing = true;
                phase = Phase.DEPARTED;
            }
        }
    }

    /**
     * {@code trainId} has left the platform. If it was the train being served, the platform is ready
     * for the next one.
     */
    public void release(int trainId) {
        if (servingTrain == trainId) {
            if (!passing && (phase == Phase.DEPARTED || phase == Phase.DISPATCHING)) {
                // Leaving after a dispatch — often before reaching dispatch speed, if the stop point
                // is near the platform's end: this is where the pass-throughs start counting.
                passesLeft = passThroughs;
            }
            passing = false;
            servingTrain = NO_TRAIN;
            reset();
        }
    }

    public int passThroughs() {
        return passThroughs;
    }

    public int servingTrain() {
        return servingTrain;
    }

    /**
     * Whether the platform is busy with a train other than {@code trainId}. A second train running
     * onto a platform used to be claimed on the spot, resetting the first one's cycle — and the two
     * then took turns every tick, neither ever dwelling or leaving. The platform finishes with the
     * train it has; the next waits.
     */
    public boolean servesAnother(int trainId) {
        return servingTrain != NO_TRAIN && servingTrain != trainId;
    }

    /**
     * Holds a train waiting for this platform at rest where it is, with the platform's own brakes.
     * It is met as a fresh arrival once the train ahead has gone.
     */
    public double holdWaiting(Train train) {
        return VelocityServo.accelerationToHold(train.velocity(), 0.0D, brakeDeceleration, tickSeconds);
    }

    /** The same platform, fresh: waiting for its first arrival, serving nobody. */
    public StationPlatform freshCopy() {
        return new StationPlatform(sectionId(), startDistance(), endDistance(), stopDistance,
            brakeDeceleration, dwellTicks, dispatchAcceleration, dispatchSpeed, tickSeconds,
            passThroughs);
    }

    /**
     * Whether the platform currently has a train under its control and not yet released — true
     * during both {@link Phase#ARRIVING} (still braking) and {@link Phase#DWELLING} (stopped and
     * waiting). False once dispatch has begun, even though the train may still be physically
     * within the span for a few more ticks.
     */
    /**
     * Whether the platform is in control of its train: braking it in, holding it, or pushing it
     * out. Dispatch counts — for its first tick or two the train is still barely moving, and a
     * platform that disowned it then would have its own launch misread as a stall and the train
     * latched VALLEYED on the spot (found in play, on the second lap of the demo).
     */
    public boolean isHolding() {
        return phase != Phase.DEPARTED;
    }

    public Phase phase() {
        return phase;
    }

    /** Configuration accessors, so the element can be written to a save file without reflection. */
    public double brakeDeceleration() {
        return brakeDeceleration;
    }

    public int dwellTicks() {
        return dwellTicks;
    }

    public double dispatchAcceleration() {
        return dispatchAcceleration;
    }

    public double dispatchSpeed() {
        return dispatchSpeed;
    }

    public double stopDistance() {
        return stopDistance;
    }
}
