package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Makes auto-generated support columns solid.
 *
 * <p>Hooks {@code GetCollisionBoxesEvent}, which Forge fires from {@code World.getCollisionBoxes}
 * for every entity movement query. Appending to that list is what makes something collidable
 * without it being a block — which matters, because track deliberately is not blocks: that is what
 * lets a train run through unloaded chunks and keeps a long circuit from being hundreds of tile
 * entities. Placing real blocks for supports would reintroduce exactly the problems
 * {@code WorldSavedData} was chosen to avoid.</p>
 *
 * <p>Player-placed {@code BlockTrackSupport} already collides; it is a real block. This closes the
 * gap so generated and placed supports behave the same, which was the alternative worth avoiding
 * — one kind you can walk through and one you cannot is more confusing than either alone.</p>
 *
 * <p><b>This event is hot.</b> It fires on every entity move, several times per entity per tick,
 * and on every particle move too, so the cheap rejections come first: particles, no track, the
 * whole network's box, each section's box, and only then the individual columns. Both boxes are
 * built when track changes ({@link TrackSection#extent()}, {@link
 * com.micatechnologies.minecraft.rcmc.track.TrackNetwork#extent()}), never per query. Column
 * geometry is cached by {@link TrackSupports}; recomputing terrain probes here would be
 * ruinous.</p>
 */
public final class TrackCollisionHandler {

    /**
     * Extra margin on the per-section bounds test, in blocks.
     *
     * <p>A section's cached bounds describe the track itself; its columns hang <em>below</em> that,
     * down to the ground, so a query near the base of a tall support is outside the track's own
     * box. Rather than track a second bounds volume, the test is simply generous downward — a
     * missed column is a player falling through a support, which is the bug being fixed.</p>
     */
    private static final double VERTICAL_MARGIN = 256.0D;

    /** Curves bulge slightly beyond their control points; this keeps a column near a section's
     *  edge from being missed. */
    private static final double SLACK = 4.0D;

    @SubscribeEvent
    public void onGetCollisionBoxes(GetCollisionBoxesEvent event) {
        // Particles query with no entity. Supports exist to hold up players and mobs, and a
        // bubble drifting through a column is invisible, while the query volume is not: over
        // water every suspended particle asks every tick.
        if (event.getEntity() == null) {
            return;
        }
        RcmcWorldState state = RcmcWorldState.of(event.getWorld());
        if (state == null || state.network().isEmpty()) {
            return;
        }

        AxisAlignedBB query = event.getAabb();
        if (!mayContainColumns(state.network().extent(), query)) {
            return;
        }
        for (TrackSection section : state.network().sections()) {
            if (!mayContainColumns(section.extent(), query)) {
                continue;
            }
            for (TrackSupports.Column column : TrackSupports.columnsNear(section, event.getWorld(), query)) {
                AxisAlignedBB bounds = column.toBounds();
                if (bounds.intersects(query)) {
                    event.getCollisionBoxesList().add(bounds);
                }
                // The arm reaching in to banked or inverted track is structure a player can stand
                // on, same as the column. Omitting it would leave a walkable-looking beam that is
                // not there.
                AxisAlignedBB arm = column.armBounds();
                if (arm != null && arm.intersects(query)) {
                    event.getCollisionBoxesList().add(arm);
                }
            }
        }
    }

    /** Cheap reject against track's node box, widened downward to where columns reach. */
    private static boolean mayContainColumns(TrackSection.Extent extent, AxisAlignedBB query) {
        return extent.near(query.minX, query.minY, query.minZ, query.maxX, query.maxY, query.maxZ,
            SLACK, VERTICAL_MARGIN);
    }
}
