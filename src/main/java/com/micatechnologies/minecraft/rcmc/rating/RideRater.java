package com.micatechnologies.minecraft.rcmc.rating;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.physics.GForces;
import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;

/**
 * Rates a coaster before it ever opens.
 *
 * <p>This is the concrete answer to "compute from a simulated run so a rating exists before the ride
 * ever opens, exactly as in RCT" (see {@code MASTER_PLAN.md}'s Phase 8.1 entry): it drives a
 * {@link Train} around a {@link TrackNetwork} the same way the live game does — same
 * {@link PhysicsIntegrator}, same {@link RideElementSet} hardware — but at design time, against
 * nothing but the track and train definitions, with no rider ever having boarded. It samples
 * {@link GForces} at every tick exactly as {@code client.hud.RideTelemetry} does for a live rider,
 * reduces the whole run to a {@link RideStatistics}, and hands that to {@link RideRating#from}.</p>
 *
 * <p><b>Deterministic.</b> No randomness, no wall-clock, no world state — only the network, the
 * elements, the train spec, and the physics constants supplied to the constructor. The same
 * arguments always produce the same {@link RideStatistics} and therefore the same {@link RideRating}
 * — see {@code RideRaterTest}'s determinism assertion.</p>
 *
 * <p><b>How the run ends.</b> A closed circuit has no natural finish line the way a single
 * {@code TrackSection} does — the network this class walks may join several sections together — so
 * "one lap" is detected by distance rather than by topology: the run stops once cumulative
 * along-track travel (summed every tick as {@code |velocity| * tickSeconds}, which is exact for a
 * symplectic-Euler step and accurate to within a fraction of a tick's motion regardless of how many
 * section boundaries were crossed) reaches the network's total track length, or once the tick budget
 * runs out, or once the train faults ({@link Train.Status#DEAD_END} or {@link Train.Status#VALLEYED}
 * — both are legitimate, useful results: an unfinishable layout should rate as such, not hang the
 * rater). For a single-circuit network (by far the common case, and every example in
 * {@code RideRaterTest}) this is exact; for a network containing track the rated train never visits,
 * "total track length" over-counts and the run instead ends on the tick budget, which is a documented
 * approximation rather than a silent one.</p>
 */
public final class RideRater {

    /** Matches {@code docs/design/PHYSICS.md}'s default sub-step count for the live simulation. */
    public static final int DEFAULT_SUB_STEPS = 4;

    /** 10 minutes of simulated ride time at 20 ticks/s — generously past any real circuit's ride
     *  time, so a normal coaster always finishes on the lap-length check, not this ceiling. Exists
     *  purely so a layout that can never complete a lap (see the class javadoc) still terminates. */
    public static final int DEFAULT_MAX_TICKS = 12_000;

    /** Distance sampled either side of the car to estimate curvature and its direction — see
     *  {@link #sampleCurvature}. Matches {@code client.hud.RideTelemetry}'s own constant; both solve
     *  the identical problem the identical way, independently, for the reason explained there. */
    private static final double CURVATURE_HALF_STEP = 0.5D;

    /** {@code |dot(curvatureDirection, horizontalRight)|} below which a curving moment is treated as
     *  mostly vertical (a hill, not a turn) rather than attributed to either a left or a right turn,
     *  for direction-change counting. Tuned by feel, not derived. */
    private static final double TURN_DIRECTION_DOT_THRESHOLD = 0.3D;

    /** Arbitrary id passed to {@link RideElementSet}'s per-train callbacks. Every implementation in
     *  this codebase ignores the id and keys off the train's current track position instead, so any
     *  constant value works; this exists only because the interface requires one. */
    private static final int TRAIN_ID = 1;

    private final PhysicsIntegrator integrator;
    private final double gravity;
    private final int subSteps;
    private final double tickSeconds;
    private final int maxTicks;
    private final SafetyLimits safetyLimits;

