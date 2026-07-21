package com.micatechnologies.minecraft.rcmc.client.render;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.World;

/**
 * Smooths the door position a client is shown.
 *
 * <p>The authoritative fraction comes from the server on the service-sync cadence — a handful of
 * updates a second — so using it directly makes the leaves jump between a few discrete positions
 * several times per close. That is not a sync bug to fix by syncing harder: a door takes a second
 * and a half to travel, and sending sixty updates for it would spend bandwidth restating something
 * the client can work out for itself.</p>
 *
 * <p>So the client eases toward whatever the server last said, capped at the real speed of the
 * doors. The cap is what makes it look right rather than merely smooth: the leaves never move
 * faster than they actually do, so a late update produces a fractionally longer travel rather than
 * a visible snap. Nothing here feeds back into the simulation — {@code doorsOpen} still decides
 * boarding, and that stays server-authoritative.</p>
 *
 * <p>Client-only, and keyed by train id because every car of a consist opens together.</p>
 */
final class MetroDoorAnimation {

    /** Ticks a door takes to travel, matching the stop controller's open/close phase lengths. */
    private static final double DOOR_TRAVEL_TICKS = 30.0D;

    /** Beyond this gap the client gives up easing and snaps — a train that just came into view. */
    private static final double SNAP_THRESHOLD = 0.85D;

    /** trainId -> {displayed fraction, world-tick clock when it was last advanced}. */
    private static final Map<Integer, double[]> STATE = new HashMap<>();

    private MetroDoorAnimation() {
    }

    /**
     * The fraction to draw this frame, advanced toward {@code target} at the doors' own speed.
     *
     * <p>Safe to call once per car per frame: the clock guard means only the first call in a frame
     * advances the animation, so a six-car train does not move its doors six times as fast.</p>
     */
    static float fraction(World world, int trainId, double target, float partialTicks) {
        double clock = world.getTotalWorldTime() + partialTicks;
        double[] state = STATE.get(trainId);
        if (state == null) {
            STATE.put(trainId, new double[] {target, clock});
            return (float) target;
        }
        double elapsed = clock - state[1];
        if (elapsed > 0.0D) {
            state[1] = clock;
            double gap = target - state[0];
            if (Math.abs(gap) >= SNAP_THRESHOLD) {
                state[0] = target;
            } else {
                double step = elapsed / DOOR_TRAVEL_TICKS;
                state[0] += Math.max(-step, Math.min(step, gap));
            }
        }
        return (float) Math.max(0.0D, Math.min(1.0D, state[0]));
    }

    /** Drops state for trains that have gone away, so the map cannot grow without bound. */
    static void forget(int trainId) {
        STATE.remove(trainId);
    }
}
