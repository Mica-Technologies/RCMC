package com.micatechnologies.minecraft.rcmc;

import com.micatechnologies.minecraft.rcmc.entity.EntityCoasterCar;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main mod class for RCMC (Rollercoaster Minecraft).
 *
 * <p>RCMC adds spline-based rollercoaster track that is laid out as continuous curves
 * rather than block-aligned rails, and multi-car trains that ride it under a real physics
 * simulation (gravity, rolling/air resistance, lift hills, launches, brake runs, banking
 * and G-force limits).</p>
 *
 * <p>Lifecycle contract for the rest of the mod:</p>
 * <ul>
 *   <li>{@code preInit} — config, capabilities, network channel, tile entities, event bus
 *       registrations. Nothing here may touch a {@code World}.</li>
 *   <li>{@code init} — GUI handler, inter-mod-agnostic wiring.</li>
 *   <li>{@code postInit} — anything that must observe other mods' completed registries.</li>
 *   <li>{@code serverStarting} — server commands only.</li>
 * </ul>
 *
 * <p><b>Side discipline.</b> Everything reachable from this class must be loadable on a
 * dedicated server. Client-only code (renderers, camera control, the ride HUD) is reached
 * exclusively through {@link RcmcClientProxy}, never directly — a single stray import of a
 * {@code net.minecraft.client} type in common code fails the CI server smoke test, which is
 * exactly what that test exists to catch.</p>
 */
@Mod(modid = RcmcConstants.MOD_NAMESPACE,
     version = RcmcConstants.MOD_VERSION,
     name = RcmcConstants.MOD_NAME,
     acceptedMinecraftVersions = "[1.12.2]")
public class Rcmc {

    public static final Logger LOGGER = LogManager.getLogger(RcmcConstants.MOD_NAMESPACE);

    @SidedProxy(clientSide = "com.micatechnologies.minecraft.rcmc.RcmcClientProxy",
                serverSide = "com.micatechnologies.minecraft.rcmc.RcmcCommonProxy")
    public static RcmcProxy proxy;

    @Mod.Instance(RcmcConstants.MOD_NAMESPACE)
    public static Rcmc instance;

    /** Entity network ids, allocated in registration order. Must be stable across versions. */
    private static int entityId = 0;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        RcmcConfig.init(event.getSuggestedConfigurationFile());
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new RcmcWorldState.Hooks());
        com.micatechnologies.minecraft.rcmc.net.RcmcNetwork.init();
        RcmcTab.initTabElements();
        proxy.preInit(event);
        LOGGER.info("I am {} at version {}", RcmcConstants.MOD_NAME, RcmcConstants.MOD_VERSION);
    }

    @SubscribeEvent
    public void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        event.getRegistry().register(
            EntityEntryBuilder.create()
                .entity(EntityCoasterCar.class)
                .id(new ResourceLocation(RcmcConstants.MOD_NAMESPACE, "coaster_car"), entityId++)
                .name("coaster_car")
                // Tracking range and frequency are both far above vanilla's minecart defaults
                // (80 blocks / every 3 ticks). A coaster is visible from across a park, and its
                // cars are reconstructed each tick from synced train state rather than from
                // position packets — so the tracker's job here is presence, not motion.
                // sendsVelocityUpdates is false: this entity's motion fields are derived, and
                // vanilla velocity packets would only add traffic nothing consumes.
                .tracker(160, 1, false)
                .build());
    }

    @SubscribeEvent
    public void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(RcmcRegistry.getBlocks().toArray(new Block[0]));
    }

    @SubscribeEvent
    public void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(RcmcRegistry.getItems().toArray(new Item[0]));
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new com.micatechnologies.minecraft.rcmc.command.CommandRcmc());
    }
}
