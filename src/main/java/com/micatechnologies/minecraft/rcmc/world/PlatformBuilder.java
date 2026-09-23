package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.block.BlockAirGate;
import com.micatechnologies.minecraft.rcmc.block.RcmcBlocks;
import com.micatechnologies.minecraft.rcmc.block.TileAirGate;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Lays a platform beside a stretch of track, from the track's own geometry.
 *
 * <p>Shared by metro and coaster stations: both want decking at the height of the car floor, a
 * warning-striped edge course facing the track, and nothing bulldozed — blocks go only into air,
 * replaceable plants, or natural ground (grass, dirt, sand) at deck level, so a station on open
 * ground is laid into the surface. The platform follows the alignment, so it curves with it, and stays level
 * where the track is banked, because people stand on it.</p>
 *
 * <p>A coaster platform also gets air gates ({@link BlockAirGate}) standing on its edge along the
 * stretch where the train stops, each linked to the station it serves.</p>
 */
public final class PlatformBuilder {

    /** What a build laid. */
    public static final class Result {
        public final int blocks;
        public final int gates;

        public Result(int blocks, int gates) {
            this.blocks = blocks;
            this.gates = gates;
        }
    }

    private PlatformBuilder() {
    }

    /**
     * @param from, to       the stretch of {@code section} to lay beside
     * @param floorAbove     how far above the track the car floor is, which the deck's top meets
     * @param innerOffset    how far from the centreline the edge course stands
     * @param width          blocks of platform, edge course included
     * @param gatesFrom, gatesTo where along it air gates stand on the edge; {@code gatesTo < gatesFrom}
     *                       for none
     * @param gateSection    the section whose station the gates serve
     */
    public static Result lay(World world, TrackSection section, double from, double to, double floorAbove,
                             double innerOffset, int width, boolean left, boolean right,
                             double gatesFrom, double gatesTo, int gateSection) {
        Set<BlockPos> placed = new HashSet<>();
        Set<BlockPos> gated = new HashSet<>();
        int blocks = 0;
        int gates = 0;
        for (double s = from; s <= to; s += 0.5D) {
            TrackFrame frame = section.frameAtDistance(s);
            // Horizontal projection of the frame's right axis: a platform is level even where the
            // track it serves is banked.
            double rx = frame.right.x;
            double rz = frame.right.z;
            double rl = Math.sqrt(rx * rx + rz * rz);
            if (rl < 1.0e-6D) {
                continue;
            }
            rx /= rl;
            rz /= rl;
            // A block's top face is at its y + 1, and the floor it must meet is frame.y +
            // floorAbove. Rounded, because block tops are integers and the floor need not be: the
            // residual is at most half a block, inside a player's 0.6 step height either way.
            int surfaceY = (int) Math.round(frame.position.y + floorAbove - 1.0D);
            boolean gateHere = s >= gatesFrom && s <= gatesTo;
            for (int sign = -1; sign <= 1; sign += 2) {
                if ((sign < 0 && !left) || (sign > 0 && !right)) {
                    continue;
                }
                for (int i = 0; i < width; i++) {
                    double d = innerOffset + i;
                    BlockPos pos = new BlockPos(
                        Math.floor(frame.position.x + rx * d * sign),
                        surfaceY,
                        Math.floor(frame.position.z + rz * d * sign));
                    EnumFacing facing = EnumFacing.getFacingFromVector(
                        (float) (-rx * sign), 0.0F, (float) (-rz * sign));
                    if (i == 0 && gateHere && gated.add(pos) && placeGate(world, pos.up(), facing, gateSection)) {
                        gates++;
                    }
                    if (!placed.add(pos) || !(isFree(world, pos) || isGround(world, pos))) {
                        continue;
                    }
                    if (i == 0) {
                        // The edge course carries the warning strip, facing the track it serves.
                        world.setBlockState(pos, RcmcBlocks.platformEdge.getDefaultState()
                            .withProperty(BlockHorizontal.FACING, facing), 2);
                    } else {
                        world.setBlockState(pos, RcmcBlocks.platform.getDefaultState(), 2);
                    }
                    blocks++;
                }
            }
        }
        return new Result(blocks, gates);
    }

    private static boolean placeGate(World world, BlockPos pos, EnumFacing facing, int sectionId) {
        if (!isFree(world, pos)) {
            return false;
        }
        world.setBlockState(pos, RcmcBlocks.airGate.getDefaultState()
            .withProperty(BlockHorizontal.FACING, facing), 2);
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileAirGate) {
            ((TileAirGate) tile).link(sectionId);
        }
        return true;
    }

    /**
     * Natural ground at deck level: a station on open ground is laid into the surface rather than
     * leaving the grass as its platform. Only these — never anything a player could have built.
     */
    private static boolean isGround(World world, BlockPos pos) {
        net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
        return block == net.minecraft.init.Blocks.GRASS || block == net.minecraft.init.Blocks.DIRT
            || block == net.minecraft.init.Blocks.SAND || block == net.minecraft.init.Blocks.GRAVEL
            || block == net.minecraft.init.Blocks.MYCELIUM || block == net.minecraft.init.Blocks.GRASS_PATH;
    }

    private static boolean isFree(World world, BlockPos pos) {
        return world.isAirBlock(pos) || world.getBlockState(pos).getBlock().isReplaceable(world, pos);
    }
}
