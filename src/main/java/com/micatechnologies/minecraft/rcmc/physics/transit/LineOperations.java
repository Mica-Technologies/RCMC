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

    /**
     * @param dwellTicks   ticks the doors stay open at each stop, {@code 0..MAX_TICKS}
     * @param headwayTicks least ticks between departures from one platform in one direction;
     *                     {@code 0} turns regulation off
     */
    public LineOperations(int dwellTicks, int headwayTicks) {
        if (dwellTicks < 0 || dwellTicks > MAX_TICKS || headwayTicks < 0 || headwayTicks > MAX_TICKS) {
            throw new IllegalArgumentException("dwell and headway must be 0.." + MAX_TICKS
                + " ticks, got " + dwellTicks + " and " + headwayTicks);
        }
        this.dwellTicks = dwellTicks;
        this.headwayTicks = headwayTicks;
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

    public LineOperations withDwellTicks(int ticks) {
        return new LineOperations(ticks, headwayTicks);
    }

    public LineOperations withHeadwayTicks(int ticks) {
        return new LineOperations(dwellTicks, ticks);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LineOperations && ((LineOperations) o).dwellTicks == dwellTicks
            && ((LineOperations) o).headwayTicks == headwayTicks;
    }

    @Override
    public int hashCode() {
        return 31 * dwellTicks + headwayTicks;
    }

    @Override
    public String toString() {
        return "LineOperations{dwell=" + dwellTicks + ", headway=" + headwayTicks + '}';
    }
}
