package com.micatechnologies.minecraft.rcmc;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

/**
 * Forge {@link Configuration}-backed settings, loaded once in
 * {@link Rcmc#preInit(net.minecraftforge.fml.common.event.FMLPreInitializationEvent)}.
 *
 * <p>Values are read into static fields at load time rather than queried per-tick: the
 * physics integrator runs for every car of every train every tick, and
 * {@code Configuration.get(...)} does string lookups and I/O bookkeeping that have no place
 * in that path.</p>
 *
 * <p><b>Server authority.</b> Everything in the {@code physics} category affects simulation
 * results, so it must match between client and server or riders will visibly desync. The
 * server's values are the truth; the client's copies are overwritten from a sync packet on
 * join once that packet exists. Client-only presentation settings belong in a separate
 * {@code client} category that is deliberately never synced.</p>
 */
public final class RcmcConfig {

    public static final String CATEGORY_PHYSICS = "physics";
    public static final String CATEGORY_CLIENT = "client";

    /**
     * Downward acceleration in blocks/s². Real gravity (9.81) reads far too floaty at
     * Minecraft's one-block-per-metre scale, where a "tall" coaster is 40 blocks rather
     * than 40 metres of real steel. Tune here rather than in code.
     */
    public static double gravity = 9.81D;

    /**
     * Fraction of speed lost per second to rolling resistance and drivetrain friction,
     * applied as an exponential decay so it is timestep-independent.
     */
    public static double rollingResistance = 0.01D;

    /**
     * Quadratic air-drag coefficient (per block of speed, per second). Dominates at the
     * high end of a drop and is what stops a well-designed circuit from accumulating speed
     * forever across laps.
     */
    public static double airDrag = 0.0015D;

    /**
     * Number of physics sub-steps per Minecraft tick. One 50 ms step is far too coarse at
     * coaster speeds — a train doing 30 blocks/s covers 1.5 blocks per tick, enough to cut
     * the corner on a tight helix. Sub-stepping is the cheapest fix and costs only integrator
     * time, not network traffic.
     */
    public static int physicsSubSteps = 4;

    /**
     * Hard ceiling on train speed in blocks/s. Exists as a safety valve, not a design
     * target: beyond roughly this speed the entity movement and tracking code in 1.12.2
     * starts producing artefacts that no amount of interpolation hides.
     */
    public static double maxSpeed = 60.0D;

    /** Whether the client applies camera roll to riders in banked track and inversions. */
    public static boolean enableCameraRoll = true;

    private static Configuration config;

    private RcmcConfig() {
        throw new AssertionError("No instances.");
    }

    public static void init(File configFile) {
        config = new Configuration(configFile);
        load();
    }

    private static void load() {
        config.load();

        config.addCustomCategoryComment(CATEGORY_PHYSICS,
            "Coaster physics simulation. These values change simulation RESULTS, so on a "
                + "multiplayer server the server's copy is authoritative — a client with "
                + "different values will visibly desync from the train it is riding.");
        config.addCustomCategoryComment(CATEGORY_CLIENT,
            "Client-side presentation only. Never synced; safe to differ per player.");

        gravity = config.get(CATEGORY_PHYSICS, "gravity", gravity,
            "Downward acceleration in blocks/s^2.").getDouble();
        rollingResistance = config.get(CATEGORY_PHYSICS, "rollingResistance", rollingResistance,
            "Fraction of speed lost per second to rolling resistance (exponential decay).").getDouble();
        airDrag = config.get(CATEGORY_PHYSICS, "airDrag", airDrag,
            "Quadratic air drag coefficient.").getDouble();
        physicsSubSteps = config.get(CATEGORY_PHYSICS, "physicsSubSteps", physicsSubSteps,
            "Physics sub-steps per game tick. Higher is more accurate on tight curves and "
                + "costs CPU only, not bandwidth.", 1, 32).getInt();
        maxSpeed = config.get(CATEGORY_PHYSICS, "maxSpeed", maxSpeed,
            "Hard speed ceiling in blocks/s.").getDouble();

        enableCameraRoll = config.get(CATEGORY_CLIENT, "enableCameraRoll", enableCameraRoll,
            "Roll the rider's camera with banked track and inversions. Disable if it causes "
                + "motion discomfort.").getBoolean();

        if (config.hasChanged()) {
            config.save();
        }
    }
}
