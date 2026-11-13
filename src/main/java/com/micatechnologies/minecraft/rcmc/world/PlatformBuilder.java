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
        int[] count = new int[2];
        walk(section, from, to, floorAbove, innerOffset, width, left, right, (pos, course, facing, s) -> {
            boolean gateHere = s >= gatesFrom && s <= gatesTo;
            if (course == 0 && gateHere && gated.add(pos) && placeGate(world, pos.up(), facing, gateSection)) {
                count[1]++;
            }
            if (!placed.add(pos) || !(isFree(world, pos) || isGround(world, pos))) {
                return true;
            }
            if (course == 0) {
                // The edge course carries the warning strip, facing the track it serves.
                world.setBlockState(pos, RcmcBlocks.platformEdge.getDefaultState()
                    .withProperty(BlockHorizontal.FACING, facing), 2);
            } else {
                world.setBlockState(pos, RcmcBlocks.platform.getDefaultState(), 2);
            }
            count[0]++;
            return true;
        });
        return new Result(count[0], count[1]);
    }

    /** The widest deck a removal follows outward from the edge course, in blocks. */
    private static final int REMOVE_REACH = 12;

    /**
     * Takes up the platform {@link #lay} put beside a stretch of track, and the air gates on its
     * edge that serve {@code gateSection}.
     *
     * <p>Worked out from the track's geometry, the same walk {@code lay} makes, rather than from a
     * record of what was placed: platforms laid before this existed have no record. Only RCMC's own
     * platform, platform edge and air gate blocks are taken, so anything a player built on or
     * beside the deck stays. The first {@code width} courses are always looked at; past them the
     * deck is followed outward only while it continues, since a platform may have been laid wider
     * than {@code width} but one that merely stands nearby is someone else's.</p>
     *
     * <p>Leaves air. Where a deck was laid into the ground, that is a shallow trench: what the
     * ground was is not known.</p>
     *
     * @return the deck blocks and gates removed; blocks in chunks that are not loaded are left
     */
    public static Result remove(World world, TrackSection section, double from, double to,
                                double floorAbove, double innerOffset, int width, int gateSection) {
        Set<BlockPos> seen = new HashSet<>();
        int[] count = new int[2];
        walk(section, from, to, floorAbove, innerOffset, REMOVE_REACH, true, true, (pos, course, facing, s) -> {
            if (!world.isBlockLoaded(pos)) {
                return course < width;
            }
            if (course == 0 && seen.add(pos.up())) {
                TileEntity tile = world.getTileEntity(pos.up());
                if (world.getBlockState(pos.up()).getBlock() == RcmcBlocks.airGate
                    && (!(tile instanceof TileAirGate)
                        || ((TileAirGate) tile).linkedSection() == gateSection
                        || ((TileAirGate) tile).linkedSection() == TileAirGate.UNLINKED)) {
                    world.setBlockToAir(pos.up());
                    count[1]++;
                }
            }
            net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
            boolean deck = block == RcmcBlocks.platform || block == RcmcBlocks.platformEdge;
            if (deck && seen.add(pos)) {
                world.setBlockToAir(pos);
                count[0]++;
            }
            // Already taken this pass counts as deck: the walk visits a block from several samples.
            return course < width - 1 || deck || seen.contains(pos);
        });
        return new Result(count[0], count[1]);
    }

    /** One block of a platform's footprint. */
    private interface Cell {
        /**
         * @param course 0 for the edge course, counting outward
         * @param facing toward the track
         * @param s      distance along the section this cell was reached from
         * @return whether to go on to the next course outward on this side at this sample
         */
        boolean visit(BlockPos pos, int course, EnumFacing facing, double s);
    }

    /**
     * Visits every block of the footprint a platform beside {@code section} covers, edge course
     * outward, each side, every half block along. One walk for laying and for removal, so the two
     * agree about where a platform is. A block is visited once per sample that reaches it.
     */
    private static void walk(TrackSection section, double from, double to, double floorAbove,
                             double innerOffset, int width, boolean left, boolean right, Cell cell) {
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
                    if (!cell.visit(pos, i, facing, s)) {
                        break;
                    }
                }
            }
        }
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
