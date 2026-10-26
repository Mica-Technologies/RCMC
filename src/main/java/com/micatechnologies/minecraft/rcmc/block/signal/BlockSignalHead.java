package com.micatechnologies.minecraft.rcmc.block.signal;

import com.micatechnologies.minecraft.rcmc.Rcmc;
import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.RcmcTab;
import com.micatechnologies.minecraft.rcmc.physics.transit.SignalAspect;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
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
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * A rail signal head, in any of the {@link SignalHeadStyle styles} on any of the
 * {@link SignalHeadMount mounts}. Style comes from the item (one item per style in the creative
 * tab); the mount from the style and the face clicked: a side gives a wall bracket, the underside a
 * ceiling hanger, and the top the style's own mount — a post, a mast, a dwarf on the ground.
 *
 * <p>The block model is only a hit box; the head itself, its mount and its emissive lenses are
 * drawn by a tile renderer, which is what lets a head turn to any angle to face a curving track,
 * and a mast or gantry reach down to the ground.</p>
 *
 * <p>Interaction, with an empty hand: right-click opens the head's screen, where everything about
 * it is set. Sneak-right-click is a shortcut that links a head to the nearest signal of any
 * signalled line, or unlinks a linked one. Heads try to link on placement, so one placed by a
 * signalled line repeats that line's signal straight away.</p>
 *
 * <p>Style and mount are kept in the tile entity. The block's only property is {@link #LEGACY},
 * which holds the metadata of heads placed before that, so they can be read once and migrated.</p>
 */
public class BlockSignalHead extends Block {

    public static final String NAME = "signal_head";

    /** The metadata of a head placed when style and mount were kept there; 0 for any newer head. */
    public static final PropertyInteger LEGACY = PropertyInteger.create("legacy", 0, 15);

    private static final AxisAlignedBB POST_SHAPE =
        new AxisAlignedBB(0.375D, 0.0D, 0.375D, 0.625D, 1.0D, 0.625D);
    private static final AxisAlignedBB HEAD_SHAPE =
        new AxisAlignedBB(0.25D, 0.0D, 0.25D, 0.75D, 1.0D, 0.75D);
    private static final AxisAlignedBB GROUND_SHAPE =
        new AxisAlignedBB(0.25D, 0.0D, 0.25D, 0.75D, 0.5D, 0.75D);

    public BlockSignalHead() {
        super(Material.IRON);
        setRegistryName(RcmcConstants.MOD_NAMESPACE, NAME);
        setTranslationKey(RcmcConstants.MOD_NAMESPACE + "." + NAME);
        setCreativeTab(RcmcTab.RCMC_TAB);
        setHardness(1.5F);
        setResistance(8.0F);
        setSoundType(SoundType.METAL);
        setDefaultState(blockState.getBaseState().withProperty(LEGACY, 0));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, LEGACY);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(LEGACY, meta & 15);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(LEGACY);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        return getDefaultState();
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

