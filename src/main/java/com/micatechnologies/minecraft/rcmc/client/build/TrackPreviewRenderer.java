package com.micatechnologies.minecraft.rcmc.client.build;

import com.micatechnologies.minecraft.rcmc.client.render.track.MeshQuad;
import com.micatechnologies.minecraft.rcmc.client.render.track.TrackMesh;
import com.micatechnologies.minecraft.rcmc.client.render.track.TrackMeshBuilder;
import com.micatechnologies.minecraft.rcmc.item.RcmcItems;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.track.validation.TrackIssue;
import com.micatechnologies.minecraft.rcmc.track.validation.TrackValidator;
import com.micatechnologies.minecraft.rcmc.track.validation.ValidationLimits;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * Draws the track a click would produce, before the click.
 *
 * <p>A spline through placed nodes is not something a person can picture. Worse, centripetal
 * Catmull-Rom uses every point as a tangent handle for its neighbours, so adding a node reshapes
 * the curve <em>on both sides</em> of itself — the effect of a placement genuinely is not
 * predictable by eye. Without a preview, building is guess, place, undo, repeat.</p>
 *
 * <p>Drawn as translucent track through the existing {@link TrackMeshBuilder}, not as a
 * centreline. A line does not tell you whether the rails will clip terrain or how the banking will
 * sit, which are the two things a builder is actually judging.</p>
 *
 * <p><b>Never touches the section mesh cache.</b> Preview geometry changes every frame the cursor
 * moves; writing it into a cache keyed by section id would make committed track flicker.</p>
 */
@SideOnly(Side.CLIENT)
public final class TrackPreviewRenderer {

    /** Provisional section id. Never added to the network, so it only has to avoid confusion. */
    private static final int PREVIEW_SECTION_ID = Integer.MIN_VALUE;

    private static final float[] VALID_COLOR = {0.35F, 0.85F, 1.0F};
    private static final float[] WARNING_COLOR = {1.0F, 0.72F, 0.20F};
    private static final float PREVIEW_ALPHA = 0.55F;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || !isHoldingTrackTool(player)) {
            return;
        }

        Vec3 candidate = candidateNode(mc);
        if (candidate == null) {
            return;
        }

        List<TrackNode> nodes = new ArrayList<>(ClientBuildSession.nodes());
        nodes.add(new TrackNode(candidate, ClientBuildSession.bankDegrees(), null));

        boolean closing = ClientBuildSession.isClosing();
        // Below the minimum a section needs there is no curve to preview yet. The node marker
        // still draws, so the first click is not invisible feedback.
        TrackSection preview = null;
        if (nodes.size() >= (closing ? 3 : 2)) {
            try {
                preview = new TrackSection(PREVIEW_SECTION_ID, nodes, closing, null);
            }
            catch (IllegalArgumentException e) {
                // Degenerate placement — coincident nodes, most likely. Fall through to drawing
                // just the markers rather than showing nothing, so the builder can see why.
                preview = null;
            }
        }

        double camX = interpolate(mc.getRenderViewEntity().prevPosX,
            mc.getRenderViewEntity().posX, event.getPartialTicks());
        double camY = interpolate(mc.getRenderViewEntity().prevPosY,
            mc.getRenderViewEntity().posY, event.getPartialTicks());
        double camZ = interpolate(mc.getRenderViewEntity().prevPosZ,
            mc.getRenderViewEntity().posZ, event.getPartialTicks());

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableCull();
        // Preview must be visible through terrain — a builder placing a node behind a hill still
        // needs to see what the curve does there.
        GlStateManager.disableDepth();

        if (preview != null) {
            drawPreview(preview, camX, camY, camZ);
        }
        drawNodeMarkers(nodes, camX, camY, camZ);

        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    /**
     * Draws the provisional track, tinted by whether the validator objects to it.
     *
     * <p>Running the validator here is the RCT loop in miniature: the game tells you what you are
     * about to build while you can still move the cursor, rather than after you have committed.</p>
     */
    private static void drawPreview(TrackSection preview, double camX, double camY, double camZ) {
        boolean warned = false;
        for (TrackIssue issue : new TrackValidator(ValidationLimits.DEFAULT).validate(preview)) {
            if (issue.severity() != TrackIssue.Severity.INFO) {
                warned = true;
                break;
            }
        }
        float[] tint = warned ? WARNING_COLOR : VALID_COLOR;

        TrackMesh mesh = TrackMeshBuilder.build(preview);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (MeshQuad quad : mesh.quads) {
            vertex(buffer, quad.a, camX, camY, camZ, tint);
            vertex(buffer, quad.b, camX, camY, camZ, tint);
            vertex(buffer, quad.c, camX, camY, camZ, tint);
            vertex(buffer, quad.d, camX, camY, camZ, tint);
        }
        tessellator.draw();
    }

    /** Small cubes at each node, with the one under the cursor picked out. */
    private static void drawNodeMarkers(List<TrackNode> nodes, double camX, double camY, double camZ) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        for (int i = 0; i < nodes.size(); i++) {
            boolean cursor = i == nodes.size() - 1;
            float[] color = cursor ? new float[] {1.0F, 1.0F, 1.0F} : VALID_COLOR;
            double half = cursor ? 0.16D : 0.11D;
            Vec3 p = nodes.get(i).position();
            cube(buffer, p.x - camX, p.y - camY, p.z - camZ, half, color);
        }
        tessellator.draw();
    }

    private static void vertex(BufferBuilder buffer, Vec3 v,
                               double camX, double camY, double camZ, float[] tint) {
        buffer.pos(v.x - camX, v.y - camY, v.z - camZ)
            .color(tint[0], tint[1], tint[2], PREVIEW_ALPHA).endVertex();
    }

    private static void cube(BufferBuilder buffer, double x, double y, double z, double h,
                             float[] c) {
        double x1 = x - h;
        double y1 = y - h;
        double z1 = z - h;
        double x2 = x + h;
        double y2 = y + h;
        double z2 = z + h;
        face(buffer, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, c);
        face(buffer, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2, c);
        face(buffer, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, c);
        face(buffer, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, c);
        face(buffer, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, c);
        face(buffer, x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2, c);
    }

    private static void face(BufferBuilder buffer, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3,
                             double x4, double y4, double z4, float[] c) {
        buffer.pos(x1, y1, z1).color(c[0], c[1], c[2], 0.85F).endVertex();
        buffer.pos(x2, y2, z2).color(c[0], c[1], c[2], 0.85F).endVertex();
        buffer.pos(x3, y3, z3).color(c[0], c[1], c[2], 0.85F).endVertex();
        buffer.pos(x4, y4, z4).color(c[0], c[1], c[2], 0.85F).endVertex();
    }

    /**
     * Where a click would place a node right now.
     *
     * <p>Must match {@code ItemTrackTool.onItemUse} exactly — block above the face hit, centred.
     * If the two ever disagree the preview is a lie, which is worse than no preview at all.</p>
     */
    private static Vec3 candidateNode(Minecraft mc) {
        RayTraceResult hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.getBlockPos() == null) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
    }

    private static boolean isHoldingTrackTool(EntityPlayer player) {
        return RcmcItems.trackTool != null
            && (player.getHeldItemMainhand().getItem() == RcmcItems.trackTool
                || player.getHeldItemOffhand().getItem() == RcmcItems.trackTool);
    }

    private static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }
}
