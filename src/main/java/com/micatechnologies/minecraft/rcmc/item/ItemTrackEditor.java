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

    /**
     * How far down the player's look ray to search for track.
     *
     * <p>Well beyond vanilla reach on purpose. There is no block to interact with, so nothing here
     * is bounded by reach; a builder looking at a lift hill from the ground is pointing at it just
     * as unambiguously from thirty blocks as from three.</p>
     */
    private static final double LOOK_RANGE = 64.0D;

    /** How far off the look ray track may sit and still count as aimed at. */
    private static final double AIM_RADIUS = 1.5D;

    /** Which part a colour change applies to; cycled independently of the colour itself. */
    private static final Map<UUID, com.micatechnologies.minecraft.rcmc.track.TrackPalette.Part>
        PAINT_PART = new HashMap<>();

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

        // The look ray first: the block that was clicked is whatever happened to be BEHIND the
        // track, so the ray is the thing that actually describes what the player was pointing at.
        TrackPicker.Hit hit = pickAlongLook(player, state);
        if (hit == null) {
            Vec3 query = new Vec3(pos.getX() + hitX, pos.getY() + hitY, pos.getZ() + hitZ);
            hit = TrackPicker.pick(state.network(), query, PICK_RADIUS);
        }
        if (hit == null) {
            say(player, TextFormatting.GRAY, "No track under the cursor, and none within "
                + (int) PICK_RADIUS + " blocks of where you clicked.");
            return EnumActionResult.SUCCESS;
        }
        apply(player, world, state, hit);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (world.isRemote) {
            return new ActionResult<>(EnumActionResult.SUCCESS, held);
        }
        RcmcWorldState state = RcmcWorldState.of(world);

        // Track is rendered geometry with no block behind it, so aiming at track hanging in the air
        // is a MISS as far as vanilla is concerned and arrives here rather than at onItemUse. This
        // handler doing nothing but clear the selection was the whole of "the wand does nothing".
        TrackPicker.Hit hit = pickAlongLook(player, state);
        if (hit != null) {
            apply(player, world, state, hit);
            return new ActionResult<>(EnumActionResult.SUCCESS, held);
        }
        if (player.isSneaking()) {
            SELECTIONS.remove(player.getUniqueID());
            say(player, TextFormatting.GRAY, "Selection cleared.");
        }
        else {
            say(player, TextFormatting.GRAY, "No track under the cursor.");
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, held);
    }

    /** Selects what was pointed at, or deletes its section when sneaking. */
    private static void apply(EntityPlayer player, World world, RcmcWorldState state,
                              TrackPicker.Hit hit) {
        TrackSection section = state.network().section(hit.ref.sectionId());
        if (section == null) {
            return;
        }
        if (player.isSneaking()) {
            deleteSection(player, world, state, section);
            return;
        }
        int span = TrackPicker.spanIndexAt(section, hit.ref.distance());
        SELECTIONS.put(player.getUniqueID(), new Selection(section.id(), span, hit.ref.distance()));
        report(player, state, section, hit.ref.distance(), span);
    }

    /**
     * What the player is pointing at, by casting their look ray against the track itself.
     *
     * <p>Run server-side, where the selection lives; the server tracks the player's rotation from
     * their movement packets, so the ray is the same one the client drew its crosshair along.</p>
     */
    private static TrackPicker.Hit pickAlongLook(EntityPlayer player, RcmcWorldState state) {
        net.minecraft.util.math.Vec3d eyes = player.getPositionEyes(1.0F);
        net.minecraft.util.math.Vec3d look = player.getLookVec();
        return TrackPicker.pickAlongRay(state.network(),
            new Vec3(eyes.x, eyes.y, eyes.z), new Vec3(look.x, look.y, look.z),
            LOOK_RANGE, AIM_RADIUS);
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

    /**
     * Repaints one part of the selected section, cycling that part's colour.
     *
     * <p>Colour is a property of the whole section rather than of a span. Track is painted end to
     * end in every park worth looking at, and per-span colour would let a builder produce a
     * patchwork by accident — the thing a fixed palette exists to prevent.</p>
     */
    public static void cycleSelectedColour(EntityPlayer player, World world) {
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

        com.micatechnologies.minecraft.rcmc.track.TrackPalette.Part part =
            PAINT_PART.getOrDefault(player.getUniqueID(),
                com.micatechnologies.minecraft.rcmc.track.TrackPalette.Part.RAIL);
        com.micatechnologies.minecraft.rcmc.track.TrackPalette.Colour next =
            section.palette().of(part).next();

        // replaceSection keeps the section's joins, which a remove-and-add would silently drop.
        state.network().replaceSection(section.withPalette(section.palette().with(part, next)));
        state.markTrackDirty(world);
        broadcast(world, state);
        say(player, TextFormatting.GREEN, part.name().toLowerCase(java.util.Locale.ROOT)
            + " -> " + next.label());
    }

    /** Switches which part subsequent colour changes apply to. */
    public static void cyclePaintPart(EntityPlayer player) {
        com.micatechnologies.minecraft.rcmc.track.TrackPalette.Part[] parts =
            com.micatechnologies.minecraft.rcmc.track.TrackPalette.Part.values();
        com.micatechnologies.minecraft.rcmc.track.TrackPalette.Part current =
            PAINT_PART.getOrDefault(player.getUniqueID(), parts[0]);
        com.micatechnologies.minecraft.rcmc.track.TrackPalette.Part next =
            parts[(current.ordinal() + 1) % parts.length];
        PAINT_PART.put(player.getUniqueID(), next);
        say(player, TextFormatting.AQUA, "Painting: " + next.name().toLowerCase(java.util.Locale.ROOT));
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
        tooltip.add(TextFormatting.GRAY + "Right-click while looking at track: select a span");
        tooltip.add(TextFormatting.GRAY + "G: cycle that span's segment type");
        tooltip.add(TextFormatting.GRAY + "Sneak + right-click track: delete the section");
        tooltip.add(TextFormatting.GRAY + "C: cycle that part's colour");
        tooltip.add(TextFormatting.GRAY + "V: choose which part to paint");
        tooltip.add(TextFormatting.GRAY + "Sneak + right-click air: clear selection");
    }
}
