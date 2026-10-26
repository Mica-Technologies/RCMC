package com.micatechnologies.minecraft.rcmc.block.sign;

import com.micatechnologies.minecraft.rcmc.Rcmc;
import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.RcmcTab;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * A tunnel exit sign: the long green "EXIT" plate on a tunnel or platform wall, with an arrow and
 * the distance to the nearest way out each way — in blocks. Three variants: both ways, left only
 * and right only, chosen by the item.
 *
 * <p>Placed against a wall, facing out from it; the plate is 4.5 blocks long, centred on the block
 * it is placed in, and drawn by a tile renderer. Right-click opens a screen to type the
 * distances.</p>
 */
public class BlockTunnelExitSign extends Block {

    public static final String NAME = "tunnel_exit_sign";

    /** Which way the arrows point. The ordinal is the item damage. */
    public enum Variant implements IStringSerializable {
        BOTH("both"), LEFT("left"), RIGHT("right");

        private final String name;

        Variant(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        public boolean hasLeft() {
            return this != RIGHT;
        }

        public boolean hasRight() {
            return this != LEFT;
        }

        public static Variant fromOrdinal(int ordinal) {
            Variant[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : BOTH;
        }
    }

    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyEnum<Variant> VARIANT = PropertyEnum.create("variant", Variant.class);

    public BlockTunnelExitSign() {
        super(Material.IRON);
        setRegistryName(RcmcConstants.MOD_NAMESPACE, NAME);
        setTranslationKey(RcmcConstants.MOD_NAMESPACE + "." + NAME);
        setCreativeTab(RcmcTab.RCMC_TAB);
        setHardness(1.0F);
        setResistance(5.0F);
        setSoundType(SoundType.METAL);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH)
            .withProperty(VARIANT, Variant.BOTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, VARIANT);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3))
            .withProperty(VARIANT, Variant.fromOrdinal((meta >> 2) & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(VARIANT).ordinal() << 2);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX,
                                            float hitY, float hitZ, int meta, EntityLivingBase placer,
                                            EnumHand hand) {
        // On a wall it faces out from the wall; on a floor or ceiling, back at whoever placed it.
        EnumFacing out = facing.getAxis().isHorizontal() ? facing : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, out).withProperty(VARIANT, Variant.fromOrdinal(meta));
    }

    @Override
    public int damageDropped(IBlockState state) {
        return state.getValue(VARIANT).ordinal();
    }

    @Override
    public void getSubBlocks(CreativeTabs tab, NonNullList<ItemStack> items) {
        for (Variant variant : Variant.values()) {
            items.add(new ItemStack(this, 1, variant.ordinal()));
        }
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, net.minecraft.util.math.RayTraceResult target, World world,
                                  BlockPos pos, EntityPlayer player) {
        return new ItemStack(this, 1, state.getValue(VARIANT).ordinal());
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileTunnelExitSign();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!player.getHeldItem(hand).isEmpty()) {
            return false;
        }
        if (world.isRemote) {
            Rcmc.proxy.openTunnelExitSign(pos);
        }
        return true;
    }

    /** A thin plate against the wall behind it, one block of its length and its full height. */
    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        double low = 0.3D;
        double high = 0.75D;
        switch (state.getValue(FACING)) {
            case SOUTH:
                return new AxisAlignedBB(0.0D, low, 0.0D, 1.0D, high, 0.0625D);
            case WEST:
                return new AxisAlignedBB(0.9375D, low, 0.0D, 1.0D, high, 1.0D);
            case EAST:
                return new AxisAlignedBB(0.0D, low, 0.0D, 0.0625D, high, 1.0D);
            default:
                return new AxisAlignedBB(0.0D, low, 0.9375D, 1.0D, high, 1.0D);
        }
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return NULL_AABB;
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
