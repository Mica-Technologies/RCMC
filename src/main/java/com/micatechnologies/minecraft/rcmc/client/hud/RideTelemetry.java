package com.micatechnologies.minecraft.rcmc.client.hud;

import com.micatechnologies.minecraft.rcmc.physics.GForces;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;

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
 * <p><b>Measured at the rider's heart, not the rail</b> — see {@code physics.Heartline}, which
 * finds the curve the rider's chest follows, and how fast it moves, by sampling it either side of
 * the car. {@code RideRater} measures the same way, so the HUD and {@code /rcmc rate} agree. Near an
 * open section's end the sample is cut short and slid inward, which {@link
 * Reading#curvatureUncertain} flags; {@link GForceEffects} only reacts to a smoothed value (see
 * {@link GForceSmoother}), so that washes out.</p>
 */
public final class RideTelemetry {

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
         * True when the curvature sample fell within {@code Heartline.HALF_STEP} of a section
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

        // Measured where the rider is, not on the rail — see Heartline. The rating does the same,
        // so the HUD and /rcmc rate agree.
        com.micatechnologies.minecraft.rcmc.physics.Heartline.Sample heart =
            com.micatechnologies.minecraft.rcmc.physics.Heartline.at(section, s);
        boolean uncertain = heart.clipped;
        GForces gForces = GForces.at(frame, Math.abs(velocity) * heart.speedFactor, heart.curvature,
            heart.direction, alongTrackAcceleration, gravity);
        return new Reading(frame, Math.abs(velocity), gForces, uncertain);
    }
}
