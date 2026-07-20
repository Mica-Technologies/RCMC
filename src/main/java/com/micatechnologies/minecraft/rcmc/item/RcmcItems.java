package com.micatechnologies.minecraft.rcmc.item;

import com.micatechnologies.minecraft.rcmc.RcmcRegistry;

/**
 * Every item RCMC adds, created once and handed to {@link RcmcRegistry}.
 *
 * <p>Instantiated from {@code preInit} rather than in a static initialiser: an item's constructor
 * calls {@code setCreativeTab}, which touches {@code RcmcTab}, and class-load ordering between two
 * classes that reference each other is not something to leave to chance.</p>
 */
public final class RcmcItems {

    public static ItemTrackTool trackTool;
    public static ItemTrackEditor trackEditor;

    private RcmcItems() {
        throw new AssertionError("No instances.");
    }

    public static void init() {
        trackTool = RcmcRegistry.addItem(new ItemTrackTool());
        trackEditor = RcmcRegistry.addItem(new ItemTrackEditor());
    }
}
