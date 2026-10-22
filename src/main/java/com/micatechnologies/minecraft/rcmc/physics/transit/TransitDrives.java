package com.micatechnologies.minecraft.rcmc.physics.transit;

/**
 * Stock drive configurations — the traction, braking and dwell numbers a metro service runs with.
 *
 * <p><b>Why this exists as a class rather than a constructor call at each site.</b> There are two
 * places a service can begin: an operator typing {@code /rcmc line start}, and a saved world coming
 * back with a service already running. Those must produce a train that drives <em>identically</em>.
 * If they did not, a line would quietly change character across a restart — accelerating harder,
 * dwelling longer, braking later — and the cause would be almost impossible to spot, because both
 * paths would look correct in isolation.</p>
 *
 * <p>Pure Java like the rest of {@code physics.transit}, so the numbers are reachable from the
 * codec without dragging Minecraft types into a save path, and testable without a game.</p>
 *
 * <p>These are stock defaults, not per-train configuration. Per-stock drive profiles — a heavy
 * long consist accelerating differently from a compact one — belong in the train spec when they
 * arrive, and this class is where the default they fall back to should live.</p>
 */
public final class TransitDrives {

    /**
     * Dwell at a station, in ticks — ten seconds.
     *
     * <p>Five was the original value and is a realistic off-peak dwell. It was fine while nobody
     * could board, and became wrong the moment they could: a dwell that expires while a player is
     * still walking down the platform reads as the doors being broken, not as a tight schedule.</p>
     */
    public static final int METRO_DWELL_TICKS = 200;

    private TransitDrives() {
        throw new AssertionError("No instances.");
    }

    /**
     * The default metro drive: a jerk-limited ATO controller cruising at {@code cruiseSpeed}.
     *
     * <p>{@code tickSeconds} is a parameter rather than a constant read from the mod's own config,
     * because that is how every other class in this package takes it — {@code TrainDriver},
     * {@code DriveTyres}, {@code ChainLift}. Reaching for the constant here would be the first
     * dependency from {@code physics} onto the build-generated mod metadata, for a number the
     * caller already has.</p>
     *
     * @param cruiseSpeed line speed between stations, blocks/s
     * @param tickSeconds length of a game tick in seconds
     */
    public static TransitStopController metro(double cruiseSpeed, double tickSeconds) {
        return metro(cruiseSpeed, tickSeconds, 0.0D, 0.0D);
    }

    /** Stock traction acceleration, blocks/s². */
    public static final double METRO_ACCELERATION = 1.2D;

    /** Stock service braking, blocks/s². */
    public static final double METRO_DECELERATION = 1.2D;

    /**
     * The metro drive with a line's own rates.
     *
     * <p>Propulsion used to cut out at a fixed 22 blocks/s, so any cruise speed above that was
     * never reached however it was set. The cut-out now sits just above the cruise speed, and the
     * traction power grows with it so a fast line still pulls away at line speed — though, as on a
     * real train, acceleration tails off as speed rises.</p>
     *
     * @param acceleration traction acceleration, blocks/s², or {@code 0} for the stock rate
     * @param deceleration service braking, blocks/s², or {@code 0} for the stock rate
     */
    public static TransitStopController metro(double cruiseSpeed, double tickSeconds,
                                              double acceleration, double deceleration) {
        double accel = acceleration > 0.0D ? acceleration : METRO_ACCELERATION;
        double brake = deceleration > 0.0D ? deceleration : METRO_DECELERATION;
        double power = Math.max(24.0D * accel / METRO_ACCELERATION, 0.8D * accel * cruiseSpeed);
        double cutOut = Math.max(22.0D, cruiseSpeed + 2.0D);
        TrainDriver driver = new TrainDriver(new TractionProfile(accel, power, cutOut),
            brake, 2.0D, 1.5D, tickSeconds);
        return new TransitStopController(driver, cruiseSpeed, 0.75D, 30, METRO_DWELL_TICKS, 30);
    }
}
