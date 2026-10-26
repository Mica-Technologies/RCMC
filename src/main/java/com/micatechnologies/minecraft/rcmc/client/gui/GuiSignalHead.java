package com.micatechnologies.minecraft.rcmc.client.gui;

import com.micatechnologies.minecraft.rcmc.block.signal.BlockSignalHead;
import com.micatechnologies.minecraft.rcmc.block.signal.SignalHeadMount;
import com.micatechnologies.minecraft.rcmc.block.signal.SignalHeadStyle;
import com.micatechnologies.minecraft.rcmc.block.signal.TileSignalHead;
import com.micatechnologies.minecraft.rcmc.net.PacketSignalHeadEdit;
import com.micatechnologies.minecraft.rcmc.net.PacketSignalHeadEdit.Edit;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.physics.transit.SignalAspect;
import java.io.IOException;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * A signal head's screen: its style, mount and facing, what controls it, and what it is linked to.
 *
 * <p>Holds no state of its own. It reads the head's tile entity, which the server keeps in sync,
 * every frame; each button is one {@link PacketSignalHeadEdit}, and the change comes back through
 * the tile's own update packet. The buttons are rebuilt whenever the head changes, because which
 * of them make sense depends on its style, mount and mode.</p>
 */
@SideOnly(Side.CLIENT)
public class GuiSignalHead extends GuiScreen {

    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 232;
    private static final int ROW = 24;

    private static final int ID_STYLE_PREV = 0;
    private static final int ID_STYLE_NEXT = 1;
    private static final int ID_MOUNT_PREV = 2;
    private static final int ID_MOUNT_NEXT = 3;
    private static final int ID_HAND = 4;
    private static final int ID_SPAN_DOWN = 5;
    private static final int ID_SPAN_UP = 6;
    private static final int ID_TURN_BASE = 10;
    private static final int ID_FACE_TRACK = 14;
    private static final int ID_CONTROL_BASE = 20;
    private static final int ID_ASPECT_BASE = 30;
    private static final int ID_SUBSIDIARY = 40;
    private static final int ID_APPROACH = 41;
    private static final int ID_LINK = 42;
    private static final int ID_SWITCH = 43;
    private static final int ID_BRANCH_DOWN = 44;
    private static final int ID_BRANCH_UP = 45;
    private static final int ID_TEXT_SET = 46;

    private static final int[] TURNS = {-90, -15, 15, 90};

    private final BlockPos pos;
    private String shape = "";
    private GuiTextField routeText;

    public GuiSignalHead(BlockPos pos) {
        this.pos = pos;
    }

    private TileSignalHead head() {
        if (mc == null || mc.world == null) {
            return null;
        }
        TileEntity tile = mc.world.getTileEntity(pos);
        return tile instanceof TileSignalHead ? (TileSignalHead) tile : null;
    }

