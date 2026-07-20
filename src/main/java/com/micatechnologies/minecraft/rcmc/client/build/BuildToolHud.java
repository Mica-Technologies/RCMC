package com.micatechnologies.minecraft.rcmc.client.build;

import com.micatechnologies.minecraft.rcmc.item.RcmcItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * An on-screen legend for the track builder: what each control does, and what it is currently set
 * to.
 *
 * <p>The tool has grown four modal settings and five inputs, none of which are discoverable from
 * the item alone. A tooltip only helps someone who thinks to look in their inventory, and chat
 * feedback scrolls away — the state a builder needs is the state they have <em>right now</em>,
 * visible while they work.</p>
 *
 * <p>Shown only while the tool is held, so it costs nothing the rest of the time and cannot be
 * mistaken for permanent HUD clutter.</p>
 */
@SideOnly(Side.CLIENT)
public final class BuildToolHud {

    private static final int MARGIN = 6;
    private static final int LINE_HEIGHT = 10;
    private static final int PANEL_ALPHA = 0x90000000;

    private static final int HEADING = 0xFF55FFFF;
    private static final int VALUE = 0xFFFFFFFF;
    private static final int HINT = 0xFFAAAAAA;
    private static final int WARN = 0xFFFFB020;

    private static final String[] SEGMENT_LABELS =
        {"Plain track", "Chain lift", "Brake run", "Station"};

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || !holdingTool(player) || mc.gameSettings.showDebugInfo) {
            return;
        }

        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        lines.add("Track Builder");
        colors.add(HEADING);

        int type = ClientBuildSession.segmentType();
        lines.add("Segment: " + (type >= 0 && type < SEGMENT_LABELS.length
            ? SEGMENT_LABELS[type] : "?"));
        colors.add(VALUE);

        lines.add(String.format("Bank: %+.0f°   Height: %+.1f",
            ClientBuildSession.bankDegrees(), ClientBuildSession.heightOffset()));
        colors.add(VALUE);

        lines.add("Nodes: " + ClientBuildSession.nodes().size()
            + (ClientBuildSession.isClosing() ? "   (circuit)" : ""));
        colors.add(VALUE);

        // The line that explains the height offset's most surprising behaviour. Offset is relative
        // to the terrain under the cursor and persists between placements, so a big change makes
        // the track climb it all between two adjacent nodes. Seeing the grade beforehand turns
        // that from a surprise into a choice.
        BuildCursor.Segment segment = BuildCursor.pendingSegment(mc);
        if (segment != null) {
            lines.add(String.format("Next: %.1f out, %+.1f up  (%.0f°)",
                segment.run, segment.rise, segment.gradeDegrees));
            colors.add(segment.isSteep() ? WARN : VALUE);
            if (segment.isSteep()) {
                lines.add("  steep - place a node partway to ease it");
                colors.add(WARN);
            }
        }

        lines.add("");
        colors.add(HINT);
        lines.add("G  segment type      R  reset");
        colors.add(HINT);
        lines.add("Shift+scroll  height  Ctrl+scroll  bank");
        colors.add(HINT);
        lines.add("Right-click  place    Shift+RC  finish");
        colors.add(HINT);

        draw(mc, lines, colors);
    }

    private static void draw(Minecraft mc, List<String> lines, List<Integer> colors) {
        ScaledResolution resolution = new ScaledResolution(mc);
        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, mc.fontRenderer.getStringWidth(line));
        }

        // Top-left, below anything a resource pack might put in the very corner, and well clear of
        // the hotbar and the ride HUD in the bottom corners.
        int x = MARGIN;
        int y = MARGIN;
        int width = widest + MARGIN * 2;
        int height = lines.size() * LINE_HEIGHT + MARGIN * 2;
        if (y + height > resolution.getScaledHeight()) {
            return;
        }

        Gui.drawRect(x, y, x + width, y + height, PANEL_ALPHA);
        for (int i = 0; i < lines.size(); i++) {
            mc.fontRenderer.drawStringWithShadow(lines.get(i),
                x + MARGIN, y + MARGIN + i * LINE_HEIGHT, colors.get(i));
        }
    }

    private static boolean holdingTool(EntityPlayer player) {
        return RcmcItems.trackTool != null
            && (player.getHeldItemMainhand().getItem() == RcmcItems.trackTool
                || player.getHeldItemOffhand().getItem() == RcmcItems.trackTool);
    }
}
