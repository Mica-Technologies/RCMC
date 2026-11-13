package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.block.BlockTrackSupport;
import com.micatechnologies.minecraft.rcmc.track.ClearanceSweep;
import com.micatechnologies.minecraft.rcmc.track.ClearanceSweep.Occupancy;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Runs {@link ClearanceSweep} against the blocks of a world: what a train on a section would clip.
 *
 * <p>A block is in the way at a point when the point is inside one of its collision boxes — so a
 * slab, a fence or a stair is judged by its real shape, not its cube. A block with no collision is in
 * the way too when the point is inside its outline, unless it is the kind of thing a car brushes
 * through: anything replaceable (grass, snow, vines), a plant, or a liquid. That keeps torches,
 * signs, wires and banners in the report and flowers out of it.</p>
 *
 * <p>Never loads a chunk: a point in an unloaded one is {@link Occupancy#UNKNOWN} and counted.</p>
 */
public final class ClearanceChecks {

    /** A tiny box around a sample point, to ask a block for the collision boxes it would meet. */
    private static final double PROBE = 1.0e-3D;

    private ClearanceChecks() {
    }

    /** One intruding block, with what it is. */
    public static final class Intrusion {
        public final BlockPos pos;
        public final String block;
        public final double distance;

        Intrusion(BlockPos pos, String block, double distance) {
            this.pos = pos;
            this.block = block;
            this.distance = distance;
        }
    }

    /** The intrusions found, and how much could not be checked. */
    public static final class Report {
        public final List<Intrusion> intrusions;
        public final int unknownBlocks;

        Report(List<Intrusion> intrusions, int unknownBlocks) {
            this.intrusions = intrusions;
            this.unknownBlocks = unknownBlocks;
        }
    }

    public static Report check(World world, TrackSection section) {
        ClearanceSweep.Result result = ClearanceSweep.sweep(section, obstacles(world));
        List<Intrusion> out = new ArrayList<>(result.hits.size());
        for (ClearanceSweep.Hit hit : result.hits) {
            BlockPos pos = new BlockPos(hit.x, hit.y, hit.z);
            IBlockState state = world.getBlockState(pos);
            out.add(new Intrusion(pos, String.valueOf(state.getBlock().getRegistryName()), hit.distance));
        }
        return new Report(out, result.unknownBlocks);
    }

    /** The label the envelope a section is swept with goes by, for a report's heading. */
    public static String envelopeLabel(TrackSection section) {
        String first = ClearanceSweep.Envelope.forStyle(section.styleAtDistance(0.0D)).name;
        for (com.micatechnologies.minecraft.rcmc.track.TrackNode node : section.nodes()) {
            if (node.styleId() != null
                && !ClearanceSweep.Envelope.forStyle(node.styleId()).name.equals(first)) {
                return "coaster and metro";
            }
        }
        return first;
    }

    static ClearanceSweep.Obstacles obstacles(World world) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<AxisAlignedBB> boxes = new ArrayList<>();
        return (bx, by, bz, x, y, z) -> {
            pos.setPos(bx, by, bz);
            if (by < 0 || by >= world.getHeight()) {
                return Occupancy.CLEAR;
            }
            if (!world.isBlockLoaded(pos)) {
                return Occupancy.UNKNOWN;
            }
            IBlockState state = world.getBlockState(pos);
            if (state.getMaterial() == Material.AIR || ownFurniture(state)) {
                return Occupancy.CLEAR;
            }
            AxisAlignedBB probe = new AxisAlignedBB(x - PROBE, y - PROBE, z - PROBE, x + PROBE, y + PROBE, z + PROBE);
            boxes.clear();
            state.addCollisionBoxToList(world, pos, probe, boxes, null, false);
            if (!boxes.isEmpty()) {
                return Occupancy.BLOCKED;
            }
            if (passable(world, pos, state)) {
                return Occupancy.CLEAR;
            }
            AxisAlignedBB outline = state.getBoundingBox(world, pos).offset(pos);
            return outline.intersects(probe) ? Occupancy.BLOCKED : Occupancy.CLEAR;
        };
    }

    /**
     * What RCMC itself puts against the train on purpose: a coaster platform's edge and air gates
     * meet the car so riders can step aboard, and a support column meets the track it holds. Laying
     * a station would otherwise report its own platform as standing in the train's way.
     */
    private static boolean ownFurniture(IBlockState state) {
        net.minecraft.block.Block block = state.getBlock();
        return block instanceof BlockTrackSupport
            || block instanceof com.micatechnologies.minecraft.rcmc.block.BlockAirGate
            || block instanceof com.micatechnologies.minecraft.rcmc.block.BlockPlatform
            || block instanceof com.micatechnologies.minecraft.rcmc.block.BlockPlatformEdge;
    }

    /** Things a car brushes through without hitting anything. */
    static boolean passable(World world, BlockPos pos, IBlockState state) {
        Material material = state.getMaterial();
        return material.isLiquid() || material == Material.PLANTS || material == Material.VINE
            || material == Material.WEB || material == Material.FIRE || material == Material.PORTAL
            || state.getBlock().isReplaceable(world, pos);
    }
}
