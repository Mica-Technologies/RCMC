package com.micatechnologies.minecraft.rcmc.block.sign;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/**
 * A board part's one piece of state: which block of the board is the master.
 *
 * <p>Stored rather than searched for. A part could scan its neighbourhood for a master whose
 * footprint covers it, but two boards hung back to back would each find the other's, and a part
 * would answer differently depending on which way the scan happened to run. A stored position is
 * unambiguous and survives the master being renamed, relinked or re-faced.</p>
 *
 * <p>Never synced: nothing on the client asks. The parts render nothing, and the item a
 * middle-click yields is a constant.</p>
 */
public class TileArrivalBoardPart extends TileEntity {

    private BlockPos master;

    /** The board this part belongs to, or {@code null} for a part that was never claimed. */
    public BlockPos master() {
        return master;
    }

    public void setMaster(BlockPos position) {
        this.master = position;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        master = compound.hasKey("Master") ? BlockPos.fromLong(compound.getLong("Master")) : null;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (master != null) {
            compound.setLong("Master", master.toLong());
        }
        return compound;
    }
}
