package com.micatechnologies.minecraft.rcmc.client.render.sign;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Draws a tunnel exit sign item as its plate, from the same texture the placed sign uses: the
 * texture is too wide for the block atlas, so an ordinary item model cannot show it.
 */
public class ItemTunnelExitSignRenderer extends TileEntityItemStackRenderer {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(RcmcConstants.MOD_NAMESPACE, "textures/blocks/tunnel_exit_sign.png");

    @Override
    public void renderByItem(ItemStack stack) {
        double v0 = Math.max(0, Math.min(2, stack.getMetadata())) * 160.0D / 512.0D;
        double v1 = v0 + 160.0D / 512.0D;
        double half = 0.5D;
        double top = 0.5D + 0.5D * 160.0D / 1024.0D;
        double bottom = 0.5D - 0.5D * 160.0D / 1024.0D;
        Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);
        GlStateManager.disableCull();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(0.5D - half, bottom, 0.5D).tex(0.0D, v1).endVertex();
        buffer.pos(0.5D + half, bottom, 0.5D).tex(1.0D, v1).endVertex();
        buffer.pos(0.5D + half, top, 0.5D).tex(1.0D, v0).endVertex();
        buffer.pos(0.5D - half, top, 0.5D).tex(0.0D, v0).endVertex();
        tessellator.draw();
        GlStateManager.enableCull();
    }
}
