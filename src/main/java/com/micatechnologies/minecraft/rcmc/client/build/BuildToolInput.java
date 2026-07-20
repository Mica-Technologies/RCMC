package com.micatechnologies.minecraft.rcmc.client.build;

import com.micatechnologies.minecraft.rcmc.item.RcmcItems;
import com.micatechnologies.minecraft.rcmc.net.PacketBuildAdjust;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/**
 * Keyboard and scroll input for the track builder tool.
 *
 * <p>Two adjustments, both of which change what the next click produces:</p>
 * <ul>
 *   <li><b>{@code G}</b> cycles the segment type — plain, chain lift, brake run, station.</li>
 *   <li><b>Sneak + scroll</b> raises or lowers the placement height.</li>
 * </ul>
 *
 * <p><b>Why scroll rather than click-and-drag for height.</b> Drag was the natural request, and it
 * is the one interaction Minecraft makes genuinely awkward: an item receives a click and a release,
 * with nothing in between, so a drag has to be reconstructed from held-state polled per tick —
 * during which the player is also still looking around, and the same button is the one that places
 * a node. Sneak+scroll is unambiguous, works identically in every control scheme, and is what other
 * building tools in the ecosystem use, so it is already in muscle memory.</p>
 *
 * <p>Both send a request to the server rather than adjusting anything locally: the build session is
 * server-authoritative, and a client that held its own copy could preview a node the server would
 * not place — the one thing the preview must never do.</p>
 */
@SideOnly(Side.CLIENT)
public final class BuildToolInput {

    /** Blocks of height change per scroll notch. Fine enough to place accurately, coarse enough
     *  to cross a lift hill's worth of height without wearing out a scroll wheel. */
    private static final double HEIGHT_STEP = 0.5D;

    public static final KeyBinding CYCLE_SEGMENT = new KeyBinding(
        "key.rcmc.cycle_segment", Keyboard.KEY_G, "key.categories.rcmc");

    public static void register() {
        ClientRegistry.registerKeyBinding(CYCLE_SEGMENT);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!CYCLE_SEGMENT.isPressed()) {
            return;
        }
        // isPressed() consumes one queued press, so this fires once per keystroke rather than every
        // tick the key is held.
        if (holdingTool(Minecraft.getMinecraft().player)) {
            RcmcNetwork.sendToServer(
                new PacketBuildAdjust(PacketBuildAdjust.Action.CYCLE_TYPE, 0.0D));
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.getDwheel() == 0) {
            return;
        }
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null || !player.isSneaking() || !holdingTool(player)) {
            return;
        }
        // Cancelled so the hotbar does not also change — scrolling off the tool mid-adjustment
        // would be maddening.
        event.setCanceled(true);
        RcmcNetwork.sendToServer(new PacketBuildAdjust(PacketBuildAdjust.Action.ADJUST_HEIGHT,
            Math.signum(event.getDwheel()) * HEIGHT_STEP));
    }

    private static boolean holdingTool(EntityPlayer player) {
        return player != null && RcmcItems.trackTool != null
            && (player.getHeldItemMainhand().getItem() == RcmcItems.trackTool
                || player.getHeldItemOffhand().getItem() == RcmcItems.trackTool);
    }
}
