package com.micatechnologies.minecraft.rcmc.block.signal;

import net.minecraft.util.IStringSerializable;

/** The look of a signal head. Its ordinal is the item damage and part of the block metadata. */
public enum SignalHeadStyle implements IStringSerializable {
    /** Modern LED head: three lenses stacked red, yellow, green. */
    LED("led"),
    /** Searchlight: one lens that changes colour. */
    SEARCHLIGHT("searchlight"),
    /** Compact dwarf/ground signal: a stop lens over a proceed lens. */
    DWARF("dwarf");

    private static final SignalHeadStyle[] VALUES = values();

    private final String name;

    SignalHeadStyle(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public static SignalHeadStyle fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : LED;
    }
}