    /** What decides which buttons there are; when it changes, they are rebuilt. */
    private static String shapeOf(TileSignalHead head) {
        return head.style() + "/" + head.mount() + "/" + head.control() + "/" + head.hand() + "/"
            + head.manualAspect() + "/" + head.manualSubsidiary() + "/" + head.approachLit() + "/"
            + (head.linkedSwitch() != null) + "/" + head.routeBranch() + "/" + head.isLinked();
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
        TileSignalHead head = head();
        if (head == null) {
            return;
        }
        shape = shapeOf(head);
        int l = left() + 96;
        int t = top() + 22;
        int w = PANEL_WIDTH - 104;

        buttonList.add(new GuiButton(ID_STYLE_PREV, l, t, 20, 18, "<"));
        buttonList.add(new GuiButton(ID_STYLE_NEXT, l + w - 20, t, 20, 18, ">"));

        t += ROW;
        buttonList.add(new GuiButton(ID_MOUNT_PREV, l, t, 20, 18, "<"));
        buttonList.add(new GuiButton(ID_MOUNT_NEXT, l + 100, t, 20, 18, ">"));
        buttonList.add(new GuiButton(ID_HAND, l + 124, t, 44, 18, head.hand() > 0 ? "Right" : "Left"));
        boolean spanned = head.mount() == SignalHeadMount.GANTRY || head.mount() == SignalHeadMount.CANTILEVER;
        if (spanned) {
            buttonList.add(new GuiButton(ID_SPAN_DOWN, l + w - 42, t, 20, 18, "-"));
            buttonList.add(new GuiButton(ID_SPAN_UP, l + w - 20, t, 20, 18, "+"));
        }

        t += ROW;
        for (int i = 0; i < TURNS.length; i++) {
            buttonList.add(new GuiButton(ID_TURN_BASE + i, l + i * 30, t, 28, 18,
                (TURNS[i] > 0 ? "+" : "") + TURNS[i]));
        }
        buttonList.add(new GuiButton(ID_FACE_TRACK, l + 124, t, w - 124, 18, "Face track"));

        t += ROW;
        TileSignalHead.Control[] controls = TileSignalHead.Control.values();
        int cw = (w - 4) / controls.length;
        for (int i = 0; i < controls.length; i++) {
            GuiButton button = new GuiButton(ID_CONTROL_BASE + i, l + i * (cw + 2), t, cw, 18,
                controlName(controls[i]));
            button.enabled = head.control() != controls[i];
            buttonList.add(button);
        }

        t += ROW;
        SignalAspect[] aspects = SignalAspect.shownBy(head.style().aspects());
        int aw = (w - 2 * (aspects.length - 1)) / aspects.length;
        for (int i = 0; i < aspects.length; i++) {
            GuiButton button = new GuiButton(ID_ASPECT_BASE + aspects[i].ordinal(), l + i * (aw + 2), t, aw, 18,
                colourOf(aspects[i]) + aspectName(aspects[i], head.style()));
            button.enabled = head.control() != TileSignalHead.Control.MANUAL
                || head.manualAspect() != aspects[i];
            buttonList.add(button);
        }

        t += ROW;
        if (head.style().hasSubsidiary() || head.style().isRouteIndicator()) {
            buttonList.add(new GuiButton(ID_SUBSIDIARY, l, t, 104, 18,
                subsidiaryName(head.style()) + ": " + (head.manualSubsidiary() ? "on" : "off")));
        }
        buttonList.add(new GuiButton(ID_APPROACH, l + 108, t, w - 108, 18,
            "Approach lit: " + (head.approachLit() ? "on" : "off")));

        t += ROW;
        buttonList.add(new GuiButton(ID_LINK, l, t, 104, 18, head.isLinked() ? "Unlink signal" : "Link signal"));
        if (head.style().isRouteIndicator()) {
            buttonList.add(new GuiButton(ID_SWITCH, l + 108, t, w - 108, 18,
                head.linkedSwitch() != null ? "Unlink switch" : "Link switch"));

            t += ROW;
            buttonList.add(new GuiButton(ID_BRANCH_DOWN, l, t, 20, 18, "-"));
            buttonList.add(new GuiButton(ID_BRANCH_UP, l + 84, t, 20, 18, "+"));
            if (head.style() == SignalHeadStyle.THEATRE) {
                routeText = new GuiTextField(99, fontRenderer, l + 108, t + 1, 50, 16);
                routeText.setMaxStringLength(3);
                int branch = head.routeBranch();
                routeText.setText(branch < head.routeTexts().size() ? head.routeTexts().get(branch) : "");
                buttonList.add(new GuiButton(ID_TEXT_SET, l + 162, t, 40, 18, "Set"));
            }
            else {
                routeText = null;
            }
        }
        else {
            routeText = null;
        }
    }

    private static String controlName(TileSignalHead.Control control) {
        switch (control) {
            case REDSTONE:
                return "Redstone";
            case LINKED:
                return "Linked";
            default:
                return "Manual";
        }
    }

