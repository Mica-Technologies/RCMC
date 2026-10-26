package com.micatechnologies.minecraft.rcmc.client.render.sign;

import com.micatechnologies.minecraft.rcmc.block.signal.SignalHeadStyle;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.item.ItemStack;

/**
 * Draws signal head items with the head renderer itself, so there are not eighteen hand-made
 * item models to keep in step with it. The item model is {@code builtin/entity}, which hands the
 * drawing here with the hand, frame and inventory transforms already applied.
 */
public class ItemSignalHeadRenderer extends TileEntityItemStackRenderer {

    private final RenderSignalHead heads = new RenderSignalHead();

    @Override
    public void renderByItem(ItemStack stack) {
        heads.renderItem(SignalHeadStyle.fromOrdinal(stack.getMetadata()));
    }
}
