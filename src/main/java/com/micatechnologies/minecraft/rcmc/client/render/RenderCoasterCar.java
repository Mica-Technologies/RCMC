package com.micatechnologies.minecraft.rcmc.client.render;

import com.micatechnologies.minecraft.rcmc.entity.EntityCoasterCar;
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
        drawBox(1.4F, 1.0F, 2.6F);
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();

        GlStateManager.popMatrix();

        super.doRender(entity, x, y, z, entityYaw, partialTicks);
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
     * An axis-aligned box in the car's local frame: width along {@code right}, height along
     * {@code up}, length along {@code forward}. The nose is tinted so the direction of travel and
     * any roll are both readable at a glance — which is the entire point of a placeholder.
     */
    private static void drawBox(float width, float height, float length) {
        float hw = width / 2.0F;
        float hl = length / 2.0F;
        float bottom = 0.0F;
        float top = height;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        // Sides
        quad(buffer, -hw, bottom, -hl, -hw, top, -hl, hw, top, -hl, hw, bottom, -hl, 0.75F, 0.15F, 0.15F);
        quad(buffer, hw, bottom, hl, hw, top, hl, -hw, top, hl, -hw, bottom, hl, 0.95F, 0.55F, 0.15F);
        quad(buffer, -hw, bottom, hl, -hw, top, hl, -hw, top, -hl, -hw, bottom, -hl, 0.60F, 0.12F, 0.12F);
        quad(buffer, hw, bottom, -hl, hw, top, -hl, hw, top, hl, hw, bottom, hl, 0.60F, 0.12F, 0.12F);
        // Top and bottom. The top is lightest so roll reads clearly when the car inverts.
        quad(buffer, -hw, top, -hl, -hw, top, hl, hw, top, hl, hw, top, -hl, 0.85F, 0.85F, 0.90F);
        quad(buffer, -hw, bottom, hl, -hw, bottom, -hl, hw, bottom, -hl, hw, bottom, hl, 0.20F, 0.20F, 0.22F);

        tessellator.draw();
    }

    private static void quad(BufferBuilder buffer,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float r, float g, float b) {
        buffer.pos(x1, y1, z1).color(r, g, b, 1.0F).endVertex();
        buffer.pos(x2, y2, z2).color(r, g, b, 1.0F).endVertex();
        buffer.pos(x3, y3, z3).color(r, g, b, 1.0F).endVertex();
        buffer.pos(x4, y4, z4).color(r, g, b, 1.0F).endVertex();
    }
}
