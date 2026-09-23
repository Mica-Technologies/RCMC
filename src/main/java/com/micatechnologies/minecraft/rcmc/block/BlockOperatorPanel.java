package com.micatechnologies.minecraft.rcmc.block;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.RcmcTab;
import com.micatechnologies.minecraft.rcmc.world.RideOperations;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * The ride operator panel: a console placed at a coaster's station. Right-click it to open, test
 * and close the ride, dispatch trains, stop it in an emergency, tune its hardware and manage its
 * trains.
 */
public class BlockOperatorPanel extends Block {

    public static final String NAME = "operator_panel";

    public static final PropertyDirection FACING =
        PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    /** The console: a waist-high body under a sloping desk. */
    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(
        1.0D / 16, 0.0D, 1.0D / 16, 15.0D / 16, 13.0D / 16, 15.0D / 16);

    public BlockOperatorPanel() {
        super(Material.IRON);
        setRegistryName(RcmcConstants.MOD_NAMESPACE, NAME);
        setTranslationKey(RcmcConstants.MOD_NAMESPACE + "." + NAME);
        setCreativeTab(RcmcTab.RCMC_TAB);
        setHardness(2.0F);
        setResistance(10.0F);
        setSoundType(SoundType.METAL);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileOperatorPanel();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        // The controls face whoever placed it — they will be standing where the operator stands.
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote) {
            return;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileOperatorPanel && placer instanceof EntityPlayer) {
            boolean linked = ((TileOperatorPanel) tile).linkToNearestRide();
            ((EntityPlayer) placer).sendMessage(new TextComponentString(linked
                ? TextFormatting.GREEN + "Operator panel linked to Coaster #"
                    + ((TileOperatorPanel) tile).linkedSection() + "."
                : TextFormatting.YELLOW + "No coaster station within "
                    + (int) TileOperatorPanel.LINK_RANGE + " blocks — it will link when one is built."));
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY,
                                    float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileOperatorPanel) || !(player instanceof EntityPlayerMP)) {
            return true;
        }
        TileOperatorPanel panel = (TileOperatorPanel) tile;
        if (!panel.ensureLinked()) {
            player.sendMessage(new TextComponentString(TextFormatting.YELLOW
                + "This panel is not near a coaster station. Place it within "
                + (int) TileOperatorPanel.LINK_RANGE + " blocks of one."));
            return true;
        }
        RideOperations.open((EntityPlayerMP) player, pos, panel.linkedSection());
        return true;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
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

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos,
                                            EnumFacing face) {
        return face == EnumFacing.DOWN ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }
}
