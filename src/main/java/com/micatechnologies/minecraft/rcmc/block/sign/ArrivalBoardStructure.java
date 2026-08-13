package com.micatechnologies.minecraft.rcmc.block.sign;

import com.micatechnologies.minecraft.rcmc.block.RcmcBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The shape of an arrival board, and the block bookkeeping that keeps that shape whole.
 *
 * <p><b>Why the board is a multiblock at all.</b> Its screen is drawn four blocks across and two
 * tall, which the block holding it plainly is not. A one-block board with a five-block picture
 * lights wrong, outlines wrong when you look at it, and lets you walk straight through the middle
 * of it — the panel is scenery painted on air. Making the space it occupies real fixes all three at
 * once, and it is the reason the render was sized to its final footprint first.</p>
 *
 * <p><b>Five columns, not four.</b> The screen is four blocks wide and centred on the master, so it
 * runs from 2.0 blocks left of the master's centre to 2.0 right — which straddles five block
 * columns, the outer two half-covered. Claiming four would leave half a block of screen hanging in
 * open air at each end. The art is unchanged; the block layout is what was chosen to contain
 * it.</p>
 *
 * <p>The master is the top-centre block — the one the player places, the one that holds the link
 * and the facing, and the only one with a renderer. The other nine are
 * {@link BlockArrivalBoardPart}, which draw nothing and exist to occupy space.</p>
 */
public final class ArrivalBoardStructure {

    /** Part columns either side of the master's own. Five wide in total. */
    public static final int HALF_WIDTH = 2;

    /** Rows, counting the master's own: the master's, and the one below it. */
    public static final int HEIGHT = 2;

    /**
     * Half the screen's thickness, from {@code RenderArrivalBoard}'s panel. The parts' collision and
     * selection boxes are this slab, so what you bump into and what you outline is the screen and
     * not a block-shaped approximation of it.
     */
    private static final double HALF_THICKNESS = 0.06D;

    private static final AxisAlignedBB SLAB_ALONG_X = new AxisAlignedBB(
        0.0D, 0.0D, 0.5D - HALF_THICKNESS, 1.0D, 1.0D, 0.5D + HALF_THICKNESS);
    private static final AxisAlignedBB SLAB_ALONG_Z = new AxisAlignedBB(
        0.5D - HALF_THICKNESS, 0.0D, 0.0D, 0.5D + HALF_THICKNESS, 1.0D, 1.0D);

    /**
     * Set while a board is tearing its own parts down, so that the parts going away do not each
     * decide the board is being broken and try to tear it down again.
     *
     * <p>Removing a part is how a player breaks the whole board — that propagation is the point. But
     * the master removing its own parts uses the same block change, and without a flag the first
     * part cleared would re-enter the master's demolition, which would clear the parts again. A
     * plain static is enough: every path here is a world block change, and those only happen on the
     * server thread.</p>
     */
    private static boolean demolishing;

    private ArrivalBoardStructure() {
        throw new AssertionError("No instances.");
    }

    /**
     * Which world axis the board's width runs along, from the sign's facing.
     *
     * <p>Facings are quantised to quarter turns (see {@code TileTransitSignBase}), so this is only
     * ever {@code X} or {@code Z}, and a 180° flip leaves the footprint exactly where it was — only
     * a quarter turn moves blocks. The renderer draws the screen along its local {@code +x} after
     * rotating by {@code -facing}, which puts the width on {@code X} at 0° and 180°, and on
     * {@code Z} at 90° and 270°.</p>
     */
    public static EnumFacing.Axis widthAxis(float facingDegrees) {
        int quarter = Math.round(facingDegrees / 90.0F) & 3;
        return (quarter & 1) == 0 ? EnumFacing.Axis.X : EnumFacing.Axis.Z;
    }

    /** The screen's slab, for a board whose width runs along {@code axis}. */
    public static AxisAlignedBB slab(EnumFacing.Axis axis) {
        return axis == EnumFacing.Axis.X ? SLAB_ALONG_X : SLAB_ALONG_Z;
    }

    /**
     * Every block the board occupies apart from the master itself — nine of them, in the master's
     * row and the row below.
     */
    public static List<BlockPos> partPositions(BlockPos master, EnumFacing.Axis axis) {
        List<BlockPos> positions = new ArrayList<>(HEIGHT * (2 * HALF_WIDTH + 1) - 1);
        for (int row = 0; row < HEIGHT; row++) {
            for (int column = -HALF_WIDTH; column <= HALF_WIDTH; column++) {
                if (row == 0 && column == 0) {
                    continue;
                }
                positions.add(along(master, axis, column).down(row));
            }
        }
        return positions;
    }

    /** True when every part position is free for the board to take. */
    public static boolean isClear(World world, BlockPos master, EnumFacing.Axis axis) {
        for (BlockPos pos : partPositions(master, axis)) {
            if (pos.getY() < 0 || pos.getY() >= world.getHeight() || !world.isBlockLoaded(pos)) {
                return false;
            }
            if (!world.getBlockState(pos).getBlock().isReplaceable(world, pos)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Fills in the parts around an already-placed master. Call {@link #isClear} first — this
     * overwrites whatever it finds.
     */
    public static void build(World world, BlockPos master, EnumFacing.Axis axis) {
        IBlockState part = RcmcBlocks.arrivalBoardPart.getDefaultState()
            .withProperty(BlockArrivalBoardPart.AXIS, axis);
        for (BlockPos pos : partPositions(master, axis)) {
            world.setBlockState(pos, part, 3);
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileArrivalBoardPart) {
                ((TileArrivalBoardPart) tile).setMaster(master);
            }
        }
    }

    /**
     * Removes the parts belonging to {@code master}, leaving the master itself alone. Parts that
     * belong to some other board are left where they are, so two boards built back to back cannot
     * eat each other.
     */
    public static void clear(World world, BlockPos master, EnumFacing.Axis axis) {
        demolishing = true;
        try {
            for (BlockPos pos : partPositions(master, axis)) {
                if (isPartOf(world, pos, master)) {
                    world.setBlockToAir(pos);
                }
            }
        } finally {
            demolishing = false;
        }
    }

    /**
     * Breaking any one block of a board breaks the whole board — called by a part as it goes away.
     * Nothing happens if the master is already gone, or if the master is the one doing the
     * removing.
     *
     * @param drop whether the board's item should be dropped; false when a creative player broke it
     */
    static void demolish(World world, BlockPos partPos, boolean drop) {
        if (demolishing || world.isRemote) {
            return;
        }
        TileEntity tile = world.getTileEntity(partPos);
        if (!(tile instanceof TileArrivalBoardPart)) {
            return;
        }
        BlockPos master = ((TileArrivalBoardPart) tile).master();
        if (master == null || !world.isBlockLoaded(master)
            || world.getBlockState(master).getBlock() != RcmcBlocks.arrivalBoard) {
            return;
        }
        // destroyBlock drops the board's single item; the master's own breakBlock then clears the
        // remaining parts. One item for one board, however many of its blocks were hit.
        if (drop) {
            world.destroyBlock(master, true);
        } else {
            world.setBlockToAir(master);
        }
    }

    private static boolean isPartOf(World world, BlockPos pos, BlockPos master) {
        if (!(world.getBlockState(pos).getBlock() instanceof BlockArrivalBoardPart)) {
            return false;
        }
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof TileArrivalBoardPart
            && master.equals(((TileArrivalBoardPart) tile).master());
    }

    private static BlockPos along(BlockPos from, EnumFacing.Axis axis, int steps) {
        return axis == EnumFacing.Axis.X ? from.add(steps, 0, 0) : from.add(0, 0, steps);
    }
}
