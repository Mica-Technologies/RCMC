package com.micatechnologies.minecraft.rcmc.item;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.RcmcTab;
import com.micatechnologies.minecraft.rcmc.builder.TrackBuildSession;
import com.micatechnologies.minecraft.rcmc.net.PacketBuildSessionSync;
import com.micatechnologies.minecraft.rcmc.net.PacketTrackSync;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.track.validation.TrackIssue;
import com.micatechnologies.minecraft.rcmc.track.validation.TrackValidator;
import com.micatechnologies.minecraft.rcmc.track.validation.ValidationLimits;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import java.util.List;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The track builder tool.
 *
 * <p>Controls:</p>
 * <ul>
 *   <li><b>Right-click a block</b> — place a track node one block above it.</li>
 *   <li><b>Sneak + right-click a block</b> — place a node and commit the section.</li>
 *   <li><b>Right-click air</b> — commit the section as-is.</li>
 *   <li><b>Sneak + right-click air</b> — undo the last node; on an empty session, cancel.</li>
 * </ul>
 *
 * <p>Nodes accumulate in a {@link TrackBuildSession} rather than going straight into the network:
 * a section needs at least two nodes to have geometry, every edit rebuilds its splines, and a
 * half-built curve must never be visible to a train.</p>
 *
 * <p>On commit the section is run through {@link TrackValidator} and any findings are reported to
 * the builder — but the section is committed regardless. That is the project's deliberate stance:
 * RollerCoaster Tycoon lets you build the lethal ride and then tells you what you have built,
 * which is far more fun than a refusal.</p>
 */
public class ItemTrackTool extends Item {

    public static final String NAME = "track_tool";

    public ItemTrackTool() {
        setRegistryName(RcmcConstants.MOD_NAMESPACE, NAME);
        setTranslationKey(RcmcConstants.MOD_NAMESPACE + "." + NAME);
        setCreativeTab(RcmcTab.RCMC_TAB);
        setMaxStackSize(1);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }
        TrackBuildSession session = TrackBuildSession.of(player.getUniqueID());

        // Centre the node on the block and lift it clear of the surface, so track laid along the
        // ground sits on top of it rather than inside it.
        Vec3 position = new Vec3(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
        session.add(new TrackNode(position, session.bankDegrees(), null));

        pushSession(player, session);

        if (player.isSneaking()) {
            commit(player, world, session);
        }
        else {
            say(player, TextFormatting.GRAY, "Node " + session.size() + " placed at "
                + String.format("%.1f, %.1f, %.1f", position.x, position.y, position.z)
                + (session.bankDegrees() == 0.0D ? ""
                   : " (bank " + String.format("%.0f", session.bankDegrees()) + "°)"));
            if (session.size() == 1) {
                say(player, TextFormatting.DARK_GRAY,
                    "Right-click air to finish, sneak+right-click air to undo.");
            }
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (world.isRemote) {
            return new ActionResult<>(EnumActionResult.SUCCESS, held);
        }
        TrackBuildSession session = TrackBuildSession.of(player.getUniqueID());

        if (player.isSneaking()) {
            TrackNode removed = session.undo();
            if (removed == null) {
                say(player, TextFormatting.YELLOW, "Nothing to undo.");
            }
            else {
                say(player, TextFormatting.YELLOW,
                    "Removed the last node; " + session.size() + " remaining.");
            }
            pushSession(player, session);
        }
        else {
            commit(player, world, session);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, held);
    }

    /**
     * Turns the pending nodes into a real section.
     *
     * <p>A closed circuit needs three nodes and an open run needs two; below that there is no
     * curve to build, so the session is left intact rather than discarded — the builder has
     * simply not finished yet.</p>
     */
    private static void commit(EntityPlayer player, World world, TrackBuildSession session) {
        boolean closing = session.isClosing();
        int minimum = closing ? 3 : 2;
        if (session.size() < minimum) {
            say(player, TextFormatting.RED, "Need at least " + minimum + " nodes to build "
                + (closing ? "a circuit" : "a section") + "; you have " + session.size() + ".");
            return;
        }

        RcmcWorldState state = RcmcWorldState.of(world);
        int id = state.network().allocateSectionId();
        TrackSection section;
        try {
            section = new TrackSection(id, session.pending(), closing, null);
        }
        catch (IllegalArgumentException e) {
            // Degenerate node layout — report it and keep the session so the builder can undo
            // rather than losing everything they placed.
            say(player, TextFormatting.RED, "Could not build that section: " + e.getMessage());
            return;
        }

        state.network().addSection(section);
        state.markTrackDirty(world);
        RcmcNetwork.sendToAllIn(new PacketTrackSync(state.network()), world.provider.getDimension());
        session.reset();
        pushSession(player, session);

        say(player, TextFormatting.GREEN, "Built section #" + id + " — "
            + String.format("%.1f", section.totalLength()) + " blocks, "
            + section.nodes().size() + " nodes"
            + (closing ? " (circuit)" : "") + ".");

        report(player, section);
        say(player, TextFormatting.DARK_GRAY, "Run /rcmc train " + id + " to put a train on it.");
    }

    /**
     * Mirrors the session to its owner so the ghost preview has something to draw.
     *
     * <p>To that one player only, and only on change. A half-built curve is that builder's scratch
     * work — nobody else has any use for it, and it changes when they click, not every tick.</p>
     */
    private static void pushSession(EntityPlayer player, TrackBuildSession session) {
        if (player instanceof net.minecraft.entity.player.EntityPlayerMP) {
            RcmcNetwork.sendTo(new PacketBuildSessionSync(session),
                (net.minecraft.entity.player.EntityPlayerMP) player);
        }
    }

    /** Surfaces validator findings without blocking the build. */
    private static void report(EntityPlayer player, TrackSection section) {
        List<TrackIssue> issues = new TrackValidator(ValidationLimits.DEFAULT).validate(section);
        if (issues.isEmpty()) {
            return;
        }
        int shown = 0;
        for (TrackIssue issue : issues) {
            if (issue.severity() == TrackIssue.Severity.INFO) {
                continue;
            }
            if (shown++ >= 5) {
                say(player, TextFormatting.GRAY, "  ...and " + (issues.size() - shown)
                    + " more. Run the validator for the full list.");
                break;
            }
            TextFormatting colour = issue.severity() == TrackIssue.Severity.ERROR
                ? TextFormatting.RED : TextFormatting.GOLD;
            say(player, colour, "  " + issue.severity() + " at "
                + String.format("%.0f", issue.distanceBlocks()) + "m: " + issue.message());
        }
    }

    private static void say(EntityPlayer player, TextFormatting colour, String message) {
        player.sendMessage(new TextComponentString(colour + message));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + "Right-click a block: place a track node");
        tooltip.add(TextFormatting.GRAY + "Sneak + right-click a block: place and finish");
        tooltip.add(TextFormatting.GRAY + "Right-click air: finish the section");
        tooltip.add(TextFormatting.GRAY + "Sneak + right-click air: undo the last node");
        tooltip.add(TextFormatting.DARK_GRAY + "Bank and circuit mode: /rcmc build");
    }
}
