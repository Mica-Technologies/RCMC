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
import com.micatechnologies.minecraft.rcmc.physics.element.BrakeRun;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.RcmcConfig;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
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
        return "/rcmc <demo|train|clear|info|build>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args, BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "demo", "train", "clear", "info", "build");
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
        for (java.util.Map.Entry<Integer, Train> entry : state.trains().asMap().entrySet()) {
            Train train = entry.getValue();
            reply(sender, TextFormatting.GRAY, "  train #" + entry.getKey() + " "
                + train.spec().carCount() + " cars, v="
                + String.format("%.2f", train.velocity()) + " blocks/s, "
                + train.status() + " @ " + train.reference());
        }
    }

    private static void reply(ICommandSender sender, TextFormatting colour, String message) {
        sender.sendMessage(new TextComponentString(colour + message));
    }
}
