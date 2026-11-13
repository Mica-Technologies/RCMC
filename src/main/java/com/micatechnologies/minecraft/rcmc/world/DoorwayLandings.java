package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.physics.CarSeating;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Where the platform is, outside a metro car's doorway: how wide the gap between the car's side and
 * the first thing a passenger could stand on, level with the car floor.
 *
 * <p>A platform is blocks and a train is not. The platform's edge falls wherever the block grid
 * puts it — against the car on straight track that happens to line up, most of a block away on a
 * diagonal or a curve, further still beside a platform built by hand a little too far out. A
 * passenger stepping off was put a fixed distance from the car whatever was there, and one walking
 * on crossed whatever was there; either way, where the grid left a gap they went down it.</p>
 *
 * <p>So the gap is measured, per doorway, and three things use the one answer: the car's floor
 * reaches out across it while the doors are open ({@link TrainFloors}), a gap plate is drawn doing
 * so, and a passenger stepping off is put on the platform rather than at a fixed offset. Measured
 * the same way on both sides, from the same track and the same blocks, so the client's prediction
 * and the server agree.</p>
 */
public final class DoorwayLandings {

    /** The widest gap, from the car's side to the platform's edge, that is bridged. Wider than
     *  this there is no platform here, only something near the train. */
    public static final double MAX_GAP = 1.75D;

    /** How far onto the platform a bridged floor reaches past its edge. */
    public static final double OVERLAP = 0.30D;

    /** How far a platform's top may stand above or below the car floor and still be stepped to:
     *  a player's step height. */
    private static final double STEP_HEIGHT = 0.6D;

    private static final double PROBE_STEP = 0.125D;

    /** What is outside one doorway. */
    public static final class Landing {

        /** From the car's side out to the platform's edge, in blocks; 0 where they meet. */
        public final double gap;

        /** World height of the platform's top there. */
        public final double top;

        Landing(double gap, double top) {
            this.gap = gap;
            this.top = top;
        }
    }

    private DoorwayLandings() {
        throw new AssertionError("No instances.");
    }

    /**
     * The landing outside the doorway centred {@code along} blocks from the middle of the car whose
     * body frame is {@code frame}, on {@code side} ({@code +1} the frame's right, {@code -1} its
     * left), or {@code null} if there is nothing to stand on within {@link #MAX_GAP}.
     *
     * <p>The gap is the widest found across the doorway, so on a platform whose edge runs at an
     * angle to the car the whole opening is covered, not just its middle. Never loads a chunk.</p>
     */
    public static Landing find(World world, TrackFrame frame, double along, int side) {
        if (world == null || frame == null) {
            return null;
        }
        // The middle of the doorway decides whether there is a platform here at all.
        Landing middle = probe(world, frame, along, side);
        if (middle == null) {
            return null;
        }
        double gap = middle.gap;
        double reach = CarSeating.DOOR_HALF_WIDTH - 0.3D;
        for (double offset = -reach; offset <= reach; offset += reach * 2.0D) {
            Landing edge = probe(world, frame, along + offset, side);
            if (edge != null && edge.gap > gap) {
                gap = edge.gap;
            }
        }
        return new Landing(gap, middle.top);
    }

    private static Landing probe(World world, TrackFrame frame, double along, int side) {
        double floor = CarSeating.METRO_FLOOR_HEIGHT;
        double bx = frame.position.x + frame.up.x * floor + frame.forward.x * along;
        double by = frame.position.y + frame.up.y * floor + frame.forward.y * along;
        double bz = frame.position.z + frame.up.z * floor + frame.forward.z * along;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (double gap = 0.0D; gap <= MAX_GAP + 1.0e-9D; gap += PROBE_STEP) {
            // A hair past the distance, so a platform edge exactly there is found at that distance.
            double out = side * (CarSeating.METRO_BODY_HALF_WIDTH + gap + 0.01D);
            double x = bx + frame.right.x * out;
            double z = bz + frame.right.z * out;
            double top = standingTop(world, pos, x, by, z);
            if (Double.isNaN(top)) {
                continue;
            }
            if (top == Double.NEGATIVE_INFINITY) {
                // A wall, or a chunk that is not loaded: nothing beyond it is a platform for this door.
                return null;
            }
            return new Landing(gap, top);
        }
        return null;
    }

    /**
     * The top of whatever could be stood on at {@code (x, z)} within a step of height {@code y}:
     * {@code NaN} for nothing, negative infinity for something that blocks the way instead.
     */
    private static double standingTop(World world, BlockPos.MutableBlockPos pos, double x, double y, double z) {
        for (int i = 0; i < 2; i++) {
            pos.setPos(x, y - (i == 0 ? 0.01D : STEP_HEIGHT), z);
            if (!world.isBlockLoaded(pos)) {
                return Double.NEGATIVE_INFINITY;
            }
            IBlockState state = world.getBlockState(pos);
            AxisAlignedBB box = state.getCollisionBoundingBox(world, pos);
            if (box == null || box == Block.NULL_AABB) {
                continue;
            }
            double top = pos.getY() + box.maxY;
            double lx = x - pos.getX();
            double lz = z - pos.getZ();
            if (lx < box.minX || lx > box.maxX || lz < box.minZ || lz > box.maxZ) {
                continue;
            }
            if (top > y + STEP_HEIGHT) {
                return Double.NEGATIVE_INFINITY;
            }
            if (top < y - STEP_HEIGHT) {
                continue;
            }
            // Room to stand: a block with nothing solid in the two blocks above its top.
            for (int up = 0; up < 2; up++) {
                pos.setPos(x, top + 0.5D + up, z);
                IBlockState above = world.getBlockState(pos);
                AxisAlignedBB head = above.getCollisionBoundingBox(world, pos);
                if (head != null && head != Block.NULL_AABB) {
                    return Double.NEGATIVE_INFINITY;
                }
            }
            return top;
        }
        return Double.NaN;
    }
}