    private static String subsidiaryName(SignalHeadStyle style) {
        switch (style) {
            case STARTER:
                return "Depart";
            case CALL_ON:
                return "Call-on";
            case ROUTE_FEATHER:
            case THEATRE:
                return "Route";
            default:
                return "Lunar";
        }
    }

    /** What a driver calls the aspect on this head: a semaphore is "on" or "off", not a colour. */
    private static String aspectName(SignalAspect aspect, SignalHeadStyle style) {
        if (style.isSemaphore() || style == SignalHeadStyle.TRIP_COCK) {
            return aspect == SignalAspect.GREEN ? "Off" : "On";
        }
        switch (aspect) {
            case DOUBLE_YELLOW:
                return "YY";
            case YELLOW:
                return "Yellow";
            case GREEN:
                return "Green";
            default:
                return "Red";
        }
    }

    private static TextFormatting colourOf(SignalAspect aspect) {
        switch (aspect) {
            case GREEN:
                return TextFormatting.GREEN;
            case YELLOW:
            case DOUBLE_YELLOW:
                return TextFormatting.YELLOW;
            default:
                return TextFormatting.RED;
        }
    }

    @Override
    public void updateScreen() {
        TileSignalHead head = head();
        if (head == null) {
            mc.displayGuiScreen(null);
            return;
        }
        if (!shapeOf(head).equals(shape)) {
            initGui();
        }
        if (routeText != null) {
            routeText.updateCursorCounter();
        }
    }

    private void send(Edit edit, int value) {
        send(edit, value, "");
    }

