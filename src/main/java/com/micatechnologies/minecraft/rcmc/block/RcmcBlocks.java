package com.micatechnologies.minecraft.rcmc.block;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.RcmcRegistry;
import net.minecraft.item.ItemBlock;

/**
 * Every block RCMC adds, plus the {@link ItemBlock} that lets each be carried.
 *
 * <p>Called from {@code preInit}, and deliberately before items: a block must exist before its
 * {@code ItemBlock} can reference it, and both must be registered before models bake.</p>
 */
public final class RcmcBlocks {

    public static BlockTrackSupport trackSupport;
    public static com.micatechnologies.minecraft.rcmc.block.sign.BlockStationSign stationSign;
    public static com.micatechnologies.minecraft.rcmc.block.sign.BlockArrivalBoard arrivalBoard;

    private RcmcBlocks() {
        throw new AssertionError("No instances.");
    }

    public static void init() {
        trackSupport = RcmcRegistry.addBlock(new BlockTrackSupport());
        stationSign = RcmcRegistry.addBlock(
            new com.micatechnologies.minecraft.rcmc.block.sign.BlockStationSign());
        arrivalBoard = RcmcRegistry.addBlock(
            new com.micatechnologies.minecraft.rcmc.block.sign.BlockArrivalBoard());

        // An ItemBlock carries the block's registry name, not its own — they share a namespace and
        // Forge matches them by name when binding models.
        RcmcRegistry.addItem(new ItemBlock(trackSupport)
            .setRegistryName(RcmcConstants.MOD_NAMESPACE, BlockTrackSupport.NAME));
        RcmcRegistry.addItem(new ItemBlock(stationSign).setRegistryName(
            RcmcConstants.MOD_NAMESPACE,
            com.micatechnologies.minecraft.rcmc.block.sign.BlockStationSign.NAME));
        RcmcRegistry.addItem(new ItemBlock(arrivalBoard).setRegistryName(
            RcmcConstants.MOD_NAMESPACE,
            com.micatechnologies.minecraft.rcmc.block.sign.BlockArrivalBoard.NAME));

        // Tile entities travel with their blocks. Registry-namespaced ids, per convention.
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(
            com.micatechnologies.minecraft.rcmc.block.sign.TileStationSign.class,
            new net.minecraft.util.ResourceLocation(RcmcConstants.MOD_NAMESPACE, "station_sign"));
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(
            com.micatechnologies.minecraft.rcmc.block.sign.TileArrivalBoard.class,
            new net.minecraft.util.ResourceLocation(RcmcConstants.MOD_NAMESPACE, "arrival_board"));
    }
}
