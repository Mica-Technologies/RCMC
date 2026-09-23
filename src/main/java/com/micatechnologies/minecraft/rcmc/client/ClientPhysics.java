package com.micatechnologies.minecraft.rcmc.client;

import com.micatechnologies.minecraft.rcmc.net.PhysicsSettings;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * The physics constants this client predicts trains with: the connected server's, once it has
 * sent them, and this client's own config until then.
 *
 * <p>Cleared on disconnect, so the next server — or the next singleplayer world — is never
 * simulated with the last one's values.</p>
 */
public final class ClientPhysics {

    private static volatile PhysicsSettings synced;

    private ClientPhysics() {
    }

    public static PhysicsSettings current() {
        PhysicsSettings settings = synced;
        return settings != null ? settings : PhysicsSettings.fromConfig();
    }

    public static void set(PhysicsSettings settings) {
        synced = settings;
    }

    /** Registered on the event bus from {@code RcmcClientProxy}. */
    public static final class Hooks {

        @SubscribeEvent
        public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            synced = null;
        }
    }
}
