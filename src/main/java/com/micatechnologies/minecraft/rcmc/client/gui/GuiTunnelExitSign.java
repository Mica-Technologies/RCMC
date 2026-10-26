package com.micatechnologies.minecraft.rcmc.client.gui;

import com.micatechnologies.minecraft.rcmc.block.sign.BlockTunnelExitSign;
import com.micatechnologies.minecraft.rcmc.block.sign.TileTunnelExitSign;
import com.micatechnologies.minecraft.rcmc.net.PacketTunnelExitSign;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import java.io.IOException;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/** Types the distances on a tunnel exit sign: one box per arrow the sign has, in blocks. */
@SideOnly(Side.CLIENT)
public class GuiTunnelExitSign extends GuiScreen {

    private static final int ID_DONE = 0;

    private final BlockPos pos;
    private GuiTextField left;
    private GuiTextField right;

    public GuiTunnelExitSign(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        TileEntity tile = mc.world.getTileEntity(pos);
        IBlockState state = mc.world.getBlockState(pos);
        if (!(tile instanceof TileTunnelExitSign) || !(state.getBlock() instanceof BlockTunnelExitSign)) {
            return;
        }
        TileTunnelExitSign sign = (TileTunnelExitSign) tile;
        BlockTunnelExitSign.Variant variant = state.getValue(BlockTunnelExitSign.VARIANT);
        int cx = width / 2;
        int y = height / 2 - 20;
        left = variant.hasLeft() ? field(cx - 110, y, sign.left()) : null;
        right = variant.hasRight() ? field(cx + 30, y, sign.right()) : null;
        (left != null ? left : right).setFocused(true);
        buttonList.add(new GuiButton(ID_DONE, cx - 50, y + 40, 100, 20, "Done"));
    }

    private GuiTextField field(int x, int y, String text) {
        GuiTextField field = new GuiTextField(1, fontRenderer, x, y, 80, 18);
        field.setMaxStringLength(TileTunnelExitSign.MAX_LENGTH);
        field.setText(text);
        return field;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_DONE) {
            save();
            mc.displayGuiScreen(null);
        }
    }

    private void save() {
        RcmcNetwork.sendToServer(new PacketTunnelExitSign(pos,
            left == null ? "" : left.getText(), right == null ? "" : right.getText()));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            save();
            mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == Keyboard.KEY_TAB && left != null && right != null) {
            boolean toRight = left.isFocused();
            left.setFocused(!toRight);
            right.setFocused(toRight);
            return;
        }
        if (left != null && left.isFocused()) {
            left.textboxKeyTyped(typedChar, keyCode);
        }
        else if (right != null && right.isFocused()) {
            right.textboxKeyTyped(typedChar, keyCode);
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (left != null) {
            left.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (right != null) {
            right.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void updateScreen() {
        if (left != null) {
            left.updateCursorCounter();
        }
        if (right != null) {
            right.updateCursorCounter();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;
        int y = height / 2 - 20;
        drawCenteredString(fontRenderer, "Exit sign distances, in blocks", cx, y - 30, 0xFFFFFF);
        if (left != null) {
            drawString(fontRenderer, "← Left", cx - 110, y - 12, 0xC0C0C0);
            left.drawTextBox();
        }
        if (right != null) {
            drawString(fontRenderer, "Right →", cx + 30, y - 12, 0xC0C0C0);
            right.drawTextBox();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
