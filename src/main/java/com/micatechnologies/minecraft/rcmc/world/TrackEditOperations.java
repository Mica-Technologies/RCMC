package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.builder.TrackBuildSession;
import com.micatechnologies.minecraft.rcmc.item.ItemTrackEditor;
import com.micatechnologies.minecraft.rcmc.item.RcmcItems;
import com.micatechnologies.minecraft.rcmc.net.PacketTrackEdit;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.net.TrackEditView;
import com.micatechnologies.minecraft.rcmc.track.SectionRemap;
import com.micatechnologies.minecraft.rcmc.track.SectionSurgery;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackStyleIds;
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
        send(player, sectionId, nodeIndex, "", true);
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
            case SPLIT: {
                if (!canSplit(section, node)) {
                    message = "Split at an inner node: an end node is already the end of the section.";
                    break;
                }
                // Peeked, not allocated: a refused split must not use an id up. Adding the new
                // section is what claims it.
                int newId = state.network().nextSectionId();
                while (state.network().hasSection(newId)) {
                    newId++;
                }
                SectionSurgeries.Outcome outcome = SectionSurgeries.apply(player.world, state,
                    SectionSurgery.split(section, node, newId), section.isClosed()
                        ? "Circuit opened at node " + (node + 1) + "; its ends are joined."
                        : "Split into #" + sectionId + " and #" + newId + ", joined at node " + (node + 1) + ".");
                message = outcome.message;
                if (outcome.applied && section.isClosed()) {
                    node = 0;
                }
                break;
            }
            case JOIN: {
                JoinTarget target = joinTarget(state.network(), section, node);
                if (target == null) {
                    message = "No end in reach to join. Bring another section's end within "
                        + (int) SectionSurgery.MERGE_REACH + " blocks of this one.";
                    break;
                }
                if (target.other == null) {
                    SectionSurgeries.Outcome outcome = SectionSurgeries.apply(player.world, state,
                        SectionSurgery.close(section), "Closed into a circuit.");
                    message = outcome.message;
                    if (outcome.applied) {
                        node = 0;
                    }
                    break;
                }
                String ours = TrackStyleIds.label(section.styleId());
                String theirs = TrackStyleIds.label(target.other.styleId());
                if (!ours.equals(theirs)) {
                    message = "#" + sectionId + " is " + ours + " track and #" + target.other.id() + " is "
                        + theirs + ". A section has one style; restyle one to match first.";
                    break;
                }
                SectionSurgeries.Outcome outcome = SectionSurgeries.apply(player.world, state,
                    SectionSurgery.merge(section, target.end, target.other, target.otherEnd),
                    "#" + target.other.id() + " merged into #" + sectionId + ".");
                message = outcome.message;
                if (outcome.applied) {
                    // The node where they met: after this section's nodes, or after the other's.
                    node = target.end == TrackNetwork.End.END
                        ? nodes - 1 : target.other.nodes().size() - 1;
                }
                break;
            }
            case REVERSE: {
                SectionSurgeries.Outcome outcome = SectionSurgeries.apply(player.world, state,
                    SectionSurgery.reverse(section), "Section #" + sectionId + " now runs the other way.");
                message = outcome.message;
                if (outcome.applied) {
                    node = nodes - 1 - node;
                }
                break;
            }
            case CYCLE_STYLE: {
                String next = TrackStyleIds.next(section.styleId());
                // replaceSection keeps joins and switches; a fresh instance also invalidates the
                // client mesh cache, which keys on section identity.
                state.network().replaceSection(section.withStyle(next));
                state.markTrackDirty(player.world);
                RcmcNetwork.sendToAllIn(new com.micatechnologies.minecraft.rcmc.net.PacketTrackSync(state.network()),
                    player.world.provider.getDimension());
                message = "Style: " + TrackStyleIds.label(next) + ".";
                break;
            }
            case REFRESH:
            default:
                break;
        }
        send(player, sectionId, node, message);
    }

    /** Where joining at a node would go: another section's end, or ({@code other} null) this one's own. */
    static final class JoinTarget {
        final TrackNetwork.End end;
        final TrackSection other;
        final TrackNetwork.End otherEnd;

        JoinTarget(TrackNetwork.End end, TrackSection other, TrackNetwork.End otherEnd) {
            this.end = end;
            this.other = other;
            this.otherEnd = otherEnd;
        }

        String label() {
            return other == null ? "Close circuit" : "Join to #" + other.id();
        }
    }

    /**
     * The nearest end in reach of end node {@code node} of open {@code section}: its own other end,
     * which closes it, or another open section's. {@code null} for an inner node, a circuit, or
     * nothing close enough.
     */
    static JoinTarget joinTarget(TrackNetwork network, TrackSection section, int node) {
        int nodes = section.nodes().size();
        if (section.isClosed() || (node != 0 && node != nodes - 1)) {
            return null;
        }
        TrackNetwork.End end = node == 0 ? TrackNetwork.End.START : TrackNetwork.End.END;
        TrackNetwork.End own = end == TrackNetwork.End.START ? TrackNetwork.End.END : TrackNetwork.End.START;
        JoinTarget best = null;
        double bestGap = Double.MAX_VALUE;
        if (nodes >= 4 && SectionSurgery.inReach(section, end, section, own)) {
            best = new JoinTarget(end, null, own);
            bestGap = section.endpointAt(end).distanceTo(section.endpointAt(own));
        }
        for (TrackSection other : network.sections()) {
            if (other.id() == section.id() || other.isClosed()) {
                continue;
            }
            for (TrackNetwork.End otherEnd : TrackNetwork.End.values()) {
                double gap = section.endpointAt(end).distanceTo(other.endpointAt(otherEnd));
                if (gap <= SectionSurgery.MERGE_REACH && gap < bestGap) {
                    best = new JoinTarget(end, other, otherEnd);
                    bestGap = gap;
                }
            }
        }
        return best;
    }

    static boolean canSplit(TrackSection section, int node) {
        int nodes = section.nodes().size();
        return section.isClosed() ? nodes >= 3 : node > 0 && node < nodes - 1;
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
        send(player, sectionId, nodeIndex, message, false);
    }

    /** {@code open}: whether this view may open the screen, or only refresh one already open. */
    private static void send(EntityPlayerMP player, int sectionId, int nodeIndex, String message,
                             boolean open) {
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
        JoinTarget target = joinTarget(state.network(), section, node);
        RcmcNetwork.sendTo(new PacketTrackEdit.View(new TrackEditView(sectionId, node, nodes,
            section.isClosed(), section.totalLength(), n.position().x, n.position().y, n.position().z,
            n.bankDegrees(), spanType, part.ordinal(), section.palette().of(part).label(), message,
            canSplit(section, node), target == null ? "" : target.label(),
            TrackStyleIds.label(section.styleId())), open), player);
        // Rechecked with every view, so what the editor shows is the ride as it is after this edit.
        RcmcNetwork.sendTo(new com.micatechnologies.minecraft.rcmc.net.PacketRideCheck(
            RideChecks.forRide(state, sectionId)), player);
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
