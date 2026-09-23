package com.micatechnologies.minecraft.rcmc.client.gui;

import com.micatechnologies.minecraft.rcmc.net.LineView;
import com.micatechnologies.minecraft.rcmc.net.PacketLineAction;
import com.micatechnologies.minecraft.rcmc.net.PacketLineAction.Action;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The metro line control desk: a line's trains and what each is doing, with the controls to add,
 * remove and hold them and to set the line's dwell and headway.
 *
 * <p>Like the coaster panel it holds no state of its own: it shows the last {@link LineView} the
 * server sent, every button is a {@link PacketLineAction}, and while open it asks for a fresh view
 * once a second so the trains stay live.</p>
 */
@SideOnly(Side.CLIENT)
public class GuiLineControl extends GuiScreen {

    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_HEIGHT = 216;
    private static final int LEFT_WIDTH = 132;
    private static final int MAX_TRAIN_ROWS = 7;
    private static final int ROW_HEIGHT = 22;

    private static final int ID_PREVIOUS = 0;
    private static final int ID_NEXT = 1;
    private static final int ID_DWELL_DOWN = 2;
    private static final int ID_DWELL_UP = 3;
    private static final int ID_HEADWAY_DOWN = 4;
    private static final int ID_HEADWAY_UP = 5;
    private static final int ID_CARS_DOWN = 6;
    private static final int ID_CARS_UP = 7;
    private static final int ID_ADD = 8;
    /** Hold buttons from here, remove buttons from {@link #ID_REMOVE_BASE}, one per train row. */
    private static final int ID_HOLD_BASE = 100;
    private static final int ID_REMOVE_BASE = 200;

    private static final int MESSAGE_TICKS = 100;

    private LineView view;
    private String message = "";
    private int messageTicks;
    private int refreshTicks;
    private final Map<Integer, Integer> rowTrain = new HashMap<>();

    public GuiLineControl(LineView view) {
        this.view = view;
        takeMessage(view);
    }

    /** A fresh view from the server; the buttons are rebuilt to match it. */
    public void update(LineView fresh) {
        this.view = fresh;
        takeMessage(fresh);
        initGui();
    }

    private void takeMessage(LineView from) {
        if (!from.message.isEmpty()) {
            message = from.message;
            messageTicks = MESSAGE_TICKS;
        }
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (height - PANEL_HEIGHT) / 2;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        rowTrain.clear();
        int l = left();
        int t = top();
        boolean any = !view.line.isEmpty();

        GuiButton previous = new GuiButton(ID_PREVIOUS, l + PANEL_WIDTH - 50, t + 4, 20, 16, "<");
        GuiButton next = new GuiButton(ID_NEXT, l + PANEL_WIDTH - 26, t + 4, 20, 16, ">");
        previous.enabled = view.lineCount > 1;
        next.enabled = view.lineCount > 1;
        buttonList.add(previous);
        buttonList.add(next);
        if (!any) {
            return;
        }

        int x = l + 8;
        stepper(ID_DWELL_DOWN, ID_DWELL_UP, x, t + 48);
        stepper(ID_HEADWAY_DOWN, ID_HEADWAY_UP, x, t + 82);
        stepper(ID_CARS_DOWN, ID_CARS_UP, x, t + 140);
        buttonList.add(new GuiButton(ID_ADD, x, t + 162, LEFT_WIDTH - 12, 18, "Add train"));

        int rx = l + LEFT_WIDTH + 8;
        for (int i = 0; i < Math.min(MAX_TRAIN_ROWS, view.trains.size()); i++) {
            LineView.TrainRow row = view.trains.get(i);
            int y = t + 44 + i * ROW_HEIGHT;
            buttonList.add(new GuiButton(ID_HOLD_BASE + i, l + PANEL_WIDTH - 74, y, 46, 18,
                row.held ? TextFormatting.GOLD + "Release" : "Hold"));
            buttonList.add(new GuiButton(ID_REMOVE_BASE + i, l + PANEL_WIDTH - 26, y, 18, 18,
                TextFormatting.RED + "x"));
            rowTrain.put(i, row.trainId);
        }
    }

