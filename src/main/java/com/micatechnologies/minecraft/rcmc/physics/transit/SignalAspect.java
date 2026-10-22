package com.micatechnologies.minecraft.rcmc.physics.transit;

/**
 * What a physical signal head shows. Ordered most restrictive first, so the ordinal doubles as
 * the compact value a tile entity stores and syncs.
 */
public enum SignalAspect {
    /** Stop: the block this head protects is occupied (or the head has lost its binding). */
    RED,
    /** Caution: the protected block is clear, but the block after it is occupied. */
    YELLOW,
    /** Clear: the protected block and the one after it are both free. */
    GREEN;

    private static final SignalAspect[] VALUES = values();

    /** The aspect for a stored ordinal; anything out of range reads as {@link #RED}. */
    public static SignalAspect fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : RED;
    }

    /** The next aspect in the decorative right-click cycle: red, yellow, green, red... */
    public SignalAspect next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }
}
