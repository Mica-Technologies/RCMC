package com.micatechnologies.minecraft.rcmc.world;

import java.util.List;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Makes trains solid: a standing train along its whole body, and an open metro car as just its
 * floor, so a passenger can walk aboard from a platform.
 *
 * <p>The car's own {@code getCollisionBoundingBox} cannot express this: one axis-aligned box cannot
 * describe a 20-block car that is not aligned to an axis, and the entity's box is square in plan, so
 * a floor derived from it covers only the middle of the car. {@link TrainFloors} builds the real
 * shape as a chain of short boxes; this hands them to Forge.</p>
 *
 * <p>Registered on <b>both sides</b>, like {@link TrackCollisionHandler} and for the same reason:
 * the server needs the floor to be real, and a client predicting its own movement must collide with
 * the same floor or the player rubber-bands at every doorway.</p>
 *
 * <p><b>This event is hot</b> — several calls per moving entity per tick. The cheap rejections come
 * first, and the boxes themselves are built at most once per tick by {@code TrainFloors}. With no
 * train berthed with its doors open, which is nearly always, this costs a map lookup and an empty
 * list check.</p>
 */
public final class TrainFloorCollision {

    @SubscribeEvent
    public void onGetCollisionBoxes(GetCollisionBoxesEvent event) {
        if (event.getEntity() == null) {
            // A query with no entity is asking about the world itself, not about something that
            // could stand on a train.
            return;
        }
        RcmcWorldState state = RcmcWorldState.of(event.getWorld());
        if (state == null || state.trains().isEmpty()) {
            return;
        }
        List<AxisAlignedBB> floors = state.trainFloors().floors(event.getWorld(), state);
        if (floors.isEmpty()) {
            return;
        }
        AxisAlignedBB query = event.getAabb();
        for (AxisAlignedBB floor : floors) {
            if (floor.intersects(query)) {
                event.getCollisionBoxesList().add(floor);
            }
        }
    }
}
