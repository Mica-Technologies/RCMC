package com.micatechnologies.minecraft.rcmc.physics;

import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;

/**
 * What a rider actually feels at a point on the track, in g.
 *
 * <p>These three numbers are the vocabulary of coaster design. Everything a ride is praised or
 * condemned for — airtime, a wrenching turn, a snappy launch — is one of them out of the ordinary
 * range, and the RCT-style excitement/intensity/nausea ratings (Phase 8.1) are functions of them.</p>
 *
 * <p><b>Sign conventions</b>, chosen to match how riders and the coaster industry talk:</p>
 * <ul>
 *   <li>{@link #vertical} — <b>+1 g is sitting still on level track.</b> Above 1 is being pressed
 *       into the seat (a valley); below 1 is lightening; <b>below 0 is airtime</b>, the most
 *       sought-after sensation in the hobby, where the restraints rather than the seat hold you in.</li>
 *   <li>{@link #lateral} — sideways, positive to the rider's right. <b>Zero is the goal</b>: a
 *       properly banked turn converts what would be side load into vertical load. Non-zero lateral
 *       is precisely the "unbanked turn" discomfort the nausea rating should punish.</li>
 *   <li>{@link #longitudinal} — along the track, positive under acceleration. Launches and brake
 *       runs live here.</li>
 * </ul>
 *
 * <p>Immutable, and free of Minecraft types like the rest of {@code physics}, so a whole circuit's
 * G profile can be computed and asserted in a unit test.</p>
 */
public final class GForces {

    public final double vertical;
    public final double lateral;
    public final double longitudinal;

    public GForces(double vertical, double lateral, double longitudinal) {
        this.vertical = vertical;
        this.lateral = lateral;
        this.longitudinal = longitudinal;
    }

    /**
     * Loads at a point, given the local geometry and motion.
     *
     * <p>The model: the centripetal acceleration needed to hold the train on the curve is
     * {@code v²·kappa}, directed toward the centre of curvature. Add gravity, express the sum in the
     * car's own frame, and that is what the rider's body has to react against.</p>
     *
     * <p>The bank angle does not appear as a separate term — it is already baked into the frame's
     * {@code up} and {@code right} axes. That is the payoff of banking being an authored property
     * of the track carried through the transported frame: "how much of the turn did the bank
     * absorb" needs no special case, it falls out of projecting onto the rotated axes.</p>
     *
     * @param frame                 the car's orientation, including authored bank
     * @param speed                 along-track speed in blocks/s
     * @param curvature             {@code kappa} at this point, in inverse blocks
     * @param curvatureDirection    unit vector toward the centre of curvature, perpendicular to
     *                              travel. Ignored when {@code curvature} is ~0.
     * @param alongTrackAcceleration current {@code dv/dt} in blocks/s²
     * @param gravity               downward acceleration in blocks/s²
     */
    public static GForces at(TrackFrame frame, double speed, double curvature,
                             com.micatechnologies.minecraft.rcmc.track.math.Vec3 curvatureDirection,
                             double alongTrackAcceleration, double gravity) {
        if (gravity <= 0.0D) {
            throw new IllegalArgumentException("gravity must be positive, got " + gravity);
        }

        // Centripetal acceleration required to follow the curve, as a world-space vector.
        double centripetal = speed * speed * curvature;
        com.micatechnologies.minecraft.rcmc.track.math.Vec3 required =
            curvature < 1.0e-9D || curvatureDirection == null
                ? com.micatechnologies.minecraft.rcmc.track.math.Vec3.ZERO
                : curvatureDirection.normalize().scale(centripetal);

        // The rider feels the reaction to (required acceleration - gravity). On level straight
        // track that reduces to +1 g upward, which is why the vertical convention has 1 as rest.
        com.micatechnologies.minecraft.rcmc.track.math.Vec3 felt =
            required.subtract(new com.micatechnologies.minecraft.rcmc.track.math.Vec3(
                0.0D, -gravity, 0.0D));

        return new GForces(
            felt.dot(frame.up) / gravity,
            felt.dot(frame.right) / gravity,
            alongTrackAcceleration / gravity);
    }

    /** True when vertical load has gone negative — the rider is out of their seat. */
    public boolean isAirtime() {
        return vertical < 0.0D;
    }

    /**
     * Largest magnitude across all three axes, as a crude single-number intensity proxy.
     *
     * <p>Deliberately crude: the real intensity rating (Phase 8.1) weights the axes differently
     * and accounts for duration, because two seconds at 4 g is a very different experience from an
     * instant at 4 g. This is for quick comparisons and debug readouts only.</p>
     */
    public double peakMagnitude() {
        return Math.max(Math.abs(vertical), Math.max(Math.abs(lateral), Math.abs(longitudinal)));
    }

    @Override
    public String toString() {
        return String.format("GForces{vert=%.2fg, lat=%.2fg, long=%.2fg}",
            vertical, lateral, longitudinal);
    }
}
