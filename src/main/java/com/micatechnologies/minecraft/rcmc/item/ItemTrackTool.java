package com.micatechnologies.minecraft.rcmc.item;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.RcmcTab;
import com.micatechnologies.minecraft.rcmc.builder.TrackBuildSession;
import com.micatechnologies.minecraft.rcmc.net.PacketBuildSessionSync;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackAttachment;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
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

    /**
     * How close to the first node a click must land to close the circuit instead of adding a node.
     *
     * <p>Generous on purpose. The failure it prevents — two nodes a fraction of a block apart —
     * produces a curve that has to turn through that gap and looks broken, and a builder aiming at
     * their own starting node is unambiguous about what they mean.</p>
     */
    public static final double SNAP_RADIUS = 3.0D;

    /** A circuit needs three nodes; below that there is no loop to close. */
    public static final int MIN_CIRCUIT_NODES = 3;

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
        Vec3 position = new Vec3(pos.getX() + 0.5D,
            pos.getY() + 1.0D + session.heightOffset(), pos.getZ() + 0.5D);

        // Clicking back on the first node closes the loop rather than adding a node almost on top
        // of it. Placing two nodes a fraction apart is the one thing guaranteed to look wrong: the
        // curve has to turn through the tiny gap between them, so a circuit built that way never
        // lined up. Snapping makes "click where you started" mean what a builder intends.
        // What counts as "back where I started" depends on whether this chain is continuing an
        // existing section. If it is, the chain's own first node is the middle of the finished
        // track, not its start — closing there would loop the extension back on itself and leave
        // the original run hanging off the side. The circuit closes at the section's OTHER free
        // end, which is where a builder carrying on round actually arrives.
        Vec3 closeAt = closeTarget(world, session);
        if (session.size() >= MIN_CIRCUIT_NODES && closeAt != null
            && position.distanceTo(closeAt) <= SNAP_RADIUS) {
            session.setClosing(true);
            say(player, TextFormatting.GREEN, "Closing the circuit at the first node.");
            pushSession(player, session);
            commit(player, world, session);
            return EnumActionResult.SUCCESS;
        }

        // Starting a chain near a free end of existing track continues that track instead of
        // laying a separate section beside it. Only on the FIRST node: snapping mid-chain would
        // hijack a layout that merely passes close to something else.
        if (session.isEmpty()) {
            TrackAttachment.Target target =
                TrackAttachment.find(state(world).network(), position, SNAP_RADIUS);
            if (target != null) {
                position = target.position;
                session.setAttachment(target);
                say(player, TextFormatting.AQUA, "Continuing section #" + target.sectionId
                    + " from its " + (target.appends() ? "end" : "start") + ".");
            }
        }

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
        com.micatechnologies.minecraft.rcmc.world.TrackLaying.Result built;
        try {
            built = com.micatechnologies.minecraft.rcmc.world.TrackLaying.lay(world, RcmcWorldState.of(world),
                session.pending(), session.pendingTypes(), closing, session.attachment(), null);
        }
        catch (com.micatechnologies.minecraft.rcmc.world.TrackLaying.Refused refused) {
            // The session is kept, so the builder can undo or add a node rather than lose
            // everything they placed.
            say(player, TextFormatting.RED, refused.getMessage());
            return;
        }
        TrackSection section = built.section;
        int id = section.id();
        // Described before the reset clears them: read after it, this always said "(none recorded)".
        String typesDescribed = describeTypes(session);
        session.reset();
        pushSession(player, session);

        say(player, TextFormatting.GREEN, (built.extended ? "Extended section #" : "Built section #")
            + id + " — " + String.format("%.1f", section.totalLength()) + " blocks, "
            + section.nodes().size() + " nodes"
            + (closing ? " (circuit)" : "") + ".");

        // Reported at commit time, unconditionally. Segment types have been reported as
        // not working twice, and each investigation stalled on not knowing WHICH half of the path
        // failed — whether the tags were recorded and the hardware never made, or the hardware was
        // made and never took effect. Saying what was actually built removes the guesswork from
        // the next report.
        say(player, TextFormatting.DARK_GRAY, "  Segments: " + typesDescribed
            + "  ->  " + built.created.size() + " element(s)");

        for (String platform : built.platforms) {
            say(player, TextFormatting.GRAY, "  Station: laid " + platform
                + ". Rebuild or change it with /rcmc ride " + id + " platform.");
        }
        report(player, section);
        say(player, TextFormatting.DARK_GRAY, "Run /rcmc train " + id + " to put a train on it.");
    }

    /**
     * The point at which clicking closes the chain into a circuit, or {@code null} if it cannot yet
     * be closed.
     */
    private static Vec3 closeTarget(World world, TrackBuildSession session) {
        TrackAttachment.Target attachment = session.attachment();
        if (attachment == null) {
            return session.isEmpty() ? null : session.pending().get(0).position();
        }
        TrackSection existing = state(world).network().section(attachment.sectionId);
        if (existing == null) {
            return session.isEmpty() ? null : session.pending().get(0).position();
        }
        // The far end: continuing from one end and arriving at the other is what makes a circuit.
        return existing.endpointAt(attachment.end == TrackNetwork.End.END
            ? TrackNetwork.End.START
            : TrackNetwork.End.END);
    }

    /** The world's RCMC state, for the snap lookup during placement. */
    private static RcmcWorldState state(World world) {
        return RcmcWorldState.of(world);
    }

    /**
     * A compact rendering of the type recorded at each node, e.g. {@code PPPLLLLPP}.
     *
     * <p>Deliberately shows one character per node rather than a summary: the failure being chased
     * is types not being recorded at all, and a summary of an empty list looks much like a summary
     * of a list of plains.</p>
     */
    private static String describeTypes(TrackBuildSession session) {
        java.util.List<TrackBuildSession.SegmentType> types = session.pendingTypes();
        if (types.isEmpty()) {
            return "(none recorded)";
        }
        StringBuilder out = new StringBuilder(types.size());
        for (TrackBuildSession.SegmentType type : types) {
            out.append(type.name().charAt(0));
        }
        return out.toString();
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

    /**
     * Surfaces validator findings without blocking the build.
     *
     * <p>Package-private so {@link ItemPieceTool} reports a committed section in exactly the same
     * words. Two builders' tools describing the same geometry differently would make a report of
     * "the validator says X" impossible to act on without also knowing which wand was held.</p>
     */
    public static void report(net.minecraft.command.ICommandSender player, TrackSection section) {
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

    static void say(net.minecraft.command.ICommandSender player, TextFormatting colour, String message) {
        player.sendMessage(new TextComponentString(colour + message));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + "Right-click a block: place a track node");
        tooltip.add(TextFormatting.GRAY + "Sneak + right-click a block: place and finish");
        tooltip.add(TextFormatting.GRAY + "Right-click air: finish the section");
        tooltip.add(TextFormatting.GRAY + "Sneak + right-click air: undo the last node");
        tooltip.add(TextFormatting.GRAY + "G: cycle segment type (plain/lift/brake/station)");
        tooltip.add(TextFormatting.GRAY + "Sneak + scroll: raise or lower placement height");
        tooltip.add(TextFormatting.DARK_GRAY + "Bank and circuit mode: /rcmc build");
    }
}
