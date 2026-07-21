package com.micatechnologies.minecraft.rcmc.physics.transit;

/**
 * The full metro service cycle for one train: run to the next station, berth, cycle the doors,
 * depart, repeat.
 *
 * <p>This is the train-side sibling of {@code physics.element.StationPlatform}. The platform is
 * track hardware that captures whatever coaster train wanders into its span; this is a controller
 * that travels with a powered train and drives it through service. The state machine is
 * deliberately small — the entire running phase, from full-speed cruising through the braking
 * curve down to the creep into the platform, is just {@link TrainDriver}'s one control law with
 * the current stop's remaining distance fed in, so "approaching" is the only moving phase:</p>
 *
 * <pre>
 *   APPROACHING --(at rest within berthTolerance)--&gt; DOORS_OPENING --(doorOpenTicks)--&gt;
 *       BOARDING --(dwellTicks)--&gt; DOORS_CLOSING --(doorCloseTicks)--&gt; APPROACHING (next stop)
 * </pre>
 *
 * <p>All phase timing is tick counts, never wall-clock — the same determinism contract as
 * {@code RideElement}, and for the same client-prediction reason.</p>
 *
 * <p><b>The caller owns the route.</b> {@code remainingToStop} is supplied per tick, measured
 * along the direction of travel, because computing it requires the track network and (from M3 on)
 * the route through junctions — things this class deliberately knows nothing about. When a stop
 * cycle completes, the caller starts passing the <em>next</em> stop's distance; a distant next
 * stop naturally puts the driver back at line speed, which is why no separate "departing" phase
 * exists. Pass {@link TrainDriver#NO_STOP} to run without a stop (an express pass-through or an
 * empty stretch of line).</p>
 *
 * <p><b>Berthing tolerates imperfection in position, never in speed.</b> The train berths when it
 * is at rest and within {@code berthTolerance} of the stop point — including having overshot it,
 * where reversing back uncommanded is exactly what a real train may not do. Stopping <em>short</em>
 * beyond tolerance needs no special case at all: the driver's stopping curve is still positive
 * there, so a train with motors simply creeps the rest of the way in. Compare
 * {@code StationPlatform}, where a coaster stopped short is documented as stuck — that difference
 * is the traction profile earning its keep.</p>
 *
 * <p>{@link #isHolding} exists for the same reason {@code RideElement.isHolding} does: a train at
 * rest with the doors open is indistinguishable, from inside the physics, from one stalled in a
 * valley — only the controller knows its own intent. It also reports holding while creeping at
 * near-zero speed on approach, because {@code Train} latches {@code VALLEYED} below 0.02 blocks/s
 * on level track, and a metro under active ATO control is never valleyed — it always has
 * motors.</p>
 */
public final class TransitStopController {

    /** Where the train is in its stop-to-stop service cycle. */
    public enum Phase {
        /** Running toward the current stop — cruising, braking, or creeping in, per the driver. */
        APPROACHING,
        /** Berthed; doors moving open. */
        DOORS_OPENING,
        /** Doors open, passengers boarding. */
        BOARDING,
        /** Doors moving closed; departs when they finish. */
        DOORS_CLOSING
    }

    /**
     * Speed below which an approaching train counts as at rest for berthing. Above {@code Train}'s
     * internal 0.02 blocks/s stopped threshold for the reasons {@code StationPlatform}'s
     * {@code STOP_SPEED_EPSILON} documents at length: the controller must decide "arrived" before
     * {@code Train} could decide "stalled" on the same data.
     */
    private static final double BERTH_SPEED_EPSILON = 0.05D;

    /**
     * Speed below which an APPROACHING train reports {@link #isHolding} — deliberately much
     * larger than {@link #BERTH_SPEED_EPSILON}, and the gap is the whole point. Holding intent
     * is evaluated once per tick from the tick's <em>starting</em> velocity, but one tick of
     * service braking removes up to {@code brake · tickSeconds} (0.06 blocks/s at the default
     * 1.2 blocks/s²) — more than the distance between the old 0.05 threshold and {@code Train}'s
     * 0.02 stall latch. A train finishing its stop could therefore read "not held" at 0.08,
     * brake through 0.02 mid-tick, and latch VALLEYED at its own platform — found live at the
     * demo line's terminus, at the exact stop point, on the first real session. The threshold
     * must exceed the largest one-tick speed change with margin; physically it is also simply
     * true — a slow train under active ATO approach control is never stalled, it has motors.
     */
    private static final double HOLDING_SPEED = 0.5D;

    private final TrainDriver driver;
    private final double cruiseSpeed;
    private final double berthTolerance;
    private final int doorOpenTicks;
    private final int dwellTicks;
    private final int doorCloseTicks;

    private Phase phase = Phase.APPROACHING;
    private int phaseTicksRemaining;
    private int stopsServed;