    private void send(Edit edit, int value, String text) {
        RcmcNetwork.sendToServer(new PacketSignalHeadEdit(pos, edit, value, text));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        TileSignalHead head = head();
        if (head == null) {
            return;
        }
        int styles = SignalHeadStyle.values().length;
        int mounts = SignalHeadMount.values().length;
        int id = button.id;
        if (id >= ID_TURN_BASE && id < ID_TURN_BASE + TURNS.length) {
            send(Edit.TURN, TURNS[id - ID_TURN_BASE]);
            return;
        }
        if (id >= ID_CONTROL_BASE && id < ID_CONTROL_BASE + TileSignalHead.Control.values().length) {
            send(Edit.CONTROL, id - ID_CONTROL_BASE);
            return;
        }
        if (id >= ID_ASPECT_BASE && id < ID_ASPECT_BASE + SignalAspect.values().length) {
            send(Edit.ASPECT, id - ID_ASPECT_BASE);
            return;
        }
        switch (id) {
            case ID_STYLE_PREV:
                send(Edit.STYLE, (head.style().ordinal() + styles - 1) % styles);
                break;
            case ID_STYLE_NEXT:
                send(Edit.STYLE, (head.style().ordinal() + 1) % styles);
                break;
            case ID_MOUNT_PREV:
                send(Edit.MOUNT, (head.mount().ordinal() + mounts - 1) % mounts);
                break;
            case ID_MOUNT_NEXT:
                send(Edit.MOUNT, (head.mount().ordinal() + 1) % mounts);
                break;
            case ID_HAND:
                send(Edit.HAND, -head.hand());
                break;
            case ID_SPAN_DOWN:
                send(Edit.SPAN, head.span() - 1);
                break;
            case ID_SPAN_UP:
                send(Edit.SPAN, head.span() + 1);
                break;
            case ID_FACE_TRACK:
                send(Edit.FACE_TRACK, 0);
                break;
            case ID_SUBSIDIARY:
                send(Edit.SUBSIDIARY, head.manualSubsidiary() ? 0 : 1);
                break;
            case ID_APPROACH:
                send(Edit.APPROACH_LIT, head.approachLit() ? 0 : 1);
                break;
            case ID_LINK:
                send(head.isLinked() ? Edit.UNLINK_SIGNAL : Edit.LINK_SIGNAL, 0);
                break;
            case ID_SWITCH:
                send(head.linkedSwitch() != null ? Edit.UNLINK_SWITCH : Edit.LINK_SWITCH, 0);
                break;
            case ID_BRANCH_DOWN:
                send(Edit.ROUTE_BRANCH, head.routeBranch() - 1);
                break;
            case ID_BRANCH_UP:
                send(Edit.ROUTE_BRANCH, head.routeBranch() + 1);
                break;
            case ID_TEXT_SET:
                if (routeText != null) {
                    send(Edit.ROUTE_TEXT, head.routeBranch(), routeText.getText());
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (routeText != null && routeText.isFocused()) {
            routeText.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (routeText != null) {
            routeText.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int l = left();
        int t = top();
        drawRect(l, t, l + PANEL_WIDTH, t + PANEL_HEIGHT, 0xE0101418);
        TileSignalHead head = head();
        if (head == null) {
            return;
        }
        drawCenteredString(fontRenderer, "Signal head", l + PANEL_WIDTH / 2, t + 6, 0xFFFFFF);

        int x = l + 8;
        int y = t + 27;
        int vx = l + 96 + 26;
        label("Style", x, y);
        drawString(fontRenderer, fontRenderer.trimStringToWidth(styleName(head.style()), PANEL_WIDTH - 154),
            vx, y, 0xFFE080);
        y += ROW;
        label("Mount", x, y);
        drawString(fontRenderer, head.mount().getName()
            + (head.mount() == SignalHeadMount.GANTRY || head.mount() == SignalHeadMount.CANTILEVER
                ? "" : ""), vx, y, 0xFFE080);
        if (head.mount() == SignalHeadMount.GANTRY || head.mount() == SignalHeadMount.CANTILEVER) {
            drawString(fontRenderer, head.span() + " blocks", l + PANEL_WIDTH - 8 - 42 - 50, y, 0xC0C0C0);
        }
        y += ROW;
        label("Facing " + Math.round(head.facingDegrees()) + "°", x, y);
        y += ROW;
        label("Control", x, y);
        y += ROW;
        label("Aspect", x, y);
        y += ROW;
        label("Lamps", x, y);
        y += ROW;
        label("Linked to", x, y);
        if (head.style().isRouteIndicator()) {
            y += ROW;
            label("Branch " + head.routeBranch(), x, y);
            drawString(fontRenderer, "" + head.routeBranch(), l + 96 + 50, y, 0xFFE080);
        }
        String link = head.isLinked()
            ? head.lineName() + ", #" + head.sectionId() + " at "
                + String.format(Locale.ROOT, "%.0f", head.distance()) + (head.direction() > 0 ? " fwd" : " rev")
            : "no signal";
        if (head.linkedSwitch() != null) {
            link += "; switch #" + head.linkedSwitch().sectionId + " "
                + head.linkedSwitch().end.name().toLowerCase(Locale.ROOT);
        }
        String showing = "Showing " + BlockSignalHead.name(head.aspect()) + (head.lampsLit() ? "" : ", dark");
        drawString(fontRenderer, fontRenderer.trimStringToWidth(showing + " • " + link, PANEL_WIDTH - 16),
            x, t + PANEL_HEIGHT - 14, 0x9090A0);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (routeText != null) {
            routeText.drawTextBox();
        }
    }

    /** "Colour Light, 4-aspect" for "Signal Head (Colour Light, 4-aspect)": the part that differs. */
    private static String styleName(SignalHeadStyle style) {
        String full = I18n.format("tile.rcmc.signal_head." + style.getName() + ".name");
        int open = full.indexOf("Signal Head (");
        if (open == 0 && full.endsWith(")")) {
            return full.substring("Signal Head (".length(), full.length() - 1);
        }
        return full;
    }

    private void label(String text, int x, int y) {
        drawString(fontRenderer, text, x, y, 0xC0C0C0);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
