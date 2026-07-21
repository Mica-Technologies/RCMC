package com.micatechnologies.minecraft.rcmc.command;

import com.micatechnologies.minecraft.rcmc.builder.TrackBuildSession;
import com.micatechnologies.minecraft.rcmc.debug.DemoCoaster;
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
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.rating.RideRater;
import com.micatechnologies.minecraft.rcmc.rating.RideRating;
import com.micatechnologies.minecraft.rcmc.rating.RideStatistics;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
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
        return "/rcmc <demo|train|clear|info|build|paint|rate|block|rmsection>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args, BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "demo", "train", "clear", "info", "build",
                "paint", "rate", "block", "rmsection");
        }
        if (args.length == 2 && "build".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "bank", "circuit", "status", "cancel");
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
            case "rmsection":
                removeSection(sender, world, state, args);
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
        // Start inside the station if there is one, so a train spawns where a ride would load it
        // rather than at whatever point the geometry happens to call distance zero.
        double startDistance = state.elements().elements().stream()
            .filter(e -> e.sectionId() == sectionId && e instanceof StationPlatform)
            // Exactly ON the stop point, not short of it. A station brakes an arriving train but
            // never pushes one, so a train spawned at rest before the stop point simply sits there
            // forever instead of dwelling and dispatching.
            .mapToDouble(e -> ((StationPlatform) e).stopDistance())
            .findFirst().orElse(0.0D);

        TrainSpec spec = new TrainSpec(carCount, 3.0D, 0.5D, 4);
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
