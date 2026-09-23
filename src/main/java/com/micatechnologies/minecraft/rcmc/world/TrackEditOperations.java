package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.builder.TrackBuildSession;
import com.micatechnologies.minecraft.rcmc.item.ItemTrackEditor;
import com.micatechnologies.minecraft.rcmc.item.RcmcItems;
import com.micatechnologies.minecraft.rcmc.net.PacketTrackEdit;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.net.TrackEditView;
import com.micatechnologies.minecraft.rcmc.track.SectionRemap;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackPalette;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * The server side of the track editor screen: what each press does to the track, and the view of
 * the node it answers with.
 *
 * <p>Every change goes through {@link TrackEdits}, so a moved node carries its hardware, stations
 * and trains with it, and every change is saved and undoable like any other track edit.</p>
 */
public final class TrackEditOperations {

    /** How far from a node the editor may reach, blocks. The screen is for track in view. */
    private static final double REACH = 96.0D;

    /** Nodes a section keeps at least: two for open track, three for a circuit. */
    private static final int MIN_OPEN_NODES = 2;
    private static final int MIN_CLOSED_NODES = 3;

    private TrackEditOperations() {
    }

    /** Opens the editor on node {@code nodeIndex} of section {@code sectionId}. */
    public static void open(EntityPlayerMP player, int sectionId, int nodeIndex) {
        send(player, sectionId, nodeIndex, "");
    }

    public static void handle(EntityPlayerMP player, int sectionId, int nodeIndex,
                              PacketTrackEdit.Action action, double value) {
        RcmcWorldState state = RcmcWorldState.of(player.world);
        TrackSection section = state == null ? null : state.network().section(sectionId);
        if (section == null || !holdingEditor(player)) {
            return;
        }
        int nodes = section.nodes().size();
        int node = Math.max(0, Math.min(nodes - 1, nodeIndex));
        if (player.getDistanceSq(x(section, node), y(section, node), z(section, node)) > REACH * REACH) {
            return;
        }
        String message = "";
        switch (action) {
            case SELECT_NODE:
                node = Math.floorMod((int) Math.round(value), nodes);
                break;
            case MOVE_X:
            case MOVE_Y:
            case MOVE_Z: {
                TrackNode old = section.nodes().get(node);
                double step = clamp(value, -4.0D, 4.0D);
                Vec3 delta = action == PacketTrackEdit.Action.MOVE_X ? new Vec3(step, 0, 0)
                    : action == PacketTrackEdit.Action.MOVE_Y ? new Vec3(0, step, 0) : new Vec3(0, 0, step);
                TrackSection edited = section.withNode(node,
                    new TrackNode(old.position().add(delta), old.bankDegrees(), old.styleId()));
                TrackEdits.apply(player.world, state, edited, SectionRemap.nodeChanged(section, edited));
                break;
            }
            case BANK: {
                TrackNode old = section.nodes().get(node);
                double bank = clamp(old.bankDegrees() + clamp(value, -45.0D, 45.0D), -180.0D, 180.0D);
                TrackSection edited = section.withNode(node,
                    new TrackNode(old.position(), bank, old.styleId()));
                TrackEdits.apply(player.world, state, edited, SectionRemap.nodeChanged(section, edited));
                break;
            }
            case INSERT_AFTER: {
                TrackNode inserted = nodeAfter(section, node);
                TrackSection edited = section.withNodeInserted(node + 1, inserted);
                TrackEdits.apply(player.world, state, edited,
                    SectionRemap.nodeInserted(section, edited, node + 1));
                node = node + 1;
                message = "Node added.";
                break;
            }
            case DELETE_NODE: {
                int min = section.isClosed() ? MIN_CLOSED_NODES : MIN_OPEN_NODES;
                if (nodes <= min) {
                    message = "A " + (section.isClosed() ? "circuit" : "section") + " needs at least "
                        + min + " nodes. Delete the whole section instead.";
                    break;
                }
                TrackSection edited = section.withNodeRemoved(node);
                TrackEdits.apply(player.world, state, edited, SectionRemap.nodeRemoved(section, edited, node));
                node = Math.min(node, nodes - 2);
                message = "Node removed.";
                break;
            }
            case SET_SPAN_TYPE: {
                int spans = section.isClosed() ? nodes : nodes - 1;
                if (node >= spans) {
                    message = "The last node of open track starts no span.";
                    break;
                }
                TrackBuildSession.SegmentType[] types = TrackBuildSession.SegmentType.values();
                int ordinal = (int) Math.round(value);
                if (ordinal < 0 || ordinal >= types.length) {
                    break;
                }
                ItemTrackEditor.setSpanType(player.world, state, section, node, types[ordinal]);
                message = "Span " + (node + 1) + " is now: " + types[ordinal].label();
                break;
            }
            case CYCLE_PAINT_PART:
                ItemTrackEditor.cyclePaintPart(player);
                break;
            case CYCLE_COLOUR:
                ItemTrackEditor.select(player, sectionId, Math.min(node, Math.max(0, nodes - 2)),
                    section.nodeDistance(node));
                ItemTrackEditor.cycleSelectedColour(player, player.world);
                break;
            case DELETE_SECTION:
                if (value != 1.0D) {
                    message = "Press again to delete the whole of section #" + sectionId + ".";
                    break;
                }
                ItemTrackEditor.deleteSection(player, player.world, sectionId);
                // Nothing left to show.
                return;
            case REFRESH:
            default:
                break;
        }
        send(player, sectionId, node, message);
    }

