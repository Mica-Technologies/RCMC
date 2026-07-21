package com.micatechnologies.minecraft.rcmc.client.render;

import com.micatechnologies.minecraft.rcmc.entity.EntityCoasterCar;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.world.MetroDoors;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Renders a coaster car.
 *
 * <p><b>Placeholder geometry, real transform.</b> The box is temporary — models arrive in Phase
 * 4.3 — but the orientation pipeline is the one the mod will keep, and it is the part worth
 * getting right first.</p>
 *
 * <p><b>Why a matrix rather than yaw/pitch.</b> 1.12.2 entities carry only two rotation angles, so
 * a banked or inverted car is inexpressible through {@code rotationYaw}/{@code rotationPitch} —
 * there is no roll. Instead the car's full orthonormal {@link TrackFrame} is pushed onto the
 * matrix stack directly as a basis. Roll then costs nothing, inversions work, and the entity's two
 * angles are left as display-only values for whatever vanilla systems read them.</p>
 *
 * <p>The frame's three axes <em>are</em> a rotation matrix: {@code right}, {@code up} and
 * {@code forward} are orthonormal by construction (see {@code TrackFrame}), so loading them as
 * columns is exactly the rotation taking model space to world space. That is cheaper and more
 * numerically direct than converting to a quaternion first — though
 * {@code GlStateManager.rotate(org.lwjgl.util.vector.Quaternion)} does exist in 1.12.2 if a
 * quaternion path is ever wanted for interpolation.</p>
 */
public class RenderCoasterCar extends Render<EntityCoasterCar> {

    private static final ResourceLocation TEXTURE = null;

    public RenderCoasterCar(RenderManager manager) {
        super(manager);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityCoasterCar entity) {
        return TEXTURE;
    }

    @Override
    public void doRender(EntityCoasterCar entity, double x, double y, double z,
                         float entityYaw, float partialTicks) {
        TrackFrame frame = interpolatedFrame(entity, partialTicks);
        if (frame == null) {
            return;
        }

        // Stamp the frame so RiddenTrainRenderer knows vanilla's entity pass reached this car and
        // does not draw it a second time.
        RiddenTrainRenderer.markDrawn(entity.getEntityId());

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);

        // Column-major 4x4: the frame's axes as the rotation's columns.
        java.nio.FloatBuffer matrix = org.lwjgl.BufferUtils.createFloatBuffer(16);
        matrix.put(new float[] {
            (float) frame.right.x,   (float) frame.right.y,   (float) frame.right.z,   0.0F,
            (float) frame.up.x,      (float) frame.up.y,      (float) frame.up.z,      0.0F,
            (float) frame.forward.x, (float) frame.forward.y, (float) frame.forward.z, 0.0F,
            0.0F,                    0.0F,                    0.0F,                    1.0F,
        });
        matrix.flip();
        GlStateManager.multMatrix(matrix);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        // Culling off, deliberately, and not merely as a shortcut.
        //
        // The basis loaded above is (right, up, forward), and TrackFrame defines
        // right = forward x up — so right x up = -forward and the triple is LEFT-handed. Its
        // determinant is -1, which makes the matrix a reflection rather than a rotation, and a
        // reflection inverts the winding of every face. With culling on, GL then discards exactly
        // the faces that should be visible and keeps the ones facing away, so flying around a car
        // shows the inside of its far wall.
        //
        // Turning culling off is correct for this placeholder box and costs only fill rate on a
        // handful of quads. It is NOT the right answer for a real model: those need a proper
        // rotation, which means resolving the handedness at the source rather than here. Doing
        // that now would flip the sign of frame.right everywhere, and lateral G and camera roll
        // both key off it — not a change to make casually alongside a rendering fix.
        GlStateManager.disableCull();

