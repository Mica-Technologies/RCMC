package com.micatechnologies.minecraft.rcmc.command;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.builder.TrackBuildSession.SegmentType;
import com.micatechnologies.minecraft.rcmc.item.ItemTrackEditor;
import com.micatechnologies.minecraft.rcmc.physics.element.BrakeRun;
import com.micatechnologies.minecraft.rcmc.physics.element.LaunchTrack;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideTuning;
import com.micatechnologies.minecraft.rcmc.track.Polyline;
import com.micatechnologies.minecraft.rcmc.track.SectionRemap;
import com.micatechnologies.minecraft.rcmc.track.SupportExclusion;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.track.storage.ElementCodec;
import com.micatechnologies.minecraft.rcmc.world.ClearanceChecks;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import com.micatechnologies.minecraft.rcmc.world.RideChecks;
import com.micatechnologies.minecraft.rcmc.world.TrackEditOperations;
import com.micatechnologies.minecraft.rcmc.world.TrackEdits;
import com.micatechnologies.minecraft.rcmc.world.TrackSupports;
import com.micatechnologies.minecraft.rcmc.net.PacketTrackSync;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * Changing a ride after it is laid, from a command: {@code /rcmc node} for its geometry,
 * {@code /rcmc element} for its hardware's settings, and {@code /rcmc clearance} for what stands in
 * the train's way.
 *
 * <p>Each is the command form of something a player can already do by hand — the track editor
 * screen, the operator panel — and goes through the same code, so a node moved here carries its
 * hardware and trains with it and a lift tuned here is the lift the panel shows. What the commands
 * add is that a script, or a console with no player in the world, can do it, and read back what it
 * did in text it can parse.</p>
 *
 * <p>Nodes are numbered from 1, as {@code /rcmc check} and the editor screen number them, so
 * "lateral G at node 14" is fixed with {@code /rcmc node <id> set 14 ...}.</p>
 */
final class EditCommands {

    /** How far from a coordinate the node it names may be, blocks. */
    private static final double NODE_REACH = 8.0D;

    /** Sections longer than this are not swept after {@code track lay}: say how to, instead. */
    static final double AUTO_CLEARANCE_MAX_LENGTH = 3000.0D;

    /** Intrusions listed by {@code /rcmc clearance} unless asked for more. */
    private static final int DEFAULT_LISTED = 20;

    private EditCommands() {
        throw new AssertionError("No instances.");
    }

    // ---- /rcmc node ----

    static String nodeUsage() {
        return "/rcmc node <sectionId> list | set <n|x,y,z> [pos=x,y,z] [move=dx,dy,dz] [bank=deg]"
            + " [type=<segment>] | insert after <n|x,y,z> [x,y,z[,bank]] | delete <n|x,y,z>";
    }

