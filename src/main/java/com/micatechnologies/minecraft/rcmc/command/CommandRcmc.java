package com.micatechnologies.minecraft.rcmc.command;

import com.micatechnologies.minecraft.rcmc.builder.TrackBuildSession;
import com.micatechnologies.minecraft.rcmc.debug.DemoCoaster;
import com.micatechnologies.minecraft.rcmc.debug.DemoMetro;
import com.micatechnologies.minecraft.rcmc.net.PacketElementSync;
import com.micatechnologies.minecraft.rcmc.net.PacketTrackSync;
import com.micatechnologies.minecraft.rcmc.net.PacketTrainRemove;
import com.micatechnologies.minecraft.rcmc.net.PacketTrainSync;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.entity.EntityCoasterCar;
import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.block.BlockSection;
import com.micatechnologies.minecraft.rcmc.physics.block.BlockSystem;
import com.micatechnologies.minecraft.rcmc.physics.element.BrakeRun;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.RcmcConfig;
import com.micatechnologies.minecraft.rcmc.block.RcmcBlocks;
import com.micatechnologies.minecraft.rcmc.physics.CarSeating;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.rating.RideRater;
import com.micatechnologies.minecraft.rcmc.rating.RideRating;
import com.micatechnologies.minecraft.rcmc.rating.RideStatistics;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * {@code /rcmc} — operator and debug commands.
 *
 * <p>Currently serves the high-speed motion spike: {@code /rcmc demo} builds a complete circuit
 * and puts a train on it, so the smoothness question can be answered by looking at a real client
 * before a track editor exists. These subcommands are development scaffolding and will be replaced
 * by the builder tool and ride-controller GUI.</p>
 */
public class CommandRcmc extends CommandBase {

