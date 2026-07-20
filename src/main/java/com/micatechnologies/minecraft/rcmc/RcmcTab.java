package com.micatechnologies.minecraft.rcmc;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;

/**
 * Creative inventory tab for all RCMC content.
 */
public final class RcmcTab {

    public static final CreativeTabs RCMC_TAB = new CreativeTabs(RcmcConstants.MOD_NAMESPACE) {
        @Override
        public ItemStack createIcon() {
            return ICON;
        }
    };

    /**
     * Tab icon. Deliberately a mutable static rather than an inline {@code new ItemStack(...)}
     * in {@code createIcon()}: {@link CreativeTabs} is constructed during class-load, long
     * before item registration, so referencing an RCMC item directly there yields an air
     * stack. {@link #initTabElements()} swaps in the real icon once registration is done.
     */
    private static ItemStack ICON = new ItemStack(Items.MINECART);

    private RcmcTab() {
        throw new AssertionError("No instances.");
    }

    /**
     * Called from {@code preInit} after the feature packages have populated
     * {@link RcmcRegistry}. Point {@link #ICON} at the track-piece item once it exists.
     */
    public static void initTabElements() {
    }
}
