package com.micatechnologies.minecraft.rcmc.block.sign;

import net.minecraft.nbt.NBTTagCompound;

/**
 * The line-map sign's tile entity: the base link plus which of the station's lines the map is
 * showing. Stored as a free-running counter rather than a line name, so "cycle to the next
 * line" needs no knowledge of the registry here — the renderer takes it modulo however many
 * lines currently serve the station, which also makes the sign self-heal when lines are added
 * or removed.
 */
public class TileStationSign extends TileTransitSignBase {

    private int lineIndex;

    public int lineIndex() {
        return lineIndex;
    }

    /** Advances the map to the station's next line — the sign's right-click action. */
    public void cycleLine() {
        lineIndex++;
        pushUpdate();
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        lineIndex = compound.getInteger("LineIndex");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("LineIndex", lineIndex);
        return compound;
    }
}
