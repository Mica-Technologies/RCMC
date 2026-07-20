package com.micatechnologies.minecraft.rcmc.block;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.RcmcRegistry;
import net.minecraft.item.ItemBlock;

/**
 * Every block RCMC adds, plus the {@link ItemBlock} that lets each be carried.
 *
 * <p>Called from {@code preInit}, and deliberately before items: a block must exist before its
 * {@code ItemBlock} can reference it, and both must be registered before models bake.</p>
 */
public final class RcmcBlocks {

    public static BlockTrackSupport trackSupport;

    private RcmcBlocks() {
        throw new AssertionError("No instances.");
    }

    public static void init() {
        trackSupport = RcmcRegistry.addBlock(new BlockTrackSupport());

        // An ItemBlock carries the block's registry name, not its own — they share a namespace and
        // Forge matches them by name when binding models.
        RcmcRegistry.addItem(new ItemBlock(trackSupport)
            .setRegistryName(RcmcConstants.MOD_NAMESPACE, BlockTrackSupport.NAME));
    }
}
