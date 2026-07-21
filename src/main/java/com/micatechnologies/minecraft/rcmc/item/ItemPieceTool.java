package com.micatechnologies.minecraft.rcmc.item;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.RcmcTab;
import com.micatechnologies.minecraft.rcmc.builder.PieceBuildSession;
import com.micatechnologies.minecraft.rcmc.builder.PiecePalette;
import com.micatechnologies.minecraft.rcmc.net.PacketElementSync;
import com.micatechnologies.minecraft.rcmc.net.PacketPieceSessionSync;
import com.micatechnologies.minecraft.rcmc.net.PacketTrackSync;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import java.util.List;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The prefab piece builder: a coaster assembled by clicking one standard maneuver onto the end of
 * the last, rather than by placing every control point of a spline.
 *
 * <p>Controls:</p>
 * <ul>
 *   <li><b>Right-click a block</b> — start a chain there, or append the selected piece to it.</li>
 *   <li><b>Sneak + right-click a block</b> — append and commit.</li>
 *   <li><b>Right-click air</b> — commit the chain as a section.</li>
 *   <li><b>Sneak + right-click air</b> — undo the last <em>piece</em>; on an empty chain, cancel.</li>
 *   <li><b>G</b> / <b>Ctrl+scroll</b> — cycle the selected piece.</li>
 *   <li><b>Shift+scroll</b> — resize the selected piece.</li>
 *   <li><b>R</b> — undo the last piece, which is the same action as sneak+right-click air and
 *       exists because the sneak <em>flag</em> is never set while flying, and building is done
 *       flying.</li>
 * </ul>
 *
 * <p><b>Why this exists alongside {@link ItemTrackTool} rather than replacing it.</b> The two tools
 * answer different questions. Freeform placement answers "the track has to go exactly here, between
 * these two buildings"; the piece palette answers "give me a good vertical loop". A spline editor
 * cannot produce the second — the bank profile that balances lateral G through a curve, or the
 * clothoid-ish curvature that keeps a loop's G survivable, are numbers nobody eyeballs by dragging
 * control points. Everything that makes those pieces good already lives in {@code track.element};
 * this item is the path from a player's hand to it.</p>
 *
 * <p>Only the direction of travel is chosen by the player, and only once, when the chain is
 * anchored. After that a piece's placement is fully determined by the exit state of the piece
 * before it — which is the entire point, and is what {@link PieceBuildSession} guarantees.</p>
 */
public class ItemPieceTool extends Item {

    public static final String NAME = "piece_tool";

