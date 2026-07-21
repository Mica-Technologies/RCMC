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
 * The static line-map sign: a post-mounted panel showing one line's stops in order, with a
 * "you are here" marker at its linked station. Right-click cycles through the lines serving the
 * station, for interchange platforms. The map itself is auto-generated at render time from the
 * synced line registry, so it can never go stale — see {@link TileTransitSignBase}.
 */
public class BlockStationSign extends BlockTransitSignBase {

    public static final String NAME = "station_sign";

    /** The mounting post; the panel above it is the renderer's. */
    private static final AxisAlignedBB SHAPE =
        new AxisAlignedBB(0.375D, 0.0D, 0.375D, 0.625D, 1.0D, 0.625D);

    public BlockStationSign() {
        super(NAME);
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileStationSign();
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
        if (tile instanceof TileStationSign) {
            TileStationSign sign = (TileStationSign) tile;
            // Relink first, so a sign placed before its station existed heals on a click.
            sign.linkToNearestStation();
            sign.cycleLine();
            player.sendMessage(new TextComponentString(TextFormatting.AQUA + (sign.isLinked()
                ? "Line map: station " + sign.stationName() + " (click to cycle lines)"
                : "No station within range — create one with /rcmc station <name>")));
        }
        return true;
    }
}
