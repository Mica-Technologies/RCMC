package com.micatechnologies.minecraft.rcmc.block.sign;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.RcmcTab;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Shared behaviour for the transit signs: metal fixture blocks whose content is drawn entirely
 * by a tile renderer, and which link themselves to the nearest station on placement.
 *
 * <p>No blockstate properties, deliberately — facing lives on the tile entity (see
 * {@link TileTransitSignBase}), so each sign needs exactly one model variant and the renderer
 * does the rotating.</p>
 */
public abstract class BlockTransitSignBase extends Block {

    protected BlockTransitSignBase(String name) {
        super(Material.IRON);
        setRegistryName(RcmcConstants.MOD_NAMESPACE, name);
        setTranslationKey(RcmcConstants.MOD_NAMESPACE + "." + name);
        setCreativeTab(RcmcTab.RCMC_TAB);
        setHardness(1.5F);
        setResistance(8.0F);
        setSoundType(SoundType.METAL);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public abstract TileEntity createTileEntity(World world, IBlockState state);

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileTransitSignBase) || world.isRemote) {
            return;
        }
        TileTransitSignBase sign = (TileTransitSignBase) tile;
        // Face the placer: quantised to 90° so a row of signs along a platform lines up.
        int quarter = net.minecraft.util.math.MathHelper.floor(
            placer.rotationYaw * 4.0F / 360.0F + 0.5F) & 3;
        sign.setFacingDegrees(quarter * 90.0F);
        sign.linkToNearestStation();
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }
}
