package com.micatechnologies.minecraft.rcmc.client.hud;

import com.micatechnologies.minecraft.rcmc.physics.GForces;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;

/**
 * Turns a train's raw simulation state into the numbers a rider actually feels: speed and the
 * three {@link GForces} axes.
 *
 * <p><b>Free of Minecraft types</b>, exactly like the {@code physics} and {@code track} packages
 * it reads from (see {@code CLAUDE.md}). It lives under {@code client.hud} rather than one of
 * those packages only because it exists purely to serve the rider-feedback use case; being
 * Minecraft-free is what makes {@link #compute} unit-testable on a bare JVM, the same reasoning
 * that keeps {@code client.render.track.TrackMeshBuilder} pure despite also living under
 * {@code client}.</p>
 *
 * <p><b>Curvature direction — an honest estimate, not an exact result.</b> {@link GForces#at}
 * needs a unit vector toward the centre of curvature, but nothing downstream of
 * {@code CatmullRomSpline} exposes the curve's second derivative as a vector — only
 * {@code curvatureAt}, a scalar magnitude. {@code TrackValidator} hits the identical problem for
 * its lateral-G warning and solves it by finite-differencing the tangent; this does the same
 * thing and gets the direction for free out of the same difference. The Frenet formula
 * {@code dT/ds = kappa * N} says the tangent's rate of change points toward the centre of
 * curvature, so sampling the tangent {@link #CURVATURE_HALF_STEP} blocks ahead of and behind the
 * car and normalising {@code tangent(ahead) - tangent(behind)} gives both the direction and,
 * divided by the sampled arc length, the magnitude — in one finite difference.</p>
 *
 * <p><b>Where this degrades, and how it is flagged.</b> Near an open section's unconnected ends,
 * or across a closed circuit's start/finish seam, {@code TrackSection.tangentAtDistance} clamps
 * one of the two samples short, so the difference quietly narrows to less than
 * {@code 2 * CURVATURE_HALF_STEP} of track on one side only. That biases the magnitude low and
 * can make the direction noisier right at the boundary. This case sets
 * {@link Reading#curvatureUncertain}, but is deliberately <em>not</em> corrected for — a fully
 * robust fix needs {@code TrackSection} to expose its own wrap-vs-clamp logic, which it does not,
 * and duplicating that logic here would silently drift out of sync with it. In practice this is a
 * minor approximation: {@link GForceEffects} only reacts to a multi-second-smoothed value (see
 * {@link GForceSmoother}), so a boundary artefact lasting a fraction of a section washes out
 * completely; only a HUD reading grabbed at that exact instant could show a very slightly wrong
 * number for one frame.</p>
 */
public final class RideTelemetry {

    /**
     * Distance, in blocks, sampled on either side of the car to estimate curvature. Small enough
     * to stay local — curvature genuinely changes over a couple of blocks on a tight element —
     * and large enough that the tangent difference is not swamped by floating-point noise; the
     * same order of magnitude as {@code TrackValidator}'s default sample spacing.
     */
    static final double CURVATURE_HALF_STEP = 0.5D;

    private RideTelemetry() {
        throw new AssertionError("No instances.");
    }

    /** One rider's-eye snapshot of the physics: speed and the three felt G-force axes. */
    public static final class Reading {

        /** The sampled car's full orientation, including authored bank. */
        public final TrackFrame frame;

        /** Along-track speed, always non-negative — direction of travel does not change how it feels. */
        public final double speedBlocksPerSecond;

        public final GForces gForces;

        /**
         * True when the curvature sample fell within {@link #CURVATURE_HALF_STEP} of a section
         * boundary, where the direction/magnitude estimate is one-sided rather than centred. See
         * the class javadoc.
         */
        public final boolean curvatureUncertain;

        Reading(TrackFrame frame, double speedBlocksPerSecond, GForces gForces, boolean curvatureUncertain) {
            this.frame = frame;
            this.speedBlocksPerSecond = speedBlocksPerSecond;
            this.gForces = gForces;
            this.curvatureUncertain = curvatureUncertain;
        }
    }

    /**
     * Computes a {@link Reading} for one car of {@code train}, or {@code null} if the car's
     * section is not present in {@code network} — the same transient
     * {@code EntityCoasterCar.onUpdate()} tolerates when track and train sync packets land a tick
     * apart on the client.
     *
     * @param train            the rider's train
     * @param network          the network the train's section lives in
     * @param carIndex         which car the rider is on
     * @param previousVelocity the train's velocity, in blocks/s, at the previous sample — used to
     *                         derive {@code dv/dt} for the longitudinal axis. Pass the train's
     *                         current velocity for the very first sample of a ride, which
     *                         correctly reads as zero acceleration rather than a spurious launch
     *                         spike from an undefined "previous" state.
     * @param dtSeconds        time between this sample and the previous one, in seconds
     * @param gravity          downward acceleration in blocks/s², must be positive (see
     *                         {@link GForces#at})
     */
    public static Reading compute(Train train, TrackNetwork network, int carIndex,
                                   double previousVelocity, double dtSeconds, double gravity) {
        TrackRef ref = train.refOfCar(network, carIndex);
        TrackSection section = network.section(ref.sectionId());
        if (section == null) {
            return null;
        }

        double s = ref.distance();
        TrackFrame frame = section.frameAtDistance(s);

        double velocity = train.velocity();
        double alongTrackAcceleration = dtSeconds > 0.0D ? (velocity - previousVelocity) / dtSeconds : 0.0D;

        double total = section.totalLength();
        double lo = Math.max(0.0D, s - CURVATURE_HALF_STEP);
        double hi = Math.min(total, s + CURVATURE_HALF_STEP);
        double sampledSpan = hi - lo;

        // Within CURVATURE_HALF_STEP of either end (open section) means at least one sample got
        // clamped short of a full half-step — see the class javadoc on why this is flagged and
        // not corrected.
        boolean uncertain = sampledSpan < CURVATURE_HALF_STEP * 2.0D - 1.0e-6D;

        double curvature = 0.0D;
        Vec3 direction = null;
        if (sampledSpan > 1.0e-6D) {
            Vec3 tangentLo = section.tangentAtDistance(lo);
            Vec3 tangentHi = section.tangentAtDistance(hi);
            Vec3 deltaTangent = tangentHi.subtract(tangentLo);
            curvature = deltaTangent.length() / sampledSpan;
            direction = deltaTangent.normalize();
        }

        GForces gForces = GForces.at(frame, velocity, curvature, direction, alongTrackAcceleration, gravity);
        return new Reading(frame, Math.abs(velocity), gForces, uncertain);
    }
}
