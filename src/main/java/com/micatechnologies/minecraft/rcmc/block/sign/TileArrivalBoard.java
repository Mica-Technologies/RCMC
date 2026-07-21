package com.micatechnologies.minecraft.rcmc.block.sign;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The arrival board's tile entity. Everything it displays is derived at render time from the
 * synced transit registry and service snapshots — the board itself stores only its link and
 * facing, both from the base class. Exists as its own type so the board's renderer can be bound
 * to it.
 */
public class TileArrivalBoard extends TileTransitSignBase {

    /**
     * The board's screen is far larger than the block that holds it — roughly 3.2 blocks wide and
     * 1.5 tall, hanging below its ceiling mount — so the default one-block render box would cull
     * it the moment the mounting block itself left the view frustum. The screen would vanish while
     * still plainly on screen, which reads as a rendering bug rather than as culling.
     *
     * <p>Kept in step with the panel extents in {@code RenderArrivalBoard}: this box must contain
     * whatever that draws, with a little slack.</p>
     */
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(
            pos.getX() - 1.6D, pos.getY() - 1.0D, pos.getZ() - 1.6D,
            pos.getX() + 2.6D, pos.getY() + 1.5D, pos.getZ() + 2.6D);
    }
}
