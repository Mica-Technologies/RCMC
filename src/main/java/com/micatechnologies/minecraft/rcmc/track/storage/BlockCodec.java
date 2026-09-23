package com.micatechnologies.minecraft.rcmc.track.storage;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.physics.block.BlockSection;
import com.micatechnologies.minecraft.rcmc.physics.block.BlockSystem;
import com.micatechnologies.minecraft.rcmc.physics.block.BlockSystems;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Saves each coaster's block sections: where the blocks are, and how hard their brakes stop a train.
 *
 * <p>Authored state, like the track and the ride hardware, so it is written into the undo snapshot
 * as well as the save. Before this existed a coaster's signalling vanished on every restart, and a
 * multi-train ride came back with nothing keeping its trains apart.</p>
 *
 * <p>Only the layout is saved, not occupancy: which train holds which block is worked out again from
 * the trains' positions on the first tick.</p>
 */
public final class BlockCodec {

    private static final String KEY_SYSTEMS = "BlockSystems";
    private static final String KEY_SECTION = "Section";
    private static final String KEY_CLOSED = "Closed";
    private static final String KEY_SAFETY = "Safety";
    private static final String KEY_BRAKE = "Brake";
    private static final String KEY_BLOCKS = "Blocks";
    private static final String KEY_ID = "Id";
    private static final String KEY_START = "Start";
    private static final String KEY_END = "End";

    private BlockCodec() {
    }

    public static void write(BlockSystems systems, NBTTagCompound root) {
        NBTTagList list = new NBTTagList();
        if (systems != null) {
            for (int sectionId : systems.sectionIds()) {
                BlockSystem system = systems.get(sectionId);
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger(KEY_SECTION, sectionId);
                tag.setBoolean(KEY_CLOSED, system.isClosedCircuit());
                tag.setBoolean(KEY_SAFETY, system.isSafetyEnabled());
                tag.setDouble(KEY_BRAKE, system.brakeDeceleration());
                NBTTagList blocks = new NBTTagList();
                for (BlockSection block : system.blocks()) {
                    NBTTagCompound b = new NBTTagCompound();
                    b.setString(KEY_ID, block.id());
                    b.setInteger(KEY_SECTION, block.sectionId());
                    b.setDouble(KEY_START, block.startDistance());
                    b.setDouble(KEY_END, block.endDistance());
                    blocks.appendTag(b);
                }
                tag.setTag(KEY_BLOCKS, blocks);
                list.appendTag(tag);
            }
        }
        root.setTag(KEY_SYSTEMS, list);
    }

    public static BlockSystems read(NBTTagCompound root) {
        BlockSystems systems = new BlockSystems();
        if (root == null || !root.hasKey(KEY_SYSTEMS)) {
            return systems;
        }
        NBTTagList list = root.getTagList(KEY_SYSTEMS, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            double brake = tag.getDouble(KEY_BRAKE);
            BlockSystem system = new BlockSystem(tag.getBoolean(KEY_CLOSED), tag.getBoolean(KEY_SAFETY),
                brake > 0.0D ? brake : 4.0D, RcmcConstants.SECONDS_PER_TICK);
            NBTTagList blocks = tag.getTagList(KEY_BLOCKS, 10);
            for (int j = 0; j < blocks.tagCount(); j++) {
                NBTTagCompound b = blocks.getCompoundTagAt(j);
                system.addBlock(new BlockSection(b.getString(KEY_ID), b.getInteger(KEY_SECTION),
                    b.getDouble(KEY_START), b.getDouble(KEY_END)));
            }
            systems.put(tag.getInteger(KEY_SECTION), system);
        }
        return systems;
    }
}
