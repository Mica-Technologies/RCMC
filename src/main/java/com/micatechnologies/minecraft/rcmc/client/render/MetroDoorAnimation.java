package com.micatechnologies.minecraft.rcmc.client.render;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.World;

/**
 * Works out the door position a client is shown.
 *
 * <p>The authoritative fraction comes from the server on the service-sync cadence — a handful of
 * updates a second — so using it directly makes the leaves jump between a few discrete positions
 * several times per close. That is not a sync bug to fix by syncing harder: a door takes a second
 * and a half to travel, and sending sixty updates for it would spend bandwidth restating something
 * the client can work out for itself.</p>
 *
 * <p>So each sync carries the rate the fraction is moving at as well, and the client carries it
 * forward from the moment the sync arrived. An earlier version instead eased toward the last synced
 * value at door speed; that is always behind by however late the sync was, so the server finished
 * closing and pulled away with the leaves still drawn half open, and a missed update showed up as a
 * snap. Prediction has no lag to accumulate. What is drawn still eases toward the prediction, so a
 * correction from the next sync is never a jump — but at twice door speed, so it catches up.</p>
 *
 * <p>Nothing here feeds back into the simulation — {@code doorsOpen} still decides boarding, and
 * that stays server-authoritative. Client-only, and keyed by train id because every car of a
 * consist opens together.</p>
 */
final class MetroDoorAnimation {

    /** Ticks a door takes to travel, matching the stop controller's open/close phase lengths. */
    private static final double DOOR_TRAVEL_TICKS = 30.0D;

    /** How much faster than the doors themselves the drawn position may move to catch up. */
    private static final double CATCH_UP = 2.0D;

    /**
     * trainId -> {displayed fraction, clock last advanced, synced fraction, synced rate,
     * clock the sync was first seen}.
     */
    private static final Map<Integer, double[]> STATE = new HashMap<>();

    private MetroDoorAnimation() {
    }

    /**
     * The fraction to draw this frame.
     *
     * <p>Safe to call once per car per frame: the clock guard means only the first call in a frame
     * advances the animation, so a six-car train does not move its doors six times as fast.</p>
     *
     * @param synced the last fraction the server sent
     * @param rate   how fast that fraction was changing per tick when it was sent
     */
    static float fraction(World world, int trainId, double synced, double rate, float partialTicks) {
        double clock = world.getTotalWorldTime() + partialTicks;
        double[] state = STATE.get(trainId);
        if (state == null) {
            // First sight of this train: nothing on screen to ease from, so draw it where it is.
            STATE.put(trainId, new double[] {synced, clock, synced, rate, clock});
            return (float) clamp(synced);
        }
        if (synced != state[2] || rate != state[3]) {
            state[2] = synced;
            state[3] = rate;
            state[4] = clock;
        }
        double predicted = clamp(state[2] + state[3] * (clock - state[4]));
        double elapsed = clock - state[1];
        if (elapsed > 0.0D) {
            state[1] = clock;
            double step = CATCH_UP * elapsed / DOOR_TRAVEL_TICKS;
            double gap = predicted - state[0];
            state[0] += Math.max(-step, Math.min(step, gap));
        }
        return (float) clamp(state[0]);
    }

    private static double clamp(double fraction) {
        return Math.max(0.0D, Math.min(1.0D, fraction));
    }

    /** Drops state for trains that have gone away, so the map cannot grow without bound. */
    static void forget(int trainId) {
        STATE.remove(trainId);
    }
}
