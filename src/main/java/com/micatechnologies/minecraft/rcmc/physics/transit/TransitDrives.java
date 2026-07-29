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
        TrainDriver driver = new TrainDriver(
            new TractionProfile(1.2D, 24.0D, 22.0D),
            1.2D, 2.0D, 1.5D, tickSeconds);
        return new TransitStopController(driver, cruiseSpeed, 0.75D, 30, METRO_DWELL_TICKS, 30);
    }
}