    /**
     * @param integrator   the physics integrator the rated run advances the train with — must be the
     *                     same one the live ride will use, or the rating is not honest
     * @param gravity      downward acceleration in blocks/s². <b>Must match {@code integrator}'s own
     *                     gravity</b> — {@link PhysicsIntegrator} does not expose a getter for it, so
     *                     this is supplied separately rather than read back out. Passing a mismatched
     *                     value desynchronises the along-track dynamics from the felt G-forces.
     * @param subSteps     physics sub-steps per tick, forwarded to {@link Train#tick}
     * @param tickSeconds  length of one simulated tick, in seconds
     * @param maxTicks     hard cap on ticks simulated — see the class javadoc's "how the run ends"
     * @param safetyLimits G-force ceilings used to compute each rating's {@link SafetyVerdict}, or
     *                     {@code null} to use {@link SafetyLimits#DEFAULT}
     */
    public RideRater(PhysicsIntegrator integrator, double gravity, int subSteps, double tickSeconds,
                      int maxTicks, SafetyLimits safetyLimits) {
        if (integrator == null) {
            throw new IllegalArgumentException("integrator must not be null");
        }
        if (gravity <= 0.0D) {
            throw new IllegalArgumentException("gravity must be positive, got " + gravity);
        }
        if (subSteps < 1) {
            throw new IllegalArgumentException("subSteps must be >= 1, got " + subSteps);
        }
        if (tickSeconds <= 0.0D) {
            throw new IllegalArgumentException("tickSeconds must be positive, got " + tickSeconds);
        }
        if (maxTicks < 1) {
            throw new IllegalArgumentException("maxTicks must be >= 1, got " + maxTicks);
        }
        this.integrator = integrator;
        this.gravity = gravity;
        this.subSteps = subSteps;
        this.tickSeconds = tickSeconds;
        this.maxTicks = maxTicks;
        this.safetyLimits = safetyLimits == null ? SafetyLimits.DEFAULT : safetyLimits;
    }

    /** Convenience: standard sub-step count, the real game's tick length, the default tick budget,
     *  and default safety limits — everything a caller needs to supply is {@code integrator} and
     *  {@code gravity}, which must agree with each other regardless. */
    public static RideRater standard(PhysicsIntegrator integrator, double gravity) {
        return new RideRater(integrator, gravity, DEFAULT_SUB_STEPS, RcmcConstants.SECONDS_PER_TICK,
            DEFAULT_MAX_TICKS, SafetyLimits.DEFAULT);
    }

    /** Rates a ride, starting the simulated train from rest at {@code start}. */
    public RideRating rate(TrackNetwork network, RideElementSet elements, TrackRef start, TrainSpec trainSpec) {
        return rate(network, elements, start, trainSpec, 0.0D);
    }

    /** Rates a ride, starting the simulated train at {@code start} with {@code initialVelocity} —
     *  useful for rating a bare track shape with no lift or launch hardware attached yet. */
    public RideRating rate(TrackNetwork network, RideElementSet elements, TrackRef start,
                            TrainSpec trainSpec, double initialVelocity) {
        return RideRating.from(simulate(network, elements, start, trainSpec, initialVelocity), safetyLimits);
    }

    /**
     * Runs the simulated lap and returns the raw statistics, without turning them into a rating —
     * exposed separately so a caller (or a test) can inspect the physical numbers on their own terms.
     *
     * @param elements may be {@code null}, meaning no ride hardware: the train coasts on gravity and
     *                 drag alone, exactly as if an empty {@link RideElementSet} had been supplied
     */
    public RideStatistics simulate(TrackNetwork network, RideElementSet elements, TrackRef start,
                                    TrainSpec trainSpec, double initialVelocity) {
        if (network == null) {
            throw new IllegalArgumentException("network must not be null");
        }
        if (start == null) {
            throw new IllegalArgumentException("start must not be null");
        }
        if (trainSpec == null) {
            throw new IllegalArgumentException("trainSpec must not be null");
        }
        if (!network.hasSection(start.sectionId())) {
            throw new IllegalArgumentException("no section " + start.sectionId() + " in network");
        }
        RideElementSet hardware = elements == null ? new RideElementSet() : elements;

        Train train = new Train(trainSpec, integrator, start, initialVelocity);
        Accumulator acc = new Accumulator();
        acc.seed(network.frameAt(start));

        double targetLapLength = totalNetworkLength(network);
        double travelled = 0.0D;
        double previousVelocity = train.velocity();
        int ticksSimulated = 0;

        while (ticksSimulated < maxTicks) {
            if (!network.hasSection(train.reference().sectionId())) {
                // Track the train sits on vanished out from under it. Cannot happen from this
                // class's own actions (the network is never mutated here), but mirrors
                // TrainManager's identical defensive check rather than assuming it can't.
                break;
            }

            train.setHeld(hardware.isHolding(TRAIN_ID, train));
            double accel = hardware.forTrain(TRAIN_ID, train);
            train.tick(network, accel, subSteps, tickSeconds);
            ticksSimulated++;

            if (train.status() == Train.Status.DEAD_END) {
                // Train.step snaps velocity to exactly zero the instant a train runs off unconnected
                // track (see its own javadoc). That is a legitimate fault to report via finalStatus,
                // but sampling this tick's along-track acceleration would read it as a fabricated
                // multi-g "impact" deceleration — an artefact of how the fault is modelled, not
                // anything a rider actually experienced. Stop without recording it.
                break;
            }

            double velocity = train.velocity();
            travelled += Math.abs(velocity) * tickSeconds;

            TrackRef ref = train.reference();
            TrackSection section = network.section(ref.sectionId());
            if (section != null) {
                sampleTick(acc, section, ref.distance(), velocity, previousVelocity);
            }
            previousVelocity = velocity;

            if (!train.isRunning()) {
                break;
            }
            if (targetLapLength > 0.0D && travelled >= targetLapLength) {
                break;
            }
        }

        return acc.build(tickSeconds, ticksSimulated, travelled, train.status());
    }

