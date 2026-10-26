package com.micatechnologies.minecraft.rcmc.block.signal;

import net.minecraft.util.IStringSerializable;

/**
 * How a signal head is held up. Chosen at placement from the face clicked and the style, and
 * changed afterwards in the head's screen. The ordinal is saved, so new mounts go on the end.
 */
public enum SignalHeadMount implements IStringSerializable {
    /** On a short post standing on the block below. */
    POST("post"),
    /** On a bracket off a wall beside it: a tunnel wall, usually. */
    WALL("wall"),
    /** Hung from the block above: a tunnel roof. */
    CEILING("ceiling"),
    /** At ground level, nothing under it but its own base: dwarf signals and trip cocks. */
    GROUND("ground"),
    /** On a tall mast with a ladder, down to the first solid block. */
    MAST("mast"),
    /** On a gantry: a beam across the tracks on two legs. */
    GANTRY("gantry"),
    /** On a cantilever: one mast to the side, an arm out over the track. */
    CANTILEVER("cantilever"),
    /** On a low post at the end of a platform, at a driver's eye level. */
    PLATFORM("platform");

    private static final SignalHeadMount[] VALUES = values();

    private final String name;

    SignalHeadMount(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    /** It stands on the ground below, so it reaches down to the first solid block. */
    public boolean reachesGround() {
        return this == MAST || this == GANTRY || this == CANTILEVER;
    }

    public SignalHeadMount next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static SignalHeadMount fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : POST;
    }

    public static SignalHeadMount byName(String name) {
        for (SignalHeadMount mount : VALUES) {
            if (mount.name.equalsIgnoreCase(name)) {
                return mount;
            }
        }
        return null;
    }
}