    static void node(ICommandSender sender, World world, RcmcWorldState state, String[] args)
        throws CommandException {
        if (args.length < 3) {
            throw new CommandException(nodeUsage());
        }
        TrackSection section = section(state, args[1]);
        String sub = args[2].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list":
                listNodes(sender, state, section);
                return;
            case "set":
                setNode(sender, world, state, section, args);
                return;
            case "insert":
                insertNode(sender, world, state, section, args);
                return;
            case "delete":
                deleteNode(sender, world, state, section, args);
                return;
            default:
                throw new CommandException(nodeUsage());
        }
    }

    private static void listNodes(ICommandSender sender, RcmcWorldState state, TrackSection section) {
        int nodes = section.nodes().size();
        reply(sender, TextFormatting.AQUA, "Section #" + section.id() + " - "
            + (section.isClosed() ? "circuit" : "open") + ", " + nodes + " nodes (numbered from 1), "
            + fmt1(section.totalLength()) + " blocks");
        for (int i = 0; i < nodes; i++) {
            TrackNode node = section.nodes().get(i);
            boolean startsSpan = i < spans(section);
            reply(sender, TextFormatting.GRAY, "  " + (i + 1) + ": " + Polyline.format(node)
                + "  @ " + fmt2(section.nodeDistance(i))
                + (startsSpan ? "  span " + typeName(ItemTrackEditor.spanTypeOf(state, section, i)) : "")
                + (node.styleId() == null ? "" : "  style " + node.styleId()));
        }
    }

    private static void setNode(ICommandSender sender, World world, RcmcWorldState state, TrackSection section,
                                String[] args) throws CommandException {
        if (args.length < 5) {
            throw new CommandException("/rcmc node <sectionId> set <n|x,y,z> [pos=x,y,z] [move=dx,dy,dz]"
                + " [bank=deg] [type=<segment>]");
        }
        int index = nodeIndex(section, args[3]);
        Map<String, String> values = keyValues(args, 4, "pos", "move", "bank", "type");
        if (values.containsKey("pos") && values.containsKey("move")) {
            throw new CommandException("Give pos= or move=, not both");
        }
        TrackNode old = section.nodes().get(index);
        Vec3 position = old.position();
        if (values.containsKey("pos")) {
            position = vector(values.get("pos"), "pos");
        }
        if (values.containsKey("move")) {
            position = position.add(vector(values.get("move"), "move"));
        }
        double bank = old.bankDegrees();
        if (values.containsKey("bank")) {
            bank = CommandBase.parseDouble(values.get("bank"), -180.0D, 180.0D);
        }
        SegmentType type = null;
        if (values.containsKey("type")) {
            if (index >= spans(section)) {
                throw new CommandException("Node " + (index + 1) + " is the end of open track; no span starts"
                    + " there for type= to set");
            }
            type = TrackCommands.segmentType(values.get("type"));
        }

        boolean moves = !position.equals(old.position()) || bank != old.bankDegrees();
        // Only a real change of type re-lays the span: laying the same type again would put back
        // default hardware and lose whatever the span's lift or brake was tuned to.
        boolean retypes = type != null && type != ItemTrackEditor.spanTypeOf(state, section, index);
        if (!moves && !retypes) {
            reply(sender, TextFormatting.GRAY, "Node " + (index + 1) + " of #" + section.id()
                + " is already like that; nothing changed.");
            return;
        }
        final Vec3 newPosition = position;
        final double newBank = bank;
        final SegmentType newType = type;
        state.asOneEdit(world, () -> {
            TrackSection current = section;
            if (moves) {
                current = section.withNode(index, new TrackNode(newPosition, newBank, old.styleId()));
                TrackEdits.apply(world, state, current, SectionRemap.nodeChanged(section, current));
            }
            if (retypes) {
                ItemTrackEditor.setSpanType(world, state, state.network().section(section.id()), index, newType);
            }
        });
        TrackSection edited = state.network().section(section.id());
        reply(sender, TextFormatting.GREEN, "Node " + (index + 1) + " of #" + section.id() + " is now "
            + Polyline.format(edited.nodes().get(index))
            + (index < spans(edited) ? ", span " + typeName(ItemTrackEditor.spanTypeOf(state, edited, index)) : "")
            + ". Section is " + fmt1(edited.totalLength()) + " blocks.");
        afterEdit(sender, world, state, edited);
    }

    private static void insertNode(ICommandSender sender, World world, RcmcWorldState state,
                                   TrackSection section, String[] args) throws CommandException {
        if (args.length < 5 || !"after".equalsIgnoreCase(args[3])) {
            throw new CommandException("/rcmc node <sectionId> insert after <n|x,y,z> [x,y,z[,bank]]");
        }
        int index = nodeIndex(section, args[4]);
        TrackNode inserted;
        if (args.length > 5) {
            List<Polyline.Point> points;
            try {
                points = Polyline.parse(String.join(" ", Arrays.copyOfRange(args, 5, args.length)));
            }
            catch (IllegalArgumentException e) {
                throw new CommandException(e.getMessage());
            }
            if (points.size() != 1 || points.get(0).type != null) {
                throw new CommandException("insert takes one node, x,y,z or x,y,z,bank; set its span's type"
                    + " afterwards with /rcmc node " + section.id() + " set " + (index + 2) + " type=...");
            }
            inserted = new TrackNode(points.get(0).position, points.get(0).bankDegrees, null);
        }
        else {
            // As the editor screen's "insert": halfway along the span, on the track as it runs now.
            inserted = TrackEditOperations.nodeAfter(section, index);
        }
        TrackSection edited = section.withNodeInserted(index + 1, inserted);
        TrackEdits.apply(world, state, edited, SectionRemap.nodeInserted(section, edited, index + 1));
        reply(sender, TextFormatting.GREEN, "Inserted node " + (index + 2) + " of #" + section.id() + " at "
            + Polyline.format(inserted) + "; the nodes after it are renumbered. Section is "
            + fmt1(edited.totalLength()) + " blocks, " + edited.nodes().size() + " nodes.");
        afterEdit(sender, world, state, edited);
    }

    private static void deleteNode(ICommandSender sender, World world, RcmcWorldState state,
                                   TrackSection section, String[] args) throws CommandException {
        if (args.length < 4) {
            throw new CommandException("/rcmc node <sectionId> delete <n|x,y,z>");
        }
        int index = nodeIndex(section, args[3]);
        int min = section.isClosed() ? 3 : 2;
        if (section.nodes().size() <= min) {
            throw new CommandException("A " + (section.isClosed() ? "circuit" : "section") + " needs at least "
                + min + " nodes; remove the whole section with /rcmc rmsection " + section.id());
        }
        TrackSection edited = section.withNodeRemoved(index);
        TrackEdits.apply(world, state, edited, SectionRemap.nodeRemoved(section, edited, index));
        reply(sender, TextFormatting.GREEN, "Deleted node " + (index + 1) + " of #" + section.id()
            + "; the nodes after it are renumbered. Section is " + fmt1(edited.totalLength()) + " blocks, "
            + edited.nodes().size() + " nodes.");
        afterEdit(sender, world, state, edited);
    }

    /** What a builder wants to know straight after changing geometry: is the ride still good. */
    private static void afterEdit(ICommandSender sender, World world, RcmcWorldState state, TrackSection edited) {
        List<com.micatechnologies.minecraft.rcmc.rating.RideWarning> warnings =
            RideChecks.forRide(state, edited.id());
        if (!warnings.isEmpty()) {
            reply(sender, TextFormatting.YELLOW, "  Ride check: " + warnings.size()
                + (warnings.size() == 1 ? " warning" : " warnings") + " - /rcmc check " + edited.id());
        }
        clearanceSummary(sender, world, edited);
    }

    /** {@code n} (from 1) or {@code x,y,z} (the nearest node), as an index from 0. */
    private static int nodeIndex(TrackSection section, String arg) throws CommandException {
        int nodes = section.nodes().size();
        if (arg.indexOf(',') >= 0) {
            Vec3 point = vector(arg, "node");
            int best = -1;
            double bestDistance = Double.MAX_VALUE;
            for (int i = 0; i < nodes; i++) {
                double d = section.nodes().get(i).position().distanceTo(point);
                if (d < bestDistance) {
                    best = i;
                    bestDistance = d;
                }
            }
            if (best < 0 || bestDistance > NODE_REACH) {
                throw new CommandException("No node of #" + section.id() + " within " + (int) NODE_REACH
                    + " blocks of " + arg);
            }
            return best;
        }
        int n = CommandBase.parseInt(arg);
        if (n < 1 || n > nodes) {
            throw new CommandException("Section #" + section.id() + " has nodes 1 to " + nodes + "; " + n
                + " is not one of them");
        }
        return n - 1;
    }

    private static int spans(TrackSection section) {
        int nodes = section.nodes().size();
        return section.isClosed() ? nodes : nodes - 1;
    }

    private static String typeName(SegmentType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    // ---- /rcmc element ----

    static String elementUsage() {
        return "/rcmc element <sectionId> list | set <n | at <sectionId> <distance> | near <x> <y> <z>>"
            + " [speed=<b/s>] [target=<b/s>] [force=<b/s2>] [dwell=<s>] [passes=<n>]";
    }

    /** One piece of a ride's hardware, as {@code /rcmc element list} numbers it. */
    private static final class Hardware {
        final int elementIndex;
        final RideElement element;
        final List<RideTuning.Setting> settings = new ArrayList<>();

        Hardware(int elementIndex, RideElement element) {
            this.elementIndex = elementIndex;
            this.element = element;
        }
    }

    static void element(ICommandSender sender, World world, RcmcWorldState state, String[] args)
        throws CommandException {
        if (args.length < 3) {
            throw new CommandException(elementUsage());
        }
        TrackSection section = section(state, args[1]);
        List<Hardware> hardware = hardwareOf(state, section.id());
        String sub = args[2].toLowerCase(Locale.ROOT);
        if ("list".equals(sub)) {
            listHardware(sender, state, section, hardware);
            return;
        }
        if (!"set".equals(sub) || args.length < 5) {
            throw new CommandException(elementUsage());
        }
        if (hardware.isEmpty()) {
            throw new CommandException("The ride #" + section.id() + " is part of has no adjustable hardware");
        }
        int consumed;
        Hardware target;
        if (isInteger(args[3]) && !isNumber(args[4])) {
            int n = CommandBase.parseInt(args[3]);
            if (n < 1 || n > hardware.size()) {
                throw new CommandException("The ride has hardware 1 to " + hardware.size() + "; see /rcmc element "
                    + section.id() + " list");
            }
            target = hardware.get(n - 1);
            consumed = 1;
        }
        else {
            TrackCommands.NamedPoint point = TrackCommands.trackPoint(sender, state, args, 3);
            if (point.consumed == 0) {
                throw new CommandException(elementUsage());
            }
            target = hardwareAt(hardware, point.ref);
            if (target == null) {
                throw new CommandException("No adjustable hardware of this ride at #" + point.ref.sectionId()
                    + " " + fmt2(point.ref.distance()));
            }
            consumed = point.consumed;
        }
        Map<String, String> values = keyValues(args, 3 + consumed, "speed", "target", "force", "dwell", "passes");
        if (values.isEmpty()) {
            throw new CommandException("Nothing to set. " + elementUsage());
        }
        if (values.containsKey("speed") && values.containsKey("target")) {
            throw new CommandException("speed= and target= are the same setting; give one");
        }
        // Resolve every key before changing anything, so a bad one leaves the ride as it was.
        Map<RideTuning.Parameter, Double> changes = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            RideTuning.Parameter parameter = parameterFor(target, entry.getKey());
            changes.put(parameter, CommandBase.parseDouble(entry.getValue()));
        }
        int number = hardware.indexOf(target) + 1;
        List<String> applied = new ArrayList<>();
        for (Map.Entry<RideTuning.Parameter, Double> change : changes.entrySet()) {
            RideTuning.Parameter parameter = change.getKey();
            double asked = change.getValue();
            double set = RideTuning.apply(state.elements(), state.rides().members(section.id()), state.network(),
                target.elementIndex, parameter, asked, RcmcConstants.SECONDS_PER_TICK);
            if (Double.isNaN(set)) {
                throw new CommandException("Hardware " + number + " changed while it was being set; list it again");
            }
            String text = keyFor(parameter) + "=" + value(parameter, set);
            if (Math.abs(set - asked) > 1.0e-9D) {
                text += " (asked " + fmt2(asked) + "; " + parameter.label.toLowerCase(Locale.ROOT) + " runs "
                    + fmt2(parameter.min) + " to " + fmt2(parameter.max) + ")";
            }
            applied.add(text);
        }
        // Authored hardware: saved, and undoable as one step, like the operator panel's changes.
        state.markTrackDirty(world);
        reply(sender, TextFormatting.GREEN, "Hardware " + number + " (" + kind(target.element) + "): "
            + String.join(", ", applied) + ".");
    }

    private static void listHardware(ICommandSender sender, RcmcWorldState state, TrackSection section,
                                     List<Hardware> hardware) {
        if (hardware.isEmpty()) {
            reply(sender, TextFormatting.YELLOW, "The ride #" + section.id() + " is part of has no adjustable"
                + " hardware. Lay some with @lift, @brake, @launch, @tyres or @station.");
            return;
        }
        reply(sender, TextFormatting.AQUA, "Ride hardware of #" + section.id() + " - " + hardware.size()
            + " piece(s), numbered from 1 for /rcmc element " + section.id() + " set <n>");
        for (int i = 0; i < hardware.size(); i++) {
            Hardware h = hardware.get(i);
            StringBuilder line = new StringBuilder();
            line.append("  ").append(i + 1).append(": ").append(kind(h.element))
                .append(" #").append(h.element.sectionId()).append(' ')
                .append(fmt2(h.element.startDistance())).append('-').append(fmt2(h.element.endDistance()));
            TrackSection on = state.network().section(h.element.sectionId());
            if (on != null) {
                line.append(" (").append(nodesCovering(on, h.element.startDistance(), h.element.endDistance()))
                    .append(')');
            }
            for (RideTuning.Setting setting : h.settings) {
                line.append("  ").append(keyFor(setting.parameter)).append('=').append(value(setting.parameter,
                    setting.value));
            }
            reply(sender, TextFormatting.GRAY, line.toString());
        }
    }

    /**
     * "nodes 8 to 10", numbered from 1: the nodes a stretch from {@code from} to {@code to} starts and
     * ends at, or nearest to. Hardware laid by segment type runs exactly node to node.
     */
    private static String nodesCovering(TrackSection section, double from, double to) {
        int first = nearestNode(section, from);
        int last = nearestNode(section, to);
        if (to >= section.totalLength() - 1.0e-6D && section.isClosed()) {
            last = 0;   // A circuit's end is its first node again.
        }
        return "nodes " + (first + 1) + " to " + (last + 1);
    }

    private static int nearestNode(TrackSection section, double distance) {
        int best = 0;
        double bestGap = Double.MAX_VALUE;
        for (int i = 0; i < section.nodes().size(); i++) {
            double gap = Math.abs(section.nodeDistance(i) - distance);
            if (gap < bestGap) {
                best = i;
                bestGap = gap;
            }
        }
        if (!section.isClosed() && Math.abs(section.totalLength() - distance) < bestGap) {
            best = section.nodes().size() - 1;
        }
        return best;
    }

    /** The ride's hardware, one entry per piece, in the order the operator panel lists it. */
    private static List<Hardware> hardwareOf(RcmcWorldState state, int sectionId) {
        List<Hardware> out = new ArrayList<>();
        Map<Integer, Hardware> byIndex = new LinkedHashMap<>();
        List<RideElement> all = state.elements().elements();
        for (RideTuning.Setting setting : RideTuning.settingsFor(state.elements(), state.rides().members(sectionId),
            state.network(), RcmcConstants.SECONDS_PER_TICK)) {
            Hardware h = byIndex.get(setting.elementIndex);
            if (h == null) {
                h = new Hardware(setting.elementIndex, all.get(setting.elementIndex));
                byIndex.put(setting.elementIndex, h);
                out.add(h);
            }
            h.settings.add(setting);
        }
        return out;
    }

    /** The piece of hardware whose first element covers {@code ref}, or {@code null}. */
    private static Hardware hardwareAt(List<Hardware> hardware, TrackRef ref) {
        for (Hardware h : hardware) {
            if (h.element.sectionId() == ref.sectionId() && h.element.contains(ref)) {
                return h;
            }
        }
        return null;
    }

    /** What {@code key} means on {@code h}'s kind of hardware. */
    private static RideTuning.Parameter parameterFor(Hardware h, String key) throws CommandException {
        for (RideTuning.Setting setting : h.settings) {
            if (keyFor(setting.parameter).equals(key)
                || ("target".equals(key) && "speed".equals(keyFor(setting.parameter)))) {
                return setting.parameter;
            }
        }
        List<String> keys = new ArrayList<>();
        for (RideTuning.Setting setting : h.settings) {
            keys.add(keyFor(setting.parameter) + "=");
        }
        throw new CommandException("A " + kind(h.element) + " has no " + key + "=; it takes " + keys);
    }

    /** The key a parameter is set and listed by. */
    private static String keyFor(RideTuning.Parameter parameter) {
        switch (parameter) {
            case LAUNCH_FORCE:
                return "force";
            case STATION_DWELL:
                return "dwell";
            case STATION_PASSES:
                return "passes";
            default:
                return "speed";
        }
    }

    /** A setting's value as listed: a count as a whole number, anything else to two places. */
    private static String value(RideTuning.Parameter parameter, double v) {
        return parameter == RideTuning.Parameter.STATION_PASSES ? Long.toString(Math.round(v)) : fmt2(v);
    }

    private static String kind(RideElement element) {
        String type = ElementCodec.typeOf(element);
        if (element instanceof BrakeRun) {
            return ((BrakeRun) element).mode() == BrakeRun.Mode.TRIM ? "trim brake" : "block brake";
        }
        if (element instanceof LaunchTrack && ((LaunchTrack) element).targetSpeed() < 0.0D) {
            return "backward launch";
        }
        return type == null ? "hardware" : type.replace('_', ' ');
    }

    // ---- /rcmc supports ----

    private static final String SUPPORTS_USAGE = "/rcmc supports <sectionId> <list | clear"
        + " | exclude <x0> <z0> <x1> <z1> | include <number>>";

    /**
     * {@code /rcmc supports <sectionId> ...} — where the section's automatic support columns may
     * not stand.
     *
     * <p>Columns go in every nine blocks of track whatever is underneath, which put them in the
     * middle of a boardwalk the track crossed, with no way to say otherwise. An exclusion is a box
     * of ground no column of this section stands in; hand-placed {@code rcmc:track_support} bents
     * at its edges then carry the span, as on a real ride.</p>
     */
    static void supports(ICommandSender sender, World world, RcmcWorldState state, String[] args)
        throws CommandException {
        if (args.length < 3) {
            throw new CommandException(SUPPORTS_USAGE);
        }
        TrackSection section = section(state, args[1]);
        List<SupportExclusion> boxes = new ArrayList<>(section.supportExclusions());
        int columnsBefore = TrackSupports.columnsFor(section, world).size();
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "list":
                if (boxes.isEmpty()) {
                    reply(sender, TextFormatting.GRAY, "Section #" + section.id() + " has no support"
                        + " exclusions: " + columnsBefore + " columns, wherever the spacing puts them.");
                    return;
                }
                reply(sender, TextFormatting.GOLD, "Support exclusions on #" + section.id() + " (x z to x z), "
                    + columnsBefore + " columns standing:");
                for (int i = 0; i < boxes.size(); i++) {
                    SupportExclusion box = boxes.get(i);
                    reply(sender, TextFormatting.WHITE, "  " + (i + 1) + ". " + box.minX + " " + box.minZ
                        + " to " + box.maxX + " " + box.maxZ);
                }
                return;
            case "clear":
                if (boxes.isEmpty()) {
                    reply(sender, TextFormatting.GRAY, "Section #" + section.id() + " has no support exclusions.");
                    return;
                }
                boxes.clear();
                break;
            case "exclude": {
                if (args.length < 7) {
                    throw new CommandException("/rcmc supports <sectionId> exclude <x0> <z0> <x1> <z1>");
                }
                SupportExclusion box = new SupportExclusion(blockCoordinate(args[3]), blockCoordinate(args[4]),
                    blockCoordinate(args[5]), blockCoordinate(args[6]));
                if (boxes.contains(box)) {
                    throw new CommandException("Section #" + section.id() + " already excludes " + box);
                }
                if (boxes.size() >= MAX_SUPPORT_EXCLUSIONS) {
                    throw new CommandException("A section holds at most " + MAX_SUPPORT_EXCLUSIONS
                        + " support exclusions; remove one first");
                }
                boxes.add(box);
                break;
            }
            case "include": {
                if (args.length < 4) {
                    throw new CommandException("/rcmc supports <sectionId> include <number> — see list");
                }
                if (boxes.isEmpty()) {
                    throw new CommandException("Section #" + section.id() + " has no support exclusions");
                }
                boxes.remove(CommandBase.parseInt(args[3], 1, boxes.size()) - 1);
                break;
            }
            default:
                throw new CommandException(SUPPORTS_USAGE);
        }
        TrackSection edited = section.withSupportExclusions(boxes);
        state.network().replaceSection(edited);
        state.markTrackDirty(world);
        PacketTrackSync.broadcast(state.network(), world.provider.getDimension());
        int columnsAfter = TrackSupports.columnsFor(edited, world).size();
        reply(sender, TextFormatting.GREEN, "Section #" + section.id() + " now has " + boxes.size()
            + " support exclusion" + (boxes.size() == 1 ? "" : "s") + ": " + columnsBefore + " columns before, "
            + columnsAfter + " now.");
        if (columnsAfter == columnsBefore && "exclude".equalsIgnoreCase(args[2])) {
            reply(sender, TextFormatting.GRAY, "  No column stood in that box. Columns are placed by where"
                + " their foot is; on banked track that is beside the rails, not under them.");
        }
    }

    /** Most exclusion boxes one section keeps: each is tested for every column of the section. */
    private static final int MAX_SUPPORT_EXCLUSIONS = 64;

    /** A block coordinate as typed, whole or with a fraction: the block that point is in. */
    private static int blockCoordinate(String arg) throws CommandException {
        return (int) Math.floor(CommandBase.parseDouble(arg, -3.0E7D, 3.0E7D));
    }

    // ---- /rcmc clearance ----

    static void clearance(ICommandSender sender, World world, RcmcWorldState state, String[] args)
        throws CommandException {
        if (args.length < 2) {
            throw new CommandException("/rcmc clearance <sectionId> [max listed]");
        }
        TrackSection section = section(state, args[1]);
        int listed = args.length > 2 ? CommandBase.parseInt(args[2], 1, 10000) : DEFAULT_LISTED;
        ClearanceChecks.Report report = ClearanceChecks.check(world, section);
        String heading = "Clearance #" + section.id() + " (" + ClearanceChecks.envelopeLabel(section)
            + " envelope, " + fmt1(section.totalLength()) + " blocks): ";
        if (report.intrusions.isEmpty()) {
            reply(sender, report.unknownBlocks == 0 ? TextFormatting.GREEN : TextFormatting.YELLOW, heading
                + "nothing in the way" + unknownNote(report) + ".");
            return;
        }
        reply(sender, TextFormatting.GOLD, heading + report.intrusions.size() + " intruding block"
            + (report.intrusions.size() == 1 ? "" : "s") + unknownNote(report));
        for (int i = 0; i < Math.min(listed, report.intrusions.size()); i++) {
            ClearanceChecks.Intrusion hit = report.intrusions.get(i);
            reply(sender, TextFormatting.YELLOW, "  " + hit.pos.getX() + " " + hit.pos.getY() + " " + hit.pos.getZ()
                + " " + hit.block + "  @ " + fmt1(hit.distance) + " " + RideChecks.whereOn(section, hit.distance));
        }
        if (report.intrusions.size() > listed) {
            reply(sender, TextFormatting.GRAY, "  ... and " + (report.intrusions.size() - listed) + " more;"
                + " /rcmc clearance " + section.id() + " " + report.intrusions.size() + " lists them all.");
        }
    }

    /**
     * One line on what stands in {@code section}'s way, or nothing when it is clear: said after
     * laying or editing, where a full list would bury the result of the command itself.
     */
    static void clearanceSummary(ICommandSender sender, World world, TrackSection section) {
        if (section.totalLength() > AUTO_CLEARANCE_MAX_LENGTH) {
            reply(sender, TextFormatting.GRAY, "  Clearance not checked on a section this long; run /rcmc clearance "
                + section.id() + ".");
            return;
        }
        ClearanceChecks.Report report = ClearanceChecks.check(world, section);
        if (report.intrusions.isEmpty()) {
            if (report.unknownBlocks > 0) {
                reply(sender, TextFormatting.GRAY, "  Clearance: nothing in the way where loaded" + unknownNote(report)
                    + ".");
            }
            return;
        }
        ClearanceChecks.Intrusion first = report.intrusions.get(0);
        reply(sender, TextFormatting.GOLD, "  Clearance: " + report.intrusions.size() + " block"
            + (report.intrusions.size() == 1 ? " intrudes" : "s intrude") + ", first " + first.block + " at "
            + first.pos.getX() + " " + first.pos.getY() + " " + first.pos.getZ() + " ("
            + RideChecks.whereOn(section, first.distance) + "). /rcmc clearance " + section.id() + " for all.");
    }

    private static String unknownNote(ClearanceChecks.Report report) {
        return report.unknownBlocks == 0 ? ""
            : "; " + report.unknownBlocks + " block(s) in unloaded chunks not checked";
    }

    // ---- shared ----

    private static TrackSection section(RcmcWorldState state, String arg) throws CommandException {
        int id = CommandBase.parseInt(arg);
        TrackSection section = state.network().section(id);
        if (section == null) {
            throw new CommandException("No section #" + id + " - try /rcmc info");
        }
        return section;
    }

    /**
     * {@code key=value} arguments from {@code args[from..]}, keys lower-cased. Refuses an unknown key
     * or a repeated one, so a typo cannot be silently ignored.
     */
    private static Map<String, String> keyValues(String[] args, int from, String... allowed) throws CommandException {
        Map<String, String> out = new LinkedHashMap<>();
        List<String> known = Arrays.asList(allowed);
        for (int i = from; i < args.length; i++) {
            int eq = args[i].indexOf('=');
            if (eq <= 0 || eq == args[i].length() - 1) {
                throw new CommandException("'" + args[i] + "' is not key=value; keys are " + known);
            }
            String key = args[i].substring(0, eq).toLowerCase(Locale.ROOT);
            if (!known.contains(key)) {
                throw new CommandException("Unknown key '" + key + "'; keys are " + known);
            }
            if (out.put(key, args[i].substring(eq + 1)) != null) {
                throw new CommandException(key + "= is given twice");
            }
        }
        return out;
    }

    private static Vec3 vector(String text, String what) throws CommandException {
        String[] parts = text.split(",");
        if (parts.length != 3) {
            throw new CommandException(what + " needs x,y,z, not '" + text + "'");
        }
        return new Vec3(CommandBase.parseDouble(parts[0].trim()), CommandBase.parseDouble(parts[1].trim()),
            CommandBase.parseDouble(parts[2].trim()));
    }

    private static boolean isInteger(String text) {
        try {
            Integer.parseInt(text);
            return true;
        }
        catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isNumber(String text) {
        try {
            return Double.isFinite(Double.parseDouble(text));
        }
        catch (NumberFormatException e) {
            return false;
        }
    }

    private static String fmt1(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String fmt2(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static void reply(ICommandSender sender, TextFormatting colour, String message) {
        sender.sendMessage(new TextComponentString(colour + message));
    }
}