    public ItemPieceTool() {
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
        PieceBuildSession session = PieceBuildSession.of(player.getUniqueID());

        if (!session.isStarted()) {
            session.start(anchorFrame(player, pos));
            ItemTrackTool.say(player, TextFormatting.GREEN, "Chain started, heading "
                + compass(player) + ".");
            ItemTrackTool.say(player, TextFormatting.DARK_GRAY,
                "Right-click again to add a " + describeSelection(session)
                    + "; right-click air to finish.");
        }
        else {
            String description = describeSelection(session);
            List<TrackNode> added = session.append();
            if (added.isEmpty()) {
                ItemTrackTool.say(player, TextFormatting.RED,
                    "Could not build a " + description + " from the end of that chain.");
            }
            else {
                ItemTrackTool.say(player, TextFormatting.GRAY, "Added " + description
                    + " — piece " + session.pieceCount() + ", " + session.nodeCount() + " nodes.");
            }
        }

        pushSession(player, session);

        if (player.isSneaking()) {
            commit(player, world, session);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (world.isRemote) {
            return new ActionResult<>(EnumActionResult.SUCCESS, held);
        }
        PieceBuildSession session = PieceBuildSession.of(player.getUniqueID());
        if (player.isSneaking()) {
            undo(player, session);
        }
        else {
            commit(player, world, session);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, held);
    }

    /**
     * Takes the last piece back off, or cancels the chain if there is nothing on it.
     *
     * <p>Shared with the {@code R} keybind, because the sneak gesture that would otherwise be the
     * only way to reach this does not fire while flying — {@code isSneaking()} reports the sneak
     * flag, and a flying player holding shift descends rather than sneaks.</p>
     */
    public static void undo(EntityPlayer player, PieceBuildSession session) {
        PiecePalette.Entry removed = session.undoPiece();
        if (removed != null) {
            ItemTrackTool.say(player, TextFormatting.YELLOW, "Removed the last piece; "
                + session.pieceCount() + " remaining.");
        }
        else if (session.isStarted()) {
            session.reset();
            ItemTrackTool.say(player, TextFormatting.YELLOW, "Chain cancelled.");
        }
        else {
            ItemTrackTool.say(player, TextFormatting.YELLOW, "Nothing to undo.");
        }
        pushSession(player, session);
    }

    /**
     * Commits the chain as a section, by exactly the path {@link ItemTrackTool} uses.
     *
     * <p>Both packets, always. The element sync carries no new hardware here — the piece palette is
     * pure geometry and creates none — but a client that receives track without the accompanying
     * element state has been the source of renders that disagree with the server about what is on a
     * section, so the pair is kept together rather than optimised apart.</p>
     */
    private static void commit(EntityPlayer player, World world, PieceBuildSession session) {
        if (!session.isStarted()) {
            ItemTrackTool.say(player, TextFormatting.RED,
                "No chain to finish — right-click a block to start one.");
            return;
        }
        if (session.pieceCount() < 1) {
            ItemTrackTool.say(player, TextFormatting.RED,
                "Add at least one piece before finishing.");
            return;
        }

        RcmcWorldState state = RcmcWorldState.of(world);
        int id = state.network().allocateSectionId();
        TrackSection section;
        try {
            section = new TrackSection(id, session.nodes(), false, null);
        }
        catch (IllegalArgumentException e) {
            // Keep the chain so the builder can undo back to whatever broke it, rather than losing
            // every piece they placed.
            ItemTrackTool.say(player, TextFormatting.RED,
                "Could not build that section: " + e.getMessage());
            return;
        }

        state.network().addSection(section);
        state.markTrackDirty(world);
        int dimension = world.provider.getDimension();
        RcmcNetwork.sendToAllIn(new PacketTrackSync(state.network()), dimension);
        RcmcNetwork.sendToAllIn(new PacketElementSync(state.elements()), dimension);

        int pieces = session.pieceCount();
        session.reset();
        pushSession(player, session);

        ItemTrackTool.say(player, TextFormatting.GREEN, "Built section #" + id + " — "
            + String.format("%.1f", section.totalLength()) + " blocks, "
            + pieces + " piece(s), " + section.nodes().size() + " nodes.");
        ItemTrackTool.report(player, section);
        ItemTrackTool.say(player, TextFormatting.DARK_GRAY,
            "Run /rcmc train " + id + " to put a train on it.");
    }

    /**
     * Where and which way a new chain starts.
     *
     * <p>One block above the clicked block and centred on it, matching {@link ItemTrackTool} so
     * track from either tool sits at the same height on the same terrain. Direction comes from
     * where the player is looking, flattened — the pieces supply every subsequent change of
     * heading and pitch themselves, so an anchor that inherited the player's pitch would start the
     * whole coaster tilted with no way to level it.</p>
     */
    private static TrackFrame anchorFrame(EntityPlayer player, BlockPos pos) {
        Vec3 position = new Vec3(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
        Vec3d look = player.getLookVec();
        double x = look.x;
        double z = look.z;
        if (x * x + z * z < 1.0e-6D) {
            // Looking straight up or down leaves no heading at all; fall back to the facing the
            // player's body is turned to, which is never degenerate.
            EnumFacing facing = player.getHorizontalFacing();
            x = facing.getDirectionVec().getX();
            z = facing.getDirectionVec().getZ();
        }
        return new TrackFrame(position, new Vec3(x, 0.0D, z).normalize(), Vec3.UP);
    }

    private static String describeSelection(PieceBuildSession session) {
        PiecePalette.Entry entry = session.selectedEntry();
        double parameter = session.selectedParameter();
        return entry.displayName(parameter) + " (" + entry.describeParameter(parameter) + ")";
    }

    private static String compass(EntityPlayer player) {
        return player.getHorizontalFacing().getName();
    }

    /** Mirrors the chain and the pending piece to its owner, for the ghost preview and the HUD. */
    public static void pushSession(EntityPlayer player, PieceBuildSession session) {
        if (player instanceof EntityPlayerMP) {
            RcmcNetwork.sendTo(new PacketPieceSessionSync(session), (EntityPlayerMP) player);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + "Right-click a block: start a chain, or add a piece");
        tooltip.add(TextFormatting.GRAY + "Sneak + right-click a block: add and finish");
        tooltip.add(TextFormatting.GRAY + "Right-click air: finish the section");
        tooltip.add(TextFormatting.GRAY + "G / Ctrl+scroll: choose the piece");
        tooltip.add(TextFormatting.GRAY + "Shift+scroll: resize the piece");
        tooltip.add(TextFormatting.GRAY + "R: undo the last piece");
    }
}
