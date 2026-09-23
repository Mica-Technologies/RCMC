package com.micatechnologies.minecraft.rcmc.client.build;

import com.micatechnologies.minecraft.rcmc.builder.TransitBuildSession;
import com.micatechnologies.minecraft.rcmc.item.ItemTransitTool;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackPicker;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * The transit tool's ghost: where on the track a click lands, and what it will do there.
 *
 * <p>The tool acts on the track the player is looking along, not on a block, so before this there
 * was nothing to aim with — a station went wherever the server's pick fell, and "which station will
 * this platform join?" was answered only after the click. The preview picks the same way the server
 * does and asks the tool's own station-finding code, against this client's copy of the network, so
 * what it shows is what the click does.</p>
 */
@SideOnly(Side.CLIENT)
public final class TransitToolPreview {

    /** The tool's mode and picked line stops, from the server. */
    private static volatile int mode;
    private static volatile List<String> stops = Collections.emptyList();

    /** Same reach and aim as the tool, or the ghost would point somewhere a click cannot. */
    private static final double LOOK_RANGE = 64.0D;
    private static final double AIM_RADIUS = 1.5D;

    public static void update(int newMode, List<String> newStops) {
        mode = newMode;
        stops = Collections.unmodifiableList(new ArrayList<>(newStops));
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || !holdingTransitTool(player) || mc.currentScreen != null) {
            return;
        }
        RcmcWorldState state = RcmcWorldState.of(mc.world);
        if (state == null || state.network().isEmpty()) {
            return;
        }
        TransitBuildSession.Mode[] modes = TransitBuildSession.Mode.values();
        TransitBuildSession.Mode current = modes[Math.max(0, Math.min(modes.length - 1, mode))];
        TrackNetwork network = state.network();

        // Stops already picked for a line, numbered, so the order being built is visible.
        if (current == TransitBuildSession.Mode.LINE) {
            List<String> picked = stops;
            for (int i = 0; i < picked.size(); i++) {
                TransitStation station = state.transit().station(picked.get(i));
                if (station != null && network.hasSection(station.stopPoint().sectionId())) {
                    Vec3 at = network.frameAt(station.stopPoint()).position;
                    post(at, 0.3F, 0.8F, 1.0F);
                    label(at, (i + 1) + ". " + station.name());
                }
            }
        }

        net.minecraft.util.math.Vec3d eyes = player.getPositionEyes(event.getPartialTicks());
        net.minecraft.util.math.Vec3d look = player.getLook(event.getPartialTicks());
        TrackPicker.Hit hit = TrackPicker.pickAlongRay(network, new Vec3(eyes.x, eyes.y, eyes.z),
            new Vec3(look.x, look.y, look.z), LOOK_RANGE, AIM_RADIUS);
        if (hit == null) {
            return;
        }
        Vec3 at = network.frameAt(hit.ref).position;
        String what;
        switch (current) {
            case STATION:
                if (player.isSneaking()) {
                    TransitStation near = ItemTransitTool.nearestStation(state.transit(), hit.ref);
                    what = near == null ? "No station here to remove" : "Remove " + near.name();
                }
                else {
                    ItemStack held = player.getHeldItemMainhand();
                    what = held.hasDisplayName() ? "Station: " + held.getDisplayName().trim() : "New station";
                }
                break;
            case PLATFORM: {
                TransitStation near = ItemTransitTool.nearestStationInWorld(state, hit.ref);
                what = near == null ? "No station within reach" : "Add a platform to " + near.name();
                break;
            }
            case LINE: {
                TransitStation near = ItemTransitTool.nearestStation(state.transit(), hit.ref);
                List<String> picked = stops;
                if (near == null) {
                    what = "No station here";
                }
                else if (!picked.isEmpty() && picked.get(picked.size() - 1).equalsIgnoreCase(near.name())) {
                    what = near.name() + " is already the last stop";
                }
                else {
                    what = "Stop " + (picked.size() + 1) + ": " + near.name();
                }
                break;
            }
            case SWITCH:
                what = "Pick this track's nearest end";
                break;
            case STYLE:
            default:
                what = "Change this track's style";
                break;
        }
        float[] colour = colourFor(current);
        post(at, colour[0], colour[1], colour[2]);
        label(at, what);
    }

    private static float[] colourFor(TransitBuildSession.Mode mode) {
        switch (mode) {
            case STATION:
                return new float[] {0.35F, 1.0F, 0.45F};
            case PLATFORM:
                return new float[] {1.0F, 0.85F, 0.2F};
            case LINE:
                return new float[] {0.3F, 0.8F, 1.0F};
            case SWITCH:
                return new float[] {1.0F, 0.45F, 0.35F};
            case STYLE:
            default:
                return new float[] {0.85F, 0.6F, 1.0F};
        }
    }

    /** A thin post standing up from {@code at}, drawn through terrain so it is never lost. */
    private static void post(Vec3 at, float r, float g, float b) {
        RenderManager manager = Minecraft.getMinecraft().getRenderManager();
        double x = at.x - manager.viewerPosX;
        double y = at.y - manager.viewerPosY;
        double z = at.z - manager.viewerPosZ;
        double h = 0.12D;
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
        double[][] faces = {{-h, -h, h, -h}, {h, -h, h, h}, {h, h, -h, h}, {-h, h, -h, -h}};
        for (double[] f : faces) {
            buffer.pos(x + f[0], y, z + f[1]).color(r, g, b, 0.85F).endVertex();
            buffer.pos(x + f[2], y, z + f[3]).color(r, g, b, 0.85F).endVertex();
            buffer.pos(x + f[2], y + 3.0D, z + f[3]).color(r, g, b, 0.85F).endVertex();
            buffer.pos(x + f[0], y + 3.0D, z + f[1]).color(r, g, b, 0.85F).endVertex();
        }
        tessellator.draw();
        net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(
            net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, lastX, lastY);
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private static void label(Vec3 at, String text) {
        Minecraft mc = Minecraft.getMinecraft();
        RenderManager manager = mc.getRenderManager();
        EntityRenderer.drawNameplate(mc.fontRenderer, text,
            (float) (at.x - manager.viewerPosX), (float) (at.y - manager.viewerPosY + 3.4D),
            (float) (at.z - manager.viewerPosZ), 0, manager.playerViewY, manager.playerViewX,
            mc.gameSettings.thirdPersonView == 2, false);
    }

    private static boolean holdingTransitTool(EntityPlayer player) {
        return player.getHeldItemMainhand().getItem() instanceof ItemTransitTool
            || player.getHeldItemOffhand().getItem() instanceof ItemTransitTool;
    }
}
