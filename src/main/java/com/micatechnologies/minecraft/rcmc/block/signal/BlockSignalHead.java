package com.micatechnologies.minecraft.rcmc.block.signal;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.RcmcTab;
import com.micatechnologies.minecraft.rcmc.physics.transit.SignalAspect;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
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
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * A rail signal head: modern LED three-aspect, searchlight or dwarf, on a post, a wall bracket or
 * a ceiling hanger. Style comes from the item (one item per style in the creative tab); mount
 * from the face clicked: the top of a block gives a post, a side a wall bracket, the underside a
 * ceiling hanger.
 *
 * <p>The block model is only a hit box; the head itself, and its emissive lenses, are drawn by a
 * tile renderer, which is what lets the head turn to any angle to face a curving track.</p>
 *
 * <p>Interaction, with an empty hand: right-click a decorative head to cycle red, yellow, green;
 * right-click a linked head to see what it is bound to. Sneak-right-click links an unlinked head
 * to the nearest signal of any signalled line, or unlinks a linked one. Heads try to link on
 * placement, so one placed by a signalled line repeats that line's signal straight away.</p>
 */
public class BlockSignalHead extends Block {

    public static final String NAME = "signal_head";

    public static final PropertyEnum<SignalHeadStyle> STYLE =
        PropertyEnum.create("style", SignalHeadStyle.class);
    public static final PropertyEnum<SignalHeadMount> MOUNT =
        PropertyEnum.create("mount", SignalHeadMount.class);

    private static final AxisAlignedBB POST_SHAPE =
        new AxisAlignedBB(0.375D, 0.0D, 0.375D, 0.625D, 1.0D, 0.625D);
    private static final AxisAlignedBB HEAD_SHAPE =
        new AxisAlignedBB(0.25D, 0.0D, 0.25D, 0.75D, 1.0D, 0.75D);
    private static final AxisAlignedBB DWARF_SHAPE =
        new AxisAlignedBB(0.25D, 0.0D, 0.25D, 0.75D, 0.5D, 0.75D);

    public BlockSignalHead() {
        super(Material.IRON);
        setRegistryName(RcmcConstants.MOD_NAMESPACE, NAME);
        setTranslationKey(RcmcConstants.MOD_NAMESPACE + "." + NAME);
        setCreativeTab(RcmcTab.RCMC_TAB);
        setHardness(1.5F);
        setResistance(8.0F);
        setSoundType(SoundType.METAL);
        setDefaultState(blockState.getBaseState()
            .withProperty(STYLE, SignalHeadStyle.LED)
            .withProperty(MOUNT, SignalHeadMount.POST));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, STYLE, MOUNT);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState()
            .withProperty(STYLE, SignalHeadStyle.fromOrdinal(meta & 3))
            .withProperty(MOUNT, SignalHeadMount.fromOrdinal((meta >> 2) & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(STYLE).ordinal() | (state.getValue(MOUNT).ordinal() << 2);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        SignalHeadMount mount = facing == EnumFacing.UP ? SignalHeadMount.POST
            : facing == EnumFacing.DOWN ? SignalHeadMount.CEILING : SignalHeadMount.WALL;
        return getDefaultState()
            .withProperty(STYLE, SignalHeadStyle.fromOrdinal(meta))
            .withProperty(MOUNT, mount);
    }

    @Override
    public int damageDropped(IBlockState state) {
        return state.getValue(STYLE).ordinal();
    }

    @Override
    public void getSubBlocks(CreativeTabs tab, NonNullList<ItemStack> items) {
        for (SignalHeadStyle style : SignalHeadStyle.values()) {
            items.add(new ItemStack(this, 1, style.ordinal()));
        }
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileSignalHead();
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        TileEntity tile = world.getTileEntity(pos);
        if (world.isRemote || !(tile instanceof TileSignalHead)) {
            return;
        }
        TileSignalHead head = (TileSignalHead) tile;
        // Face the placer, who is usually standing on the track looking at where trains come from.
        int quarter = MathHelper.floor(placer.rotationYaw * 4.0F / 360.0F + 0.5F) & 3;
        head.setFacingDegrees(quarter * 90.0F);
        if (state.getValue(MOUNT) == SignalHeadMount.WALL) {
            head.setWallSide(findWall(world, pos));
        }
        head.linkToNearestBoundary();
    }

    /** The horizontal side with something solid to bolt a bracket to, or -1. */
    static int findWall(World world, BlockPos pos) {
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            if (world.isSideSolid(pos.offset(side), side.getOpposite(), false)) {
                return side.getHorizontalIndex();
            }
        }
        return -1;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (!player.getHeldItem(hand).isEmpty()) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileSignalHead)) {
            return true;
        }
        TileSignalHead head = (TileSignalHead) tile;
        if (player.isSneaking()) {
            if (head.isLinked()) {
                head.unlink();
                tell(player, TextFormatting.YELLOW, "Signal unlinked: right-click to set its aspect.");
            } else if (head.linkToNearestBoundary()) {
                tell(player, TextFormatting.GREEN, "Signal linked: " + describe(head));
            } else {
                tell(player, TextFormatting.RED, "No signalled line within "
                    + (int) TileSignalHead.LINK_RANGE
                    + " blocks; add signalling with /rcmc line signals <name> <count>");
            }
            return true;
        }
        if (head.isLinked()) {
            tell(player, TextFormatting.AQUA, describe(head)
                + " (sneak-right-click to unlink)");
        } else {
            head.cycleAspect();
            tell(player, TextFormatting.AQUA, "Decorative signal: " + name(head.aspect()));
        }
        return true;
    }

    private static String describe(TileSignalHead head) {
        return "line " + head.lineName() + ", section #" + head.sectionId() + " at "
            + String.format(java.util.Locale.ROOT, "%.1f", head.distance()) + ", trains running "
            + (head.direction() > 0 ? "forward" : "reverse") + "; showing " + name(head.aspect());
    }

    private static String name(SignalAspect aspect) {
        return aspect.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static void tell(EntityPlayer player, TextFormatting colour, String message) {
        player.sendMessage(new TextComponentString(colour + message));
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        if (state.getValue(STYLE) == SignalHeadStyle.DWARF
            && state.getValue(MOUNT) == SignalHeadMount.POST) {
            return DWARF_SHAPE;
        }
        return state.getValue(MOUNT) == SignalHeadMount.POST ? POST_SHAPE : HEAD_SHAPE;
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
