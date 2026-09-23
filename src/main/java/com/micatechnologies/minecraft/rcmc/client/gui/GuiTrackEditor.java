package com.micatechnologies.minecraft.rcmc.client.gui;

import com.micatechnologies.minecraft.rcmc.builder.TrackBuildSession;
import com.micatechnologies.minecraft.rcmc.net.PacketTrackEdit;
import com.micatechnologies.minecraft.rcmc.net.PacketTrackEdit.Action;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.net.TrackEditView;
import com.micatechnologies.minecraft.rcmc.track.TrackPalette;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The track editor: one node of a committed section, and everything that can be done to it —
 * move it, re-bank it, add or remove nodes, set what the span from it is laid as, paint the
 * section, or delete it.
 *
 * <p>Keybind cycling was the only way to edit track before, one type per press through a list
 * that had grown to nine. Here every type is a button, every nudge is a click, and the node being
 * edited is marked in the world behind the panel, which is kept clear for exactly that reason.</p>
 *
 * <p>The server owns the track: each press is sent, the server edits and answers with a fresh view,
 * and the screen shows whatever that says.</p>
 */
@SideOnly(Side.CLIENT)
public class GuiTrackEditor extends GuiScreen {

    private static final int WIDTH = 300;
    private static final int HEIGHT = 206;

    private static final int ID_PREV = 0;
    private static final int ID_NEXT = 1;
    private static final int ID_STEP = 2;
    private static final int ID_BANK_DOWN = 3;
    private static final int ID_BANK_UP = 4;
    private static final int ID_INSERT = 5;
    private static final int ID_DELETE_NODE = 6;
    private static final int ID_DELETE_SECTION = 7;
    private static final int ID_PAINT_PART = 8;
    private static final int ID_COLOUR = 9;
    /** Move buttons: 20 + 2·axis for minus, 21 + 2·axis for plus. */
    private static final int ID_MOVE_BASE = 20;
    /** Span type buttons from here, one per type. */
    private static final int ID_TYPE_BASE = 40;

    /** Steps a nudge can move a node by, blocks. */
    private static final double[] STEPS = {0.5D, 1.0D, 4.0D};
    private static final double BANK_STEP = 5.0D;

    /** The node on screen, for the in-world marker. Null while no editor is open. */
    private static volatile TrackEditView current;

    private TrackEditView view;
    private int step = 0;
    private boolean confirmingDelete;
    private String message = "";
    private int messageTicks;

    public GuiTrackEditor(TrackEditView view) {
        this.view = view;
        this.message = view.message;
        this.messageTicks = view.message.isEmpty() ? 0 : 100;
        current = view;
    }

    /** The node being edited, if the editor is open — what the world marker draws. */
    public static TrackEditView current() {
        return current;
    }

