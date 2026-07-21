package com.micatechnologies.minecraft.rcmc.client.render;

import com.micatechnologies.minecraft.rcmc.entity.EntityCoasterCar;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
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
        emitModel(entity);
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();

        GlStateManager.popMatrix();

        super.doRender(entity, x, y, z, entityYaw, partialTicks);
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
    private static void emitModel(EntityCoasterCar entity) {
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
            // Pantographs on alternate cars, like a real EMU consist.
            MetroCarModel.emit(buffer, length, drawCoupling, entity.carIndex() % 2 == 0,
                colourOf(spec, TrainSpec.Part.BODY, 3),
                colourOf(spec, TrainSpec.Part.TRIM, 4),
                colourOf(spec, TrainSpec.Part.SEATS, 1));
        } else {
            CarModel.emit(buffer, length, seatRows, couplingGap, drawCoupling,
                colourOf(spec, TrainSpec.Part.BODY, 3),
                colourOf(spec, TrainSpec.Part.TRIM, 4),
                colourOf(spec, TrainSpec.Part.SEATS, 1));
        }
        tessellator.draw();
    }
}
