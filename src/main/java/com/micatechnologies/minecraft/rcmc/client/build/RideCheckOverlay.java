package com.micatechnologies.minecraft.rcmc.client.build;

import com.micatechnologies.minecraft.rcmc.item.ItemTrackEditor;
import com.micatechnologies.minecraft.rcmc.rating.RideWarning;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * Shows the ride check on the track itself: a band over each stretch where riders feel too much,
 * amber for over the limit and red for well over it, labelled with what was measured.
 *
 * <p>Drawn while the track editor is held — including behind its screen, which leaves the track in
 * view for exactly this — so a builder sees where the trouble is while fixing it, and sees it go when
 * it is fixed.</p>
 */
@SideOnly(Side.CLIENT)
public final class RideCheckOverlay {

    private static volatile List<RideWarning> warnings = Collections.emptyList();

    private static final float[] CAUTION = {1.0F, 0.72F, 0.12F};
    private static final float[] DANGER = {1.0F, 0.18F, 0.12F};

    /** Band half-width either side of the centreline, and how far above it, in blocks. */
    private static final double HALF_WIDTH = 0.9D;
    private static final double LIFT = 0.25D;
    /** A point warning — a stall — is drawn this far either way. */
    private static final double POINT_HALF_LENGTH = 2.0D;

    public static void update(List<RideWarning> fresh) {
        warnings = Collections.unmodifiableList(new ArrayList<>(fresh));
    }

    /** What the last check found, for the editor screen. */
    public static List<RideWarning> warnings() {
        return warnings;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        List<RideWarning> shown = warnings;
        if (player == null || shown.isEmpty() || !holdingEditor(player)) {
            return;
        }
        RcmcWorldState state = RcmcWorldState.of(mc.world);
        if (state == null) {
            return;
        }
        for (RideWarning warning : shown) {
            TrackSection section = state.network().section(warning.sectionId);
            if (section != null) {
                float[] colour = warning.severity == RideWarning.Severity.DANGER ? DANGER : CAUTION;
                double from = warning.from;
                double to = warning.to;
                if (to - from < POINT_HALF_LENGTH * 2.0D) {
                    double mid = (from + to) * 0.5D;
                    from = mid - POINT_HALF_LENGTH;
                    to = mid + POINT_HALF_LENGTH;
                }
                drawBand(section, from, to, colour);
                label(section.frameAtDistance((warning.from + warning.to) * 0.5D).position, warning.label());
            }
        }
    }

    /**
     * A flat band over the track from {@code from} to {@code to}, drawn through terrain and at full
     * brightness so it reads at night and behind a hill. Public for the build preview, which marks
     * its own problems the same way.
     */
    public static void drawBand(TrackSection section, double from, double to, float[] colour) {
        RenderManager manager = Minecraft.getMinecraft().getRenderManager();
        double total = section.totalLength();
        from = section.isClosed() ? from : Math.max(0.0D, from);
        to = section.isClosed() ? to : Math.min(total, to);
        if (to <= from) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);
        float lastX = OpenGlHelper.lastBrightnessX;
        float lastY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        double step = 1.0D;
        for (double s = from; s < to; s += step) {
            double e = Math.min(to, s + step);
            Vec3[] a = edges(section, wrap(s, total, section.isClosed()));
            Vec3[] b = edges(section, wrap(e, total, section.isClosed()));
            vertex(buffer, a[0], manager, colour);
            vertex(buffer, a[1], manager, colour);
            vertex(buffer, b[1], manager, colour);
            vertex(buffer, b[0], manager, colour);
        }
        tessellator.draw();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private static double wrap(double s, double total, boolean closed) {
        return closed ? ((s % total) + total) % total : s;
    }

    private static Vec3[] edges(TrackSection section, double s) {
        TrackFrame frame = section.frameAtDistance(s);
        Vec3 centre = frame.position.add(frame.up.scale(LIFT));
        return new Vec3[] {centre.add(frame.right.scale(-HALF_WIDTH)), centre.add(frame.right.scale(HALF_WIDTH))};
    }

    private static void vertex(BufferBuilder buffer, Vec3 at, RenderManager manager, float[] colour) {
        buffer.pos(at.x - manager.viewerPosX, at.y - manager.viewerPosY, at.z - manager.viewerPosZ)
            .color(colour[0], colour[1], colour[2], 0.55F).endVertex();
    }

    private static void label(Vec3 at, String text) {
        Minecraft mc = Minecraft.getMinecraft();
        RenderManager manager = mc.getRenderManager();
        EntityRenderer.drawNameplate(mc.fontRenderer, text,
            (float) (at.x - manager.viewerPosX), (float) (at.y - manager.viewerPosY + 1.6D),
            (float) (at.z - manager.viewerPosZ), 0, manager.playerViewY, manager.playerViewX,
            mc.gameSettings.thirdPersonView == 2, false);
    }

    private static boolean holdingEditor(EntityPlayer player) {
        return player.getHeldItemMainhand().getItem() instanceof ItemTrackEditor
            || player.getHeldItemOffhand().getItem() instanceof ItemTrackEditor;
    }
}
