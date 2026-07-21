package com.micatechnologies.minecraft.rcmc;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Sided-proxy contract. The only sanctioned bridge from common code into client-only
 * classes — see the side-discipline note on {@link Rcmc}.
 */
public interface RcmcProxy {

    void preInit(FMLPreInitializationEvent event);

    void init(FMLInitializationEvent event);

    void postInit(FMLPostInitializationEvent event);

    /**
     * Whether the local player is aboard train {@code trainId}. Always false on a server, which
     * has no local player.
     *
     * <p>Exists so common code can ask a client-only question through the sanctioned bridge rather
     * than reaching for {@code Minecraft.getMinecraft()} — the exact import that compiles fine and
     * then takes a dedicated server down on boot.</p>
     */
    boolean isLocalPlayerAboard(int trainId);
}
