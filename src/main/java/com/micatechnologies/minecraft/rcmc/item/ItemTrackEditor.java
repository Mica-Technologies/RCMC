package com.micatechnologies.minecraft.rcmc.item;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.RcmcTab;
import com.micatechnologies.minecraft.rcmc.builder.TrackBuildSession;
import com.micatechnologies.minecraft.rcmc.net.PacketElementSync;
import com.micatechnologies.minecraft.rcmc.net.PacketTrackSync;
import com.micatechnologies.minecraft.rcmc.net.PacketTrainRemove;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.track.TrackPicker;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.track.storage.ElementCodec;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * Edits track that already exists, so a coaster does not have to be rebuilt to change one thing.
 *
 * <p>Controls:</p>
 * <ul>
 *   <li><b>Right-click near track</b> — select it, and report what is there.</li>
 *   <li><b>G</b> (with a selection) — cycle the segment type of the span you selected.</li>
 *   <li><b>Sneak + right-click near track</b> — delete that whole section and its hardware.</li>
 * </ul>
 *
 * <p>Edits work on <em>spans</em> — the stretch between two placed nodes — rather than at a point.
 * A builder retyping "this bit of track" means the piece they can see, not an infinitesimal
 * position, and a span is the smallest thing that has a length for an element to occupy.</p>
 */
public class ItemTrackEditor extends Item {

    public static final String NAME = "track_editor";

    /**
     * How far from track a click may land and still select it.
     *
     * <p>Generous, because track is thin and a player is pointing at it from a distance. The
     * failure mode of being too tight is a tool that appears not to work; too loose merely selects
     * something a click away, which the report line makes obvious.</p>
     */
    private static final double PICK_RADIUS = 4.0D;

    /** Per-player selection. Server-side scratch state, like the build session. */
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

    /** A selected span: which section, and which node-to-node stretch of it. */
    public static final class Selection {

        public final int sectionId;
        public final int spanIndex;
        public final double distance;

        Selection(int sectionId, int spanIndex, double distance) {
            this.sectionId = sectionId;
            this.spanIndex = spanIndex;
            this.distance = distance;
        }
    }

    public ItemTrackEditor() {
        setRegistryName(RcmcConstants.MOD_NAMESPACE, NAME);
        setTranslationKey(RcmcConstants.MOD_NAMESPACE + "." + NAME);
        setCreativeTab(RcmcTab.RCMC_TAB);
        setMaxStackSize(1);
    }

    public static Selection selectionOf(EntityPlayer player) {
        return SELECTIONS.get(player.getUniqueID());
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }
        RcmcWorldState state = RcmcWorldState.of(world);
        Vec3 query = new Vec3(pos.getX() + 0.5D + hitX - 0.5D,
            pos.getY() + hitY, pos.getZ() + 0.5D + hitZ - 0.5D);

        TrackPicker.Hit hit = TrackPicker.pick(state.network(), query, PICK_RADIUS);
        if (hit == null) {
            say(player, TextFormatting.GRAY, "No track within " + (int) PICK_RADIUS + " blocks.");
            return EnumActionResult.SUCCESS;
        }
        TrackSection section = state.network().section(hit.ref.sectionId());

        if (player.isSneaking()) {
            deleteSection(player, world, state, section);
            return EnumActionResult.SUCCESS;
        }

