package com.micatechnologies.minecraft.rcmc;

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
