package com.micatechnologies.minecraft.rcmc.block.sign;

import net.minecraft.block.material.EnumPushReaction;
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
 * The electronic arrival board: a ceiling-hung panel showing, per direction, how many stops
 * away the next trains are — RCMC's version of the platform countdown clock:
 *
 * <pre>
 *   INBOUND   1 stop away
 *             3 stops away
 *   OUTBOUND  3 stops away
 *             7 stops away
 * </pre>
 *
 * <p>Rows are computed at render time from the synced service snapshots ({@code
 * PacketServiceSync}) via {@code ArrivalEstimator} — stops-away, not minutes, because stops-away
 * is exact and deterministic. Right-click relinks to the nearest station.</p>
 *
 * <p>This block is the <b>master</b> of a ten-block board: it is the one placed, the one carrying
 * the station link and the facing, and the only one drawn. The rest of the footprint is filled
 * with {@link BlockArrivalBoardPart} on placement. A board placed before that footprint existed
 * stays one block and keeps working — see {@link TileArrivalBoard#isStructured()}.</p>
 *
 * @see ArrivalBoardStructure
 */
public class BlockArrivalBoard extends BlockTransitSignBase {

    public static final String NAME = "arrival_board";

    /**
     * The one-block shape a board that has no parts uses: the ceiling stub the model draws. Boards
     * placed before the multiblock existed keep it, because it is the truth about them — they
     * really are one block.
     */
    private static final AxisAlignedBB STUB =
        new AxisAlignedBB(0.0625D, 0.35D, 0.28125D, 0.9375D, 1.0D, 0.71875D);

    public BlockArrivalBoard() {
        super(NAME);
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileArrivalBoard();
    }

    /**
     * The screen's slab once the board is a multiblock, so that what you outline and what you walk
     * into is the panel you can see — the whole reason the board grew a footprint.
     */
    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        TileEntity tile = source.getTileEntity(pos);
        if (tile instanceof TileArrivalBoard && ((TileArrivalBoard) tile).isStructured()) {
            return ArrivalBoardStructure.slab(
                ArrivalBoardStructure.widthAxis(((TileArrivalBoard) tile).facingDegrees()));
        }
        return STUB;
    }

    /** A board shoved a block sideways would leave its nine parts behind. */
    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    /**
     * Claims the rest of the footprint, or refuses the placement outright.
     *
     * <p>Runs after {@code super}, not before it: the base class settles the facing — first from
     * the placer, then possibly again from the track it links to — and the footprint runs along
     * that facing, so there is nothing to validate until it has stopped moving.</p>
     *
     * <p>Obstruction rolls the placement back rather than building a partial board. Half a board is
     * a screen with a bite out of it and nine blocks that no longer agree on what they are; giving
     * the item back and saying why is the only outcome a player can act on.</p>
     */
    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (world.isRemote) {
            return;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileArrivalBoard)) {
            return;
        }
        TileArrivalBoard board = (TileArrivalBoard) tile;
        EnumFacing.Axis axis = ArrivalBoardStructure.widthAxis(board.facingDegrees());
        if (!ArrivalBoardStructure.isClear(world, pos, axis)) {
            rollBack(world, pos, placer);
            return;
        }
        board.setStructured(true);
        ArrivalBoardStructure.build(world, pos, axis);
    }

    /** Breaking the master takes its parts with it. Its own item drops through the usual path. */
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileArrivalBoard && ((TileArrivalBoard) tile).isStructured()) {
            ArrivalBoardStructure.clear(world, pos,
                ArrivalBoardStructure.widthAxis(((TileArrivalBoard) tile).facingDegrees()));
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileArrivalBoard) {
            TileArrivalBoard board = (TileArrivalBoard) tile;
            board.linkToNearestStation();
            player.sendMessage(new TextComponentString(TextFormatting.AQUA + (board.isLinked()
                ? "Arrival board linked to station " + board.stationName()
                : "No station within range — create one with /rcmc station <name>")));
        }
        return true;
    }

    private void rollBack(World world, BlockPos pos, EntityLivingBase placer) {
        world.setBlockToAir(pos);
        boolean creative = placer instanceof EntityPlayer
            && ((EntityPlayer) placer).capabilities.isCreativeMode;
        if (!creative) {
            spawnAsEntity(world, pos, new ItemStack(this));
        }
        if (placer instanceof EntityPlayer) {
            ((EntityPlayer) placer).sendMessage(new TextComponentString(TextFormatting.RED
                + "No room for an arrival board here — it needs "
                + (ArrivalBoardStructure.HALF_WIDTH * 2 + 1) + " blocks across, centred here, and "
                + ArrivalBoardStructure.HEIGHT + " down."));
        }
    }
}