    private static double totalNetworkLength(TrackNetwork network) {
        double total = 0.0D;
        for (TrackSection section : network.sections()) {
            total += section.totalLength();
        }
        return total;
    }

    private void sampleTick(Accumulator acc, TrackSection section, double distance, double velocity,
                             double previousVelocity) {
        TrackFrame frame = section.frameAtDistance(distance);
        CurvatureSample curvature = sampleCurvature(section, distance);
        double alongTrackAcceleration = (velocity - previousVelocity) / tickSeconds;

        GForces g = GForces.at(frame, Math.abs(velocity), curvature.magnitude, curvature.direction,
            alongTrackAcceleration, gravity);

        acc.record(frame, g, Math.abs(velocity), tickSeconds, curvature);
    }

    /**
     * Curvature magnitude and the direction toward the centre of curvature at {@code s}, by
     * finite-differencing the tangent {@link #CURVATURE_HALF_STEP} blocks either side.
     *
     * <p>{@code CatmullRomSpline.curvatureAt} gives the magnitude but only as a scalar in
     * spline-parameter space, and {@link GForces#at} needs a world-space direction too.
     * {@code client.hud.RideTelemetry} solves the identical problem for live-ride HUD readings this
     * same way — see its javadoc for the underlying Frenet-formula justification
     * ({@code dT/ds = kappa * N}, so the tangent's rate of change points toward the centre of
     * curvature). It is re-derived locally here rather than depended on, because it lives under the
     * {@code client} package tree and {@code CLAUDE.md} rule 2 forbids common code reaching
     * client-only classes — a design-time rating must be computable with no client present at all,
     * e.g. from a dedicated server. This mirrors the policy {@code track.element} already follows for
     * not reaching into another package's private helpers: a few duplicated lines is a smaller risk
     * than a cross-package or cross-side dependency.</p>
     */
    private static CurvatureSample sampleCurvature(TrackSection section, double s) {
        double total = section.totalLength();
        double lo = Math.max(0.0D, s - CURVATURE_HALF_STEP);
        double hi = Math.min(total, s + CURVATURE_HALF_STEP);
        double span = hi - lo;
        if (span < 1.0e-6D) {
            return new CurvatureSample(0.0D, null);
        }
        Vec3 tangentLo = section.tangentAtDistance(lo);
        Vec3 tangentHi = section.tangentAtDistance(hi);
        Vec3 deltaTangent = tangentHi.subtract(tangentLo);
        double magnitude = deltaTangent.length() / span;
        Vec3 direction = magnitude < 1.0e-9D ? null : deltaTangent.normalize();
        return new CurvatureSample(magnitude, direction);
    }

    /** The world-horizontal "right" direction for {@code forward} — i.e. what {@code TrackFrame.right}
     *  would be on perfectly level, unbanked track — used to classify a turn as left or right
     *  independent of authored bank. {@code null} when {@code forward} is too close to vertical for a
     *  horizontal turn direction to mean anything (a vertical drop or climb). */
    private static Vec3 horizontalRight(Vec3 forward) {
        Vec3 rightHoriz = forward.cross(Vec3.UP);
        return rightHoriz.lengthSquared() < 1.0e-9D ? null : rightHoriz.normalize();
    }