    /** The style a dropped or picked head carries back to its item. */
    private static int itemDamage(IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof TileSignalHead ? ((TileSignalHead) tile).style().ordinal() : 0;
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos,
                         IBlockState state, int fortune) {
        drops.add(new ItemStack(this, 1, itemDamage(world, pos)));
    }

    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player,
                                   boolean willHarvest) {
        // Keep the tile until the drops are worked out, so the drop is the right style.
        if (willHarvest) {
            return true;
        }
        return super.removedByPlayer(state, world, pos, player, false);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, BlockPos pos, IBlockState state,
                             TileEntity tile, ItemStack stack) {
        super.harvestBlock(world, player, pos, state, tile, stack);
        world.setBlockToAir(pos);
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos,
                                  EntityPlayer player) {
        return new ItemStack(this, 1, itemDamage(world, pos));
    }

    /**
     * Sets up a just-placed head from its item and the face clicked. Called by
     * {@link ItemSignalHead}, which is the only thing that knows both.
     */
    static void setUpPlaced(World world, BlockPos pos, EnumFacing clicked, EntityLivingBase placer,
                            SignalHeadStyle style) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileSignalHead)) {
            return;
        }
        TileSignalHead head = (TileSignalHead) tile;
        SignalHeadMount mount = clicked == EnumFacing.DOWN ? SignalHeadMount.CEILING
            : clicked != EnumFacing.UP ? SignalHeadMount.WALL : style.defaultMount();
        head.initialise(style, mount);
        if (world.isRemote) {
            return;
        }
        // Face the placer, who is usually standing on the track looking at where trains come from.
        int quarter = MathHelper.floor(placer.rotationYaw * 4.0F / 360.0F + 0.5F) & 3;
        head.setFacingDegrees(quarter * 90.0F);
        if (mount == SignalHeadMount.WALL) {
            head.setWallSide(clicked.getOpposite().getHorizontalIndex());
        }
        if (style.isRouteIndicator()) {
            head.linkToNearestSwitch();
        }
        else if (style != SignalHeadStyle.CALL_ON) {
            head.linkToNearestBoundary();
        }
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
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block,
                                BlockPos fromPos) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof TileSignalHead) {
            TileSignalHead head = (TileSignalHead) tile;
            // Redstone heads answer at once rather than on their next poll; the ground under a
            // mast may have been dug out or built up.
            head.measureReach();
            head.refresh();
        }
    }

    @Override
    public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos,
                                      EnumFacing side) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof TileSignalHead
            && ((TileSignalHead) tile).control() == TileSignalHead.Control.REDSTONE;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (!player.getHeldItem(hand).isEmpty()) {
            return false;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileSignalHead)) {
            return true;
        }
        TileSignalHead head = (TileSignalHead) tile;
        if (!player.isSneaking()) {
            if (world.isRemote) {
                Rcmc.proxy.openSignalHead(pos);
            }
            return true;
        }
        if (world.isRemote) {
            return true;
        }
        if (head.isLinked()) {
            head.unlink();
            tell(player, TextFormatting.YELLOW, "Signal unlinked: set its aspect in its screen.");
        }
        else if (head.linkToNearestBoundary()) {
            tell(player, TextFormatting.GREEN, "Signal linked: " + describe(head));
        }
        else {
            tell(player, TextFormatting.RED, "No signalled line within "
                + (int) TileSignalHead.LINK_RANGE
                + " blocks; add signalling with /rcmc line signals <name> <count>");
        }
        return true;
    }

    /** "line BlueLine, section #19 at 1234.5, trains running forward; showing green". */
    public static String describe(TileSignalHead head) {
        return "line " + head.lineName() + ", section #" + head.sectionId() + " at "
            + String.format(java.util.Locale.ROOT, "%.1f", head.distance()) + ", trains running "
            + (head.direction() > 0 ? "forward" : "reverse") + "; showing " + name(head.aspect());
    }

    public static String name(SignalAspect aspect) {
        return aspect.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    private static void tell(EntityPlayer player, TextFormatting colour, String message) {
        player.sendMessage(new TextComponentString(colour + message));
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        TileEntity tile = source.getTileEntity(pos);
        if (!(tile instanceof TileSignalHead)) {
            return HEAD_SHAPE;
        }
        TileSignalHead head = (TileSignalHead) tile;
        switch (head.mount()) {
            case GROUND:
                return GROUND_SHAPE;
            case POST:
            case MAST:
            case PLATFORM:
                return POST_SHAPE;
            default:
                return HEAD_SHAPE;
        }
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        // A head the size of a block would stop a player walking under a ceiling hanger or past a
        // wall bracket in a tunnel; only what stands on the ground is solid.
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileSignalHead) {
            SignalHeadMount mount = ((TileSignalHead) tile).mount();
            if (mount == SignalHeadMount.CEILING || mount == SignalHeadMount.WALL
                || mount == SignalHeadMount.GANTRY || mount == SignalHeadMount.CANTILEVER) {
                return NULL_AABB;
            }
        }
        return getBoundingBox(state, world, pos);
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
