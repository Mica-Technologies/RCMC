package com.micatechnologies.minecraft.rcmc.physics.transit;

/**
 * The door position one viewer is shown for one train, worked out between the server's updates.
 *
 * <p>The authoritative fraction arrives a few times a second, with the rate it was changing at.
 * Drawn raw it steps; eased toward the last value it is always late, by however old the update is.
 * So the position is <em>predicted</em> from the last update and its rate, and what is drawn eases
 * toward the prediction at up to twice door speed: a correction is never a jump, and a late or
 * missed update costs nothing because the leaves were already moving.</p>
 *
 * <p>Three things this owes that the first version did not:</p>
 * <ul>
 *   <li><b>Its clock is the caller's.</b> It was the world's total time, which a client does not
 *       own: the server corrects it every second, backwards whenever the server is running slow,
 *       and the animation then stood still until the clock had climbed back past where it had
 *       been — doors that opened late, in steps, on exactly the busy servers where they were
 *       wanted. A clock that steps backwards is now followed, keeping the progress made.</li>
 *   <li><b>It does not run the leaves backwards mid-travel.</b> On a slow server the prediction,
 *       counted in the viewer's faster ticks, gets ahead of the next update; easing back to it made
 *       opening doors twitch shut and open again. While the server says the doors are opening the
 *       drawn position only waits, and likewise closing.</li>
 *   <li><b>It forgets.</b> Train ids are reused, and a train not drawn for a while has moved on; a
 *       position left over from before is not eased from, it is replaced.</li>
 * </ul>
 *
 * <p>Pure arithmetic on numbers handed in, so it is tested without a game. Nothing here feeds the
 * simulation: whether a doorway can be walked through is the server's {@code doorsOpen}.</p>
 */
public final class DoorMotion {

    /** Ticks a door takes to travel, matching the stop controller's open and close phases. */
    public static final double TRAVEL_TICKS = 30.0D;

    /** How much faster than the doors themselves the drawn position may move to catch up. */
    public static final double CATCH_UP = 2.0D;

    /** Ticks without being asked after which the drawn position is no longer worth easing from. */
    public static final double STALE_TICKS = 40.0D;

    private boolean seen;
    private double shown;
    private double clock;
    private double synced;
    private double rate;
    private double syncedAt;

    /**
     * The fraction to draw at {@code now}, 0 shut to 1 open.
     *
     * @param now    the viewer's clock in ticks, fractional; need not start at zero
     * @param synced the last fraction the server sent
     * @param rate   how fast that fraction was changing per tick when it was sent
     */
    public double advance(double now, double synced, double rate) {
        if (!seen || now - clock > STALE_TICKS) {
            // First sight, or first in a while: nothing on screen to ease from, so draw it where
            // the server says it is.
            seen = true;
            shown = clamp(synced);
            clock = now;
            this.synced = synced;
            this.rate = rate;
            syncedAt = now;
            return shown;
        }
        if (now < clock) {
            // The clock stepped back. Carry both marks back with it, so the travel already made
            // stands and the leaves go on from where they are.
            syncedAt -= clock - now;
            clock = now;
        }
        if (synced != this.synced || rate != this.rate) {
            this.synced = synced;
            this.rate = rate;
            syncedAt = now;
        }
        double predicted = clamp(this.synced + this.rate * (now - syncedAt));
        double elapsed = now - clock;
        clock = now;
        if (elapsed > 0.0D) {
            double step = CATCH_UP * elapsed / TRAVEL_TICKS;
            double gap = predicted - shown;
            if ((this.rate > 0.0D && gap < 0.0D) || (this.rate < 0.0D && gap > 0.0D)) {
                // Ahead of a door that is still moving this way: wait for it, do not go back.
                gap = 0.0D;
            }
            shown += Math.max(-step, Math.min(step, gap));
        }
        return clamp(shown);
    }

    private static double clamp(double fraction) {
        return Math.max(0.0D, Math.min(1.0D, fraction));
    }
}
