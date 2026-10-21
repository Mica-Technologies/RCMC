package com.micatechnologies.minecraft.rcmc.command;

import com.micatechnologies.minecraft.rcmc.builder.TrackBuildSession;
import com.micatechnologies.minecraft.rcmc.builder.TrackBuildSession.SegmentType;
import com.micatechnologies.minecraft.rcmc.item.ItemTrackTool;
import com.micatechnologies.minecraft.rcmc.track.Polyline;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackPicker;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.TrackStyleIds;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import com.micatechnologies.minecraft.rcmc.world.TrackLaying;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * Track authoring without the track tool: {@code /rcmc track lay}, {@code /rcmc track nodes}, and
 * the position-free ways of naming a point on the track that the station commands accept.
 *
 * <p>The tool is the right way to build by hand and the wrong way to build from a generator. It
 * snaps every node to a block centre, sets heights in whole blocks, and needs a player standing at
 * each station. A city line drawn by a script has its arcs already worked out to the decimal, and
 * a server whose only player is busy elsewhere cannot spare it. These commands take the geometry
 * as given and lay it through the same path the tool commits through
 * ({@link TrackLaying#lay}), so the result is indistinguishable from a click-built section.</p>
 */
final class TrackCommands {

    /** Where {@code file <name>} polylines are read from and {@code nodes … file} writes to. */
    static final String POLYLINE_DIR = "rcmc/polylines";

    /** A file name with no path in it. */
    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}");

    /** Well past a city line (the 23 km loop that prompted this is 748 nodes), well short of a typo. */
    private static final int MAX_NODES = 20000;

    /** As the station commands have always used for the player's position. */
    private static final double PICK_RADIUS = 16.0D;

    private TrackCommands() {
        throw new AssertionError("No instances.");
    }

    // ---- /rcmc track ----

    static void track(ICommandSender sender, World world, RcmcWorldState state, String[] args)
        throws CommandException {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "lay":
                lay(sender, world, state, args);
                return;
            case "nodes":
                nodes(sender, state, args);
                return;
            default:
                throw new CommandException(usage());
        }
    }

    static String usage() {
        return "/rcmc track lay <style> [open|circuit] <x,y,z;...|file <name>>"
            + " | lay <style> append <sectionId> [close] <x,y,z;...|file <name>>"
            + " | nodes <sectionId> [file <name>]";
    }

    /**
     * {@code /rcmc track lay <style> [open|circuit] <nodes>} and
     * {@code /rcmc track lay <style> append <sectionId> [close] <nodes>}.
     */
    private static void lay(ICommandSender sender, World world, RcmcWorldState state, String[] args)
        throws CommandException {
        if (args.length < 4) {
            throw new CommandException(usage());
        }
        String styleId = resolveStyle(args[2]);
        int at = 3;
        boolean closing = false;
        TrackSection appendTo = null;
        String mode = args[at].toLowerCase(Locale.ROOT);
        if ("open".equals(mode) || "circuit".equals(mode)) {
            closing = "circuit".equals(mode);
            at++;
        }
        else if ("append".equals(mode)) {
            if (args.length < at + 3) {
                throw new CommandException("/rcmc track lay <style> append <sectionId> [close] <nodes>");
            }
            int sectionId = CommandBase.parseInt(args[at + 1]);
            appendTo = state.network().section(sectionId);
            if (appendTo == null) {
                throw new CommandException("No section with id " + sectionId);
            }
            if (appendTo.isClosed()) {
                throw new CommandException("Section #" + sectionId + " is a closed circuit; it has no end to"
                    + " continue");
            }
            at += 2;
            if ("close".equalsIgnoreCase(args[at])) {
                closing = true;
                at++;
            }
        }
        if (at >= args.length) {
            throw new CommandException("No nodes given. " + usage());
        }

        List<Polyline.Point> points = readPoints(args, at);
        if (points.size() > MAX_NODES) {
            throw new CommandException(points.size() + " nodes is more than the " + MAX_NODES
                + " one command will lay; split the line into sections");
        }
        String nodeStyle = null;
        if (appendTo != null) {
            // An extension keeps the section's style. A different transit style given here starts
            // from the first new node — the per-node style the section already supports.
            String effective = appendTo.styleAtDistance(appendTo.totalLength());
            if (!sameStyle(styleId, effective)) {
                if (styleId == null) {
                    throw new CommandException("Section #" + appendTo.id() + " is "
                        + TrackStyleIds.label(effective) + "; restyle it with /rcmc style rather than"
                        + " appending coaster track to it");
                }
                nodeStyle = styleId;
            }
        }

        List<TrackNode> nodes = new ArrayList<>(points.size());
        List<SegmentType> types = new ArrayList<>(points.size());
        SegmentType running = SegmentType.PLAIN;
        for (Polyline.Point point : points) {
            if (point.type != null) {
                running = segmentType(point.type);
            }
            nodes.add(new TrackNode(point.position, point.bankDegrees, nodeStyle));
            types.add(running);
        }

        TrackLaying.Result built;
        try {
            built = TrackLaying.lay(world, state, nodes, types, closing,
                appendTo == null ? null : TrackLaying.endOf(appendTo), styleId);
        }
        catch (TrackLaying.Refused refused) {
            throw new CommandException(refused.getMessage());
        }
        TrackSection section = built.section;
        reply(sender, TextFormatting.GREEN, (built.extended ? "Extended section #" : "Built section #")
            + section.id() + " - " + String.format(Locale.ROOT, "%.1f", section.totalLength()) + " blocks, "
            + section.nodes().size() + " nodes" + (section.isClosed() ? " (circuit)" : "") + ", style "
            + TrackStyleIds.label(section.styleId()) + ".");
        if (!built.created.isEmpty()) {
            reply(sender, TextFormatting.GRAY, "  " + built.created.size() + " element(s) from segment types.");
        }
        for (String platform : built.platforms) {
            reply(sender, TextFormatting.GRAY, "  Station: laid " + platform + ".");
        }
        ItemTrackTool.report(sender, section);
    }

    /** {@code /rcmc track nodes <sectionId> [file <name>]}. */
    private static void nodes(ICommandSender sender, RcmcWorldState state, String[] args)
        throws CommandException {
        if (args.length < 3) {
            throw new CommandException("/rcmc track nodes <sectionId> [file <name>]");
        }
        int sectionId = CommandBase.parseInt(args[2]);
        TrackSection section = state.network().section(sectionId);
        if (section == null) {
            throw new CommandException("No section with id " + sectionId);
        }
        if (args.length >= 5 && "file".equalsIgnoreCase(args[3])) {
            File file = polylineFile(args[4]);
            try {
                Files.createDirectories(file.getParentFile().toPath());
                Files.write(file.toPath(), Polyline.write(section).getBytes(StandardCharsets.UTF_8));
            }
            catch (IOException e) {
                throw new CommandException("Could not write " + file + ": " + e.getMessage());
            }
            reply(sender, TextFormatting.GREEN, "Wrote " + section.nodes().size() + " nodes of section #"
                + sectionId + " to config/" + POLYLINE_DIR + "/" + file.getName() + ".");
            return;
        }
        reply(sender, TextFormatting.AQUA, "Section #" + sectionId + " - "
            + (section.isClosed() ? "circuit" : "open") + ", " + section.nodes().size() + " nodes, "
            + String.format(Locale.ROOT, "%.1f", section.totalLength()) + " blocks, style "
            + TrackStyleIds.label(section.styleId()));
        for (int i = 0; i < section.nodes().size(); i++) {
            TrackNode node = section.nodes().get(i);
            reply(sender, TextFormatting.GRAY, "  " + i + ": " + Polyline.format(node) + "  @ "
                + String.format(Locale.ROOT, "%.2f", section.nodeDistance(i))
                + (node.styleId() == null ? "" : "  style " + node.styleId()));
        }
    }

    /** The nodes at {@code args[from..]}: {@code file <name>}, or the polyline itself. */
    private static List<Polyline.Point> readPoints(String[] args, int from) throws CommandException {
        String text;
        if ("file".equalsIgnoreCase(args[from])) {
            if (from + 1 >= args.length) {
                throw new CommandException("file needs a name, read from config/" + POLYLINE_DIR + "/");
            }
            File file = polylineFile(args[from + 1]);
            if (!file.isFile()) {
                throw new CommandException("No polyline file config/" + POLYLINE_DIR + "/" + file.getName());
            }
            try {
                text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            }
            catch (IOException e) {
                throw new CommandException("Could not read " + file + ": " + e.getMessage());
            }
        }
        else {
            // Rejoined with spaces: Minecraft splits arguments on them, and "x, y, z" is legal.
            text = String.join(" ", Arrays.copyOfRange(args, from, args.length));
        }
        try {
            return Polyline.parse(text);
        }
        catch (IllegalArgumentException e) {
            throw new CommandException(e.getMessage());
        }
    }

    private static File polylineFile(String name) throws CommandException {
        if (!FILE_NAME.matcher(name).matches()) {
            throw new CommandException("'" + name + "' is not a plain file name (letters, digits, _ . -)");
        }
        String withExtension = name.indexOf('.') >= 0 ? name : name + ".txt";
        File dir = new File(net.minecraftforge.fml.common.Loader.instance().getConfigDir(), POLYLINE_DIR);
        return new File(dir, withExtension);
    }

    private static String resolveStyle(String argument) throws CommandException {
        try {
            return TrackStyleIds.resolve(argument);
        }
        catch (IllegalArgumentException e) {
            throw new CommandException(e.getMessage());
        }
    }

    private static boolean sameStyle(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** {@code @lift}, {@code @block-brake}, {@code @BLOCK_BRAKE}… to a segment type. */
    static SegmentType segmentType(String name) throws CommandException {
        String key = name.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
        for (SegmentType type : SegmentType.values()) {
            if (type.name().replace("_", "").toLowerCase(Locale.ROOT).equals(key)) {
                return type;
            }
        }
        List<String> names = new ArrayList<>();
        for (SegmentType type : SegmentType.values()) {
            names.add(type.name().toLowerCase(Locale.ROOT));
        }
        throw new CommandException("Unknown segment type '@" + name + "' - one of " + names);
    }

    // ---- /rcmc section split, and switches at a point ----

    /** {@code /rcmc section split <sectionId> <distance | near <x> <y> <z>>}. */
    static void section(ICommandSender sender, World world, RcmcWorldState state, String[] args)
        throws CommandException {
        if (args.length < 4 || !"split".equalsIgnoreCase(args[1])) {
            throw new CommandException("/rcmc section split <sectionId> <distance | near <x> <y> <z>>");
        }
        int sectionId = CommandBase.parseInt(args[2]);
        double distance = distanceAlong(state, sectionId, args, 3);
        try {
            reply(sender, TextFormatting.GREEN,
                com.micatechnologies.minecraft.rcmc.world.TrackSplits.splitAt(world, state, sectionId, distance).message);
        }
        catch (com.micatechnologies.minecraft.rcmc.world.TrackSplits.Refused refused) {
            throw new CommandException(refused.getMessage());
        }
    }

    /**
     * {@code /rcmc switch create <throughSectionId> <distance | near <x> <y> <z>> <divergeSectionId>}:
     * a switch at a point along a section, a circuit included, splitting it there.
     */
    static void switchAtPoint(ICommandSender sender, World world, RcmcWorldState state, String[] args)
        throws CommandException {
        int throughId = CommandBase.parseInt(args[2]);
        int divergeArg = "near".equalsIgnoreCase(args[3]) ? 7 : 4;
        if (args.length <= divergeArg) {
            throw new CommandException("/rcmc switch create <throughSection> <distance | near <x> <y> <z>>"
                + " <divergeSection>");
        }
        double distance = distanceAlong(state, throughId, args, 3);
        int divergeId = CommandBase.parseInt(args[divergeArg]);
        try {
            reply(sender, TextFormatting.GREEN, com.micatechnologies.minecraft.rcmc.world.TrackSplits.switchAt(
                world, state, throughId, distance, divergeId));
        }
        catch (com.micatechnologies.minecraft.rcmc.world.TrackSplits.Refused refused) {
            throw new CommandException(refused.getMessage());
        }
    }

    /** Whether {@code args} is the point form of switch create, rather than the ends form. */
    static boolean isSwitchAtPoint(String[] args) {
        return args.length >= 5 && ("near".equalsIgnoreCase(args[3]) || isNumber(args[3]));
    }

    /** {@code <distance>} or {@code near <x> <y> <z>} at {@code args[from]}, as a distance along the section. */
    private static double distanceAlong(RcmcWorldState state, int sectionId, String[] args, int from)
        throws CommandException {
        TrackSection section = state.network().section(sectionId);
        if (section == null) {
            throw new CommandException("No section with id " + sectionId);
        }
        if (!"near".equalsIgnoreCase(args[from])) {
            return CommandBase.parseDouble(args[from]);
        }
        if (from + 3 >= args.length) {
            throw new CommandException("near needs <x> <y> <z>");
        }
        Vec3 point = new Vec3(CommandBase.parseDouble(args[from + 1]), CommandBase.parseDouble(args[from + 2]),
            CommandBase.parseDouble(args[from + 3]));
        TrackPicker.Hit hit = TrackPicker.pickOn(section, point);
        if (hit == null || hit.distanceFromQuery > PICK_RADIUS) {
            throw new CommandException("Section #" + sectionId + " does not pass within " + (int) PICK_RADIUS
                + " blocks of " + fmt(point.x) + " " + fmt(point.y) + " " + fmt(point.z));
        }
        return hit.ref.distance();
    }

    // ---- /rcmc build height ----

    static void buildHeight(ICommandSender sender, EntityPlayer player, TrackBuildSession session, String[] args)
        throws CommandException {
        if (args.length < 3) {
            throw new CommandException("/rcmc build height <blocks>  (now "
                + fmt(session.heightOffset()) + ")");
        }
        double requested = CommandBase.parseDouble(args[2], TrackBuildSession.MIN_HEIGHT_OFFSET,
            TrackBuildSession.MAX_HEIGHT_OFFSET);
        session.setHeightOffset(requested);
        reply(sender, TextFormatting.GREEN, "Track tool nodes now sit " + fmt(session.heightOffset())
            + " blocks above the clicked block's top, until the next commit.");
    }

    // ---- points on the track, for the station commands ----

    /** A track point named by a command, and how many arguments naming it took. */
    static final class NamedPoint {
        final TrackRef ref;
        final int consumed;

        NamedPoint(TrackRef ref, int consumed) {
            this.ref = ref;
            this.consumed = consumed;
        }
    }

    /**
     * The point on the track named at {@code args[from..]}:
     * <ul>
     *   <li>{@code at <sectionId> <distance>}, or just {@code <sectionId> <distance>} — exact;</li>
     *   <li>{@code near <x> <y> <z>} — the nearest track within 16 blocks of that point;</li>
     *   <li>nothing — the nearest track within 16 blocks of the player, as before.</li>
     * </ul>
     * The first two need no player, so a script can name every berth on a line without moving
     * anyone, and a console can do it at all.
     */
    static NamedPoint trackPoint(ICommandSender sender, RcmcWorldState state, String[] args, int from)
        throws CommandException {
        if (from < args.length && "at".equalsIgnoreCase(args[from])) {
            if (from + 2 >= args.length) {
                throw new CommandException("at needs <sectionId> <distance>");
            }
            return new NamedPoint(exact(state, args[from + 1], args[from + 2]), 3);
        }
        if (from < args.length && "near".equalsIgnoreCase(args[from])) {
            if (from + 3 >= args.length) {
                throw new CommandException("near needs <x> <y> <z>");
            }
            Vec3 point = new Vec3(CommandBase.parseDouble(args[from + 1]),
                CommandBase.parseDouble(args[from + 2]), CommandBase.parseDouble(args[from + 3]));
            return new NamedPoint(pick(state, point, "of " + fmt(point.x) + " " + fmt(point.y) + " "
                + fmt(point.z)), 4);
        }
        if (from + 1 < args.length && isInteger(args[from]) && isNumber(args[from + 1])) {
            return new NamedPoint(exact(state, args[from], args[from + 1]), 2);
        }
        EntityPlayer player = CommandBase.getCommandSenderAsPlayer(sender);
        return new NamedPoint(pick(state, new Vec3(player.posX, player.posY, player.posZ),
            "- stand beside the track, or name the point with 'at <sectionId> <distance>'"), 0);
    }

    private static TrackRef exact(RcmcWorldState state, String sectionArg, String distanceArg)
        throws CommandException {
        int sectionId = CommandBase.parseInt(sectionArg);
        TrackSection section = state.network().section(sectionId);
        if (section == null) {
            throw new CommandException("No section with id " + sectionId);
        }
        double distance = CommandBase.parseDouble(distanceArg);
        if (distance < 0.0D || distance > section.totalLength()) {
            throw new CommandException("Section #" + sectionId + " runs 0 to "
                + fmt(section.totalLength()) + "; " + fmt(distance) + " is off it");
        }
        return new TrackRef(sectionId, distance);
    }

    private static TrackRef pick(RcmcWorldState state, Vec3 point, String where) throws CommandException {
        TrackPicker.Hit hit = TrackPicker.pick(state.network(), point, PICK_RADIUS);
        if (hit == null) {
            throw new CommandException("No track within " + (int) PICK_RADIUS + " blocks " + where);
        }
        return hit.ref;
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

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static void reply(ICommandSender sender, TextFormatting colour, String message) {
        sender.sendMessage(new TextComponentString(colour + message));
    }
}
