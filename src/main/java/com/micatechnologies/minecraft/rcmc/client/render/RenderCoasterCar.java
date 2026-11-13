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

    private static final MetroCarModel.Pass[] METRO_PASSES = {
        MetroCarModel.Pass.EXTERIOR, MetroCarModel.Pass.INTERIOR};

    /** Scratch for the car's basis matrix; render thread only. */
    private static final java.nio.FloatBuffer BASIS = org.lwjgl.BufferUtils.createFloatBuffer(16);

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

        // Column-major 4x4: the frame's axes as the rotation's columns. One buffer, refilled: a
        // direct buffer allocated per car per frame is native memory the collector frees late.
        java.nio.FloatBuffer matrix = BASIS;
        matrix.clear();
        matrix.put((float) frame.right.x).put((float) frame.right.y).put((float) frame.right.z).put(0.0F);
        matrix.put((float) frame.up.x).put((float) frame.up.y).put((float) frame.up.z).put(0.0F);
        matrix.put((float) frame.forward.x).put((float) frame.forward.y).put((float) frame.forward.z).put(0.0F);
        matrix.put(0.0F).put(0.0F).put(0.0F).put(1.0F);
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

        // A lit saloon is drawn full-bright, the rest of the car in the world's light: see
        // emitModel. The full-bright part must be forced through the lightmap, not the colours —
        // the car is drawn with DefaultVertexFormats.POSITION_COLOR, which carries no lightmap
        // element, so every vertex takes whatever lightmap coordinate was last set, the ambient
        // light of wherever the car is, and no vertex colour can climb back out of a dark one.
        emitModel(entity, partialTicks);
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();

        // Interior signage last, inside the same car-space transform but after texturing is back —
        // the font needs it. Only metro stock has a saloon to hang a sign in.
        TrainSpec spec = specOf(entity);
        if (spec != null && spec.carStyle() == TrainSpec.CarStyle.METRO) {
            double bodyLength = spec.carLength() / 0.72D;
            boolean cabFront = entity.carIndex() == 0;
            boolean cabRear = entity.carIndex() == spec.carCount() - 1;
            MetroInteriorSign.draw(entity.world, entity.trainId(), bodyLength, cabFront, cabRear, partialTicks);
            MetroExteriorSign.draw(entity.world, entity.trainId(), bodyLength, partialTicks);
            MetroExteriorSign.drawEnds(entity.world, entity.trainId(), bodyLength, cabFront, cabRear,
                partialTicks);
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
        if (spec != null && spec.carStyle() == TrainSpec.CarStyle.METRO) {
            // Smoothed client-side: the synced value arrives a few times a second, and drawing it
            // raw makes the leaves jump between a handful of positions per close.
            float doorFraction = MetroDoorAnimation.fraction(entity.world, entity.trainId(),
                MetroDoors.openFraction(entity.world, entity.trainId()),
                MetroDoors.openRate(entity.world, entity.trainId()), partialTicks);
            boolean lightsOn = SaloonLighting.lit(entity);
            // A car's outermost ends are the ones with nothing coupled beyond them.
            boolean outerFront = entity.carIndex() == 0;
            boolean outerRear = entity.carIndex() == spec.carCount() - 1;
            // Pantographs on alternate cars, like a real EMU consist.
            // Only the platform side opens. The side is track-relative and so is this model's
            // +x axis (the frame is loaded straight onto the matrix stack above), so no conversion
            // is needed here — and metro stock is double-ended, so a reversing service does not
            // flip the model out from under it.
            com.micatechnologies.minecraft.rcmc.physics.transit.DoorSide side =
                com.micatechnologies.minecraft.rcmc.world.MetroDoors.openSide(
                    entity.world, entity.trainId());
            float rightFraction = side.opensRight() ? doorFraction : 0.0F;
            float leftFraction = side.opensLeft() ? doorFraction : 0.0F;
            // Two passes: the outside of the car in the light of wherever it is, then the saloon,
            // full-bright while its lights are on. Drawing the whole car full-bright made a train
            // in a dark tunnel glow from end to end like a toy; a real one is dark outside with
            // its windows lit.
            float worldX = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessX;
            float worldY = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessY;
            boolean pantograph = entity.carIndex() % 2 == 0;
            float wire = (float) wireHeightFor(entity);
            boolean thirdRail = onThirdRail(entity);
            // A car whose doors are not moving looks the same from one frame to the next, so its
            // geometry is compiled once and replayed. Building it again every frame was a third of
            // the frame with a few trains in view. Only while the leaves travel, a second and a
            // half a stop, is it built per frame as before.
            boolean doorsStill = (rightFraction == 0.0F || rightFraction == 1.0F)
                && (leftFraction == 0.0F || leftFraction == 1.0F);
            CarDisplayLists.Entry compiled = doorsStill ? CarDisplayLists.entryFor(entity.getEntityId()) : null;
            if (compiled != null && !compiled.isMetro(spec, drawCoupling, pantograph, wire, thirdRail,
                rightFraction, leftFraction, lightsOn, outerFront, outerRear)) {
                float[] body = colourOf(spec, TrainSpec.Part.BODY, 3);
                float[] trim = colourOf(spec, TrainSpec.Part.TRIM, 4);
                float[] seats = colourOf(spec, TrainSpec.Part.SEATS, 1);
                compiled.release();
                for (MetroCarModel.Pass pass : METRO_PASSES) {
                    int list = CarDisplayLists.begin();
                    buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                    MetroCarModel.setPass(pass);
                    MetroCarModel.emit(buffer, length, drawCoupling, pantograph, wire, thirdRail, rightFraction,
                        leftFraction, lightsOn, outerFront, outerRear, body, trim, seats);
                    MetroCarModel.setPass(MetroCarModel.Pass.ALL);
                    tessellator.draw();
                    CarDisplayLists.end();
                    if (pass == MetroCarModel.Pass.EXTERIOR) {
                        compiled.exterior = list;
                    } else {
                        compiled.interior = list;
                    }
                }
                compiled.glass = CarDisplayLists.begin();
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                MetroCarModel.emitGlazing(buffer, length, rightFraction, leftFraction, outerFront, outerRear);
                tessellator.draw();
                CarDisplayLists.end();
                compiled.setMetro(spec, drawCoupling, pantograph, wire, thirdRail, rightFraction, leftFraction,
                    lightsOn, outerFront, outerRear);
            }
            if (compiled != null) {
                GlStateManager.callList(compiled.exterior);
                if (lightsOn) {
                    net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(
                        net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
                }
                GlStateManager.callList(compiled.interior);
            } else {
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                float[] body = colourOf(spec, TrainSpec.Part.BODY, 3);
                float[] trim = colourOf(spec, TrainSpec.Part.TRIM, 4);
                float[] seats = colourOf(spec, TrainSpec.Part.SEATS, 1);
                for (MetroCarModel.Pass pass : METRO_PASSES) {
                    if (pass == MetroCarModel.Pass.INTERIOR) {
                        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                        if (lightsOn) {
                            net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(
                                net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
                        }
                    }
                    MetroCarModel.setPass(pass);
                    MetroCarModel.emit(buffer, length, drawCoupling, pantograph, wire, thirdRail, rightFraction,
                        leftFraction, lightsOn, outerFront, outerRear, body, trim, seats);
                    MetroCarModel.setPass(MetroCarModel.Pass.ALL);
                    tessellator.draw();
                }
            }
            net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(
                net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, worldX, worldY);
            // A display list leaves the colour it last drew as the current one, behind the state
            // manager's back.
            GlStateManager.resetColor();

            // Head and tail lamps, full-bright whatever the light around the car: a lamp is a
            // light, and at night is the one part of the train that must not go dark.
            MetroCarModel.Lamp[] lamps = MetroCabs.lamps(entity.world, entity.trainId(), train,
                outerFront, outerRear);
            if (lamps[0] != MetroCarModel.Lamp.OFF || lamps[1] != MetroCarModel.Lamp.OFF) {
                float lastX = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessX;
                float lastY = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessY;
                net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(
                    net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                MetroCarModel.emitLamps(buffer, length, lamps[0], lamps[1]);
                tessellator.draw();
                net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(
                    net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, lastX, lastY);
            }

            // Glass last, blended. Translucent geometry drawn before the opaque body would blend
            // against whatever happened to precede it rather than against the car.
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.depthMask(false);
            if (compiled != null) {
                GlStateManager.callList(compiled.glass);
                GlStateManager.resetColor();
            } else {
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                MetroCarModel.emitGlazing(buffer, length, rightFraction, leftFraction, outerFront, outerRear);
                tessellator.draw();
            }
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            return;
        }
        // Before its train state arrives a car is drawn as a default one rather than not at all,
        // so cars do not pop in.
        // A coaster car has no moving parts at all, so it is compiled once and replayed: building
        // the mesh allocated a few hundred quads per car per frame.
        boolean lead = entity.carIndex() == 0;
        CarDisplayLists.Entry compiled = CarDisplayLists.entryFor(entity.getEntityId());
        if (!compiled.isCoaster(spec, lead, drawCoupling)) {
            TrainSpec shape = spec != null ? spec : new TrainSpec(1, length, couplingGap, seatRows * 2);
            compiled.release();
            compiled.exterior = CarDisplayLists.begin();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            CarModel.build(shape, lead, drawCoupling,
                colourOf(spec, TrainSpec.Part.BODY, 3),
                colourOf(spec, TrainSpec.Part.TRIM, 4),
                colourOf(spec, TrainSpec.Part.SEATS, 1)).emit(buffer);
            tessellator.draw();
            CarDisplayLists.end();
            compiled.setCoaster(spec, lead, drawCoupling);
        }
        GlStateManager.callList(compiled.exterior);
        GlStateManager.resetColor();
    }

    /**
     * Height of the contact wire over the section this car is on, or {@code 0} where there is no
     * catenary — what the pantograph stretches to meet.
     */
    private static double wireHeightFor(EntityCoasterCar entity) {
        return com.micatechnologies.minecraft.rcmc.track.TrackStyleIds.contactWireHeight(styleUnder(entity));
    }

    /** Whether the section this car is on collects from a third rail. */
    private static boolean onThirdRail(EntityCoasterCar entity) {
        return com.micatechnologies.minecraft.rcmc.track.TrackStyleIds.isThirdRail(styleUnder(entity));
    }

    /** Style of the section this car's train is on, or {@code null} when it is not known. */
    private static String styleUnder(EntityCoasterCar entity) {
        RcmcWorldState state = RcmcWorldState.of(entity.world);
        Train train = state == null ? null : state.trains().train(entity.trainId());
        if (train == null) {
            return null;
        }
        com.micatechnologies.minecraft.rcmc.track.TrackSection section =
            state.network().section(train.reference().sectionId());
        return section == null ? null : section.styleId();
    }
}
