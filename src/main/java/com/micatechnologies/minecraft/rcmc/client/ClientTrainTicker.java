package com.micatechnologies.minecraft.rcmc.client;

import com.micatechnologies.minecraft.rcmc.Rcmc;
import com.micatechnologies.minecraft.rcmc.RcmcConfig;
import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Runs the client's copy of the train simulation, once per client tick.
 *
 * <p><b>Why this class has to exist.</b> {@code TickEvent.WorldTickEvent} fires only on the
 * server — it is dispatched from the dedicated/integrated server's world loop, and no equivalent
 * runs for a {@code WorldClient}. Without a client-side ticker the client's trains only move when
 * a correction packet lands, which at the 4/s correction rate produces motion visibly stepping
 * four times a second no matter how smooth the underlying physics is.</p>
 *
 * <p>That is the whole client-prediction design working or not working: the server sends state
 * sparsely <em>because</em> the client is expected to run the identical deterministic simulation
 * in between. Skip this and the sparse correction rate stops being an optimisation and becomes the
 * frame rate of the ride.</p>
 *
 * <p>Client-only, reached exclusively through {@code RcmcClientProxy}.</p>
 */
@SideOnly(Side.CLIENT)
public final class ClientTrainTicker {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        if (world == null || mc.isGamePaused()) {
            // Pausing stops the integrated server's world tick too, so predicting through a pause
            // would run the client ahead of a server that is not moving.
            return;
        }

        RcmcWorldState state = RcmcWorldState.of(world);
        if (state == null || state.trains().isEmpty()) {
            return;
        }
        try {
            state.trains().tick(state.network(), null,
                RcmcConfig.physicsSubSteps, RcmcConstants.SECONDS_PER_TICK);
        }
        catch (RuntimeException e) {
            // A client-side prediction fault must never take the game down: the server's next
            // correction will put the train right again.
            Rcmc.LOGGER.error("Client train prediction failed this tick", e);
        }
    }
}
