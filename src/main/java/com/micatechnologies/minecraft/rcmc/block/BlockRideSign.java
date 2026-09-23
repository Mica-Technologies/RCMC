package com.micatechnologies.minecraft.rcmc.block;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.RcmcTab;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
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
 * A ride sign: the board by a coaster's queue showing its name, whether it is open, and how it
 * rates — excitement, intensity and nausea, with its top speed, length and inversions.
 *
 * <p>A post, like a station sign; the board above it is drawn by the renderer from what
 * {@link TileRideSign} was last sent. It links to the nearest coaster station when placed, and a
 * click relinks it — for a sign placed before its ride was built.</p>
 */
public class BlockRideSign extends Block {

    public static final String NAME = "ride_sign";

    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(0.375D, 0.0D, 0.375D, 0.625D, 1.0D, 0.625D);

    public BlockRideSign() {
        super(Material.IRON);
        setRegistryName(RcmcConstants.MOD_NAMESPACE, NAME);
        setTranslationKey(RcmcConstants.MOD_NAMESPACE + "." + NAME);
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
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileRideSign();
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer,
                                ItemStack stack) {
        TileEntity tile = world.getTileEntity(pos);
        if (world.isRemote || !(tile instanceof TileRideSign)) {
            return;
        }
        // Face the placer, quantised to 90° so signs along a queue line up.
        int quarter = net.minecraft.util.math.MathHelper.floor(placer.rotationYaw * 4.0F / 360.0F + 0.5F) & 3;
        ((TileRideSign) tile).setFacingDegrees(quarter * 90.0F);
        ((TileRideSign) tile).linkToNearestStation();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileRideSign) {
            TileRideSign sign = (TileRideSign) tile;
            sign.linkToNearestStation();
            player.sendMessage(new TextComponentString(TextFormatting.AQUA
                + (sign.linkedSection() == TileRideSign.UNLINKED
                ? "No coaster station within " + (int) TileRideSign.LINK_RANGE + " blocks."
                : "Ride sign for " + sign.name() + ". Name the ride with /rcmc ride "
                    + sign.linkedSection() + " name <name>.")));
        }
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
}
