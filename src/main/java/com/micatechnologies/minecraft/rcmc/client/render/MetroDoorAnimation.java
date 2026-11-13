package com.micatechnologies.minecraft.rcmc.client.render;

import com.micatechnologies.minecraft.rcmc.physics.transit.DoorMotion;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The door position this client shows for each train: one {@link DoorMotion} a train, driven by a
 * clock of this client's own ticks.
 *
 * <p>The arithmetic, and the reasons for it, are in {@code DoorMotion}. What lives here is the part
 * that needs a client: which train is which, and the clock. The clock counts client ticks rather
 * than reading the world's time, because the world's time is the server's to correct and goes
 * backwards on a server running slow.</p>
 *
 * <p>Keyed by train id, because every car of a consist opens together.</p>
 */
final class MetroDoorAnimation {

    private static final Map<Integer, DoorMotion> STATE = new HashMap<>();
    private static final Map<Integer, Long> LAST_ASKED = new HashMap<>();

    /** Client ticks since the game started, not counting paused ones. */
    private static long ticks;

    private MetroDoorAnimation() {
    }

    /** Advances the clock one client tick. Called once a tick while the game is not paused. */
    static void tick() {
        ticks++;
        if (ticks % 200L == 0L && !LAST_ASKED.isEmpty()) {
            // Trains that have gone away stop being asked about; drop them so neither map grows.
            Iterator<Map.Entry<Integer, Long>> it = LAST_ASKED.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Long> entry = it.next();
                if (ticks - entry.getValue() > 400L) {
                    STATE.remove(entry.getKey());
                    it.remove();
                }
            }
        }
    }

    /**
     * The fraction to draw this frame.
     *
     * <p>Safe to call once per car per frame: the animation advances by the clock, so a six-car
     * train does not move its doors six times as fast.</p>
     *
     * @param synced the last fraction the server sent
     * @param rate   how fast that fraction was changing per tick when it was sent
     */
    static float fraction(int trainId, double synced, double rate, float partialTicks) {
        DoorMotion motion = STATE.get(trainId);
        if (motion == null) {
            motion = new DoorMotion();
            STATE.put(trainId, motion);
        }
        LAST_ASKED.put(trainId, ticks);
        return (float) motion.advance(ticks + partialTicks, synced, rate);
    }

    /** Drops everything: the world was left. */
    static void clear() {
        STATE.clear();
        LAST_ASKED.clear();
    }
}
