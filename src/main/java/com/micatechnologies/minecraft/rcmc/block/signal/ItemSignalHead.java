package com.micatechnologies.minecraft.rcmc.block.signal;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The signal head's item: one per style, the style carried as item damage. The damage is not the
 * block's metadata: the style goes to the tile entity, which is set up here because only the item
 * knows both the style and the face that was clicked.
 */
public class ItemSignalHead extends ItemBlock {

    public ItemSignalHead(BlockSignalHead block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int damage) {
        return 0;
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        return super.getTranslationKey() + "."
            + SignalHeadStyle.fromOrdinal(stack.getMetadata()).getName();
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, BlockPos pos,
                                EnumFacing side, float hitX, float hitY, float hitZ,
                                IBlockState newState) {
        if (!super.placeBlockAt(stack, player, world, pos, side, hitX, hitY, hitZ, newState)) {
            return false;
        }
        BlockSignalHead.setUpPlaced(world, pos, side, player,
            SignalHeadStyle.fromOrdinal(stack.getMetadata()));
        return true;
    }
}
