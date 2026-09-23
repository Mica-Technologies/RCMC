package com.micatechnologies.minecraft.rcmc.block.sign;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The arrival board's tile entity. Everything it displays is derived at render time from the
 * synced transit registry and service snapshots — the board itself stores only its link, its
 * facing, and whether it owns a footprint. Exists as its own type so the board's renderer can be
 * bound to it.
 */
public class TileArrivalBoard extends TileTransitSignBase {

    /**
     * Whether this board owns the nine {@link BlockArrivalBoardPart}s around it.
     *
     * <p>False for every board placed before the board became a multiblock. Those keep working
     * exactly as they did — they render the same screen, link the same way, and break as one
     * block — because a world full of boards that silently stopped drawing, or that tried to claim
     * nine blocks of somebody's ceiling on load, would be a worse outcome than a board that is
     * simply smaller than it looks. Re-place one to get the footprint.</p>
     */
    private boolean structured;

    /**
     * How far the screen is shifted along its width from the middle of the master block, in blocks,
     * toward the positive world axis. Zero for a placed board.
     *
     * <p>A screen four blocks wide centred on a block's middle straddles half blocks at each end,
     * which is right for most ceilings and wrong for one: an island between two tracks whose clear
     * width is centred on a block <em>boundary</em>. There a centred screen overhangs one track by
     * half a block and a train standing at that platform hides the end of every row — which is
     * where the platform number is. The underground demo's islands shift theirs by half a block.</p>
     */
    private double screenShift;

    public double screenShift() {
        return screenShift;
    }

    /** Clamped to half a block either way — the screen always stays over its own footprint. */
    public void setScreenShift(double blocks) {
        this.screenShift = Math.max(-0.5D, Math.min(0.5D, blocks));
        pushUpdate();
    }

    public boolean isStructured() {
        return structured;
    }

    /**
     * Pushed to clients rather than only marked dirty: the selection and collision box is chosen
     * from this flag, and a client that still thought the board was one block would outline the
     * ceiling stub while the player is plainly looking at the screen.
     */
    public void setStructured(boolean value) {
        this.structured = value;
        pushUpdate();
    }

    /**
     * Turns the board, moving its footprint with it — or keeps the old facing if the new footprint
     * will not fit.
     *
     * <p>Facings are quarter turns, so half of them do not move a block at all: a board turned
     * 180° occupies exactly the same ten positions, and its screen is drawn on both faces anyway.
     * Only a quarter turn swings five blocks of screen through the ceiling, and that is the case
     * this handles — which is not hypothetical, because a board placed before its station exists
     * gets its facing again from the track the moment it links.</p>
     *
     * <p>The old parts come down first, since the two footprints share the master's own column and
     * a clearance check against the board's own blocks would always fail. If the new one is
     * obstructed the old one goes straight back up and the turn is refused: a board reading edge-on
     * is a nuisance, but a board with half its screen inside the wall is a bug.</p>
     */
    @Override
    public void setFacingDegrees(float degrees) {
        EnumFacing.Axis wanted = ArrivalBoardStructure.widthAxis(degrees);
        EnumFacing.Axis current = ArrivalBoardStructure.widthAxis(facingDegrees());
        if (!structured || world == null || world.isRemote || wanted == current) {
            super.setFacingDegrees(degrees);
            return;
        }
        ArrivalBoardStructure.clear(world, pos, current);
        if (!ArrivalBoardStructure.isClear(world, pos, wanted)) {
            ArrivalBoardStructure.build(world, pos, current);
            return;
        }
        super.setFacingDegrees(degrees);
        ArrivalBoardStructure.build(world, pos, wanted);
    }

    /**
     * The board's screen is far larger than the block that holds it — four blocks wide and two
     * tall, hanging below its ceiling mount — so the default one-block render box would cull it the
     * moment the mounting block itself left the view frustum. The screen would vanish while still
     * plainly on screen, which reads as a rendering bug rather than as culling.
     *
     * <p>Derived from the footprint with a block of slack all round, so it cannot drift out of step
     * with what {@code RenderArrivalBoard} draws.</p>
     */
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        double reach = ArrivalBoardStructure.HALF_WIDTH + 1;
        return new AxisAlignedBB(
            pos.getX() - reach,
            pos.getY() - ArrivalBoardStructure.HEIGHT,
            pos.getZ() - reach,
            pos.getX() + reach + 1.0D,
            pos.getY() + 1.5D,
            pos.getZ() + reach + 1.0D);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        structured = compound.getBoolean("Multiblock");
        screenShift = Math.max(-0.5D, Math.min(0.5D, compound.getDouble("ScreenShift")));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean("Multiblock", structured);
        compound.setDouble("ScreenShift", screenShift);
        return compound;
    }
}
