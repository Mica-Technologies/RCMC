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
 * <p><b>This event is hot.</b> It fires on every entity move, several times per entity per tick, so
 * the cheap rejections come first: no track, then a per-section bounds test, and only then the
 * individual columns. Column geometry itself is cached by {@link TrackSupports}; recomputing
 * terrain probes here would be ruinous.</p>
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

    @SubscribeEvent
    public void onGetCollisionBoxes(GetCollisionBoxesEvent event) {
        RcmcWorldState state = RcmcWorldState.of(event.getWorld());
        if (state == null || state.network().isEmpty()) {
            return;
        }

        AxisAlignedBB query = event.getAabb();
        for (TrackSection section : state.network().sections()) {
            if (!mayContainColumns(section, query)) {
                continue;
            }
            for (TrackSupports.Column column : TrackSupports.columnsFor(section, event.getWorld())) {
                AxisAlignedBB bounds = column.toBounds();
                if (bounds.intersects(query)) {
                    event.getCollisionBoxesList().add(bounds);
                }
            }
        }
    }

    /**
     * Cheap horizontal reject against the section's own extent, widened downward.
     *
     * <p>Recomputing a section's bounds per query would defeat the point, so this uses the node
     * positions, which are already in memory and bound the curve closely enough for a broadphase.</p>
     */
    private static boolean mayContainColumns(TrackSection section, AxisAlignedBB query) {
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (com.micatechnologies.minecraft.rcmc.track.TrackNode node : section.nodes()) {
            minX = Math.min(minX, node.position().x);
            maxX = Math.max(maxX, node.position().x);
            minZ = Math.min(minZ, node.position().z);
            maxZ = Math.max(maxZ, node.position().z);
            maxY = Math.max(maxY, node.position().y);
        }
        // Curves bulge slightly beyond their control points; a couple of blocks of slack costs
        // nothing here and avoids missing a column near a section's edge.
        double slack = 4.0D;
        return query.maxX >= minX - slack && query.minX <= maxX + slack
            && query.maxZ >= minZ - slack && query.minZ <= maxZ + slack
            && query.minY <= maxY + slack && query.maxY >= maxY - VERTICAL_MARGIN;
    }
}
