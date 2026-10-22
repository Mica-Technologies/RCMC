package com.micatechnologies.minecraft.rcmc.block.signal;

import net.minecraft.util.IStringSerializable;

/** How a signal head is held up. Chosen at placement from the face clicked. */
public enum SignalHeadMount implements IStringSerializable {
    /** On a post standing on the block below (clicked the top of a block). */
    POST("post"),
    /** On a bracket off the side of a block (clicked a side). */
    WALL("wall"),
    /** Hung from the block above (clicked the underside of a block). */
    CEILING("ceiling");

    private static final SignalHeadMount[] VALUES = values();

    private final String name;

    SignalHeadMount(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public static SignalHeadMount fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : POST;
    }
}
