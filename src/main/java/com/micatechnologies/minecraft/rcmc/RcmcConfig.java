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

    /** Whether the live speed/G-force/height readout is drawn while riding. Pure convenience. */
    public static boolean enableRideHud = true;

    /**
     * Time constant, in seconds, of the low-pass filter {@code GForceSmoother} applies before
     * {@code GForceEffects} reacts to a G reading. Larger values make the screen effects slower
     * to ramp up (and slower to release) but more resistant to single-tick spikes; see
     * {@code GForceSmoother}'s javadoc for the exact filter.
     */
    public static double gForceSmoothingSeconds = 1.0D;

    /** Whether sustained vertical G drives the grey-out/red-out screen tint. */
    public static boolean enableGForceTint = true;

    /**
     * Sustained vertical G above which the grey-out tint starts to appear. 4.5g is roughly where
     * real pilots and coaster riders begin greying out under sustained positive Gz.
     */
    public static double grayOutThresholdG = 4.5D;

    /** Sustained G above {@link #grayOutThresholdG} over which the grey-out ramps to full intensity. */
    public static double grayOutRangeG = 1.5D;

    /**
     * Sustained vertical G at or below which the red-out tint starts to appear. Negative Gz
     * (airtime taken to an extreme) pools blood toward the head at a smaller magnitude than
     * positive Gz causes greying, hence the smaller-magnitude default than {@link #grayOutThresholdG}.
     */
    public static double redOutThresholdG = -2.0D;

    /** Sustained G below {@link #redOutThresholdG} over which the red-out ramps to full intensity. */
    public static double redOutRangeG = 1.5D;

    /**
     * Peak alpha (0-1) of the grey-out/red-out tint at full ramp. Deliberately modest by default —
     * this is a comfort cue, not meant to actually blind the rider.
     */
    public static double gForceTintMaxAlpha = 0.4D;

    /** Whether sustained longitudinal G kicks the camera FOV. */
    public static boolean enableGForceFovKick = true;

    /** Degrees of FOV change per g of sustained longitudinal acceleration. */
    public static double fovKickDegreesPerG = 2.0D;

    /** Hard cap, in degrees, on the FOV kick in either direction. */
    public static double fovKickMaxDegrees = 8.0D;

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

        enableRideHud = config.get(CATEGORY_CLIENT, "enableRideHud", enableRideHud,
            "Show the live speed/G-force/height readout while riding.").getBoolean();
        gForceSmoothingSeconds = config.get(CATEGORY_CLIENT, "gForceSmoothingSeconds",
            gForceSmoothingSeconds, "Time constant, in seconds, of the smoothing applied before "
                + "G-force screen effects react. Higher is slower to ramp up and slower to "
                + "release, and more resistant to single-tick spikes.", 0.05D, 10.0D).getDouble();

        enableGForceTint = config.get(CATEGORY_CLIENT, "enableGForceTint", enableGForceTint,
            "Tint the screen grey/red under sustained heavy G. A motion-comfort and "
                + "accessibility setting, not decoration — disable freely.").getBoolean();
        grayOutThresholdG = config.get(CATEGORY_CLIENT, "grayOutThresholdG", grayOutThresholdG,
            "Sustained vertical g above which the grey-out tint starts to appear.").getDouble();
        grayOutRangeG = config.get(CATEGORY_CLIENT, "grayOutRangeG", grayOutRangeG,
            "Sustained g above grayOutThresholdG over which the grey-out ramps to full "
                + "intensity.", 0.1D, 20.0D).getDouble();
        redOutThresholdG = config.get(CATEGORY_CLIENT, "redOutThresholdG", redOutThresholdG,
            "Sustained vertical g at or below which the red-out tint starts to appear.").getDouble();
        redOutRangeG = config.get(CATEGORY_CLIENT, "redOutRangeG", redOutRangeG,
            "Sustained g below redOutThresholdG over which the red-out ramps to full intensity.",
            0.1D, 20.0D).getDouble();
        gForceTintMaxAlpha = config.get(CATEGORY_CLIENT, "gForceTintMaxAlpha", gForceTintMaxAlpha,
            "Peak opacity (0-1) of the grey-out/red-out tint at full ramp.", 0.0D, 1.0D).getDouble();

        enableGForceFovKick = config.get(CATEGORY_CLIENT, "enableGForceFovKick", enableGForceFovKick,
            "Kick the camera FOV with sustained longitudinal G (launches, brake runs). A "
                + "motion-comfort setting, not decoration — disable freely.").getBoolean();
        fovKickDegreesPerG = config.get(CATEGORY_CLIENT, "fovKickDegreesPerG", fovKickDegreesPerG,
            "Degrees of FOV change per g of sustained longitudinal acceleration.").getDouble();
        fovKickMaxDegrees = config.get(CATEGORY_CLIENT, "fovKickMaxDegrees", fovKickMaxDegrees,
            "Hard cap, in degrees, on the FOV kick in either direction.", 0.0D, 45.0D).getDouble();

        if (config.hasChanged()) {
            config.save();
        }
    }
}