    public void update(TrackEditView fresh) {
        this.view = fresh;
        current = fresh;
        if (!fresh.message.isEmpty()) {
            message = fresh.message;
            messageTicks = 100;
        }
        initGui();
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        // Low on the screen, so the node and the track around it stay in view above.
        return height - HEIGHT - 8;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int l = left();
        int t = top();
        buttonList.add(new GuiButton(ID_PREV, l + 6, t + 18, 40, 16, "< Prev"));
        buttonList.add(new GuiButton(ID_NEXT, l + 48, t + 18, 40, 16, "Next >"));

        for (int a = 0; a < 3; a++) {
            int y = t + 40 + a * 18;
            buttonList.add(new GuiButton(ID_MOVE_BASE + 2 * a, l + 96, y, 18, 16, "-"));
            buttonList.add(new GuiButton(ID_MOVE_BASE + 2 * a + 1, l + 116, y, 18, 16, "+"));
        }
        buttonList.add(new GuiButton(ID_STEP, l + 6, t + 94, 128, 16, "Step: " + format(STEPS[step]) + " block"
            + (STEPS[step] == 1.0D ? "" : "s")));
        buttonList.add(new GuiButton(ID_BANK_DOWN, l + 96, t + 114, 18, 16, "-"));
        buttonList.add(new GuiButton(ID_BANK_UP, l + 116, t + 114, 18, 16, "+"));

        GuiButton part = new GuiButton(ID_PAINT_PART, l + 6, t + 136, 62, 16,
            "Part: " + TrackPalette.Part.values()[Math.max(0, Math.min(TrackPalette.Part.values().length - 1,
                view.paintPart))].name().toLowerCase(java.util.Locale.ROOT));
        GuiButton colour = new GuiButton(ID_COLOUR, l + 72, t + 136, 62, 16, view.paintColour);
        buttonList.add(part);
        buttonList.add(colour);

        GuiButton insert = new GuiButton(ID_INSERT, l + 6, t + 158, 62, 16, "Add node");
        GuiButton delete = new GuiButton(ID_DELETE_NODE, l + 72, t + 158, 62, 16, "Delete node");
        buttonList.add(insert);
        buttonList.add(delete);
        buttonList.add(new GuiButton(ID_DELETE_SECTION, l + 6, t + 180, 128, 16, confirmingDelete
            ? TextFormatting.RED + "" + TextFormatting.BOLD + "Really delete?"
            : TextFormatting.RED + "Delete section"));

        // Span types, one column down the right-hand side: every type a click away.
        TrackBuildSession.SegmentType[] types = TrackBuildSession.SegmentType.values();
        for (int i = 0; i < types.length; i++) {
            GuiButton type = new GuiButton(ID_TYPE_BASE + i, l + 146, t + 36 + i * 17, 148, 16,
                (i == view.spanType ? TextFormatting.GREEN + "> " : "") + types[i].label());
            type.enabled = view.spanType >= 0 && i != view.spanType;
            buttonList.add(type);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        int id = button.id;
        if (id != ID_DELETE_SECTION) {
            confirmingDelete = false;
        }
        if (id == ID_PREV) {
            press(Action.SELECT_NODE, view.nodeIndex - 1);
        }
        else if (id == ID_NEXT) {
            press(Action.SELECT_NODE, view.nodeIndex + 1);
        }
        else if (id == ID_STEP) {
            step = (step + 1) % STEPS.length;
            initGui();
        }
        else if (id == ID_BANK_DOWN || id == ID_BANK_UP) {
            press(Action.BANK, id == ID_BANK_UP ? BANK_STEP : -BANK_STEP);
        }
        else if (id == ID_INSERT) {
            press(Action.INSERT_AFTER, 0.0D);
        }
        else if (id == ID_DELETE_NODE) {
            press(Action.DELETE_NODE, 0.0D);
        }
        else if (id == ID_DELETE_SECTION) {
            // Two presses: a section is a whole ride's worth of track and everything on it.
            if (confirmingDelete) {
                press(Action.DELETE_SECTION, 1.0D);
                mc.displayGuiScreen(null);
            }
            else {
                confirmingDelete = true;
                initGui();
            }
        }
        else if (id == ID_PAINT_PART) {
            press(Action.CYCLE_PAINT_PART, 0.0D);
        }
        else if (id == ID_COLOUR) {
            press(Action.CYCLE_COLOUR, 0.0D);
        }
        else if (id >= ID_TYPE_BASE) {
            press(Action.SET_SPAN_TYPE, id - ID_TYPE_BASE);
        }
        else if (id >= ID_MOVE_BASE) {
            int axis = (id - ID_MOVE_BASE) / 2;
            double sign = (id - ID_MOVE_BASE) % 2 == 1 ? 1.0D : -1.0D;
            Action[] moves = {Action.MOVE_X, Action.MOVE_Y, Action.MOVE_Z};
            press(moves[axis], sign * STEPS[step]);
        }
    }

    private void press(Action action, double value) {
        RcmcNetwork.sendToServer(new PacketTrackEdit.Press(view.sectionId, view.nodeIndex, action, value));
    }

    @Override
    public void updateScreen() {
        if (messageTicks > 0 && --messageTicks == 0) {
            message = "";
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int l = left();
        int t = top();
        // No full-screen darkening: the node is marked in the world behind, and must stay visible.
        drawRect(l, t, l + WIDTH, t + HEIGHT, 0xD0101418);
        drawRect(l + 140, t + 16, l + 141, t + HEIGHT - 6, 0xFF3A4048);

        drawString(fontRenderer, TextFormatting.WHITE + "Section #" + view.sectionId
            + TextFormatting.GRAY + "  " + (view.closed ? "circuit, " : "") + format(view.length) + " blocks",
            l + 6, t + 5, 0xFFFFFF);
        drawString(fontRenderer, "Node " + (view.nodeIndex + 1) + " of " + view.nodeCount,
            l + 94, t + 22, 0xDDDDDD);

        String[] axes = {"X", "Y", "Z"};
        double[] values = {view.x, view.y, view.z};
        for (int a = 0; a < 3; a++) {
            drawString(fontRenderer, axes[a] + "  " + format(values[a]), l + 6, t + 44 + a * 18, 0xFFFFAA);
        }
        drawString(fontRenderer, "Bank  " + format(view.bank) + "°", l + 6, t + 118, 0xFFFFAA);

        String span = view.spanType >= 0
            ? "Span " + (view.nodeIndex + 1) + " → " + (view.nodeIndex + 2 > view.nodeCount ? 1 : view.nodeIndex + 2)
            : "No span from the last node";
        drawString(fontRenderer, TextFormatting.WHITE + span, l + WIDTH - 6 - fontRenderer.getStringWidth(span),
            t + 22, 0xFFFFFF);

        if (!message.isEmpty()) {
            drawCenteredString(fontRenderer, message, width / 2, t - 12, 0x9FE0A0);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        current = null;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static String format(double v) {
        return Math.abs(v - Math.rint(v)) < 1.0e-6D ? String.valueOf((long) Math.rint(v))
            : String.format("%.1f", v);
    }
}
