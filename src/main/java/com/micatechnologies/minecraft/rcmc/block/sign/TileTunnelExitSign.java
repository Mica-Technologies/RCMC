package com.micatechnologies.minecraft.rcmc.block.sign;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * The distances a tunnel exit sign shows, in blocks, as typed: "240", or empty for none. Text
 * rather than a number so a builder can write "120" or "1.2k" or leave a side blank.
 */
public class TileTunnelExitSign extends TileEntity {

    /** Longest distance text kept. */
    public static final int MAX_LENGTH = 6;

    private String left = "";
    private String right = "";

    public String left() {
        return left;
    }

    public String right() {
        return right;
    }

    /** Sets both distances, trimmed to {@link #MAX_LENGTH}. Server side. */
    public void setDistances(String newLeft, String newRight) {
        left = clean(newLeft);
        right = clean(newRight);
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    private static String clean(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) : trimmed;
    }

    /** The plate reaches well past its block along the wall. */
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos.add(-3, 0, -3), pos.add(4, 1, 4));
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        left = compound.getString("Left");
        right = compound.getString("Right");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setString("Left", left);
        compound.setString("Right", right);
        return compound;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(super.getUpdateTag());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }
}
