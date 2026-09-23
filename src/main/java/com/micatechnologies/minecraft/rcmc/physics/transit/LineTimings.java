package com.micatechnologies.minecraft.rcmc.physics.transit;

import java.util.Arrays;

/**
 * How long a line's trains actually take between stops, learned by watching them.
 *
 * <p>An arrival board wants minutes, and minutes are the one thing the physics cannot promise:
 * they depend on hills, signals, headway holds and how long the doors stayed open. So nothing here
 * predicts them. Each leg is timed as trains run it — arrival at one stop to arrival at the next,
 * dwell included — and the board adds up the legs between a train and the station it is asking
 * about. A line that has not yet been run end to end simply has legs nobody has timed, and the
 * board shows stops for those until it has.</p>
 *
 * <p>A leg is named by the stop it <em>ends</em> at and the direction the train arrives in, which
 * is exactly the pair {@link ArrivalEstimator}'s walk produces at every step — so a terminus bounce
 * or a loop wrap needs no special case here.</p>
 */
public final class LineTimings {

    /** Weight of the newest observation: recent runs count most, one slow run does not dominate. */
    static final double SMOOTHING = 0.3D;

    private final double[] ticks;

    public LineTimings(int stationCount) {
        ticks = new double[stationCount * 2];
        Arrays.fill(ticks, -1.0D);
    }

    public int stationCount() {
        return ticks.length / 2;
    }

    /** Records one run of the leg ending at {@code stopIndex}, arriving in {@code direction}. */
    public void record(int stopIndex, int direction, long legTicks) {
        int slot = slot(stopIndex, direction);
        if (slot < 0 || legTicks <= 0) {
            return;
        }
        ticks[slot] = ticks[slot] < 0.0D
            ? legTicks : ticks[slot] + SMOOTHING * (legTicks - ticks[slot]);
    }

    /** The leg's typical duration in ticks, or a negative number if it has never been timed. */
    public double legTicks(int stopIndex, int direction) {
        int slot = slot(stopIndex, direction);
        return slot < 0 ? -1.0D : ticks[slot];
    }

    private int slot(int stopIndex, int direction) {
        if (stopIndex < 0 || stopIndex >= stationCount()) {
            return -1;
        }
        return stopIndex * 2 + (direction >= 0 ? 0 : 1);
    }
}
