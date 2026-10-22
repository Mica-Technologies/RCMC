package com.micatechnologies.minecraft.rcmc.client.render;

import com.micatechnologies.minecraft.rcmc.entity.EntityCoasterCar;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.TrainStrike;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Whether a metro car's saloon lights are on, and lighting the people inside it to match.
 *
 * <h2>Why the decision is held, not recomputed</h2>
 *
 * <p>The lights switch on the world's own light level, so a car diving into a tunnel lights up.
 * That used to be decided afresh on every frame from a single cell at the roof. In a station lit
 * from the ceiling the block light changes cell by cell, often right around the threshold, so a car
 * creeping along the platform crossed it again and again and the saloon strobed. Now each car
 * keeps its state: it switches on only well below the threshold, off only well above it, never
 * more than once every {@link #MIN_HOLD_TICKS}, and keeps whatever it had when the probe cell is
 * not loaded.</p>
 *
 * <h2>Why passengers need help</h2>
 *
 * <p>A lit car is drawn full-bright, but a player standing in it renders through vanilla, which
 * lights them from the world at their feet — pitch dark in a tunnel. They showed up as black
 * silhouettes in a bright saloon. So anyone inside a lit car's body is drawn full-bright too,
 * using the same held state, so the two can never disagree.</p>
 *
 * <p>Client-only; registered from {@code RcmcClientProxy}.</p>
 */
public final class SaloonLighting {

    /** Below this world light level the saloon lights come on. Dusk, roughly. */
    private static final int THRESHOLD = 10;

    /** Half the dead band around {@link #THRESHOLD}: on below 8, off above 12. */
    private static final int HYSTERESIS = 2;

    /** The fewest ticks a car keeps its lights in one state before it may change again. */
    private static final long MIN_HOLD_TICKS = 60L;

    /** How far from a rendered entity to look for a car it could be standing in. */
    private static final double SEARCH_RADIUS = 14.0D;

    /** car entity id -> {lit ? 1 : 0, world tick it last changed}. */
    private static final Map<Integer, long[]> STATE = new HashMap<>();

    /** How often state for cars no longer in the world is dropped. */
    private static final long PRUNE_TICKS = 600L;

    private static long lastPruned;

    private float savedX;
    private float savedY;
    private boolean overridden;

    /** Whether this car's saloon is lit this frame. */
    static boolean lit(EntityCoasterCar car) {
        long now = car.world.getTotalWorldTime();
        if (now - lastPruned >= PRUNE_TICKS) {
            // Cars leave by unloading as often as by being removed, and neither says goodbye.
            lastPruned = now;
            STATE.keySet().removeIf(id -> car.world.getEntityByID(id) == null);
        }
        long[] state = STATE.get(car.getEntityId());
        if (state == null) {
            Double level = lightLevel(car);
            state = new long[] {level != null && level < THRESHOLD ? 1L : 0L, now};
            STATE.put(car.getEntityId(), state);
            return state[0] == 1L;
        }
        if (now - state[1] < MIN_HOLD_TICKS) {
            return state[0] == 1L;
        }
        Double level = lightLevel(car);
        if (level == null) {
            return state[0] == 1L;
        }
        boolean on = state[0] == 1L;
        if (!on && level < THRESHOLD - HYSTERESIS) {
            state[0] = 1L;
            state[1] = now;
        }
        else if (on && level > THRESHOLD + HYSTERESIS) {
            state[0] = 0L;
            state[1] = now;
        }
        return state[0] == 1L;
    }

    /**
     * How bright the world is at this car's roof, or null where that is not loaded.
     *
     * <p>Sampled at the roof rather than the centre: the block the car body occupies is usually
     * air, but a tunnel bore or a station canopy is exactly where the reading matters.</p>
     */
    private static Double lightLevel(EntityCoasterCar car) {
        BlockPos at = new BlockPos(car.posX, car.posY + 2.0D, car.posZ);
        if (!car.world.isBlockLoaded(at)) {
            return null;
        }
        // Stored sky light is 15 outdoors at midnight exactly as at noon — time of day never
        // touches it. Three ways of asking were tried before this one and all three failed
        // identically, so the reasoning is recorded rather than the answer:
        //
        //   getLight(pos)          — stored light, no time term at all.
        //   getLight(pos, true)    — the flag is checkNeighbors, not a time term either.
        //   sky - getSkylightSubtracted() — the right idea, wrong field: World.skylightSubtracted
        //                            is assigned only in calculateInitialSkylight() and through
        //                            setSkylightSubtracted(), never from World.tick(), so on a
        //                            CLIENT it is frozen at its construction value. 15 - 0 = 15.
        //
        // getSunBrightness is derived from the celestial angle on every call, which makes it the
        // one form that is always current on the side that actually renders.
        int blockLight = car.world.getLightFor(EnumSkyBlock.BLOCK, at);
        int skyLight = car.world.getLightFor(EnumSkyBlock.SKY, at);
        float sun = car.world.getSunBrightness(1.0F);
        return Math.max((double) blockLight, skyLight * sun);
    }

    @SubscribeEvent
    public void onRenderLivingPre(RenderLivingEvent.Pre<?> event) {
        overridden = false;
        Entity entity = event.getEntity();
        if (!insideLitCar(entity)) {
            return;
        }
        savedX = OpenGlHelper.lastBrightnessX;
        savedY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        overridden = true;
    }

    @SubscribeEvent
    public void onRenderLivingPost(RenderLivingEvent.Post<?> event) {
        if (overridden) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, savedX, savedY);
            overridden = false;
        }
    }

    private static boolean insideLitCar(Entity entity) {
        if (entity.getRidingEntity() instanceof EntityCoasterCar) {
            EntityCoasterCar car = (EntityCoasterCar) entity.getRidingEntity();
            return isMetro(car) && lit(car);
        }
        for (EntityCoasterCar car : entity.world.getEntitiesWithinAABB(EntityCoasterCar.class,
            entity.getEntityBoundingBox().grow(SEARCH_RADIUS))) {
            if (isMetro(car) && contains(car, entity) && lit(car)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMetro(EntityCoasterCar car) {
        Train train = trainOf(car);
        return train != null && train.spec().carStyle() == TrainSpec.CarStyle.METRO;
    }

    private static Train trainOf(EntityCoasterCar car) {
        RcmcWorldState state = RcmcWorldState.of(car.world);
        return state == null ? null : state.trains().train(car.trainId());
    }

    /** Whether {@code entity}'s feet are within the car's body, in the car's own axes. */
    private static boolean contains(EntityCoasterCar car, Entity entity) {
        RcmcWorldState state = RcmcWorldState.of(car.world);
        Train train = trainOf(car);
        if (state == null || train == null
            || !state.network().hasSection(train.reference().sectionId())) {
            return false;
        }
        TrackFrame frame = train.bodyFrameOfCar(state.network(), car.carIndex());
        TrainStrike.Body body = TrainStrike.bodyOf(train.spec());
        Vec3 offset = new Vec3(entity.posX, entity.posY, entity.posZ).subtract(frame.position);
        double up = offset.dot(frame.up);
        return Math.abs(offset.dot(frame.forward)) <= body.halfLength
            && Math.abs(offset.dot(frame.right)) <= body.halfWidth
            && up >= body.bottom - 0.5D && up <= body.top;
    }
}