        int span = TrackPicker.spanIndexAt(section, hit.ref.distance());
        SELECTIONS.put(player.getUniqueID(), new Selection(section.id(), span, hit.ref.distance()));
        report(player, state, section, hit.ref.distance(), span);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (!world.isRemote && player.isSneaking()) {
            SELECTIONS.remove(player.getUniqueID());
            say(player, TextFormatting.GRAY, "Selection cleared.");
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, held);
    }

    /**
     * Replaces the hardware on the selected span with the next type in the cycle.
     *
     * <p>Any element overlapping the span is removed first. Leaving the old one in place would
     * stack a brake on top of a lift, and {@code RideElementSet} resolves overlap by insertion
     * order — so the result would depend on the order edits happened to be made, which is not
     * something a builder can reason about.</p>
     */
    public static void cycleSelectedType(EntityPlayer player, World world) {
        RcmcWorldState state = RcmcWorldState.of(world);
        Selection selection = SELECTIONS.get(player.getUniqueID());
        if (selection == null) {
            say(player, TextFormatting.GRAY, "Select a piece of track first.");
            return;
        }
        TrackSection section = state.network().section(selection.sectionId);
        if (section == null) {
            SELECTIONS.remove(player.getUniqueID());
            say(player, TextFormatting.RED, "That section no longer exists.");
            return;
        }

        double from = section.nodeDistance(selection.spanIndex);
        double to = selection.spanIndex + 1 < section.nodes().size()
            ? section.nodeDistance(selection.spanIndex + 1)
            : section.totalLength();

        TrackBuildSession.SegmentType next = nextTypeFor(state, section.id(), from, to);
        removeOverlapping(state, section.id(), from, to);

        RideElement created = com.micatechnologies.minecraft.rcmc.builder.SegmentElements
            .build(section, spanTypes(section, selection.spanIndex, next))
            .stream().findFirst().orElse(null);
        if (created != null) {
            state.elements().add(created);
        }

        state.markTrackDirty(world);
        broadcast(world, state);
        say(player, TextFormatting.GREEN, "Span " + selection.spanIndex + " of section "
            + section.id() + " is now: " + next.label());
    }

    /**
     * A type list that tags only the selected span, so {@code SegmentElements} produces exactly one
     * element for it — reusing the builder's mapping rather than duplicating the type-to-element
     * table here, where the two could drift apart.
     */
    private static List<TrackBuildSession.SegmentType> spanTypes(TrackSection section, int spanIndex,
                                                                 TrackBuildSession.SegmentType type) {
        List<TrackBuildSession.SegmentType> types = new ArrayList<>();
        for (int i = 0; i < section.nodes().size(); i++) {
            types.add(i == spanIndex ? type : TrackBuildSession.SegmentType.PLAIN);
        }
        return types;
    }

    /** The type after whatever currently occupies the span, so repeated presses cycle. */
    private static TrackBuildSession.SegmentType nextTypeFor(RcmcWorldState state, int sectionId,
                                                             double from, double to) {
        for (RideElement element : state.elements().elements()) {
            if (element.sectionId() != sectionId || element.endDistance() <= from
                || element.startDistance() >= to) {
                continue;
            }
            String type = ElementCodec.typeOf(element);
            if ("chain_lift".equals(type)) {
                return TrackBuildSession.SegmentType.BRAKE;
            }
            if ("brake".equals(type)) {
                return TrackBuildSession.SegmentType.STATION;
            }
            if ("station".equals(type)) {
                return TrackBuildSession.SegmentType.PLAIN;
            }
        }
        return TrackBuildSession.SegmentType.LIFT;
    }

    private static void removeOverlapping(RcmcWorldState state, int sectionId,
                                          double from, double to) {
        for (RideElement element : new ArrayList<>(state.elements().elements())) {
            if (element.sectionId() == sectionId
                && element.endDistance() > from && element.startDistance() < to) {
                state.elements().remove(element);
            }
        }
    }

    private static void deleteSection(EntityPlayer player, World world, RcmcWorldState state,
                                      TrackSection section) {
        for (RideElement element : new ArrayList<>(state.elements().elements())) {
            if (element.sectionId() == section.id()) {
                state.elements().remove(element);
            }
        }
        state.network().removeSection(section.id());

        // Trains on a deleted section would be orphaned — they survive it safely, but a stranded
        // train nobody can reach is clutter, and the builder deleting the track clearly means the
        // ride to go with it.
        List<Integer> orphaned = new ArrayList<>();
        for (Map.Entry<Integer, com.micatechnologies.minecraft.rcmc.physics.Train> entry
            : state.trains().asMap().entrySet()) {
            if (entry.getValue().reference().sectionId() == section.id()) {
                orphaned.add(entry.getKey());
            }
        }
        for (Integer trainId : orphaned) {
            state.trains().remove(trainId);
            RcmcNetwork.sendToAllIn(new PacketTrainRemove(trainId), world.provider.getDimension());
        }
        for (com.micatechnologies.minecraft.rcmc.entity.EntityCoasterCar car
            : new ArrayList<>(world.getEntities(
                com.micatechnologies.minecraft.rcmc.entity.EntityCoasterCar.class,
                entity -> entity != null && orphaned.contains(entity.trainId())))) {
            car.setDead();
        }

        SELECTIONS.remove(player.getUniqueID());
        state.markTrackDirty(world);
        broadcast(world, state);
        say(player, TextFormatting.YELLOW, "Deleted section " + section.id()
            + (orphaned.isEmpty() ? "." : " and " + orphaned.size() + " train(s) on it."));
    }

    private static void report(EntityPlayer player, RcmcWorldState state, TrackSection section,
                               double distance, int span) {
        say(player, TextFormatting.AQUA, "Selected section " + section.id() + ", span " + span
            + " at " + String.format("%.1f", distance) + " of "
            + String.format("%.1f", section.totalLength()) + " blocks"
            + (section.isClosed() ? " (circuit)" : ""));

        String here = "plain track";
        for (RideElement element : state.elements().elements()) {
            if (element.sectionId() == section.id() && element.contains(
                new com.micatechnologies.minecraft.rcmc.track.TrackRef(section.id(), distance))) {
                String type = ElementCodec.typeOf(element);
                here = type == null ? "unknown hardware" : type;
                break;
            }
        }
        say(player, TextFormatting.GRAY, "  Here: " + here
            + "   Bank: " + String.format("%.0f", Math.toDegrees(section.bankRadiansAt(distance)))
            + "°");
        say(player, TextFormatting.DARK_GRAY, "  G to change type, sneak+click to delete section.");
    }

    private static void broadcast(World world, RcmcWorldState state) {
        int dimension = world.provider.getDimension();
        RcmcNetwork.sendToAllIn(new PacketTrackSync(state.network()), dimension);
        RcmcNetwork.sendToAllIn(new PacketElementSync(state.elements()), dimension);
    }

    private static void say(EntityPlayer player, TextFormatting colour, String message) {
        player.sendMessage(new TextComponentString(colour + message));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + "Right-click track: select a span");
        tooltip.add(TextFormatting.GRAY + "G: cycle that span's segment type");
        tooltip.add(TextFormatting.GRAY + "Sneak + right-click track: delete the section");
        tooltip.add(TextFormatting.GRAY + "Sneak + right-click air: clear selection");
    }
}
