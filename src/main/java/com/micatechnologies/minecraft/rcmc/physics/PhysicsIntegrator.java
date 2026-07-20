package com.micatechnologies.minecraft.rcmc.physics;

import com.micatechnologies.minecraft.rcmc.track.math.ParallelTransportFrames;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;

/**
 * Advances a {@link TrainState} along a track section.
 *
 * <p>Forces acting along the track, in order of magnitude:</p>
 * <ol>
 *   <li><b>Gravity</b> — only the component along the track does work. That is
 *       {@code -g · (track forward · world up)}, i.e. {@code -g · sin(grade)}. The
 *       perpendicular component is absorbed by the rails and appears to the rider only as
 *       G-force, never as acceleration.</li>
 *   <li><b>Air drag</b> — quadratic in speed, always opposing motion. This is what makes a
 *       tall first drop feel meaningfully different from a short one and what bounds a
 *       circuit's speed across repeated laps.</li>
 *   <li><b>Rolling resistance</b> — linear in speed, always opposing motion. Small, but it
 *       is what eventually brings a valleyed train to rest instead of leaving it oscillating
 *       forever.</li>
 * </ol>
 *
 * <p><b>Integration scheme: semi-implicit (symplectic) Euler.</b> Velocity is updated first
 * from the acceleration at the current position, then position is updated using the
 * <em>new</em> velocity. That one-line difference from explicit Euler is what makes the
 * scheme symplectic, meaning it does not systematically pump energy into or out of the
 * system. It matters enormously here: explicit Euler on an oscillating system (which a
 * valleyed train in a dip literally is) gains energy every cycle, so a train would slowly
 * climb out of a valley it should be trapped in — a bug that looks like a physics engine
 * "exploding" but is really just the wrong integrator. RK4 would be more accurate per step
 * but is not symplectic and costs four force evaluations; for this system, sub-stepped
 * symplectic Euler is both cheaper and better-behaved over long runs.</p>
 *
 * <p>Stateless and free of Minecraft types, so it is directly unit-testable — see
 * {@code PhysicsIntegratorTest}, which asserts energy conservation on a frictionless track.</p>
 */
public final class PhysicsIntegrator {

    private final double gravity;
    private final double rollingResistance;
    private final double airDrag;
    private final double maxSpeed;

    /**
     * @param gravity           downward acceleration, blocks/s²
     * @param rollingResistance linear drag coefficient, 1/s
     * @param airDrag           quadratic drag coefficient, 1/block
     * @param maxSpeed          hard speed clamp, blocks/s
     */
    public PhysicsIntegrator(double gravity, double rollingResistance, double airDrag, double maxSpeed) {
        this.gravity = gravity;
        this.rollingResistance = rollingResistance;
        this.airDrag = airDrag;
        this.maxSpeed = maxSpeed;
    }

    /**
     * Advances {@code state} by {@code dt} seconds along {@code track}.
     *
     * @param externalAcceleration along-track acceleration from ride hardware — chain lift,
     *                             LSM launch, brake run, station drive tyres — in blocks/s².
     *                             Signed in the direction of increasing distance. Keeping
     *                             this a caller-supplied term rather than baking ride
     *                             elements into the integrator is what lets the same
     *                             integrator serve every element type.
     */
    public TrainState step(TrainState state, ParallelTransportFrames track,
                           double externalAcceleration, double dt) {
        TrackFrame frame = track.frameAtDistance(state.distance);

        // Gravity's along-track component. frame.forward is a unit vector, so its y component
        // is exactly sin(grade): +1 pointing straight up, -1 straight down.
        double gravityAlongTrack = -gravity * frame.forward.y;

        double v = state.velocity;
        double drag = -rollingResistance * v - airDrag * v * Math.abs(v);
        double acceleration = gravityAlongTrack + drag + externalAcceleration;

        // Semi-implicit Euler: velocity first, then position from the NEW velocity.
        double newVelocity = clampSpeed(v + acceleration * dt);
        double newDistance = state.distance + newVelocity * dt;

        return state.with(newDistance, newVelocity);
    }

    private double clampSpeed(double v) {
        if (v > maxSpeed) {
            return maxSpeed;
        }
        if (v < -maxSpeed) {
            return -maxSpeed;
        }
        return v;
    }

    /**
     * Total specific mechanical energy (per unit mass) at a state, in blocks²/s².
     *
     * <p>Not used by the simulation — it is the invariant the tests assert against. On a
     * frictionless track with no external acceleration this must stay constant, and any
     * regression in the integrator shows up here first and unambiguously.</p>
     */
    public double specificEnergy(TrainState state, ParallelTransportFrames track) {
        double height = track.frameAtDistance(state.distance).position.y;
        return 0.5D * state.velocity * state.velocity + gravity * height;
    }
}
