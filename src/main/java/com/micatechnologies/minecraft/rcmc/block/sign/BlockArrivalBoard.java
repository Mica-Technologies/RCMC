package com.micatechnologies.minecraft.rcmc.block.sign;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
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
 */
public class BlockArrivalBoard extends BlockTransitSignBase {

    public static final String NAME = "arrival_board";

    /** Hangs from the block above — mount it under a ceiling over the platform. */
    private static final AxisAlignedBB SHAPE =
        new AxisAlignedBB(0.0625D, 0.35D, 0.28125D, 0.9375D, 1.0D, 0.71875D);

    public BlockArrivalBoard() {
        super(NAME);
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileArrivalBoard();
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return SHAPE;
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
}