    @Override
    public String getName() {
        return "rcmc";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rcmc <demo|metrodemo|train|clear|info|build|paint|style|rate|block|station|line"
            + "|switch|platform|rmsection>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args, BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "demo", "metrodemo", "train", "clear",
                "info", "build", "paint", "style", "rate", "block", "station", "line", "switch",
                "platform", "rmsection", "undo", "redo");
        }
        if (args.length == 3 && "style".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args,
                com.micatechnologies.minecraft.rcmc.track.TrackStyleIds.COMMAND_CHOICES
                    .toArray(new String[0]));
        }
        if (args.length == 2 && "line".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "create", "list", "remove", "start",
                "stop", "signals");
        }
        if (args.length == 5 && "platform".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "left", "right", "both");
        }
        if (args.length == 2 && "switch".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "create", "list", "throw", "remove");
        }
        if ("switch".equalsIgnoreCase(args[0]) && args.length >= 4 && args.length % 2 == 1) {
            return getListOfStringsMatchingLastWord(args, "start", "end");
        }
        if (args.length == 2 && "station".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "list", "remove");
        }
        if (args.length == 2 && "build".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "bank", "circuit", "status", "cancel");
        }
        if (args.length == 5 && "train".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "coaster", "metro", "metrocompact", "metrolong");
        }
        return new ArrayList<>();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new CommandException(getUsage(sender));
        }
        World world = sender.getEntityWorld();
        RcmcWorldState state = RcmcWorldState.of(world);
        if (state == null) {
            throw new CommandException("No RCMC state for this world");
        }

        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "demo":
                buildDemo(sender, world, state, args);
                break;
            case "metrodemo":
                buildMetroDemo(sender, world, state, args);
                break;
            case "train":
                spawnTrain(sender, world, state, args);
                break;
            case "clear":
                clear(sender, world, state);
                break;
            case "info":
                info(sender, state);
                break;
            case "build":
                build(sender, args);
                break;
            case "paint":
                paint(sender, world, state, args);
                break;
            case "rate":
                rate(sender, state, args);
                break;
            case "block":
                block(sender, state, args);
                break;
            case "style":
                style(sender, world, state, args);
                break;
            case "station":
                station(sender, world, state, args);
                break;
            case "line":
                line(sender, world, state, args);
                break;
            case "switch":
                trackSwitch(sender, world, state, args);
                break;
            case "platform":
                platform(sender, world, state, args);
                break;
            case "rmsection":
                removeSection(sender, world, state, args);
                break;
            case "undo":
                if (state.undo(world)) {
                    reply(sender, TextFormatting.GREEN, "Undid the last edit."
                        + (state.canUndo() ? "" : " Nothing more to undo."));
                }
                else {
                    reply(sender, TextFormatting.GRAY, "Nothing to undo.");
                }
                break;
            case "redo":
                if (state.redo(world)) {
                    reply(sender, TextFormatting.GREEN, "Redid the last undone edit.");
                }
                else {
                    reply(sender, TextFormatting.GRAY, "Nothing to redo.");
                }
                break;
            default:
                throw new CommandException(getUsage(sender));
        }
    }

    private void buildDemo(ICommandSender sender, World world, RcmcWorldState state, String[] args)
        throws CommandException {
        EntityPlayer player = getCommandSenderAsPlayer(sender);
        double scale = args.length > 1 ? parseDouble(args[1], 0.4D, 4.0D) : 1.0D;
        double lift = args.length > 2 ? parseDouble(args[2], 8.0D, 120.0D) : 34.0D;

        int id = state.network().allocateSectionId();
        DemoCoaster.Result demo = DemoCoaster.build(id,
            new Vec3(player.posX, player.posY, player.posZ), scale, lift);
        TrackSection section = demo.section;
        state.network().addSection(section);

        double tick = RcmcConstants.SECONDS_PER_TICK;
        RideElementSet elements = state.elements();
        // Spans come from the layout itself rather than fractions of the lap — the shape is no
        // longer uniform, so a fraction would land the lift somewhere arbitrary.
        elements.add(new StationPlatform(id, demo.stationStart, demo.stationEnd,
            demo.stationStop, 6.0D, 60, 4.0D, 6.0D, tick));
        elements.add(new ChainLift(id, demo.liftStart, demo.liftEnd, 5.0D, 12.0D, tick));
        elements.add(new BrakeRun(id, demo.brakeStart, demo.brakeEnd,
            6.0D, 6.0D, BrakeRun.Mode.TRIM, tick));

        state.markTrackDirty(world);
        broadcastTrack(world, state);

        reply(sender, TextFormatting.GREEN, "Built demo coaster #" + id + " — "
            + String.format("%.1f", section.totalLength()) + " blocks, "
            + section.nodes().size() + " nodes, " + String.format("%.0f", lift) + "-block lift.");
        reply(sender, TextFormatting.GRAY, "Station " + fmt(demo.stationStart) + "-"
            + fmt(demo.stationEnd) + ", lift " + fmt(demo.liftStart) + "-" + fmt(demo.liftEnd)
            + ", brakes " + fmt(demo.brakeStart) + "-" + fmt(demo.brakeEnd) + ".");
        reply(sender, TextFormatting.GRAY,
            "Run /rcmc train " + id + " 5 0 to park a train in the station — it will dispatch itself.");
    }

    private static void pushSession(EntityPlayer player, TrackBuildSession session) {
        if (player instanceof net.minecraft.entity.player.EntityPlayerMP) {
            RcmcNetwork.sendTo(new com.micatechnologies.minecraft.rcmc.net.PacketBuildSessionSync(session),
                (net.minecraft.entity.player.EntityPlayerMP) player);
        }
    }

    /**
     * {@code /rcmc paint <trainId> <body|trim|seats> <colour>} — repaints a train.
     *
     * <p>A command rather than a wand because a train is not a thing you point at reliably while it
     * is moving, and painting one is a setup action rather than a building gesture. Track has the
     * editor wand precisely because track holds still.</p>
     */
    private void paint(ICommandSender sender, World world, RcmcWorldState state, String[] args)
        throws CommandException {
        if (args.length < 4) {
            throw new CommandException("/rcmc paint <trainId> <body|trim|seats> <colour>");
        }
        int trainId = parseInt(args[1]);
        Train train = state.trains().train(trainId);
        if (train == null) {
            throw new CommandException("No train with id " + trainId);
        }

        TrainSpec.Part part;
        try {
            part = TrainSpec.Part.valueOf(args[2].toUpperCase(java.util.Locale.ROOT));
        }
        catch (IllegalArgumentException e) {
            throw new CommandException("Part must be body, trim or seats");
        }

        com.micatechnologies.minecraft.rcmc.track.TrackPalette.Colour colour =
            com.micatechnologies.minecraft.rcmc.track.TrackPalette.Colour.byName(args[3], null);
        if (colour == null) {
            throw new CommandException("Unknown colour. Try: "
                + java.util.Arrays.toString(
                    com.micatechnologies.minecraft.rcmc.track.TrackPalette.Colour.values()));
        }

        // A train's spec is immutable, so repainting means replacing the train — carrying its
        // position and speed across so a running ride does not restart because someone changed
        // the paint.
        Train repainted = new Train(train.spec().withColour(part, colour.ordinal()),
            new PhysicsIntegrator(RcmcConfig.gravity, RcmcConfig.rollingResistance,
                RcmcConfig.airDrag, RcmcConfig.maxSpeed),
            train.reference(), train.velocity());
        state.trains().add(trainId, repainted);
        RcmcNetwork.sendToAllIn(new PacketTrainSync(trainId, repainted),
            world.provider.getDimension());

        reply(sender, TextFormatting.GREEN, "Train " + trainId + " "
            + part.name().toLowerCase(java.util.Locale.ROOT) + " -> " + colour.label());
    }

    private static String fmt(double blocks) {
        return String.format("%.0f", blocks);
    }

    private void spawnTrain(ICommandSender sender, World world, RcmcWorldState state, String[] args)
        throws CommandException {
        if (state.network().isEmpty()) {
            throw new CommandException("No track yet — run /rcmc demo first");
        }
        int sectionId = args.length > 1 ? parseInt(args[1]) : state.network().sections().iterator().next().id();
        TrackSection section = state.network().section(sectionId);
        if (section == null) {
            throw new CommandException("No section with id " + sectionId);
        }
        int carCount = args.length > 2 ? parseInt(args[2], 1, 12) : 5;
        double speed = args.length > 3 ? parseDouble(args[3], 0.0D, 60.0D) : 0.0D;
        String style = args.length > 4 ? args[4].toLowerCase(java.util.Locale.ROOT) : "coaster";
        // Start inside the station if there is one, so a train spawns where a ride would load it
        // rather than at whatever point the geometry happens to call distance zero.
        double startDistance = state.elements().elements().stream()
            .filter(e -> e.sectionId() == sectionId && e instanceof StationPlatform)
            // Exactly ON the stop point, not short of it. A station brakes an arriving train but
            // never pushes one, so a train spawned at rest before the stop point simply sits there
            // forever instead of dwelling and dispatching.
            .mapToDouble(e -> ((StationPlatform) e).stopDistance())
            .findFirst().orElse(0.0D);

        TrainSpec spec;
        switch (style) {
            case "metro":
                spec = TrainSpec.metroTrain(carCount);
                break;
            case "metrocompact":
                spec = TrainSpec.metroTrainCompact(carCount);
                break;
            case "metrolong":
                spec = TrainSpec.metroTrainLong(carCount);
                break;
            case "coaster":
                spec = new TrainSpec(carCount, 3.0D, 0.5D, 4);
                break;
            default:
                throw new CommandException(
                    "Unknown train style '" + style + "' — coaster, metro, metrocompact or metrolong");
        }
        PhysicsIntegrator integrator = new PhysicsIntegrator(
            RcmcConfig.gravity, RcmcConfig.rollingResistance, RcmcConfig.airDrag, RcmcConfig.maxSpeed);
        Train train = new Train(spec, integrator, new TrackRef(sectionId, startDistance), speed);

        int trainId = state.trains().allocateTrainId();
        state.trains().add(trainId, train);

        for (int i = 0; i < carCount; i++) {
            EntityCoasterCar car = new EntityCoasterCar(world, trainId, i);
            TrackFrame frame = train.frameOfCar(state.network(), i);
            car.setPosition(frame.position.x, frame.position.y, frame.position.z);
            world.spawnEntity(car);
        }

        // Push the new train immediately rather than waiting for the periodic correction — until
        // a client has the train, its car entities have nothing to derive a position from.
        RcmcNetwork.sendToAllIn(new PacketTrainSync(trainId, train), world.provider.getDimension());

        reply(sender, TextFormatting.GREEN, "Spawned train #" + trainId + " — " + carCount
            + " cars on section " + sectionId + " at " + speed + " blocks/s.");
    }

    /**
     * {@code /rcmc metrodemo} — builds a complete, ready-to-run metro line at the player: flat
     * catenary-styled alignment, three named stations, and a registered line. One command from
     * bare terrain to "spawn a train and start service".
     */
    private void buildMetroDemo(ICommandSender sender, World world, RcmcWorldState state,
                               String[] args)
        throws CommandException {
        EntityPlayer player = getCommandSenderAsPlayer(sender);
        boolean underground = args.length > 1
            && ("underground".equalsIgnoreCase(args[1]) || "loop".equalsIgnoreCase(args[1])
                || "subway".equalsIgnoreCase(args[1]));
        int id = state.network().allocateSectionId();
        Vec3 origin = new Vec3(player.posX, player.posY, player.posZ);
        DemoMetro.Result demo = underground
            ? DemoMetro.buildUndergroundLoop(id, origin)
            : DemoMetro.build(id, origin);
        state.network().addSection(demo.section);

        com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem transit = state.transit();
        java.util.List<com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation> stops =
            new ArrayList<>();
        for (int i = 0; i < demo.stationNames.length; i++) {
            com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation station =
                new com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation(
                    demo.stationNames[i], new TrackRef(id, demo.stationDistances[i]));
            transit.addStation(station);
            stops.add(station);
        }
        String lineName = underground ? "Subway" : "Metro";
        transit.addLine(new com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine(
            lineName, stops, underground));

        state.markTrackDirty(world);
        broadcastTrack(world, state);
        RcmcNetwork.sendToAllIn(new com.micatechnologies.minecraft.rcmc.net.PacketTransitSync(transit),
            world.provider.getDimension());

        reply(sender, TextFormatting.GREEN, "Built " + (underground ? "underground loop" : "metro")
            + " demo #" + id + " — " + fmt(demo.section.totalLength()) + " blocks, "
            + demo.stationNames.length + " stations (" + String.join(", ", demo.stationNames)
            + "), " + (underground ? "loop " : "") + "line '" + lineName + "'.");
        reply(sender, TextFormatting.GRAY, "Run /rcmc train " + id + " 3 0 metro, then /rcmc line start "
            + lineName + " <trainId> to begin service.");
    }

    /**
     * {@code /rcmc style <sectionId> <style>} — restyles a section: the classic coaster look, or
     * the wider transit gauge with optional overhead electrification (catenary poles, portal
     * gantries, or a tunnel conductor bar). Purely visual — the spline and the physics are
     * untouched — and per section, so one park can mix electrified metro and bare coaster.
     */
    private void style(ICommandSender sender, World world, RcmcWorldState state, String[] args)
        throws CommandException {
        if (args.length < 3) {
            throw new CommandException("/rcmc style <sectionId> <"
                + String.join("|", com.micatechnologies.minecraft.rcmc.track.TrackStyleIds.COMMAND_CHOICES)
                + "> [wireHeight]");
        }
        int sectionId = parseInt(args[1]);
        TrackSection section = state.network().section(sectionId);
        if (section == null) {
            throw new CommandException("No section with id " + sectionId);
        }
        String styleId;
        try {
            styleId = com.micatechnologies.minecraft.rcmc.track.TrackStyleIds.resolve(args[2]);
            if (args.length > 3 && styleId != null) {
                // An optional height rides on the style id itself; see TrackStyleIds.resolve for
                // why the suffix beats adding a field to every section.
                styleId = com.micatechnologies.minecraft.rcmc.track.TrackStyleIds.withWireHeight(
                    styleId, parseDouble(args[3],
                        com.micatechnologies.minecraft.rcmc.track.TrackStyleIds.MIN_CONTACT_WIRE_HEIGHT,
                        com.micatechnologies.minecraft.rcmc.track.TrackStyleIds.MAX_CONTACT_WIRE_HEIGHT));
            }
        }
        catch (IllegalArgumentException e) {
            throw new CommandException(e.getMessage());
        }
        // replaceSection keeps joins and switches; a fresh instance also invalidates the client
        // mesh cache, which keys on section identity.
        state.network().replaceSection(section.withStyle(styleId));
        state.markTrackDirty(world);
        broadcastTrack(world, state);
        double wire = com.micatechnologies.minecraft.rcmc.track.TrackStyleIds
            .contactWireHeight(styleId);
        reply(sender, TextFormatting.GREEN, "Section " + sectionId + " style -> "
            + (styleId == null ? "coaster" : styleId)
            + (wire > 0.0D ? ", contact wire at " + fmt(wire) + " blocks" : ""));
    }

    /**
     * {@code /rcmc platform <station> [length] [width] [left|right|both]} — builds a station
     * platform beside a station's stop point, at exactly the height of a metro car's floor.
     *
     * <p><b>Why a command and not just "place the blocks yourself".</b> The floor of a metro car
     * sits {@code CarSeating.METRO_FLOOR_HEIGHT} above the track and a player's step height is
     * 0.6, so a platform even one block out is the difference between walking aboard and jumping
     * at a doorway. Getting that right by hand means counting blocks against a track that is a
     * spline and may be climbing or banking as it passes. The geometry is already known here, so
     * the sensible thing is to lay it out from the geometry.</p>
     *
     * <p>The platform follows the alignment, so it curves with it. Blocks are only placed into
     * air or replaceable terrain — this fills a station out, it does not bulldoze what a builder
     * has already put there.</p>
     */
    private void platform(ICommandSender sender, World world, RcmcWorldState state, String[] args)
        throws CommandException {
        if (args.length < 2) {
            throw new CommandException(
                "/rcmc platform <station> [length] [width] [left|right|both]");
        }
        com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation station =
            state.transit().station(args[1]);
        if (station == null) {
            throw new CommandException("No station named " + args[1] + " — try /rcmc station list");
        }
        TrackSection section = state.network().section(station.stopPoint().sectionId());
        if (section == null) {
            throw new CommandException("Station " + station.name() + " is on section #"
                + station.stopPoint().sectionId() + ", which no longer exists");
        }
        double length = args.length > 2 ? parseDouble(args[2], 4.0D, 200.0D) : 40.0D;
        int width = args.length > 3 ? parseInt(args[3], 1, 12) : 4;
        String sideArg = args.length > 4 ? args[4].toLowerCase(java.util.Locale.ROOT) : "both";
        boolean left = "left".equals(sideArg) || "both".equals(sideArg);
        boolean right = "right".equals(sideArg) || "both".equals(sideArg);
        if (!left && !right) {
            throw new CommandException("Side must be left, right or both — got " + args[4]);
        }

        // Centred on the stop point, which is where the LEAD car berths — so the platform runs
        // back along the train rather than starting at its nose.
        double stop = station.stopPoint().distance();
        double from = Math.max(0.0D, stop - length * 0.75D);
        double to = Math.min(section.totalLength(), stop + length * 0.25D);

        java.util.Set<BlockPos> placed = new java.util.HashSet<>();
        int blocks = 0;
        for (double s = from; s <= to; s += 0.5D) {
            TrackFrame frame = section.frameAtDistance(s);
            // Horizontal projection of the frame's right axis: a platform is level even where the
            // track it serves is banked, because passengers stand on it.
            double rx = frame.right.x;
            double rz = frame.right.z;
            double rl = Math.sqrt(rx * rx + rz * rz);
            if (rl < 1.0e-6D) {
                continue;
            }
            rx /= rl;
            rz /= rl;
            // Derived from the car floor rather than assumed: a platform block's top face is at
            // surfaceY + 1, and the floor it must meet is frame.y + METRO_FLOOR_HEIGHT. Reading
            // that constant means raising the underframe — as happened when it went from 1.0 to
            // 1.5 — moves platforms with it instead of silently un-levelling every station.
            //
            // Rounded, because block tops are integers and the floor need not be: the residual is
            // at most half a block, inside a player's 0.6 step height either way. Flooring could
            // leave a 0.9 step at the doorway, which is the exact problem platforms exist to fix.
            int surfaceY = (int) Math.round(
                frame.position.y + CarSeating.METRO_FLOOR_HEIGHT - 1.0D);
            for (int sign = -1; sign <= 1; sign += 2) {
                if (sign < 0 && !left) {
                    continue;
                }
                if (sign > 0 && !right) {
                    continue;
                }
                for (int i = 0; i < width; i++) {
                    double d = PLATFORM_INNER_OFFSET + i;
                    BlockPos pos = new BlockPos(
                        Math.floor(frame.position.x + rx * d * sign),
                        surfaceY,
                        Math.floor(frame.position.z + rz * d * sign));
                    if (!placed.add(pos)) {
                        continue;
                    }
                    if (!world.getBlockState(pos).getBlock().isReplaceable(world, pos)
                        && !world.isAirBlock(pos)) {
                        continue;
                    }
                    if (i == 0) {
                        // The edge course carries the warning strip, facing the track it serves.
                        EnumFacing facing = EnumFacing.getFacingFromVector(
                            (float) (-rx * sign), 0.0F, (float) (-rz * sign));
                        world.setBlockState(pos, RcmcBlocks.platformEdge.getDefaultState()
                            .withProperty(net.minecraft.block.BlockHorizontal.FACING, facing), 2);
                    } else {
                        world.setBlockState(pos, RcmcBlocks.platform.getDefaultState(), 2);
                    }
                    blocks++;
                }
            }
        }

        reply(sender, TextFormatting.GREEN, "Platform built at " + station.name() + " — "
            + blocks + " blocks, " + fmt(to - from) + " blocks long, " + width + " wide.");
        double step = Math.abs(CarSeating.METRO_FLOOR_HEIGHT
            - Math.round(CarSeating.METRO_FLOOR_HEIGHT));
        reply(sender, TextFormatting.GRAY, step < 0.05D
            ? "  Its surface is level with a metro car's floor, so you can walk straight aboard."
            : "  Its surface sits " + fmt(step) + " blocks off the car floor — a short step, well"
                + " inside what you can walk up.");
    }

    /**
     * How far from the track centreline the platform edge stands, in blocks.
     *
     * <p>The metro body is 3.8 wide, so its side is 1.9 out; this leaves a small gap beyond that
     * — enough that a car sweeping through a curve does not intersect the platform, close enough
     * to step across.
     */
    private static final double PLATFORM_INNER_OFFSET = 2.2D;

    /**
     * {@code /rcmc switch …} — creates, throws, lists and removes track switches.
     *
     * <p>Switches shipped in M3 with a save format, a sync packet and a traversal model, and no way
     * whatsoever to make one: {@code addSwitch} was reachable only from the codec and the sync
     * packet, i.e. from loading or replicating a switch that already existed. In a fresh world it
     * was unreachable, which made every layout a single non-branching alignment. This is the
     * missing authoring path.</p>
     *
     * <p>Ends are named {@code <sectionId> <start|end>} because that is precisely what a
     * {@code SectionEnd} is, and the geometry check that follows — every branch must meet the
     * throat within the join gap — gives an immediate, specific error when the wrong end is
     * named. Pointing at track instead of typing ids belongs to M9's tool; the underlying
     * operation is the same one either way.</p>
     */
    private void trackSwitch(ICommandSender sender, World world, RcmcWorldState state, String[] args)
        throws CommandException {
        if (args.length < 2) {
            throw new CommandException("/rcmc switch create <throatSection> <start|end>"
                + " <branchSection> <start|end> <branchSection> <start|end> [...]"
                + " | list | throw <throatSection> <start|end> [branchIndex]"
                + " | remove <throatSection> <start|end>");
        }
        TrackNetwork network = state.network();
        switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "create": {
                // throat + at least two branches = 3 pairs after "switch create".
                if (args.length < 8 || (args.length - 2) % 2 != 0) {
                    throw new CommandException("/rcmc switch create <throatSection> <start|end>"
                        + " <branchSection> <start|end> <branchSection> <start|end> [...]"
                        + " — a switch needs a throat and at least two branches");
                }
                TrackNetwork.SectionEnd throat = parseEnd(network, args[2], args[3]);
                List<TrackNetwork.SectionEnd> branches = new ArrayList<>();
                for (int i = 4; i + 1 < args.length; i += 2) {
                    branches.add(parseEnd(network, args[i], args[i + 1]));
                }
                try {
                    network.addSwitch(throat, branches);
                }
                catch (IllegalArgumentException e) {
                    throw new CommandException(e.getMessage());
                }
                state.markTrackDirty(world);
                broadcastTrack(world, state);
                reply(sender, TextFormatting.GREEN, "Switch created at " + throat + " -> "
                    + branches.size() + " branches, lined to " + branches.get(0) + ".");
                return;
            }
            case "list": {
                if (network.switches().isEmpty()) {
                    reply(sender, TextFormatting.YELLOW, "No switches. Make one with"
                        + " /rcmc switch create <throatSection> <start|end> <branch> <start|end> ...");
                    return;
                }
                for (TrackNetwork.TrackSwitch sw : network.switches()) {
                    StringBuilder branches = new StringBuilder();
                    for (int i = 0; i < sw.branches().size(); i++) {
                        if (branches.length() > 0) {
                            branches.append(", ");
                        }
                        branches.append(i).append('=').append(sw.branches().get(i));
                        if (i == sw.selectedIndex()) {
                            branches.append(" (lined)");
                        }
                    }
                    reply(sender, TextFormatting.AQUA, sw.throat() + " -> " + branches);
                }
                return;
            }
            case "throw": {
                if (args.length < 4) {
                    throw new CommandException(
                        "/rcmc switch throw <throatSection> <start|end> [branchIndex]");
                }
                TrackNetwork.SectionEnd throat = parseEnd(network, args[2], args[3]);
                TrackNetwork.TrackSwitch sw = network.switchAt(throat);
                if (sw == null) {
                    throw new CommandException("No switch with its throat at " + throat
                        + " — try /rcmc switch list");
                }
                // No index cycles to the next branch, which is what throwing a two-way point
                // means and is the overwhelmingly common case.
                int index = args.length > 4
                    ? parseInt(args[4], 0, sw.branches().size() - 1)
                    : (sw.selectedIndex() + 1) % sw.branches().size();
                network.setSwitchSelection(throat, index);
                state.markTrackDirty(world);
                broadcastTrack(world, state);
                reply(sender, TextFormatting.GREEN, "Switch " + throat + " lined to "
                    + sw.selectedBranch() + " (branch " + index + ").");
                return;
            }
            case "remove": {
                if (args.length < 4) {
                    throw new CommandException("/rcmc switch remove <throatSection> <start|end>");
                }
                TrackNetwork.SectionEnd throat = parseEnd(network, args[2], args[3]);
                if (network.switchAt(throat) == null) {
                    throw new CommandException("No switch with its throat at " + throat);
                }
                network.removeSwitch(throat);
                state.markTrackDirty(world);
                broadcastTrack(world, state);
                reply(sender, TextFormatting.GREEN, "Switch removed from " + throat
                    + " — those ends now lead nowhere.");
                return;
            }
            default:
                throw new CommandException("Unknown switch subcommand " + args[1]);
        }
    }

    /** Parses a {@code <sectionId> <start|end>} pair, checking the section exists as it goes. */
    private TrackNetwork.SectionEnd parseEnd(TrackNetwork network, String sectionArg, String endArg)
        throws CommandException {
        int sectionId = parseInt(sectionArg);
        if (network.section(sectionId) == null) {
            throw new CommandException("No section #" + sectionId + " — try /rcmc info");
        }
        TrackNetwork.End end;
        if ("start".equalsIgnoreCase(endArg)) {
            end = TrackNetwork.End.START;
        } else if ("end".equalsIgnoreCase(endArg)) {
            end = TrackNetwork.End.END;
        } else {
            throw new CommandException("Section end must be start or end, got " + endArg);
        }
        return new TrackNetwork.SectionEnd(sectionId, end);
    }

    /**
     * {@code /rcmc station <name>} — creates (or moves) a named transit station at the track
     * point nearest the player. Also {@code list} and {@code remove <name>}.
     */
    private void station(ICommandSender sender, World world, RcmcWorldState state, String[] args)
        throws CommandException {
        if (args.length < 2) {
            throw new CommandException("/rcmc station <name> | list | remove <name>");
        }
        com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem transit = state.transit();
        switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "list": {
                if (transit.stations().isEmpty()) {
                    reply(sender, TextFormatting.YELLOW, "No stations yet.");
                    return;
                }
                for (com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation s
                    : transit.stations()) {
                    reply(sender, TextFormatting.AQUA, s.name() + " — section "
                        + s.stopPoint().sectionId() + " @ " + fmt(s.stopPoint().distance()));
                }
                return;
            }
            case "remove": {
                if (args.length < 3) {
                    throw new CommandException("/rcmc station remove <name>");
                }
                if (transit.removeStation(args[2]) == null) {
                    throw new CommandException("No station named " + args[2]);
                }
                state.markTrackDirty(world);
                RcmcNetwork.sendToAllIn(new com.micatechnologies.minecraft.rcmc.net.PacketTransitSync(transit), world.provider.getDimension());
                reply(sender, TextFormatting.GREEN, "Removed station " + args[2] + ".");
                return;
            }
            default: {
                EntityPlayer player = getCommandSenderAsPlayer(sender);
                com.micatechnologies.minecraft.rcmc.track.TrackPicker.Hit hit =
                    com.micatechnologies.minecraft.rcmc.track.TrackPicker.pick(state.network(),
                        new Vec3(player.posX, player.posY, player.posZ), 16.0D);
                if (hit == null) {
                    throw new CommandException("No track within 16 blocks — stand at the platform");
                }
                transit.addStation(new com.micatechnologies.minecraft.rcmc.physics.transit
                    .TransitStation(args[1], hit.ref));
                state.markTrackDirty(world);
                RcmcNetwork.sendToAllIn(new com.micatechnologies.minecraft.rcmc.net.PacketTransitSync(transit), world.provider.getDimension());
                reply(sender, TextFormatting.GREEN, "Station " + args[1] + " at section "
                    + hit.ref.sectionId() + " @ " + fmt(hit.ref.distance())
                    + " (trains stop with their lead car here).");
            }
        }
    }

    /**
     * How long the doors stay open at a stop, in ticks.
     *
     * <p>Ten seconds, raised from five once metro cars became genuinely boardable. Five is a
     * realistic off-peak dwell and was fine while nobody could get on, but a player has to notice
     * the train has berthed, walk to a door and right-click — and a dwell that expires mid-approach
     * reads as the doors being broken rather than as having been slow. Real dwells run 20–30
     * seconds at busy stations, so this is still on the brisk side of realistic.</p>
     */
    private static final int METRO_DWELL_TICKS = 200;

    /** Default metro drive used by {@code /rcmc line start} until per-stock configs exist. */
    private static com.micatechnologies.minecraft.rcmc.physics.transit.TransitStopController
        metroController(double cruiseSpeed) {
        com.micatechnologies.minecraft.rcmc.physics.transit.TrainDriver driver =
            new com.micatechnologies.minecraft.rcmc.physics.transit.TrainDriver(
                new com.micatechnologies.minecraft.rcmc.physics.transit.TractionProfile(
                    1.2D, 24.0D, 22.0D),
                1.2D, 2.0D, 1.5D, RcmcConstants.SECONDS_PER_TICK);
        return new com.micatechnologies.minecraft.rcmc.physics.transit.TransitStopController(
            driver, cruiseSpeed, 0.75D, 30, METRO_DWELL_TICKS, 30);
    }

    /**
     * {@code /rcmc line …} — create lines from stations, and enter/withdraw trains from
     * service. See each branch for syntax.
     */
    private void line(ICommandSender sender, World world, RcmcWorldState state, String[] args)
        throws CommandException {
        if (args.length < 2) {
            throw new CommandException(
                "/rcmc line create <name> <loop|shuttle> <stationA> <stationB> [...] | list"
                    + " | remove <name> | start <name> <trainId> [cruiseSpeed] | stop <trainId>"
                    + " | signals <name> <count|off>");
        }
        com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem transit = state.transit();
        switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "create": {
                if (args.length < 6) {
                    throw new CommandException(
                        "/rcmc line create <name> <loop|shuttle> <stationA> <stationB> [...]");
                }
                boolean loop;
                if ("loop".equalsIgnoreCase(args[3])) {
                    loop = true;
                } else if ("shuttle".equalsIgnoreCase(args[3])) {
                    loop = false;
                } else {
                    throw new CommandException("Line kind must be loop or shuttle, got " + args[3]);
                }
                java.util.List<com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation>
                    stops = new ArrayList<>();
                for (int i = 4; i < args.length; i++) {
                    com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation s =
                        transit.station(args[i]);
                    if (s == null) {
                        throw new CommandException("No station named " + args[i]
                            + " — create it first with /rcmc station " + args[i]);
                    }
                    stops.add(s);
                }
                transit.addLine(new com.micatechnologies.minecraft.rcmc.physics.transit
                    .TransitLine(args[2], stops, loop));
                state.markTrackDirty(world);
                RcmcNetwork.sendToAllIn(new com.micatechnologies.minecraft.rcmc.net.PacketTransitSync(transit), world.provider.getDimension());
                reply(sender, TextFormatting.GREEN, "Line " + args[2] + " created — "
                    + stops.size() + " stops, " + (loop ? "loop" : "shuttle") + ".");
                return;
            }
            case "list": {
                if (transit.lines().isEmpty()) {
                    reply(sender, TextFormatting.YELLOW, "No lines yet.");
                    return;
                }
                for (com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine l
                    : transit.lines()) {
                    StringBuilder stops = new StringBuilder();
                    for (com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation s
                        : l.stations()) {
                        if (stops.length() > 0) {
                            stops.append(" → ");
                        }
                        stops.append(s.name());
                    }
                    com.micatechnologies.minecraft.rcmc.physics.transit.LineSignals sig =
                        transit.signalsFor(l.name());
                    reply(sender, TextFormatting.AQUA, l.name() + " ("
                        + (l.isLoop() ? "loop" : "shuttle") + "): " + stops
                        + (sig == null ? "" : "  [" + sig.blocks().size() + " signal blocks]"));
                }
                return;
            }
            case "remove": {
                if (args.length < 3) {
                    throw new CommandException("/rcmc line remove <name>");
                }
                if (transit.removeLine(args[2]) == null) {
                    throw new CommandException("No line named " + args[2]);
                }
                state.markTrackDirty(world);
                RcmcNetwork.sendToAllIn(new com.micatechnologies.minecraft.rcmc.net.PacketTransitSync(transit), world.provider.getDimension());
                reply(sender, TextFormatting.GREEN, "Removed line " + args[2] + ".");
                return;
            }
            case "start": {
                if (args.length < 4) {
                    throw new CommandException("/rcmc line start <name> <trainId> [cruiseSpeed]");
                }
                int trainId = parseInt(args[3]);
                Train train = state.trains().train(trainId);
                if (train == null) {
                    throw new CommandException("No train with id " + trainId);
                }
                double cruise = args.length > 4 ? parseDouble(args[4], 1.0D, 40.0D) : 15.0D;
                try {
                    com.micatechnologies.minecraft.rcmc.physics.transit.LineService service =
                        transit.enterService(trainId, train, state.network(), args[2],
                            metroController(cruise));
                    reply(sender, TextFormatting.GREEN, "Train #" + trainId + " in service on "
                        + service.line().name() + ", first stop "
                        + service.line().station(service.currentStopIndex()).name()
                        + " at " + fmt(cruise) + " blocks/s.");
                }
                catch (IllegalArgumentException e) {
                    throw new CommandException(e.getMessage());
                }
                return;
            }
            case "stop": {
                if (args.length < 3) {
                    throw new CommandException("/rcmc line stop <trainId>");
                }
                int trainId = parseInt(args[2]);
                if (transit.exitService(trainId) == null) {
                    throw new CommandException("Train " + trainId + " is not in service");
                }
                reply(sender, TextFormatting.GREEN, "Train #" + trainId + " withdrawn from service.");
                return;
            }
            case "signals": {
                signals(sender, world, state, transit, args);
                return;
            }
            default:
                throw new CommandException("Unknown line subcommand " + args[1]);
        }
    }

    /**
     * {@code /rcmc line signals <name> <count|off>} — installs (or clears) block signalling on a
     * line, dividing every section the line's stations sit on into {@code count} equal blocks.
     *
     * <p>This is what makes a second train on a metro line safe. Without it a service runs with
     * unlimited movement authority and will happily drive into the back of the train ahead: the
     * ATO driver brakes for stations and for its authority, and with no signals installed its
     * authority is {@code NO_STOP}. With signals, a train's permission ends short of any block
     * another train occupies, and being held at a red is simply a berth with the doors shut — the
     * same braking law, no second mechanism.</p>
     *
     * <p><b>Equal division is a starting point, not the end state</b>, exactly as it is for the
     * coaster {@code /rcmc block}: a real layout puts boundaries where a train can sensibly be
     * held — approaching a platform, not mid-curve. Placing them individually is M9's tool's job;
     * this exists so the capability is reachable at all, which until now it was not.</p>
     *
     * <p>Two limits carry over from the coaster block system for the same reasons: occupancy is
     * tracked by lead car only, so blocks must be comfortably longer than the longest train, and N
     * trains on N blocks deadlock. Unlike the coaster system this one is direction-free, so a
     * shuttle line running both ways over one track is safe.</p>
     */
    private void signals(ICommandSender sender, World world, RcmcWorldState state,
                         com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem transit,
                         String[] args) throws CommandException {
        if (args.length < 4) {
            throw new CommandException("/rcmc line signals <name> <count|off>");
        }
        com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine line = transit.line(args[2]);
        if (line == null) {
            throw new CommandException("No line named " + args[2] + " — try /rcmc line list");
        }

        if ("off".equalsIgnoreCase(args[3])) {
            if (transit.signalsFor(line.name()) == null) {
                throw new CommandException("Line " + line.name() + " has no signalling");
            }
            transit.setSignals(line.name(), null);
            state.markTrackDirty(world);
            reply(sender, TextFormatting.YELLOW, "Signalling removed from " + line.name()
                + " — trains on it are no longer separated.");
            return;
        }

        int count = parseInt(args[3], 2, 32);
        // The line's stations are the only track we know for certain belongs to it; a route walk
        // could cross sections they never touch. Signalling what the line demonstrably occupies is
        // the honest subset — and for a single-alignment line, which is every line buildable
        // today, it is the whole thing.
        java.util.LinkedHashSet<Integer> sectionIds = new java.util.LinkedHashSet<>();
        for (com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation station
            : line.stations()) {
            sectionIds.add(station.stopPoint().sectionId());
        }

        List<BlockSection> blocks = new ArrayList<>();
        for (int sectionId : sectionIds) {
            TrackSection section = state.network().section(sectionId);
            if (section == null) {
                throw new CommandException("Line " + line.name() + " stops on section #" + sectionId
                    + ", which no longer exists — recreate the line first");
            }
            double length = section.totalLength();
            for (int i = 0; i < count; i++) {
                double from = length * i / count;
                // Last block ends exactly at the section length, so no unsignalled sliver is left
                // at the seam — same reasoning as /rcmc block.
                double to = i == count - 1 ? length : length * (i + 1) / count;
                blocks.add(new BlockSection("s" + sectionId + "-b" + (i + 1), sectionId, from, to));
            }
        }

        transit.setSignals(line.name(),
            new com.micatechnologies.minecraft.rcmc.physics.transit.LineSignals(blocks,
                com.micatechnologies.minecraft.rcmc.physics.transit.LineSignals.DEFAULT_MARGIN,
                SIGNAL_HORIZON));
        state.markTrackDirty(world);

        reply(sender, TextFormatting.GREEN, "Line " + line.name() + " signalled — " + blocks.size()
            + " blocks across " + sectionIds.size() + " section(s).");
        reply(sender, TextFormatting.GRAY, "  Run at most " + (blocks.size() - 1)
            + " trains here: " + blocks.size() + " trains on " + blocks.size()
            + " blocks deadlocks.");
    }

    /**
     * How far ahead, in blocks, a signalled line looks for occupancy. Generous on purpose: it need
     * only comfortably exceed the longest braking distance on the line, and at the 15 blocks/s
     * default cruise and the metro driver's service brake that is under 100 blocks.
     */
    private static final double SIGNAL_HORIZON = 500.0D;

    /**
     * {@code /rcmc build …} — settings for the in-hand track builder.
     *
     * <p>These live on a command rather than in the item's own interaction because they are modal
     * state ("every node from here on is banked 30°"), and there are only so many click
     * combinations available on one item before the tool becomes unlearnable. A proper GUI
     * replaces this in Phase 2.2.</p>
     */
    private void build(ICommandSender sender, String[] args) throws CommandException {
        EntityPlayer player = getCommandSenderAsPlayer(sender);
        TrackBuildSession session = TrackBuildSession.of(player.getUniqueID());
        String sub = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "status";
        // Bank and circuit mode both change what the ghost preview should show, so every path
        // below re-mirrors the session rather than only the ones that add nodes.

        switch (sub) {
            case "bank": {
                if (args.length < 3) {
                    throw new CommandException("/rcmc build bank <degrees>");
                }
                double degrees = parseDouble(args[2], -180.0D, 180.0D);
                session.setBankDegrees(degrees);
                pushSession(player, session);
                reply(sender, TextFormatting.GREEN, "Nodes placed from now on will be banked "
                    + String.format("%.0f", session.bankDegrees()) + "°.");
                break;
            }
            case "circuit": {
                boolean closing = args.length < 3 || Boolean.parseBoolean(args[2]);
                session.setClosing(closing);
                pushSession(player, session);
                reply(sender, TextFormatting.GREEN, closing
                    ? "Next section will close into a circuit (needs at least 3 nodes)."
                    : "Next section will be an open run.");
                break;
            }
            case "cancel": {
                int discarded = session.size();
                session.reset();
                pushSession(player, session);
                reply(sender, TextFormatting.YELLOW,
                    "Discarded " + discarded + " pending node(s) and reset bank/circuit mode.");
                break;
            }
            case "status":
            default: {
                pushSession(player, session);
                reply(sender, TextFormatting.AQUA, "Pending nodes: " + session.size()
                    + "  Bank: " + String.format("%.0f", session.bankDegrees()) + "°"
                    + "  Mode: " + (session.isClosing() ? "circuit" : "open"));
                break;
            }
        }
    }

    /**
     * Re-sends the whole track to everyone in the dimension.
     *
     * <p>Necessary after any edit: clients derive car positions from the geometry, so a client
     * holding stale track would place cars along the old curve while the server used the new one.
     * Wholesale resend is fine at this scale and will be replaced by deltas with the editor.</p>
     */
    private static void broadcastTrack(World world, RcmcWorldState state) {
        int dimension = world.provider.getDimension();
        RcmcNetwork.sendToAllIn(new PacketTrackSync(state.network()), dimension);
        // Ride hardware is drawn, so its spans travel with the geometry. Sending one without the
        // other leaves a lift hill rendering as plain track until the next full sync.
        RcmcNetwork.sendToAllIn(new PacketElementSync(state.elements()), dimension);
    }

    private void clear(ICommandSender sender, World world, RcmcWorldState state) {
        int trains = state.trains().count();
        int sections = state.network().sectionCount();
        state.elements().clear();

        for (EntityCoasterCar car : new ArrayList<>(
            world.getEntities(EntityCoasterCar.class, entity -> true))) {
            car.setDead();
        }
        state.trains().clear();
        state.network().clear();
        state.markTrackDirty(world);
        // Order matters only in that BOTH must be sent. Clients hold their own copies of the
        // track and the trains; dropping only the track leaves every client with a train pointing
        // at a section that no longer exists, which is what used to crash the client tick.
        RcmcNetwork.sendToAllIn(PacketTrainRemove.all(), world.provider.getDimension());
        broadcastTrack(world, state);

        reply(sender, TextFormatting.YELLOW,
            "Cleared " + sections + " section(s) and " + trains + " train(s).");
    }

    private void info(ICommandSender sender, RcmcWorldState state) {
        reply(sender, TextFormatting.AQUA, "Sections: " + state.network().sectionCount()
            + "  Trains: " + state.trains().count()
            + "  Elements: " + state.elements().count());
        for (TrackSection section : state.network().sections()) {
            reply(sender, TextFormatting.GRAY, "  #" + section.id() + " "
                + (section.isClosed() ? "circuit" : "open") + ", "
                + String.format("%.1f", section.totalLength()) + " blocks, "
                + section.nodes().size() + " nodes");
        }
        // Elements are listed with their spans because "is the lift actually there, and does it
        // cover where the train runs" is not answerable by looking at the track — a lift that was
        // never created and one that spans zero blocks look identical in the world.
        for (com.micatechnologies.minecraft.rcmc.physics.element.RideElement element
            : state.elements().elements()) {
            String type = com.micatechnologies.minecraft.rcmc.track.storage.ElementCodec.typeOf(element);
            reply(sender, TextFormatting.GRAY, "  " + (type == null ? "?" : type)
                + " on section " + element.sectionId() + "  "
                + String.format("%.1f", element.startDistance()) + " - "
                + String.format("%.1f", element.endDistance()) + " blocks");
        }
        for (Integer signalled : state.blocks().sectionIds()) {
            BlockSystem system = state.blocks().get(signalled);
            reply(sender, TextFormatting.GRAY, "  section " + signalled + " signalled: "
                + system.blockCount() + " blocks, safety "
                + (system.isSafetyEnabled() ? "on" : "OFF")
                + (system.hasCollision() ? ", COLLISION DETECTED" : ""));
        }
        for (java.util.Map.Entry<Integer, Train> entry : state.trains().asMap().entrySet()) {
            Train train = entry.getValue();
            reply(sender, TextFormatting.GRAY, "  train #" + entry.getKey() + " "
                + train.spec().carCount() + " cars, v="
                + String.format("%.2f", train.velocity()) + " blocks/s, "
                + train.status() + " @ " + train.reference());
        }
    }

    /**
     * {@code /rcmc rate [sectionId]} — simulates a train round the section and reports its ratings.
     *
     * <p>This runs the ride, it does not inspect it. {@link RideRater} steps the same
     * {@link PhysicsIntegrator} over the same geometry the live train uses, so the numbers describe
     * what a rider would actually experience rather than what the layout looks like it should do.
     * That also makes it a genuinely useful diagnostic: a coaster that stalls, or one whose lift
     * never engages, shows up here as a fault rather than as a plausible-looking rating.</p>
     *
     * <p>Simulation is entirely offline — no entity is spawned and no state is touched — so it is
     * safe to run on a circuit that already has a train on it.</p>
     */
    private void rate(ICommandSender sender, RcmcWorldState state, String[] args)
        throws CommandException {
        TrackSection section;
        if (args.length > 1) {
            int id = parseInt(args[1]);
            section = state.network().section(id);
            if (section == null) {
                throw new CommandException("No section #" + id + " — try /rcmc info");
            }
        }
        else {
            // Rating with no argument should do the obvious thing on a park with one coaster in it.
            if (state.network().sectionCount() != 1) {
                throw new CommandException("Specify a section: /rcmc rate <sectionId>");
            }
            section = state.network().sections().iterator().next();
        }

        RideRater rater = RideRater.standard(
            new PhysicsIntegrator(RcmcConfig.gravity, RcmcConfig.rollingResistance,
                RcmcConfig.airDrag, RcmcConfig.maxSpeed),
            RcmcConfig.gravity);
        // Simulate once and derive the rating from that, rather than calling rate() — the run is
        // what is expensive, and the statistics are wanted here in their own right.
        RideStatistics stats = rater.simulate(state.network(), state.elements(),
            new TrackRef(section.id(), 0.0D), TrainSpec.singleCar(), 0.0D);
        RideRating rating = RideRating.from(stats);

        reply(sender, TextFormatting.GOLD, "Ride rating — section #" + section.id());
        reply(sender, TextFormatting.WHITE, "  Excitement " + score(rating.excitement)
            + "  (" + rating.excitementVerdict + ")");
        reply(sender, TextFormatting.WHITE, "  Intensity  " + score(rating.intensity)
            + "  (" + rating.intensityVerdict + ")");
        reply(sender, TextFormatting.WHITE, "  Nausea     " + score(rating.nausea)
            + "  (" + rating.nauseaVerdict + ")");
        reply(sender, TextFormatting.GRAY, "  " + String.format("%.1f", stats.totalLengthBlocks)
            + " blocks, " + String.format("%.1f", stats.rideDurationSeconds) + "s, top speed "
            + String.format("%.1f", stats.maxSpeedBlocksPerSecond) + " blocks/s, drop "
            + String.format("%.1f", stats.maxDropHeightBlocks) + " blocks");
        reply(sender, TextFormatting.GRAY, "  G: vert +"
            + String.format("%.2f", stats.peakPositiveVerticalG) + " / "
            + String.format("%.2f", stats.peakNegativeVerticalG) + ", lat "
            + String.format("%.2f", stats.peakLateralG) + ", airtime "
            + String.format("%.1f", stats.totalAirtimeSeconds) + "s, "
            + stats.inversionCount + " inversions");

        // The safety verdict is the part a builder most needs to see, so it is not folded into the
        // numbers above: a ride can rate well and still be one that throws its riders out.
        if (rating.safety.safe) {
            reply(sender, TextFormatting.GREEN, "  Safety: passed");
        }
        else {
            reply(sender, TextFormatting.RED, "  Safety: FAILED");
            for (String violation : rating.safety.violations) {
                reply(sender, TextFormatting.RED, "    - " + violation);
            }
        }
        if (!stats.completedWithoutFault()) {
            reply(sender, TextFormatting.YELLOW, "  Train did not complete the circuit: "
                + stats.finalStatus + " — the ratings above describe an incomplete run.");
        }
    }


    /**
     * {@code /rcmc rmsection <sectionId>} — deletes one section and everything anchored to it.
     *
     * <p>{@code /rcmc clear} wipes the whole world's track, which is far too blunt once a park has
     * more than one coaster in it. Every other subcommand already works on a section id, so being
     * unable to delete by one was a gap rather than a decision.</p>
     *
     * <p>Ride hardware and trains on the section go with it. Leaving either behind would strand
     * them: an element addresses track by {@code (sectionId, distance)} and a train sits at a
     * {@link TrackRef}, so both would point at a section that no longer exists — which is exactly
     * the state that crashed a client when {@code /rcmc clear} first shipped without removing
     * trains.</p>
     */
    private void removeSection(ICommandSender sender, World world, RcmcWorldState state,
                               String[] args) throws CommandException {
        if (args.length < 2) {
            throw new CommandException("/rcmc rmsection <sectionId>");
        }
        int sectionId = parseInt(args[1]);
        TrackSection section = state.network().section(sectionId);
        if (section == null) {
            throw new CommandException("No section #" + sectionId + " — try /rcmc info");
        }

        // Trains first, and each one told to the clients explicitly. A client that keeps a train
        // whose section has gone will try to advance it and throw every tick.
        int dimension = world.provider.getDimension();
        List<Integer> doomed = new ArrayList<>();
        for (java.util.Map.Entry<Integer, Train> entry : state.trains().asMap().entrySet()) {
            Train train = entry.getValue();
            if (train.reference() != null && train.reference().sectionId() == sectionId) {
                doomed.add(entry.getKey());
            }
        }
        for (Integer trainId : doomed) {
            state.trains().remove(trainId);
            RcmcNetwork.sendToAllIn(new PacketTrainRemove(trainId), dimension);
        }
        for (EntityCoasterCar car : world.getEntities(EntityCoasterCar.class, car -> true)) {
            if (doomed.contains(car.trainId())) {
                car.setDead();
            }
        }

        int removedElements = state.elements().removeForSection(sectionId);
        state.blocks().remove(sectionId);
        state.network().removeSection(sectionId);
        state.markTrackDirty(world);

        RcmcNetwork.sendToAllIn(new PacketTrackSync(state.network()), dimension);
        RcmcNetwork.sendToAllIn(new PacketElementSync(state.elements()), dimension);

        reply(sender, TextFormatting.GREEN, "Removed section #" + sectionId + " — "
            + String.format("%.1f", section.totalLength()) + " blocks, "
            + section.nodes().size() + " nodes.");
        if (!doomed.isEmpty() || removedElements > 0) {
            reply(sender, TextFormatting.GRAY, "  Also removed " + doomed.size() + " train(s) and "
                + removedElements + " ride element(s) that were anchored to it.");
        }
    }

    /**
     * {@code /rcmc block <sectionId> <count|off>} — divides a section into equal block sections, or
     * removes its signalling.
     *
     * <p>Block signalling is what makes more than one train on a circuit safe: a train is not
     * allowed to enter a block another train occupies, and is braked to a stop at the boundary if
     * it would. Without it, a second train on a circuit will eventually run into the first.</p>
     *
     * <p>Equal division is a starting point, not the end state — real parks put boundaries where
     * the track allows a train to be held (a level brake run, not mid-drop), which is a placement
     * decision the builder should make. This exists so the capability is usable now.</p>
     *
     * <p>Two limits are worth knowing before relying on it. Occupancy is tracked by lead car only,
     * so blocks must be comfortably longer than the trains using them. And N trains on N
     * wall-to-wall blocks deadlock permanently — a proven property of exclusive fixed-block
     * signalling, not a defect — so leave fewer trains than blocks.</p>
     */
    private void block(ICommandSender sender, RcmcWorldState state, String[] args)
        throws CommandException {
        if (args.length < 3) {
            throw new CommandException("/rcmc block <sectionId> <count|off>");
        }
        int sectionId = parseInt(args[1]);
        TrackSection section = state.network().section(sectionId);
        if (section == null) {
            throw new CommandException("No section #" + sectionId + " — try /rcmc info");
        }

        if ("off".equalsIgnoreCase(args[2])) {
            if (state.blocks().remove(sectionId) == null) {
                throw new CommandException("Section #" + sectionId + " has no block signalling");
            }
            reply(sender, TextFormatting.YELLOW, "Block signalling removed from section #"
                + sectionId + " — trains on it are no longer separated.");
            return;
        }

        int count = parseInt(args[2], 2, 32);
        double length = section.totalLength();
        BlockSystem system = new BlockSystem(section.isClosed(), true,
            BLOCK_BRAKE_DECELERATION, RcmcConstants.SECONDS_PER_TICK);
        for (int i = 0; i < count; i++) {
            double from = length * i / count;
            // The last block ends exactly at the section's length rather than at a rounded
            // fraction of it, so no sliver of unsignalled track is left at the seam.
            double to = i == count - 1 ? length : length * (i + 1) / count;
            system.addBlock(new BlockSection("b" + (i + 1), sectionId, from, to));
        }
        state.blocks().put(sectionId, system);

        reply(sender, TextFormatting.GREEN, "Section #" + sectionId + " divided into " + count
            + " blocks of " + String.format("%.1f", length / count) + " blocks each"
            + (section.isClosed() ? " (circuit — the last block wraps to the first)" : ""));
        reply(sender, TextFormatting.GRAY, "  Run at most " + (count - 1)
            + " trains here: " + count + " trains on " + count + " blocks deadlocks.");
    }

    /**
     * How hard a block brake stops a train, in blocks/s². Deliberately firm — a block brake exists
     * to prevent a collision, and a gentle one that fails to stop in time is worse than none.
     */
    private static final double BLOCK_BRAKE_DECELERATION = 4.0D;

    /** Ratings read as RCT-style two-decimal scores rather than raw doubles. */
    private static String score(double value) {
        return String.format("%.2f", value);
    }

    private static void reply(ICommandSender sender, TextFormatting colour, String message) {
        sender.sendMessage(new TextComponentString(colour + message));
    }
}
