package com.micatechnologies.minecraft.rcmc.block.sign;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.block.RcmcBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * One of the nine blocks an arrival board occupies besides the block the player placed.
 *
 * <p>It draws nothing — the master's renderer draws the whole screen in one go — and exists only so
 * that the space the screen visibly fills is space the world agrees is filled. What it contributes
 * is the collision the screen ought to have, the selection outline that matches what you are
 * looking at, and the light the screen ought to interrupt.</p>
 *
 * <p>It is not an item and is not in the creative tab. Breaking one breaks the board, which drops
 * the one item the board is; middle-clicking one hands you that same item. Both are deliberate:
 * every block of the board should behave like the board, because to a player it <em>is</em> the
 * board.</p>
 *
 * @see ArrivalBoardStructure
 */
public class BlockArrivalBoardPart extends Block {

    public static final String NAME = "arrival_board_part";

    /**
     * Which way the screen runs. Held in the blockstate rather than read from the master's tile
     * entity, because the selection and collision box is asked for far more often than it changes,
     * and two values is the cheapest possible thing to store.
     */
    public static final PropertyEnum<EnumFacing.Axis> AXIS =
        PropertyEnum.create("axis", EnumFacing.Axis.class,
            EnumFacing.Axis.X, EnumFacing.Axis.Z);

    public BlockArrivalBoardPart() {
        super(Material.IRON);
        setRegistryName(RcmcConstants.MOD_NAMESPACE, NAME);
        setTranslationKey(RcmcConstants.MOD_NAMESPACE + "." + NAME);
        setHardness(1.5F);
        setResistance(8.0F);
        setSoundType(SoundType.METAL);
        setDefaultState(blockState.getBaseState().withProperty(AXIS, EnumFacing.Axis.X));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, new IProperty[] {AXIS});
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(AXIS,
            (meta & 1) == 0 ? EnumFacing.Axis.X : EnumFacing.Axis.Z);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(AXIS) == EnumFacing.Axis.X ? 0 : 1;
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileArrivalBoardPart();
    }

    /** The master's renderer draws the screen across all ten blocks; this one draws nothing. */
    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return ArrivalBoardStructure.slab(state.getValue(AXIS));
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    /** A board that a piston could shove sideways would be nine blocks and a hole. */
    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    /** The board drops its own single item as it is demolished; the parts must add nothing. */
    @Override
    public int quantityDropped(java.util.Random rand) {
        return 0;
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world,
                                  BlockPos pos, EntityPlayer player) {
        return new ItemStack(RcmcBlocks.arrivalBoard);
    }

    /** Right-clicking any part of the board relinks the board, exactly as its master does. */
    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity tile = world.getTileEntity(pos);
        BlockPos master = tile instanceof TileArrivalBoardPart
            ? ((TileArrivalBoardPart) tile).master() : null;
        if (master == null) {
            return true;
        }
        IBlockState masterState = world.getBlockState(master);
        return masterState.getBlock() == RcmcBlocks.arrivalBoard
            && masterState.getBlock().onBlockActivated(world, master, masterState, player, hand,
                facing, hitX, hitY, hitZ);
    }

    /**
     * A creative player breaking part of a board should not be handed one. Caught here rather than
     * in {@link #breakBlock} because that is the only place the breaker is known — and the board is
     * demolished before {@code super} clears this block, so the harvest that would have dropped a
     * second item never runs.
     */
    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos,
                                   EntityPlayer player, boolean willHarvest) {
        ArrivalBoardStructure.demolish(world, pos, !player.capabilities.isCreativeMode);
        return world.setBlockToAir(pos);
    }

    /** Everything else that can remove a block — explosions, commands, world edits. */
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        ArrivalBoardStructure.demolish(world, pos, true);
        super.breakBlock(world, pos, state);
    }
}
