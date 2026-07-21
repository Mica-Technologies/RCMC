package com.micatechnologies.minecraft.rcmc.physics.transit;

/**
 * The tractive-effort curve of a powered rail vehicle: how much propulsive acceleration its
 * motors can deliver at a given speed.
 *
 * <p>This is the piece of physics that separates a metro train from a coaster train. A coaster is
 * driven by track-side hardware ({@code physics.element}) and otherwise coasts; a metro carries
 * its motors with it, and those motors have a characteristic shape every electric traction system
 * shares:</p>
 *
 * <ul>
 *   <li><b>Constant force below base speed.</b> At low speed the limit is torque (or wheel-rail
 *       adhesion, whichever bites first) — the motor can deliver its full tractive force, so
 *       acceleration is flat at {@link #maxAcceleration()}.</li>
 *   <li><b>Constant power above base speed.</b> Past {@linkplain #baseSpeed() base speed} the
 *       limit is the traction package's power rating: force falls off as {@code P / v}, which is
 *       why a metro that leaps away from a platform still takes a long time to find its last few
 *       blocks per second of top speed.</li>
 *   <li><b>Nothing at or beyond max service speed.</b> The propulsion cuts out rather than chase
 *       an overspeed.</li>
 * </ul>
 *
 * <p>Everything is per unit mass, matching the rest of the physics package: "force" is expressed
 * directly as acceleration (blocks/s²) and "power" as specific power (blocks²/s³), so no mass
 * parameter ever appears. Immutable, pure Java, deterministic — see {@code RideElement}'s
 * determinism note, which binds this package equally.</p>
 */
public final class TractionProfile {

    private final double maxAcceleration;
    private final double ratedPower;
    private final double maxServiceSpeed;

    /**
     * @param maxAcceleration torque/adhesion-limited propulsive acceleration, blocks/s² — must be
     *                        positive. Typical metro stock accelerates at around 1.0–1.3 m/s².
     * @param ratedPower      specific power of the traction package, blocks²/s³ — must be
     *                        positive. Sets where constant force gives way to constant power:
     *                        {@code baseSpeed = ratedPower / maxAcceleration}.
     * @param maxServiceSpeed speed at and above which propulsion cuts out entirely, blocks/s —
     *                        must be positive. A profile whose base speed is at or above this is
     *                        legal; it is simply constant-force over its whole working range.
     */
    public TractionProfile(double maxAcceleration, double ratedPower, double maxServiceSpeed) {
        if (maxAcceleration <= 0.0D) {
            throw new IllegalArgumentException("maxAcceleration must be positive, got " + maxAcceleration);
        }
        if (ratedPower <= 0.0D) {
            throw new IllegalArgumentException("ratedPower must be positive, got " + ratedPower);
        }
        if (maxServiceSpeed <= 0.0D) {
            throw new IllegalArgumentException("maxServiceSpeed must be positive, got " + maxServiceSpeed);
        }
        this.maxAcceleration = maxAcceleration;
        this.ratedPower = ratedPower;
        this.maxServiceSpeed = maxServiceSpeed;
    }

    /**
     * Propulsive acceleration available at {@code speed}, blocks/s². Direction-agnostic — the
     * sign of {@code speed} is ignored, because the curve is symmetric for a vehicle that can run
     * both ways (which a metro, unlike most coaster hardware, can).
     */
    public double availableAcceleration(double speed) {
        double s = Math.abs(speed);
        if (s >= maxServiceSpeed) {
            return 0.0D;
        }
        if (s <= baseSpeed()) {
            return maxAcceleration;
        }
        return ratedPower / s;
    }

    /** Speed at which the constant-force region ends and constant power takes over, blocks/s. */
    public double baseSpeed() {
        return ratedPower / maxAcceleration;
    }

    public double maxAcceleration() {
        return maxAcceleration;
    }

    public double ratedPower() {
        return ratedPower;
    }

    public double maxServiceSpeed() {
        return maxServiceSpeed;
    }

    @Override
    public String toString() {
        return "TractionProfile{a=" + maxAcceleration + ", P=" + ratedPower
            + ", vmax=" + maxServiceSpeed + '}';
    }
}
