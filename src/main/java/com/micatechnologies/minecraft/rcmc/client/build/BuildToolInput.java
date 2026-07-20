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

    /** Degrees of bank per scroll notch. */
    private static final double BANK_STEP = 5.0D;

    public static final KeyBinding CYCLE_SEGMENT = new KeyBinding(
        "key.rcmc.cycle_segment", Keyboard.KEY_G, "key.categories.rcmc");

    public static final KeyBinding RESET_ADJUSTMENTS = new KeyBinding(
        "key.rcmc.reset_adjustments", Keyboard.KEY_R, "key.categories.rcmc");

    public static void register() {
        ClientRegistry.registerKeyBinding(CYCLE_SEGMENT);
        ClientRegistry.registerKeyBinding(RESET_ADJUSTMENTS);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // isPressed() consumes one queued press, so these fire once per keystroke rather than
        // every tick the key is held.
        boolean cycle = CYCLE_SEGMENT.isPressed();
        boolean reset = RESET_ADJUSTMENTS.isPressed();
        EntityPlayer player = Minecraft.getMinecraft().player;
        // G means "change the segment type" for both tools; which one is held decides whether that
        // is the type of the NEXT node placed or of the span already selected.
        if (cycle && holdingEditor(player)) {
            RcmcNetwork.sendToServer(
                new PacketBuildAdjust(PacketBuildAdjust.Action.CYCLE_SELECTED_TYPE, 0.0D));
            return;
        }
        if (!holdingTool(player)) {
            return;
        }
        if (cycle) {
            RcmcNetwork.sendToServer(
                new PacketBuildAdjust(PacketBuildAdjust.Action.CYCLE_TYPE, 0.0D));
        }
        if (reset) {
            RcmcNetwork.sendToServer(new PacketBuildAdjust(PacketBuildAdjust.Action.RESET, 0.0D));
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.getDwheel() == 0) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || !holdingTool(player)) {
            return;
        }
        boolean sneak = sneakKeyHeld(mc);
        boolean control = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
            || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        if (!sneak && !control) {
            return;
        }
        // Cancelled so the hotbar does not also change — scrolling off the tool mid-adjustment
        // would be maddening.
        event.setCanceled(true);
        double direction = Math.signum(event.getDwheel());
        if (control) {
            RcmcNetwork.sendToServer(new PacketBuildAdjust(
                PacketBuildAdjust.Action.ADJUST_BANK, direction * BANK_STEP));
        }
        else {
            RcmcNetwork.sendToServer(new PacketBuildAdjust(
                PacketBuildAdjust.Action.ADJUST_HEIGHT, direction * HEIGHT_STEP));
        }
    }

    /**
     * Whether the sneak KEY is down, which is not the same question as whether the player is
     * sneaking.
     *
     * <p>{@code EntityPlayer.isSneaking()} reports the sneaking <em>flag</em>, and while flying
     * that flag stays false — shift descends instead. Checking it meant this never fired for
     * anyone building in creative flight, which is essentially everyone, so the scroll fell
     * through to vanilla and changed hotbar slots. Reading the key binding directly asks the
     * question actually intended: "is the player holding shift right now".</p>
     */
    private static boolean sneakKeyHeld(Minecraft mc) {
        return mc.gameSettings != null && mc.gameSettings.keyBindSneak.isKeyDown();
    }

    private static boolean holdingEditor(EntityPlayer player) {
        return player != null && RcmcItems.trackEditor != null
            && (player.getHeldItemMainhand().getItem() == RcmcItems.trackEditor
                || player.getHeldItemOffhand().getItem() == RcmcItems.trackEditor);
    }

    private static boolean holdingTool(EntityPlayer player) {
        return player != null && RcmcItems.trackTool != null
            && (player.getHeldItemMainhand().getItem() == RcmcItems.trackTool
                || player.getHeldItemOffhand().getItem() == RcmcItems.trackTool);
    }
}
