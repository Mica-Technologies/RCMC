package com.micatechnologies.minecraft.rcmc.block.signal;

import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** The signal head's item: one per style, the style carried as item damage. */
public class ItemSignalHead extends ItemBlock {

    public ItemSignalHead(BlockSignalHead block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        return super.getTranslationKey() + "."
            + SignalHeadStyle.fromOrdinal(stack.getMetadata()).getName();
    }
}
