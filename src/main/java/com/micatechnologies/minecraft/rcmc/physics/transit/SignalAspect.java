package com.micatechnologies.minecraft.rcmc.physics.transit;

/**
 * What a physical signal head shows. The ordinal is the compact value a tile entity stores and
 * syncs, so new aspects go on the end; {@link #rank} is the order from most restrictive.
 */
public enum SignalAspect {
    /** Stop: the block this head protects is occupied (or the head has lost its binding). */
    RED(0),
    /** Caution: the protected block is clear, but the block after it is occupied. */
    YELLOW(1),
    /** Clear: the protected block and the ones after it are free. */
    GREEN(3),
    /**
     * Preliminary caution, on a four-aspect head: the next two blocks are clear but the one after
     * them is occupied, so the next signal will show a single yellow.
     */
    DOUBLE_YELLOW(2);

    private static final SignalAspect[] VALUES = values();

    private final int rank;

    SignalAspect(int rank) {
        this.rank = rank;
    }

    /** 0 for red, rising to green: the order a driver reads them in. */
    public int rank() {
        return rank;
    }

    /** The aspect for a stored ordinal; anything out of range reads as {@link #RED}. */
    public static SignalAspect fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : RED;
    }

    /**
     * The aspects a head that can show {@code aspects} of them uses, most restrictive first: red
     * and green for two; red, yellow and green for three; and double yellow as well for four.
     */
    public static SignalAspect[] shownBy(int aspects) {
        if (aspects <= 2) {
            return new SignalAspect[] {RED, GREEN};
        }
        if (aspects == 3) {
            return new SignalAspect[] {RED, YELLOW, GREEN};
        }
        return new SignalAspect[] {RED, YELLOW, DOUBLE_YELLOW, GREEN};
    }

    /**
     * The nearest of the aspects a head showing {@code aspects} can display to this one, never
     * less restrictive: a three-aspect head asked for double yellow shows yellow, and a two-aspect
     * head asked for yellow shows red. A signal that cannot say "caution" says "stop".
     */
    public SignalAspect within(int aspects) {
        SignalAspect best = RED;
        for (SignalAspect shown : shownBy(aspects)) {
            if (shown.rank <= rank && shown.rank >= best.rank) {
                best = shown;
            }
        }
        return best;
    }

    /** The next aspect in the decorative right-click cycle of a head showing {@code aspects}. */
    public SignalAspect next(int aspects) {
        SignalAspect[] shown = shownBy(aspects);
        for (int i = 0; i < shown.length; i++) {
            if (shown[i] == this) {
                return shown[(i + 1) % shown.length];
            }
        }
        return shown[0];
    }

    /**
     * The aspect a redstone signal of {@code power} (0 to 15) asks a head for: none is red, full
     * is green, and in between is caution — 1 to 7 yellow, 8 to 14 double yellow. A lever reads as
     * stop or clear; a comparator can show any of them. Then fitted to the head with {@link #within}.
     */
    public static SignalAspect fromRedstone(int power, int aspects) {
        SignalAspect asked;
        if (power <= 0) {
            asked = RED;
        }
        else if (power >= 15) {
            asked = GREEN;
        }
        else if (power <= 7) {
            asked = YELLOW;
        }
        else {
            asked = DOUBLE_YELLOW;
        }
        // A two-aspect head has no caution: any power short of full would read as stop, which
        // would make a comparator-driven two-aspect head useless. Any power at all clears it.
        if (aspects <= 2) {
            return power > 0 ? GREEN : RED;
        }
        return asked.within(aspects);
    }
}
