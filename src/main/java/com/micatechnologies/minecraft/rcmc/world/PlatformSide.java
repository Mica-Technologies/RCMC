package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.block.RcmcBlocks;
import com.micatechnologies.minecraft.rcmc.physics.CarSeating;
import com.micatechnologies.minecraft.rcmc.physics.transit.DoorSide;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Works out which side of the track a station's platform is on, by looking for one.
 *
 * <p><b>Why detect rather than declare.</b> A builder has already answered this question by putting
 * a platform somewhere — asking them to say it a second time in a command is the kind of duplicate
 * authoring the transit tool exists to remove, and the two answers could then disagree. Reading the
 * world means a platform laid by {@code /rcmc platform} and one built by hand are treated
 * identically — see {@link #isDecking} for what counts, which is a shape rather than a block.</p>
 *
 * <p>Lives in {@code world} because it needs a {@link World}; {@code physics.transit} stays free of
 * Minecraft types and only ever handles the {@link DoorSide} that comes out.</p>
 */
public final class PlatformSide {

    /**
     * How far out from the car's side to look for platform decking, in blocks.
     *
     * <p>Starts just outside the body and reaches a few blocks further, so a platform set back
     * behind an edge strip still counts. Beyond that it is scenery, not a platform.</p>
     */
    private static final double SEARCH_FROM = CarSeating.METRO_BODY_HALF_WIDTH + 0.5D;
    private static final double SEARCH_TO = CarSeating.METRO_BODY_HALF_WIDTH + 3.5D;

    /**
     * How far along the track to sample, either side of the stop point.
     *
     * <p>A platform runs the length of the train, and the stop point is where the <em>lead car</em>
     * berths — so sampling only at the stop point would miss a platform that starts a little behind
     * it, and would be fooled by a single stray block. Several samples along the alignment is both
     * more forgiving and harder to fool.</p>
     */
    private static final double SAMPLE_REACH = 12.0D;
    private static final int SAMPLES = 5;

    /**
     * How much clear space a deck needs above it to count as one, in blocks.
     *
     * <p>This is the whole test that separates a platform from a wall. Both are solid at car-floor
     * height — a tunnel's side wall passes through that level just as decking does — but a wall
     * carries on upwards and a platform has a passenger standing on it. Two blocks is the space
     * that passenger occupies, and it is the smallest opening anyone would build to walk through.</p>
     */
    private static final int HEADROOM = 2;

    private PlatformSide() {
        throw new AssertionError("No instances.");
    }

    /**
     * The side {@code station}'s platform is on, or {@code null} if no platform can be found.
     *
     * <p>{@code null} means "do not know", which is different from {@link DoorSide#BOTH}: the
     * caller should leave whatever the station already had rather than overwrite an authored
     * answer with a guess.</p>
     */
    public static DoorSide detect(World world, TrackNetwork network, TransitStation station) {
        if (world == null || network == null || station == null) {
            return null;
        }
        if (!network.hasSection(station.stopPoint().sectionId())) {
            return null;
        }
        boolean left = false;
        boolean right = false;
        double base = station.stopPoint().distance();
        for (int i = 0; i < SAMPLES; i++) {
            double offset = SAMPLES == 1 ? 0.0D
                : -SAMPLE_REACH + 2.0D * SAMPLE_REACH * i / (SAMPLES - 1);
            TrackFrame frame = frameAt(network, station, base + offset);
            if (frame == null) {
                continue;
            }
            left |= hasPlatform(world, frame, -1.0D);
            right |= hasPlatform(world, frame, 1.0D);
        }
        if (left && right) {
            return DoorSide.BOTH;
        }
        if (left) {
            return DoorSide.LEFT;
        }
        return right ? DoorSide.RIGHT : null;
    }

    private static TrackFrame frameAt(TrackNetwork network, TransitStation station, double distance) {
        try {
            return network.frameAt(new com.micatechnologies.minecraft.rcmc.track.TrackRef(
                station.stopPoint().sectionId(), distance));
        }
        catch (RuntimeException e) {
            // Sampled past the end of an open section. Nothing there to find; not an error.
            return null;
        }
    }

    /** Whether platform decking sits on {@code sign} side of the track at this frame. */
    private static boolean hasPlatform(World world, TrackFrame frame, double sign) {
        // At car-floor height, which is the whole point of a platform: the surface a passenger
        // walks in from is level with the saloon floor, so that is where it will be found.
        double y = frame.position.y + frame.up.y * CarSeating.METRO_FLOOR_HEIGHT;
        for (double across = SEARCH_FROM; across <= SEARCH_TO; across += 1.0D) {
            double x = frame.position.x + frame.right.x * sign * across;
            double z = frame.position.z + frame.right.z * sign * across;
            // The block UNDER foot level: decking is the surface, so its own top is at floor height.
            BlockPos pos = new BlockPos(Math.floor(x), Math.floor(y) - 1, Math.floor(z));
            if (!world.isBlockLoaded(pos)) {
                continue;
            }
            if (isDecking(world, pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the block at {@code pos} is something a passenger could step out onto.
     *
     * <p>RCMC's own platform blocks are decking by definition. Anything else has to earn it, and
     * the test is a solid top face with {@link #HEADROOM} clear above: a surface you can stand on,
     * with room to stand. That is deliberately generous about <em>material</em> — a platform built
     * from quartz, stone slabs or concrete is a platform, and the demo alignments and most
     * hand-built stations are not made of RCMC blocks at all — while staying strict about
     * <em>shape</em>, which is what actually distinguishes decking from the tunnel wall running
     * past at the same height.</p>
     *
     * <p>Nothing else nearby passes both halves. A neighbouring track's bed sits at rail level,
     * two blocks below a car floor, so it is never solid at the height this probes. A wall is
     * solid here and solid above. Only a walkable surface is solid here and open above.</p>
     */
    private static boolean isDecking(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block == RcmcBlocks.platform || block == RcmcBlocks.platformEdge) {
            return true;
        }
        if (!world.isSideSolid(pos, EnumFacing.UP)) {
            return false;
        }
        for (int up = 1; up <= HEADROOM; up++) {
            BlockPos above = pos.up(up);
            // An unloaded neighbour is unknown, not clear — refusing to guess here keeps a
            // chunk boundary from inventing a platform out of a wall.
            if (!world.isBlockLoaded(above)
                || world.getBlockState(above).getMaterial().blocksMovement()) {
                return false;
            }
        }
        return true;
    }
}