    /**
     * The node a press of "insert" adds after {@code index}: halfway along the span to the next
     * node, level with the track there — or, past the end of open track, the same distance on again.
     */
    static TrackNode nodeAfter(TrackSection section, int index) {
        int nodes = section.nodes().size();
        TrackNode here = section.nodes().get(index);
        if (index + 1 >= nodes && !section.isClosed()) {
            TrackNode before = section.nodes().get(index - 1);
            Vec3 step = here.position().subtract(before.position());
            return new TrackNode(here.position().add(step), here.bankDegrees(), null);
        }
        TrackNode next = section.nodes().get((index + 1) % nodes);
        double from = section.nodeDistance(index);
        double to = index + 1 < nodes ? section.nodeDistance(index + 1) : section.totalLength();
        return new TrackNode(section.positionAtDistance((from + to) * 0.5D),
            (here.bankDegrees() + next.bankDegrees()) * 0.5D, null);
    }

    private static void send(EntityPlayerMP player, int sectionId, int nodeIndex, String message) {
        RcmcWorldState state = RcmcWorldState.of(player.world);
        TrackSection section = state == null ? null : state.network().section(sectionId);
        if (section == null) {
            return;
        }
        int nodes = section.nodes().size();
        int node = Math.max(0, Math.min(nodes - 1, nodeIndex));
        TrackNode n = section.nodes().get(node);
        int spans = section.isClosed() ? nodes : nodes - 1;
        int spanType = node < spans ? ItemTrackEditor.spanTypeOf(state, section, node).ordinal() : -1;
        TrackPalette.Part part = ItemTrackEditor.paintPartOf(player);
        RcmcNetwork.sendTo(new PacketTrackEdit.View(new TrackEditView(sectionId, node, nodes,
            section.isClosed(), section.totalLength(), n.position().x, n.position().y, n.position().z,
            n.bankDegrees(), spanType, part.ordinal(), section.palette().of(part).label(), message)), player);
    }

    private static boolean holdingEditor(EntityPlayerMP player) {
        return player.getHeldItemMainhand().getItem() == RcmcItems.trackEditor
            || player.getHeldItemOffhand().getItem() == RcmcItems.trackEditor;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double x(TrackSection s, int node) {
        return s.nodes().get(node).position().x;
    }

    private static double y(TrackSection s, int node) {
        return s.nodes().get(node).position().y;
    }

    private static double z(TrackSection s, int node) {
        return s.nodes().get(node).position().z;
    }
}
