package com.micatechnologies.minecraft.rcmc.track.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.block.BlockSection;
import com.micatechnologies.minecraft.rcmc.physics.block.BlockSystem;
import com.micatechnologies.minecraft.rcmc.physics.block.BlockSystems;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A coaster's block sections survive a save — found lost on every restart in review. */
class BlockCodecTest {

    @Test
    @DisplayName("a coaster's blocks come back exactly as they were laid out")
    void blocksRoundTrip() {
        BlockSystems systems = new BlockSystems();
        BlockSystem system = new BlockSystem(true, true, 4.0D, 1.0D / 20.0D);
        system.addBlock(new BlockSection("b1", 3, 0.0D, 118.1D));
        system.addBlock(new BlockSection("b2", 3, 118.1D, 236.2D));
        system.addBlock(new BlockSection("b3", 3, 236.2D, 472.6D));
        systems.put(3, system);

        NBTTagCompound tag = new NBTTagCompound();
        BlockCodec.write(systems, tag);
        BlockSystem back = BlockCodec.read(tag).get(3);

        assertNotNull(back, "the ride's signalling must survive the save");
        assertEquals(3, back.blockCount());
        assertTrue(back.isClosedCircuit());
        assertTrue(back.isSafetyEnabled());
        assertEquals(4.0D, back.brakeDeceleration(), 1e-9);
        assertEquals("b2", back.blocks().get(1).id());
        assertEquals(236.2D, back.blocks().get(2).startDistance(), 1e-9);
        assertEquals(472.6D, back.blocks().get(2).endDistance(), 1e-9);
    }

    @Test
    @DisplayName("a save from before block signalling was saved loads with none")
    void olderSaveHasNoBlocks() {
        assertTrue(BlockCodec.read(new NBTTagCompound()).isEmpty());
        BlockSystems empty = new BlockSystems();
        NBTTagCompound tag = new NBTTagCompound();
        BlockCodec.write(empty, tag);
        assertFalse(!BlockCodec.read(tag).isEmpty());
    }
}
