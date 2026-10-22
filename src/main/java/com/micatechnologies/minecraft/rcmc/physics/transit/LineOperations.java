package com.micatechnologies.minecraft.rcmc.physics.transit;

/**
 * How a line is run, as opposed to where it goes: how long trains stand at each platform, and the
 * least time between two trains leaving a platform in the same direction.
 *
 * <p>Kept apart from {@link TransitLine}, which is the route, for the same reason signals are: an
 * operator retiming a line is not rebuilding it, and should not have to recreate it to do so.</p>
 */
public final class LineOperations {

    /** Stock metro dwell, no headway regulation. */
    public static final LineOperations DEFAULT =
        new LineOperations(TransitDrives.METRO_DWELL_TICKS, 0);

    /** Longest dwell or headway accepted, in ticks: ten minutes. */
    public static final int MAX_TICKS = 20 * 600;

    private final int dwellTicks;
    private final int headwayTicks;

    /** Line speed for trains entering service on this line, blocks/s; {@code 0} for the default. */
    private final double maxSpeed;

    /** Traction acceleration, blocks/s²; {@code 0} for the stock metro drive's. */
    private final double acceleration;

    /** Service braking, blocks/s²; {@code 0} for the stock metro drive's. */
    private final double deceleration;

    /**
     * @param dwellTicks   ticks the doors stay open at each stop, {@code 0..MAX_TICKS}
     * @param headwayTicks least ticks between departures from one platform in one direction;
     *                     {@code 0} turns regulation off
     */
    public LineOperations(int dwellTicks, int headwayTicks) {
        this(dwellTicks, headwayTicks, 0.0D, 0.0D, 0.0D);
    }

    /**
     * @param maxSpeed     line speed, blocks/s, or {@code 0} for the default
     * @param acceleration traction acceleration, blocks/s², or {@code 0} for the default
     * @param deceleration service braking, blocks/s², or {@code 0} for the default
     */
    public LineOperations(int dwellTicks, int headwayTicks, double maxSpeed, double acceleration,
                          double deceleration) {
        if (dwellTicks < 0 || dwellTicks > MAX_TICKS || headwayTicks < 0 || headwayTicks > MAX_TICKS) {
            throw new IllegalArgumentException("dwell and headway must be 0.." + MAX_TICKS
                + " ticks, got " + dwellTicks + " and " + headwayTicks);
        }
        if (maxSpeed < 0.0D || acceleration < 0.0D || deceleration < 0.0D) {
            throw new IllegalArgumentException("speed, acceleration and deceleration must not be negative");
        }
        this.dwellTicks = dwellTicks;
        this.headwayTicks = headwayTicks;
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;
        this.deceleration = deceleration;
    }

    public int dwellTicks() {
        return dwellTicks;
    }

    public int headwayTicks() {
        return headwayTicks;
    }

    public boolean regulatesHeadway() {
        return headwayTicks > 0;
    }

    public double maxSpeed() {
        return maxSpeed;
    }

    public double acceleration() {
        return acceleration;
    }

    public double deceleration() {
        return deceleration;
    }

    public LineOperations withDwellTicks(int ticks) {
        return new LineOperations(ticks, headwayTicks, maxSpeed, acceleration, deceleration);
    }

    public LineOperations withHeadwayTicks(int ticks) {
        return new LineOperations(dwellTicks, ticks, maxSpeed, acceleration, deceleration);
    }

    public LineOperations withMaxSpeed(double speed) {
        return new LineOperations(dwellTicks, headwayTicks, speed, acceleration, deceleration);
    }

    public LineOperations withAcceleration(double rate) {
        return new LineOperations(dwellTicks, headwayTicks, maxSpeed, rate, deceleration);
    }

    public LineOperations withDeceleration(double rate) {
        return new LineOperations(dwellTicks, headwayTicks, maxSpeed, acceleration, rate);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LineOperations)) {
            return false;
        }
        LineOperations other = (LineOperations) o;
        return other.dwellTicks == dwellTicks && other.headwayTicks == headwayTicks
            && other.maxSpeed == maxSpeed && other.acceleration == acceleration
            && other.deceleration == deceleration;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(dwellTicks, headwayTicks, maxSpeed, acceleration, deceleration);
    }

    @Override
    public String toString() {
        return "LineOperations{dwell=" + dwellTicks + ", headway=" + headwayTicks + ", maxSpeed="
            + maxSpeed + ", accel=" + acceleration + ", decel=" + deceleration + '}';
    }
}
