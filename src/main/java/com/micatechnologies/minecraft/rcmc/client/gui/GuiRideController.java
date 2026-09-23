package com.micatechnologies.minecraft.rcmc.client.gui;

import com.micatechnologies.minecraft.rcmc.net.PacketRideAction;
import com.micatechnologies.minecraft.rcmc.net.PacketRideAction.Action;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.net.RideView;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideController;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideTuning;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The ride operator panel: open, test or close a coaster, dispatch it, stop it, tune it, and add
 * or remove its trains.
 *
 * <p>Holds no ride state of its own. Everything shown comes from the last {@link RideView} the server
 * sent, every button sends a {@link PacketRideAction}, and the server answers each one with a fresh
 * view — so the panel can never show a ride as it is not. While open it also asks for a refresh once
 * a second, so the trains' speeds and positions stay live.</p>
 */
@SideOnly(Side.CLIENT)
public class GuiRideController extends GuiScreen {

    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 214;
    private static final int MAX_TRAIN_ROWS = 4;
    private static final int MAX_SETTING_ROWS = 8;

    private static final int ID_OPEN = 0;
    private static final int ID_TEST = 1;
    private static final int ID_CLOSE = 2;
    private static final int ID_STOP = 3;
    private static final int ID_AUTO = 4;
    private static final int ID_MANUAL = 5;
    private static final int ID_DISPATCH = 6;
    private static final int ID_ADD_TRAIN = 7;
    private static final int ID_CARS_DOWN = 8;
    private static final int ID_CARS_UP = 9;
    /** Remove-train buttons are numbered from here, one per listed train. */
    private static final int ID_REMOVE_BASE = 100;
    /** Setting buttons: down at 200 + 2i, up at 201 + 2i. */
    private static final int ID_SETTING_BASE = 200;

    private RideView view;
    /** The last thing the server said, kept for a few seconds so a refresh does not wipe it. */
    private String message = "";
    private int messageTicks;
    private static final int MESSAGE_TICKS = 100;
    private final Map<Integer, Integer> removeButtonTrain = new HashMap<>();
    private int refreshTicks;

    public GuiRideController(RideView view) {
        this.view = view;
        takeMessage(view);
    }

    public int sectionId() {
        return view.sectionId;
    }

    /** A fresh view from the server; the buttons are rebuilt to match it. */
    public void update(RideView fresh) {
        this.view = fresh;
        takeMessage(fresh);
        initGui();
    }

