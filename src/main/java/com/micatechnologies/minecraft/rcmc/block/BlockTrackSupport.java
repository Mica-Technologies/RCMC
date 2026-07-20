package com.micatechnologies.minecraft.rcmc.block;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.RcmcTab;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * A support pillar for holding track above the ground.
 *
 * <p>Stack them to the height you need and place a track node on top. There is deliberately no
 * "choose a height" dialog and no snapping logic in the track tool for this: the tool already
 * places a node one block above whatever it clicks, so clicking the top of a column already puts
 * the node exactly where it belongs. Adding a special case would have duplicated behaviour that
 * falls out for free.</p>
 *
 * <p>Distinct from the auto-generated supports that will later be produced under a finished
 * layout. Those serve a completed ride; these serve the act of building one, where a builder needs
 * something physical to place a node <em>on</em> before the track exists to hang supports from.</p>
 */
public class BlockTrackSupport extends Block {

    public static final String NAME = "track_support";

    /**
     * A square column narrower than a full block. Slim enough to read as steelwork rather than as
     * a wall, wide enough to still be easy to click on when building at height.
     */
    private static final AxisAlignedBB SHAPE =
        new AxisAlignedBB(0.3125D, 0.0D, 0.3125D, 0.6875D, 1.0D, 0.6875D);

    public BlockTrackSupport() {
        super(Material.IRON);
        setRegistryName(RcmcConstants.MOD_NAMESPACE, NAME);
        setTranslationKey(RcmcConstants.MOD_NAMESPACE + "." + NAME);
        setCreativeTab(RcmcTab.RCMC_TAB);
        setHardness(2.5F);
        setResistance(10.0F);
        setSoundType(SoundType.METAL);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return SHAPE;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return SHAPE;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    /**
     * Neighbouring supports must not cull each other's faces — a column is drawn from many copies
     * of a non-full-cube model, and treating it as solid would leave holes where they meet.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos,
                                        EnumFacing side) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    /**
     * A support may float. Track routinely runs where there is nothing beneath it — over water, out
     * of a cliff face — and requiring ground contact would make exactly the layouts a coaster wants
     * impossible to build.
     */
    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return world.getBlockState(pos).getBlock().isReplaceable(world, pos);
    }
}