    /** A pair of -/+ buttons at the right of a setting row. */
    private void stepper(int down, int up, int x, int y) {
        buttonList.add(new GuiButton(down, x + LEFT_WIDTH - 56, y, 20, 16, "-"));
        buttonList.add(new GuiButton(up, x + LEFT_WIDTH - 32, y, 20, 16, "+"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case ID_PREVIOUS:
                send(Action.PREVIOUS_LINE, 0);
                return;
            case ID_NEXT:
                send(Action.NEXT_LINE, 0);
                return;
            case ID_DWELL_DOWN:
                send(Action.DWELL_DOWN, 0);
                return;
            case ID_DWELL_UP:
                send(Action.DWELL_UP, 0);
                return;
            case ID_HEADWAY_DOWN:
                send(Action.HEADWAY_DOWN, 0);
                return;
            case ID_HEADWAY_UP:
                send(Action.HEADWAY_UP, 0);
                return;
            case ID_CARS_DOWN:
                send(Action.CARS_DOWN, 0);
                return;
            case ID_CARS_UP:
                send(Action.CARS_UP, 0);
                return;
            case ID_ADD:
                send(Action.ADD_TRAIN, 0);
                return;
            default:
                break;
        }
        if (button.id >= ID_REMOVE_BASE) {
            Integer train = rowTrain.get(button.id - ID_REMOVE_BASE);
            if (train != null) {
                send(Action.REMOVE_TRAIN, train);
            }
        }
        else if (button.id >= ID_HOLD_BASE) {
            Integer train = rowTrain.get(button.id - ID_HOLD_BASE);
            if (train != null) {
                send(Action.TOGGLE_HOLD, train);
            }
        }
    }

    private void send(Action action, int train) {
        RcmcNetwork.sendToServer(new PacketLineAction(action, train));
    }

    @Override
    public void updateScreen() {
        if (messageTicks > 0 && --messageTicks == 0) {
            message = "";
        }
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            send(Action.REFRESH, 0);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int l = left();
        int t = top();
        drawRect(l, t, l + PANEL_WIDTH, t + PANEL_HEIGHT, 0xE0101418);

        if (view.line.isEmpty()) {
            drawString(fontRenderer, TextFormatting.WHITE + "Line control", l + 8, t + 8, 0xFFFFFF);
            drawString(fontRenderer, TextFormatting.GRAY + "No metro lines yet: build one with the transit tool.",
                l + 8, t + 40, 0xFFFFFF);
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }
        drawRect(l + LEFT_WIDTH, t + 30, l + LEFT_WIDTH + 1, t + PANEL_HEIGHT - 18, 0xFF3A4048);
        drawString(fontRenderer, TextFormatting.WHITE + "" + TextFormatting.BOLD + view.line
            + TextFormatting.RESET + TextFormatting.GRAY + "  " + view.kind, l + 8, t + 8, 0xFFFFFF);
        String count = view.index + " / " + view.lineCount;
        drawString(fontRenderer, TextFormatting.GRAY + count,
            l + PANEL_WIDTH - 56 - fontRenderer.getStringWidth(count), t + 8, 0xFFFFFF);
        drawString(fontRenderer, fontRenderer.trimStringToWidth(view.route, PANEL_WIDTH - 16),
            l + 8, t + 20, 0x8899AA);

        int x = l + 8;
        drawString(fontRenderer, "Dwell", x, t + 36, 0xDDDDDD);
        drawString(fontRenderer, view.dwellSeconds + " s", x, t + 52, 0xFFFFAA);
        drawString(fontRenderer, "Headway", x, t + 70, 0xDDDDDD);
        drawString(fontRenderer, view.headwaySeconds == 0 ? "off" : view.headwaySeconds + " s", x, t + 86, 0xFFFFAA);
        drawString(fontRenderer, "Signals", x, t + 106, 0xDDDDDD);
        drawString(fontRenderer, view.blocks == 0 ? TextFormatting.GRAY + "none (drive on sight)"
            : view.blocks + " blocks", x, t + 118, 0xFFFFAA);
        drawString(fontRenderer, "Cars per new train", x, t + 132, 0xDDDDDD);
        drawString(fontRenderer, String.valueOf(view.cars), x, t + 144, 0xFFFFAA);

        int rx = l + LEFT_WIDTH + 8;
        drawString(fontRenderer, TextFormatting.WHITE + "Trains (" + view.trains.size() + ")", rx, t + 33, 0xFFFFFF);
        int textWidth = PANEL_WIDTH - LEFT_WIDTH - 8 - 80;
        for (int i = 0; i < Math.min(MAX_TRAIN_ROWS, view.trains.size()); i++) {
            LineView.TrainRow row = view.trains.get(i);
            int y = t + 44 + i * ROW_HEIGHT;
            String head = String.format("#%d %s %.0f b/s", row.trainId, row.direction, row.speed);
            drawString(fontRenderer, fontRenderer.trimStringToWidth(head, textWidth), rx, y, 0xDDDDDD);
            String second = row.doing + row.why;
            int colour = row.headOn ? 0xFF6060 : row.held ? 0xFFC060 : !row.why.isEmpty() ? 0xFFC060 : 0x99AABB;
            drawString(fontRenderer, fontRenderer.trimStringToWidth(second, textWidth), rx, y + 10, colour);
        }
        if (view.trains.isEmpty()) {
            drawString(fontRenderer, TextFormatting.GRAY + "No trains in service. Add one.", rx, t + 46, 0xFFFFFF);
        }
        else if (view.trains.size() > MAX_TRAIN_ROWS) {
            drawString(fontRenderer, TextFormatting.GRAY + "+ " + (view.trains.size() - MAX_TRAIN_ROWS)
                + " more: /rcmc line trains " + view.line, rx, t + 44 + MAX_TRAIN_ROWS * ROW_HEIGHT, 0xFFFFFF);
        }

        if (!message.isEmpty()) {
            drawCenteredString(fontRenderer, message, l + PANEL_WIDTH / 2, t + PANEL_HEIGHT - 12, 0x9FE0A0);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