        // Force the lightmap full-bright for a lit saloon.
        //
        // This is what "the interior lighting does not work" actually was. The car is drawn with
        // DefaultVertexFormats.POSITION_COLOR, which carries NO lightmap element, so every vertex
        // inherits whatever lightmap coordinate was last set — i.e. the ambient light of wherever
        // the car happens to be. At night that multiplies the whole model, light strips included,
        // down to darkness, and no vertex colour can climb back out of it. Tuning which world
        // light level trips the switch was fixing the wrong layer entirely.
        //
        // SignPanels already does this, and for the same reason: a lit display must not be
        // dimmed by the room it is in.
        boolean lit = specOf(entity) != null
            && specOf(entity).carStyle() == TrainSpec.CarStyle.METRO
            && saloonLightsOn(entity);
        float lastBrightnessX = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessX;
        float lastBrightnessY = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessY;
        if (lit) {
            net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(
                net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        }

        emitModel(entity, partialTicks);

        if (lit) {
            net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(
                net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit,
                lastBrightnessX, lastBrightnessY);
        }
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();

        // Interior signage last, inside the same car-space transform but after texturing is back —
        // the font needs it. Only metro stock has a saloon to hang a sign in.
        TrainSpec spec = specOf(entity);
        if (spec != null && spec.carStyle() == TrainSpec.CarStyle.METRO) {
            MetroInteriorSign.draw(entity.world, entity.trainId(),
                spec.carLength() / 0.72D, partialTicks);
        }

        GlStateManager.popMatrix();

        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    /** This car's train spec, or {@code null} before its train state has reached the client. */
    private static TrainSpec specOf(EntityCoasterCar entity) {
        RcmcWorldState state = RcmcWorldState.of(entity.world);
        Train train = state == null ? null : state.trains().train(entity.trainId());
        return train == null ? null : train.spec();
    }

    /**
     * Resolves a train's stored colour ordinal into an actual colour.
     *
     * <p>The spec keeps ordinals rather than the colour enum so {@code physics} need not depend on
     * {@code track} — see {@code TrainSpec}'s field javadoc. The renderer is the right place to
     * undo that indirection, because it is the only thing that needs a colour at all.</p>
     */
    private static float[] colourOf(TrainSpec spec, TrainSpec.Part part, int fallbackOrdinal) {
        com.micatechnologies.minecraft.rcmc.track.TrackPalette.Colour[] all =
            com.micatechnologies.minecraft.rcmc.track.TrackPalette.Colour.values();
        int ordinal = spec == null ? fallbackOrdinal : spec.colourOf(part);
        // Clamped rather than trusted: an ordinal from an older save, or a colour since removed,
        // should paint the car something rather than throw inside the render loop.
        com.micatechnologies.minecraft.rcmc.track.TrackPalette.Colour colour =
            all[Math.max(0, Math.min(all.length - 1, ordinal))];
        return new float[] { colour.red(), colour.green(), colour.blue() };
    }

    /**
     * The car's orientation blended between last tick's and this tick's, by {@code partialTicks}.
     *
     * <p>Position is already interpolated for us — {@code RenderManager} passes {@code x/y/z}
     * blended from the entity's previous and current position — but orientation is not, and using
     * the raw current frame makes a car's <em>rotation</em> step at 20 Hz while its position glides.
     * On a banked curve that reads as the body twitching against its own motion.</p>
     *
     * <p>Blending is a normalised lerp of the two basis vectors rather than a proper slerp. Over a
     * single tick the angle between successive frames is small, where nlerp and slerp differ
     * negligibly; slerp would only earn its cost if frames were ever far apart, which would itself
     * be the real bug.</p>
     */
    private static TrackFrame interpolatedFrame(EntityCoasterCar entity, float partialTicks) {
        TrackFrame current = entity.frame();
        if (current == null) {
            return null;
        }
        TrackFrame previous = entity.previousFrame();
        if (previous == null || previous == current) {
            return current;
        }
        double t = partialTicks;
        return new TrackFrame(
            current.position,
            previous.forward.lerp(current.forward, t).normalize(),
            previous.up.lerp(current.up, t).normalize());
    }

    /**
     * Draws the car, sized from its train's own spec rather than from constants.
     *
     * <p>Falling back to defaults when the train is unknown matters on the client, where a car
     * entity can exist for a tick or two before its train state arrives — drawing nothing there
     * would make cars visibly pop in.</p>
     */
    private static void emitModel(EntityCoasterCar entity, float partialTicks) {
        float length = 3.0F;
        float couplingGap = 0.5F;
        int seatRows = 2;
        boolean drawCoupling = true;
        TrainSpec spec = null;

        RcmcWorldState state = RcmcWorldState.of(entity.world);
        Train train = state == null ? null : state.trains().train(entity.trainId());
        if (train != null) {
            spec = train.spec();
            length = (float) spec.carLength();
            couplingGap = (float) spec.couplingGap();
            // Two riders abreast, so rows are half the seat count.
            seatRows = Math.max(1, spec.seatsPerCar() / 2);
            // The last car has nothing behind it; a bar there reads as a broken coupling.
            drawCoupling = entity.carIndex() < spec.carCount() - 1;
        }

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        if (spec != null && spec.carStyle() == TrainSpec.CarStyle.METRO) {
            // Smoothed client-side: the synced value arrives a few times a second, and drawing it
            // raw makes the leaves jump between a handful of positions per close.
            float doorFraction = MetroDoorAnimation.fraction(entity.world, entity.trainId(),
                MetroDoors.openFraction(entity.world, entity.trainId()), partialTicks);
            boolean lightsOn = saloonLightsOn(entity);
            // A car's outermost ends are the ones with nothing coupled beyond them.
            boolean outerFront = entity.carIndex() == 0;
            boolean outerRear = entity.carIndex() == spec.carCount() - 1;
            // Pantographs on alternate cars, like a real EMU consist.
            MetroCarModel.emit(buffer, length, drawCoupling, entity.carIndex() % 2 == 0,
                (float) wireHeightFor(entity), doorFraction, lightsOn, outerFront, outerRear,
                colourOf(spec, TrainSpec.Part.BODY, 3),
                colourOf(spec, TrainSpec.Part.TRIM, 4),
                colourOf(spec, TrainSpec.Part.SEATS, 1));
            tessellator.draw();

            // Glass last, blended. Translucent geometry drawn before the opaque body would blend
            // against whatever happened to precede it rather than against the car.
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.depthMask(false);
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            MetroCarModel.emitGlazing(buffer, length, doorFraction, outerFront, outerRear);
            tessellator.draw();
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            return;
        }
        CarModel.emit(buffer, length, seatRows, couplingGap, drawCoupling,
            colourOf(spec, TrainSpec.Part.BODY, 3),
            colourOf(spec, TrainSpec.Part.TRIM, 4),
            colourOf(spec, TrainSpec.Part.SEATS, 1));
        tessellator.draw();
    }

    /**
     * Whether this car's saloon lighting is lit: on below {@link #SALOON_LIGHT_THRESHOLD} of
     * outside light, off above it.
     *
     * <p>A real metro's lights are on continuously, but a car lit identically at noon and at
     * midnight has no lighting at all as far as a player can tell. Switching on the world's own
     * light level is what makes the interior read as lit — and it means a train diving into a
     * tunnel lights up, which is the moment the effect exists for.</p>
     *
     * <p>Sampled at the car's roof rather than its centre: the block the car body occupies is
     * usually air, but sampling inside a tunnel bore or under a station canopy is exactly where the
     * reading matters.</p>
     */
    private static boolean saloonLightsOn(EntityCoasterCar entity) {
        net.minecraft.util.math.BlockPos at = new net.minecraft.util.math.BlockPos(
            entity.posX, entity.posY + 2.0D, entity.posZ);
        if (!entity.world.isBlockLoaded(at)) {
            return false;
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
        // one form that is always current on the side that actually renders. Sky light scaled by it
        // is how bright the sky genuinely is here and now; block light needs no correction, since a
        // lamp is a lamp at any hour.
        int blockLight = entity.world.getLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, at);
        int skyLight = entity.world.getLightFor(net.minecraft.world.EnumSkyBlock.SKY, at);
        float sun = entity.world.getSunBrightness(1.0F);
        return Math.max((double) blockLight, skyLight * sun) < SALOON_LIGHT_THRESHOLD;
    }

    /** Below this world light level the saloon lights come on. Dusk, roughly. */
    private static final int SALOON_LIGHT_THRESHOLD = 10;

    /**
     * Height of the contact wire over the section this car is on, or {@code 0} where there is no
     * catenary — what the pantograph stretches to meet.
     */
    private static double wireHeightFor(EntityCoasterCar entity) {
        RcmcWorldState state = RcmcWorldState.of(entity.world);
        Train train = state == null ? null : state.trains().train(entity.trainId());
        if (train == null) {
            return 0.0D;
        }
        com.micatechnologies.minecraft.rcmc.track.TrackSection section =
            state.network().section(train.reference().sectionId());
        return section == null ? 0.0D
            : com.micatechnologies.minecraft.rcmc.track.TrackStyleIds
                .contactWireHeight(section.styleId());
    }
}