    /**
     * @param driver         the speed-control law; owned by this controller from here on — do not
     *                       also tick it directly, the jerk limiter advances per call
     * @param cruiseSpeed    cruise speed magnitude between stations, blocks/s — must be positive.
     *                       Direction is supplied per tick to {@link #acceleration}, because it
     *                       genuinely changes over a service: at terminus reversal, and whenever
     *                       the track's distance axis flips across an END-to-END join.
     * @param berthTolerance how far from the stop point a train may come to rest and still count
     *                       as berthed, blocks — must be positive. Half a car door's width is a
     *                       sensible order of magnitude.
     * @param doorOpenTicks  ticks the doors take to open — must be {@code >= 0}
     * @param dwellTicks     ticks the doors stay open for boarding — must be {@code >= 0}
     * @param doorCloseTicks ticks the doors take to close — must be {@code >= 0}
     */
    public TransitStopController(TrainDriver driver, double cruiseSpeed, double berthTolerance,
                                 int doorOpenTicks, int dwellTicks, int doorCloseTicks) {
        if (driver == null) {
            throw new IllegalArgumentException("driver is required");
        }
        if (cruiseSpeed <= 0.0D) {
            throw new IllegalArgumentException("cruiseSpeed must be positive, got " + cruiseSpeed);
        }
        if (berthTolerance <= 0.0D) {
            throw new IllegalArgumentException("berthTolerance must be positive, got " + berthTolerance);
        }
        if (doorOpenTicks < 0 || dwellTicks < 0 || doorCloseTicks < 0) {
            throw new IllegalArgumentException("door and dwell tick counts must be >= 0");
        }
        this.driver = driver;
        this.cruiseSpeed = cruiseSpeed;
        this.berthTolerance = berthTolerance;
        this.doorOpenTicks = doorOpenTicks;
        this.dwellTicks = dwellTicks;
        this.doorCloseTicks = doorCloseTicks;
    }

    /**
     * The along-track acceleration to command this tick, blocks/s². Call exactly once per game
     * tick — phase timers and the driver's jerk limiter both advance per call.
     *
     * <p>The two distance limits are deliberately separate inputs, because they mean different
     * things: {@code stationRemaining} is where the train should <em>berth and open its doors</em>;
     * {@code authorityRemaining} is how far signalling <em>permits</em> it to move at all (M4's
     * movement authority — pass {@link TrainDriver#NO_STOP} when unsignalled). The driver brakes
     * against whichever limit is nearer, but berthing tests the station distance alone: a train
     * held at a red signal short of the platform stops there, at rest, doors shut, phase still
     * {@link Phase#APPROACHING} — and proceeds when the authority extends. Folding the two into
     * one number would open the doors in the tunnel.</p>
     *
     * @param velocity           the train's current signed velocity, blocks/s
     * @param direction          direction of travel along the current section's distance axis,
     *                           {@code +1} or {@code -1}; both distances are measured along it
     * @param stationRemaining   distance to the current stop point, blocks
     * @param authorityRemaining distance the train is permitted to travel, blocks
     */
    public double acceleration(double velocity, double direction,
                               double stationRemaining, double authorityRemaining) {
        switch (phase) {
            case APPROACHING:
                if (Math.abs(velocity) <= BERTH_SPEED_EPSILON && stationRemaining <= berthTolerance) {
                    phase = Phase.DOORS_OPENING;
                    phaseTicksRemaining = doorOpenTicks;
                    // The train is at rest and staying that way; a stale braking command must not
                    // ramp back out through the jerk limiter as a phantom push.
                    driver.resetCommand();
                    return advanceDoorPhase();
                }
                double signedCruise = (direction >= 0.0D ? 1.0D : -1.0D) * cruiseSpeed;
                return driver.acceleration(velocity, signedCruise,
                    Math.min(stationRemaining, authorityRemaining));
            case DOORS_OPENING:
            case BOARDING:
            case DOORS_CLOSING:
            default:
                return advanceDoorPhase();
        }
    }

    /**
     * Counts down the current door-cycle phase and rolls into the next when it expires. Always
     * returns zero: a berthed train on level track is held by exactly no force, and
     * {@link #isHolding} — not a token push — is what tells the physics this is intentional.
     */
    private double advanceDoorPhase() {
        while (phaseTicksRemaining == 0) {
            switch (phase) {
                case DOORS_OPENING:
                    phase = Phase.BOARDING;
                    phaseTicksRemaining = dwellTicks;
                    break;
                case BOARDING:
                    phase = Phase.DOORS_CLOSING;
                    phaseTicksRemaining = doorCloseTicks;
                    break;
                case DOORS_CLOSING:
                    // Cycle complete: back to running. The caller sees the phase change and starts
                    // supplying the next stop's distance.
                    stopsServed++;
                    phase = Phase.APPROACHING;
                    return 0.0D;
                default:
                    return 0.0D;
            }
        }
        phaseTicksRemaining--;
        return 0.0D;
    }

    /**
     * Whether this controller is deliberately keeping its train at (or near) rest, as opposed to
     * the train having stalled. Feed to {@code Train.setHeld} via
     * {@code TrainManager.ExternalAcceleration.isHolding}, exactly as the coaster elements do.
     *
     * @param velocity the train's current signed velocity — needed because a slow creep on final
     *                 approach must also count as held, per the class javadoc
     */
    public boolean isHolding(double velocity) {
        if (phase != Phase.APPROACHING) {
            return true;
        }
        return Math.abs(velocity) <= HOLDING_SPEED;
    }

    /** True while passengers can pass through the doorway — the {@link Phase#BOARDING} phase. */
    public boolean doorsOpen() {
        return phase == Phase.BOARDING;
    }

    public Phase phase() {
        return phase;
    }

    /** How many complete stop cycles (berth through doors-closed) this controller has served. */
    public int stopsServed() {
        return stopsServed;
    }

    public double cruiseSpeed() {
        return cruiseSpeed;
    }

    public double berthTolerance() {
        return berthTolerance;
    }
}
