package com.micatechnologies.minecraft.rcmc;

import com.micatechnologies.minecraft.rcmc.client.ClientTrainTicker;
import com.micatechnologies.minecraft.rcmc.client.RiderCamera;
import com.micatechnologies.minecraft.rcmc.client.hud.GForceEffects;
import com.micatechnologies.minecraft.rcmc.client.hud.RideHud;
import com.micatechnologies.minecraft.rcmc.client.hud.RideMonitor;
import com.micatechnologies.minecraft.rcmc.client.render.RenderCoasterCar;
import com.micatechnologies.minecraft.rcmc.client.render.track.TrackRenderer;
import com.micatechnologies.minecraft.rcmc.entity.EntityCoasterCar;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Client-side proxy. Entity/tile renderers, the track mesh cache, keybinds, the ride HUD
 * and camera-roll wiring all get installed from here.
 *
 * <p>This class and everything it reaches may reference {@code net.minecraft.client}.
 * Nothing outside {@code RcmcClientProxy}'s reachable graph may.</p>
 */
public class RcmcClientProxy extends RcmcCommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        RenderingRegistry.registerEntityRenderingHandler(EntityCoasterCar.class, RenderCoasterCar::new);
        // Without this the client never advances its trains between server corrections, and the
        // ride visibly steps at the correction rate rather than the frame rate.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ClientTrainTicker());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new RiderCamera());
        // Draws the track itself — see TrackRenderer's javadoc for why this is a
        // RenderWorldLastEvent listener rather than a TileEntitySpecialRenderer.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new TrackRenderer());
        // Ghost preview of what a click would build — see TrackPreviewRenderer for why a spline
        // needs previewing at all.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
            new com.micatechnologies.minecraft.rcmc.client.build.TrackPreviewRenderer());
        com.micatechnologies.minecraft.rcmc.client.build.BuildToolInput.register();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
            new com.micatechnologies.minecraft.rcmc.client.build.BuildToolInput());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
            new com.micatechnologies.minecraft.rcmc.client.build.BuildToolHud());
        // RideHud and GForceEffects both read from one shared RideMonitor rather than each
        // deriving rider state independently — see RideMonitor's javadoc.
        RideMonitor rideMonitor = new RideMonitor();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(rideMonitor);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new RideHud(rideMonitor));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new GForceEffects(rideMonitor));
        // ModelRegistryEvent is on the MOD bus, but this proxy is registered to the Forge bus by
        // its own preInit, so register it here to receive it.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Binds item models. Must run on {@code ModelRegistryEvent}: models bake before {@code init},
     * so registering a variant any later leaves the item rendering as the missing-model cube.
     */
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void registerModels(net.minecraftforge.client.event.ModelRegistryEvent event) {
        net.minecraft.item.Item tool = com.micatechnologies.minecraft.rcmc.item.RcmcItems.trackTool;
        bindModel(tool);
        bindModel(com.micatechnologies.minecraft.rcmc.item.RcmcItems.trackEditor);
        bindModel(com.micatechnologies.minecraft.rcmc.item.RcmcItems.pieceTool);
        bindModel(net.minecraft.item.Item.getItemFromBlock(
            com.micatechnologies.minecraft.rcmc.block.RcmcBlocks.trackSupport));
    }

    private static void bindModel(net.minecraft.item.Item item) {
        net.minecraftforge.client.model.ModelLoader.setCustomModelResourceLocation(item, 0,
            new net.minecraft.client.renderer.block.model.ModelResourceLocation(
                item.getRegistryName(), "inventory"));
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}
