package com.micatechnologies.minecraft.rcmc.client.gui;

import com.micatechnologies.minecraft.rcmc.builder.TrackBuildSession;
import com.micatechnologies.minecraft.rcmc.net.PacketBuildAdjust;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The track tool's segment types, all at once: pick what the next stretch of track is laid as.
 *
 * <p>Opened with G. It replaced pressing G to step through the types one at a time, which was fine
 * with four and a chore with nine — a builder after a block brake had to know it was six presses
 * away. Picking closes the screen, so it is one key and one click.</p>
 */
@SideOnly(Side.CLIENT)
public class GuiSegmentPicker extends GuiScreen {

    private static final int BUTTON_WIDTH = 150;
    private static final int ROW = 22;

    private final int current;

    public GuiSegmentPicker(int currentOrdinal) {
        this.current = currentOrdinal;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        TrackBuildSession.SegmentType[] types = TrackBuildSession.SegmentType.values();
        int top = height / 2 - types.length * ROW / 2;
        for (int i = 0; i < types.length; i++) {
            buttonList.add(new GuiButton(i, width / 2 - BUTTON_WIDTH / 2, top + i * ROW, BUTTON_WIDTH, 20,
                (i == current ? TextFormatting.GREEN + "> " : "") + types[i].label()));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        RcmcNetwork.sendToServer(new PacketBuildAdjust(PacketBuildAdjust.Action.SET_TYPE, button.id));
        mc.displayGuiScreen(null);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "Lay the next track as…", width / 2,
            height / 2 - TrackBuildSession.SegmentType.values().length * ROW / 2 - 16, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