    /** A refresh answers with no message; only a real answer replaces the one on screen. */
    private void takeMessage(RideView from) {
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
        removeButtonTrain.clear();
        int x = left() + 8;
        int y = top();
        RideController.State state = stateOf(view);
        boolean stopped = view.emergencyStopped;
        boolean manual = view.dispatchMode == RideController.DispatchMode.MANUAL.ordinal();

        GuiButton open = new GuiButton(ID_OPEN, x, y + 20, 54, 18, "Open");
        open.enabled = !stopped && state != RideController.State.OPEN;
        GuiButton test = new GuiButton(ID_TEST, x + 58, y + 20, 54, 18, "Test");
        test.enabled = !stopped && state != RideController.State.TESTING;
        GuiButton close = new GuiButton(ID_CLOSE, x + 116, y + 20, 54, 18, "Close");
        close.enabled = state != RideController.State.CLOSED;
        buttonList.add(open);
        buttonList.add(test);
        buttonList.add(close);
        buttonList.add(new GuiButton(ID_STOP, x, y + 42, 170, 18, stopped
            ? TextFormatting.YELLOW + "Reset emergency stop"
            : TextFormatting.RED + "" + TextFormatting.BOLD + "EMERGENCY STOP"));

        GuiButton auto = new GuiButton(ID_AUTO, x, y + 64, 55, 18, "Auto");
        auto.enabled = manual;
        GuiButton hand = new GuiButton(ID_MANUAL, x + 57, y + 64, 55, 18, "Manual");
        hand.enabled = !manual;
        GuiButton dispatch = new GuiButton(ID_DISPATCH, x + 114, y + 64, 56, 18, "DISPATCH");
        dispatch.enabled = manual && !stopped && state != RideController.State.CLOSED;
        buttonList.add(auto);
        buttonList.add(hand);
        buttonList.add(dispatch);

        GuiButton add = new GuiButton(ID_ADD_TRAIN, x + 100, y + 86, 70, 16, "Add train");
        add.enabled = view.trains.size() < view.maxTrains;
        buttonList.add(add);
        for (int i = 0; i < Math.min(MAX_TRAIN_ROWS, view.trains.size()); i++) {
            int id = ID_REMOVE_BASE + i;
            buttonList.add(new GuiButton(id, x + 152, y + 106 + i * 18, 18, 16, "x"));
            removeButtonTrain.put(id, view.trains.get(i).trainId);
        }
        buttonList.add(new GuiButton(ID_CARS_DOWN, x + 118, y + 182, 16, 16, "-"));
        buttonList.add(new GuiButton(ID_CARS_UP, x + 154, y + 182, 16, 16, "+"));

        int rx = left() + 188;
        for (int i = 0; i < Math.min(MAX_SETTING_ROWS, view.settings.size()); i++) {
            int rowY = y + 20 + i * 20;
            buttonList.add(new GuiButton(ID_SETTING_BASE + 2 * i, rx + 126, rowY, 16, 16, "-"));
            buttonList.add(new GuiButton(ID_SETTING_BASE + 2 * i + 1, rx + 146, rowY, 16, 16, "+"));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        int id = button.id;
        switch (id) {
            case ID_OPEN:
                send(Action.OPEN);
                return;
            case ID_TEST:
                send(Action.TEST);
                return;
            case ID_CLOSE:
                send(Action.CLOSE);
                return;
            case ID_STOP:
                send(view.emergencyStopped ? Action.RESET_EMERGENCY : Action.EMERGENCY_STOP);
                return;
            case ID_AUTO:
                send(Action.MODE_AUTOMATIC);
                return;
            case ID_MANUAL:
                send(Action.MODE_MANUAL);
                return;
            case ID_DISPATCH:
                send(Action.DISPATCH);
                return;
            case ID_ADD_TRAIN:
                send(Action.ADD_TRAIN);
                return;
            case ID_CARS_DOWN:
                RcmcNetwork.sendToServer(new PacketRideAction(view.sectionId, Action.SET_CARS,
                    view.carsPerTrain - 1, 0, 0.0D));
                return;
            case ID_CARS_UP:
                RcmcNetwork.sendToServer(new PacketRideAction(view.sectionId, Action.SET_CARS,
                    view.carsPerTrain + 1, 0, 0.0D));
                return;
            default:
                break;
        }
        if (removeButtonTrain.containsKey(id)) {
            RcmcNetwork.sendToServer(new PacketRideAction(view.sectionId, Action.REMOVE_TRAIN,
                removeButtonTrain.get(id), 0, 0.0D));
            return;
        }
        if (id >= ID_SETTING_BASE) {
            int row = (id - ID_SETTING_BASE) / 2;
            boolean up = (id - ID_SETTING_BASE) % 2 == 1;
            if (row < view.settings.size()) {
                RideView.SettingRow setting = view.settings.get(row);
                RideTuning.Parameter parameter = RideTuning.Parameter.values()[setting.parameter];
                double next = setting.value + (up ? parameter.step : -parameter.step);
                RcmcNetwork.sendToServer(new PacketRideAction(view.sectionId, Action.TUNE,
                    setting.elementIndex, setting.parameter, next));
            }
        }
    }

    private void send(Action action) {
        RcmcNetwork.sendToServer(PacketRideAction.of(view.sectionId, action));
    }

    @Override
    public void updateScreen() {
        if (messageTicks > 0 && --messageTicks == 0) {
            message = "";
        }
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            send(Action.REFRESH);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int l = left();
        int t = top();
        drawRect(l, t, l + PANEL_WIDTH, t + PANEL_HEIGHT, 0xE0101418);
        drawRect(l + 180, t + 16, l + 181, t + PANEL_HEIGHT - 18, 0xFF3A4048);

        drawString(fontRenderer, TextFormatting.WHITE + view.name, l + 8, t + 6, 0xFFFFFF);
        String badge = badge();
        drawString(fontRenderer, badge, l + 176 - fontRenderer.getStringWidth(badge), t + 6, 0xFFFFFF);

        int x = l + 8;
        drawString(fontRenderer, "Trains " + view.trains.size() + " / " + view.maxTrains
            + (view.blockCount >= 2 ? TextFormatting.GRAY + " (" + view.blockCount + " blocks)" : ""),
            x, t + 90, 0xDDDDDD);
        for (int i = 0; i < Math.min(MAX_TRAIN_ROWS, view.trains.size()); i++) {
            RideView.TrainRow row = view.trains.get(i);
            String status = "RUNNING".equals(row.status) ? "" : " " + TextFormatting.RED + row.status;
            drawString(fontRenderer, String.format("#%d  %d cars  %.1f b/s", row.trainId, row.cars,
                row.speed) + status, x, t + 110 + i * 18, 0xBBBBBB);
        }
        if (view.trains.isEmpty()) {
            drawString(fontRenderer, TextFormatting.GRAY + "No trains — add one.", x, t + 110, 0xFFFFFF);
        }
        drawString(fontRenderer, "Cars per new train", x, t + 186, 0xDDDDDD);
        drawCenteredString(fontRenderer, String.valueOf(view.carsPerTrain), x + 144, t + 186, 0xFFFFFF);

        int rx = l + 188;
        drawString(fontRenderer, TextFormatting.WHITE + "Hardware", rx, t + 6, 0xFFFFFF);
        for (int i = 0; i < Math.min(MAX_SETTING_ROWS, view.settings.size()); i++) {
            RideView.SettingRow setting = view.settings.get(i);
            RideTuning.Parameter parameter = RideTuning.Parameter.values()[setting.parameter];
            drawString(fontRenderer, parameter.label, rx, t + 24 + i * 20, 0xDDDDDD);
            String value = format(setting.value) + " " + parameter.unit;
            drawString(fontRenderer, value, rx + 122 - fontRenderer.getStringWidth(value),
                t + 24 + i * 20, 0xFFFFAA);
        }
        if (view.settings.isEmpty()) {
            drawString(fontRenderer, TextFormatting.GRAY + "No adjustable hardware.", rx, t + 24, 0xFFFFFF);
        }

        if (!message.isEmpty()) {
            drawCenteredString(fontRenderer, message, l + PANEL_WIDTH / 2, t + PANEL_HEIGHT - 12,
                view.emergencyStopped ? 0xFF6060 : 0x9FE0A0);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private String badge() {
        if (view.emergencyStopped) {
            // Say what tripped it: a rollback wants the train on the lift looked at, a collision
            // wants a train taken off, and the two call for different next moves.
            String cause = "OPERATOR".equals(view.stopCause) || view.stopCause.isEmpty()
                ? "" : ": " + view.stopCause;
            return TextFormatting.RED + "" + TextFormatting.BOLD + "E-STOP" + cause;
        }
        switch (stateOf(view)) {
            case OPEN:
                return TextFormatting.GREEN + "OPEN";
            case TESTING:
                return TextFormatting.YELLOW + "TESTING";
            case CLOSED:
            default:
                return TextFormatting.GRAY + "CLOSED";
        }
    }

    private static RideController.State stateOf(RideView view) {
        RideController.State[] all = RideController.State.values();
        return view.state >= 0 && view.state < all.length ? all[view.state] : RideController.State.OPEN;
    }

    private static String format(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
