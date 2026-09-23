package com.micatechnologies.minecraft.rcmc.net;

import com.micatechnologies.minecraft.rcmc.RcmcConfig;
import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;

/**
 * The values under {@code physics} in the config — the ones that change simulation results.
 *
 * <p>A client predicts every train between the server's corrections with its own integrator, so
 * it has to integrate with the server's constants, not its own: a client whose config said lower
 * drag would run each train ahead and be pulled back four times a second. The server sends its
 * copy on join ({@link PacketPhysicsSettings}); the client keeps it apart from {@link RcmcConfig}
 * (see {@code ClientPhysics}) rather than writing it over the config, because on a singleplayer
 * client those static fields are shared with the integrated server.</p>
 */
public final class PhysicsSettings {

    public final double gravity;
    public final double rollingResistance;
    public final double airDrag;
    public final double maxSpeed;
    public final int subSteps;

    public PhysicsSettings(double gravity, double rollingResistance, double airDrag,
                           double maxSpeed, int subSteps) {
        this.gravity = gravity;
        this.rollingResistance = rollingResistance;
        this.airDrag = airDrag;
        this.maxSpeed = maxSpeed;
        this.subSteps = subSteps;
    }

    /** This side's own config. Authoritative on a server. */
    public static PhysicsSettings fromConfig() {
        return new PhysicsSettings(RcmcConfig.gravity, RcmcConfig.rollingResistance,
            RcmcConfig.airDrag, RcmcConfig.maxSpeed, RcmcConfig.physicsSubSteps);
    }

    public PhysicsIntegrator integrator() {
        return new PhysicsIntegrator(gravity, rollingResistance, airDrag, maxSpeed);
    }

    @Override
    public String toString() {
        return "PhysicsSettings{g=" + gravity + ", rolling=" + rollingResistance + ", drag="
            + airDrag + ", max=" + maxSpeed + ", subSteps=" + subSteps + '}';
    }
}
