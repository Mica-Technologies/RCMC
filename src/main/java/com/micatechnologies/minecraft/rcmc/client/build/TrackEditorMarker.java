package com.micatechnologies.minecraft.rcmc.client.build;

import com.micatechnologies.minecraft.rcmc.client.gui.GuiTrackEditor;
import com.micatechnologies.minecraft.rcmc.net.TrackEditView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * Marks the node the track editor is on: a bright post standing up through it, visible through
 * anything, for as long as the editor screen is open.
 *
 * <p>A panel full of coordinates means nothing until the player can see which node they belong
 * to — and which way the + buttons will move it.</p>
 */
@SideOnly(Side.CLIENT)
public final class TrackEditorMarker {

    private static final double HALF = 0.18D;
    private static final double HEIGHT = 2.5D;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        TrackEditView view = GuiTrackEditor.current();
        if (view == null) {
            return;
        }
        RenderManager manager = Minecraft.getMinecraft().getRenderManager();
        double x = view.x - manager.viewerPosX;
        double y = view.y - manager.viewerPosY;
        double z = view.z - manager.viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        // Unlit and full-bright: a marker drawn through terrain must read the same at night, and
        // a nameplate drawn just before leaves GL lighting on, which dims a quad with no normals.
        GlStateManager.disableLighting();
        float lastX = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessX;
        float lastY = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessY;
        net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(
            net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        float r = 1.0F;
        float g = 0.85F;
        float b = 0.15F;
        double[][] faces = {
            {-HALF, -HALF, HALF, -HALF}, {HALF, -HALF, HALF, HALF},
            {HALF, HALF, -HALF, HALF}, {-HALF, HALF, -HALF, -HALF},
        };
        for (double[] f : faces) {
            buffer.pos(x + f[0], y - 0.4D, z + f[1]).color(r, g, b, 0.9F).endVertex();
            buffer.pos(x + f[2], y - 0.4D, z + f[3]).color(r, g, b, 0.9F).endVertex();
            buffer.pos(x + f[2], y + HEIGHT, z + f[3]).color(r, g * 0.8F, b, 0.9F).endVertex();
            buffer.pos(x + f[0], y + HEIGHT, z + f[1]).color(r, g * 0.8F, b, 0.9F).endVertex();
        }
        tessellator.draw();
        net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(
            net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, lastX, lastY);
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }
}
