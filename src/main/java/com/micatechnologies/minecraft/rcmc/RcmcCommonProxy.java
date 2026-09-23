package com.micatechnologies.minecraft.rcmc;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Server-side (and shared) proxy. {@link RcmcClientProxy} extends this, so anything put
 * here runs on both sides.
 */
public class RcmcCommonProxy implements RcmcProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
    }

    @Override
    public void init(FMLInitializationEvent event) {
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
    }

    /** No local player exists on a server. */
    @Override
    public boolean isLocalPlayerAboard(int trainId) {
        return false;
    }

    /** A server has nothing to speak with; announcements are a client concern. */
    @Override
    public void speakTts(String text, String voice) {
    }

    @Override
    public void warmUpTts() {
        // No client, nothing to speak with.
    }

    @Override
    public void showLineControl(com.micatechnologies.minecraft.rcmc.net.LineView view, boolean open) {
        // Server side there is no screen to show.
    }

    @Override
    public void showRideController(com.micatechnologies.minecraft.rcmc.net.RideView view, boolean open) {
        // Server side there is no screen to show.
    }

    @Override
    public void showTrackEditor(com.micatechnologies.minecraft.rcmc.net.TrackEditView view, boolean open) {
        // Server side there is no screen to show.
    }
}