    /** Curvature magnitude plus the (possibly {@code null}) unit direction toward the centre of
     *  curvature — see {@link #sampleCurvature}. */
    private static final class CurvatureSample {
        final double magnitude;
        final Vec3 direction;

        CurvatureSample(double magnitude, Vec3 direction) {
            this.magnitude = magnitude;
            this.direction = direction;
        }
    }

    /** Mutable running totals for one simulated lap, finalised into an immutable {@link RideStatistics}
     *  once the run ends. Package-private implementation detail of {@link RideRater}. */
    private static final class Accumulator {
        double maxSpeed;
        double peakPositiveVerticalG = 1.0D;
        double peakNegativeVerticalG = 1.0D;
        double peakLateralG;
        double peakLongitudinalG;
        double totalAirtimeSeconds;
        double sustainedLateralGSeconds;
        double sustainedHighGSeconds;
        int inversionCount;
        int directionChangeCount;

        private boolean previousUpsideDown;
        private int previousTurnSign;
        private double runningMaxHeight;
        private double maxDropHeight;

        /** Primes height/orientation bookkeeping from the train's starting frame, so a "drop" that
         *  begins right at the station and a ride that never leaves upright both report correctly
         *  even before the first real G-force sample is recorded. */
        void seed(TrackFrame startFrame) {
            runningMaxHeight = startFrame.position.y;
            previousUpsideDown = startFrame.up.y < 0.0D;
        }

        void record(TrackFrame frame, GForces g, double speed, double dt, CurvatureSample curvature) {
            maxSpeed = Math.max(maxSpeed, speed);
            peakPositiveVerticalG = Math.max(peakPositiveVerticalG, g.vertical);
            peakNegativeVerticalG = Math.min(peakNegativeVerticalG, g.vertical);
            peakLateralG = Math.max(peakLateralG, Math.abs(g.lateral));
            peakLongitudinalG = Math.max(peakLongitudinalG, Math.abs(g.longitudinal));

            if (g.isAirtime()) {
                totalAirtimeSeconds += dt;
            }
            sustainedLateralGSeconds += Math.abs(g.lateral) * dt;

            double deviation = Math.max(Math.abs(g.vertical - 1.0D),
                Math.max(Math.abs(g.lateral), Math.abs(g.longitudinal)));
            if (deviation > RatingWeights.INTENSITY_SUSTAINED_G_THRESHOLD) {
                sustainedHighGSeconds += dt;
            }

            boolean upsideDown = frame.up.y < 0.0D;
            if (upsideDown && !previousUpsideDown) {
                inversionCount++;
            }
            previousUpsideDown = upsideDown;

            double height = frame.position.y;
            if (height > runningMaxHeight) {
                runningMaxHeight = height;
            }
            else {
                double drop = runningMaxHeight - height;
                if (drop > maxDropHeight) {
                    maxDropHeight = drop;
                }
            }

            if (curvature.direction != null && curvature.magnitude > RatingWeights.NAUSEA_STRAIGHT_CURVATURE_EPSILON) {
                Vec3 rightHoriz = horizontalRight(frame.forward);
                if (rightHoriz != null) {
                    double dot = curvature.direction.dot(rightHoriz);
                    if (Math.abs(dot) > TURN_DIRECTION_DOT_THRESHOLD) {
                        int turnSign = dot > 0.0D ? 1 : -1;
                        if (previousTurnSign != 0 && turnSign != previousTurnSign) {
                            directionChangeCount++;
                        }
                        previousTurnSign = turnSign;
                    }
                }
            }
        }

        RideStatistics build(double tickSeconds, int ticksSimulated, double travelled, Train.Status finalStatus) {
            double duration = ticksSimulated * tickSeconds;
            double averageSpeed = duration > 0.0D ? travelled / duration : 0.0D;
            return new RideStatistics(maxSpeed, averageSpeed, duration, travelled, maxDropHeight,
                inversionCount, peakPositiveVerticalG, peakNegativeVerticalG, peakLateralG,
                peakLongitudinalG, totalAirtimeSeconds, directionChangeCount, sustainedLateralGSeconds,
                sustainedHighGSeconds, finalStatus, ticksSimulated);
        }
    }
}
