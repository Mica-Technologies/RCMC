package com.micatechnologies.minecraft.rcmc.entity;

import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Keeps coaster and metro car entities ticking even when the terrain around them is not loaded —
 * the fix for "fast vehicles stop at the edge of loaded terrain" (Phase 1.4, option (a)).
 *
 * <h3>What actually freezes, and what does not</h3>
 *
 * <p>The 1-D train simulation does <b>not</b> freeze at a chunk boundary. It runs from
 * {@code RcmcWorldState.Hooks.onWorldTick} — a {@code WorldTickEvent} that fires once per world
 * tick regardless of which chunks are loaded — over the in-memory {@code TrainManager}, which needs
 * no blocks at all. This is the whole point of keeping {@code physics} free of Minecraft types: the
 * simulation lives above the chunk system, not inside it.</p>
 *
 * <p>What froze was the <b>entity</b>. Vanilla skips an entity's {@code onUpdate()} until a
 * 32-block radius around it is loaded (verified in {@code World.updateEntityWithOptionalForce};
 * see Appendix A.3 of the master plan). A car entity is purely presentational — every tick it reads
 * its train's along-track state and calls {@code setPosition} — so a skipped tick meant the model
 * lagged behind the simulation, stuttering at the edge of loaded terrain and then snapping forward
 * when the area reloaded. The simulation kept advancing underneath it; only the puppet stalled.</p>
 *
 * <h3>The escape hatch</h3>
 *
 * <p>Forge posts {@link EntityEvent.CanUpdate} precisely when that area check has already failed,
 * as an opt-out: {@code if (!canUpdate) canUpdate = ForgeEventFactory.canEntityUpdate(entity)}. The
 * event defaults to {@code false}; setting it {@code true} for our cars makes them keep ticking
 * anyway. No chunk tickets, no forced loading — option (b) in the plan — which are a server-admin
 * complaint generator and which the 1-D design does not need.</p>
 *
 * <p>This is safe because {@link EntityCoasterCar#onUpdate()} is world-independent: it does not call
 * {@code super.onUpdate()} (so none of vanilla's fire/water/portal/block-state probing runs), and it
 * touches only the in-memory {@code RcmcWorldState}. Ticking it in an unloaded area cannot load a
 * chunk or read a block, so it costs a handful of vector operations and nothing else.</p>
 *
 * <p>Note the limit: this rescues an entity that is <em>loaded but area-starved</em> (near the edge
 * of loaded terrain, which is the case a rider actually experiences, since a riding player keeps the
 * chunks around the train relevant). It does not resurrect an entity whose own chunk has fully
 * unloaded — that entity has left {@code loadedEntityList} and is not being iterated at all. That
 * case does not matter: with no player near enough to load the chunk there is nobody to see the car,
 * the simulation carries on regardless, and the entity re-derives its position from live train state
 * the moment its chunk reloads.</p>
 */
public final class CoasterCarUpdatePolicy {

    @SubscribeEvent
    public void onCanUpdate(EntityEvent.CanUpdate event) {
        if (event.getEntity() instanceof EntityCoasterCar) {
            // Presentational and world-independent — keep it in step with the simulation instead of
            // freezing 32 blocks short of unloaded terrain.
            event.setCanUpdate(true);
        }
    }
}
