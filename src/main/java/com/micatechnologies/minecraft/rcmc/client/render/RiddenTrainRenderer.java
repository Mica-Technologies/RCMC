package com.micatechnologies.minecraft.rcmc.client.render;

import com.micatechnologies.minecraft.rcmc.entity.EntityCoasterCar;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Draws the train you are riding when vanilla's entity loop has skipped it.
 *
 * <p><b>Why this is needed, read from the 1.12.2 sources rather than guessed.</b>
 * {@code RenderGlobal.renderEntities} does not iterate the world's entities. It iterates the
 * <em>visible render chunks</em> and, for each, pulls that chunk section's entity list:</p>
 *
 * <pre>
 *   for (ContainerLocalRenderInformation info : this.renderInfos) {
 *       Chunk chunk = this.world.getChunk(info.renderChunk.getPosition());
 *       ClassInheritanceMultiMap&lt;Entity&gt; list =
 *           chunk.getEntityLists()[info.renderChunk.getPosition().getY() / 16];
 *       ...
 *       boolean flag = this.renderManager.shouldRender(...) || entity2.isRidingOrBeingRiddenBy(mc.player);
 * </pre>
 *
 * <p>So {@code ignoreFrustumCheck} and {@code isRidingOrBeingRiddenBy} — both of which this mod's
 * cars satisfy — only affect the <em>inner</em> test. If the car's chunk section is not in
 * {@code renderInfos}, the outer loop never reaches the entity at all and no flag can save it. A
 * fast train crossing a chunk boundary, or one whose section is momentarily culled by the
 * visibility graph, simply vanishes for a frame or two. Setting a bigger render bounding box does
 * nothing, because the box is only consulted after the entity has already been found.</p>
 *
 * <p>The fix is to draw it ourselves, after the world, and only when vanilla did not:
 * {@link RenderCoasterCar} stamps each car with the frame it was drawn on, and anything belonging
 * to the ridden train that is missing this frame's stamp gets drawn here. That keeps the normal
 * path in charge whenever it works, so nothing is drawn twice.</p>
 *
 * <p>Scoped to the ridden train on purpose. Every large entity in 1.12.2 has this problem, and
 * chasing it for trains you are merely looking at would mean second-guessing the culling for the
 * whole world; a train vanishing around you while you stand in it is the case that actually
 * ruins the ride.</p>
 */
@SideOnly(Side.CLIENT)
public class RiddenTrainRenderer {

    /** Entity id -> the frame it was last drawn on by the normal entity pass. */
    private static final Map<Integer, Long> DRAWN = new HashMap<>();

    private static long frame;

    /** The frame currently being rendered; {@link RenderCoasterCar} stamps cars with it. */
    static long currentFrame() {
        return frame;
    }

    static void markDrawn(int entityId) {
        DRAWN.put(entityId, frame);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        Entity vehicle = player == null ? null : player.getRidingEntity();
        if (mc.world != null && vehicle instanceof EntityCoasterCar) {
            int trainId = ((EntityCoasterCar) vehicle).trainId();
            for (Entity entity : mc.world.getLoadedEntityList()) {
                if (!(entity instanceof EntityCoasterCar)) {
                    continue;
                }
                EntityCoasterCar car = (EntityCoasterCar) entity;
                if (car.trainId() != trainId || car.isDead) {
                    continue;
                }
                Long drawnOn = DRAWN.get(car.getEntityId());
                if (drawnOn != null && drawnOn == frame) {
                    // The normal pass got it; drawing again would double the geometry and show as
                    // z-fighting on every face.
                    continue;
                }
                // renderEntityStatic applies the same interpolation and camera offset the normal
                // pass uses, so this lands in exactly the place vanilla would have put it.
                mc.getRenderManager().renderEntityStatic(car, event.getPartialTicks(), false);
            }
        }
        // Advance last: everything above compares against the frame the entity pass stamped.
        frame++;
        if (DRAWN.size() > 512) {
            DRAWN.clear();
        }
    }
}
