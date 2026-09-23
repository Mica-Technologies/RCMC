package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.Rcmc;
import com.micatechnologies.minecraft.rcmc.RcmcConfig;
import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.net.PacketElementSync;
import com.micatechnologies.minecraft.rcmc.net.PacketTrackSync;
import com.micatechnologies.minecraft.rcmc.net.PacketTrainSync;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.storage.RcmcTrackData;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Per-world RCMC state: the track network and the trains running on it.
 *
 * <p>Exists on both sides, with different provenance. On the server the network is loaded from
 * {@link RcmcTrackData} and is authoritative. On the client it starts empty and is filled by sync
 * packets — which is why {@link #of} never falls back to reading saved data on a remote world: a
 * client's {@code getPerWorldStorage()} is never populated from disk, so doing so would silently
 * hand back an empty network that looks legitimate.</p>
 *
 * <p>Trains are held in memory on both sides and simulated identically. The server corrects the
 * client periodically; between corrections the client predicts, which is only sound because
 * {@code physics} is deterministic and free of Minecraft types.</p>
 */
public final class RcmcWorldState {

    /**
     * Keyed weakly so an unloaded world's state can be collected even if the explicit unload
     * hook is missed — a dimension leak here would pin the whole world object.
     */
    private static final Map<World, RcmcWorldState> STATES = new WeakHashMap<>();

    private TrackNetwork network;

    /**
     * Trains in this world.
     *
     * <p>On the server this is {@code RcmcTrackData}'s own manager, not a copy — the save writes
     * whatever this reference holds, so a second instance would mean the world running one set of
     * trains and persisting another. On a client it is a local mirror populated by
     * {@code PacketTrainSync}.</p>
     */
    private TrainManager trains = new TrainManager();

    /**
     * Server-side undo/redo of the authored state. {@code null} on a client, where the network is a
     * synced mirror with nothing local to undo. Seeded in {@link #of} with the freshly loaded state,
     * so the first edit's pre-edit snapshot is captured.
     */
    private EditHistory history;

    /**
     * Ride hardware. Server-side this is loaded from saved data; client-side it stays empty —
     * elements only ever affect the simulation through the acceleration they produce, and the
     * client learns that indirectly from the corrected train state rather than by running the
     * elements itself. Predicting ride hardware locally would need every element's internal state
     * (a station's dwell counter, a launch's phase) synced too, for no visible gain.
     */
    private com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet elements =
        new com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet();

    /**
     * Transit: authored stations/lines (persisted with the track) and running services. Server
     * truth, like {@link #elements} and for the same reason — a service's controller state (door
     * timers, jerk limiter) would all need syncing for the client to predict it, for no visible
     * gain over the periodic train corrections. The client's copy stays empty for now; M7's
     * signage sync will change that for the authored part.
     */
    private com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem transit =
        new com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem();

    /**
     * Each coaster's operator state — open or closed, dispatch mode, e-stop. Server-side it is
     * {@code RcmcTrackData}'s own instance, saved beside the trains; a client never holds one and
     * learns a ride's state from the operator panel's packets.
     */
    private com.micatechnologies.minecraft.rcmc.physics.ride.RideControllers rides =
        new com.micatechnologies.minecraft.rcmc.physics.ride.RideControllers();

    public com.micatechnologies.minecraft.rcmc.physics.ride.RideControllers rides() {
        return rides;
    }

    /** Watches door phases and plays the metro sounds; server-side, decoration only. */
    private final com.micatechnologies.minecraft.rcmc.sound.TransitSounds transitSounds =
        new com.micatechnologies.minecraft.rcmc.sound.TransitSounds();

    private final boolean remote;

    private RcmcWorldState(TrackNetwork network, boolean remote) {
        this.network = network;
        this.remote = remote;
    }

    /**
     * State for {@code world}, creating it on first use.
     *
     * <p>Returns {@code null} only if {@code world} is null. Safe to call from a render or tick
     * path; the lookup is a hash map hit after the first call.</p>
     */
    public static RcmcWorldState of(World world) {
        if (world == null) {
            return null;
        }
        RcmcWorldState existing = STATES.get(world);
        if (existing != null) {
            return existing;
        }
        RcmcWorldState created;
        if (world.isRemote) {
            created = new RcmcWorldState(new TrackNetwork(), true);
        }
        else {
            RcmcTrackData data = RcmcTrackData.get(world);
            created = new RcmcWorldState(data.network(), false);
            created.elements = data.elements();
            created.transit = data.transit();
            created.trains = data.trains();
            created.rides = data.rides();
            // Seed history with the loaded state so the first edit is undoable.
            created.history = new EditHistory(data.snapshot(), EditHistory.DEFAULT_DEPTH);
            // Must happen after the fields above are installed: putting a train back into service
            // walks the network to find its next stop.
            STATES.put(world, created);
            created.resumeServices(data);
            return created;
        }
        STATES.put(world, created);
        return created;
    }

    /**
     * Ticks until the next check that every train has its car entities.
     *
     * <p>Per world rather than on the event handler, which is one shared instance across every
     * dimension.</p>
     */
    private int carReconcileCountdown;

    /**
     * How often to check that trains have their cars, in ticks.
     *
     * <p>Once a second. It cannot be a one-off at load: {@code World.spawnEntity} refuses silently
     * while the target chunk is unloaded, which on the first tick after a world load it always is —
     * and cars are not saved with their chunks, so an unloaded chunk destroys them permanently. See
     * {@link TrainEntities#spawnMissingCars}. A second is short enough that nobody watches a train
     * arrive without its cars, and the check is a scan of already-loaded entities.</p>
     */
    private static final int CAR_RECONCILE_INTERVAL_TICKS = 20;

    /**
     * Puts saved services back into service.
     *
     * <p>Runs once, on first access to a freshly loaded world. Separate from the codec because it
     * needs the network to walk, which the codec has no business touching.</p>
     */
    private void resumeServices(RcmcTrackData data) {
        net.minecraft.nbt.NBTTagCompound saved = data.takePendingServices();
        int resumed = 0;
        if (saved != null) {
            resumed = com.micatechnologies.minecraft.rcmc.track.storage.TrainCodec.readServices(
                saved, trains, transit, network,
                com.micatechnologies.minecraft.rcmc.RcmcConstants.SECONDS_PER_TICK);
        }
        if (!trains.isEmpty()) {
            // Logged because a park coming back wrong is otherwise silent, and the difference
            // between "the save lost my trains" and "the save has them but nothing spawned" is the
            // first thing anyone needs to know. It is one line per world load, not per tick.
            com.micatechnologies.minecraft.rcmc.Rcmc.LOGGER.info(
                "Restored {} train(s) and resumed {} service(s) on {} track section(s)",
                trains.count(), resumed, network.sections().size());
        }
        // Check for missing cars on the very next tick rather than a second in: a world that just
        // loaded is the case that most needs them, even though it usually takes a few attempts
        // before the chunks are there to spawn into.
        carReconcileCountdown = 0;
    }

    public TrackNetwork network() {
        return network;
    }

    public TrainManager trains() {
        return trains;
    }

    /**
     * Solid floors for berthed metro cars, cached per tick. Per world, and used from the collision
     * event on both sides — see {@link TrainFloors}.
     */
    private final TrainFloors trainFloors = new TrainFloors();

    public TrainFloors trainFloors() {
        return trainFloors;
    }

    public com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet elements() {
        return elements;
    }

    public com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem transit() {
        return transit;
    }

    /**
     * Park-wide block signalling. Server-side truth, like {@link #elements}: a client that braked
     * its own predicted train for a block it believes is occupied would fight the server's
     * correction, and occupancy depends on trains the client may not have been told about.
     */
    private final com.micatechnologies.minecraft.rcmc.physics.block.BlockSystems blocks =
        new com.micatechnologies.minecraft.rcmc.physics.block.BlockSystems();

    public com.micatechnologies.minecraft.rcmc.physics.block.BlockSystems blocks() {
        return blocks;
    }

    /**
     * Render-only description of where ride hardware sits, populated from a sync packet.
     *
     * <p>Separate from {@link #elements} because the two sides need different things: the server
     * needs behaviour, the client needs only enough to draw a lift hill with a chain down it.</p>
     */
    private java.util.List<com.micatechnologies.minecraft.rcmc.track.ElementSpan> elementSpans =
        new java.util.ArrayList<>();

    public java.util.List<com.micatechnologies.minecraft.rcmc.track.ElementSpan> elementSpans() {
        return elementSpans;
    }

    public void setElementSpans(
        java.util.List<com.micatechnologies.minecraft.rcmc.track.ElementSpan> spans) {
        this.elementSpans = spans == null ? new java.util.ArrayList<>() : spans;
    }

    /**
     * Latest service snapshots, client-side — what the arrival boards render from. Populated by
     * {@code PacketServiceSync}; empty on the server, which reads its live services directly.
     */
    private java.util.List<com.micatechnologies.minecraft.rcmc.physics.transit.ServiceSnapshot>
        serviceSnapshots = new java.util.ArrayList<>();

    public java.util.List<com.micatechnologies.minecraft.rcmc.physics.transit.ServiceSnapshot>
        serviceSnapshots() {
        return serviceSnapshots;
    }

    public void setServiceSnapshots(
        java.util.List<com.micatechnologies.minecraft.rcmc.physics.transit.ServiceSnapshot> snapshots) {
        this.serviceSnapshots = snapshots == null ? new java.util.ArrayList<>() : snapshots;
    }

    /** True on a client world, where the network is a synced mirror rather than the truth. */
    public boolean isRemote() {
        return remote;
    }

    /**
     * Marks the track network as changed so it is written on the next world save.
     *
     * <p>Server-side only, and required after <em>every</em> edit: {@code WorldSavedData} has no
     * change detection, so an unmarked edit is silently lost on restart.</p>
     */
    public void markTrackDirty(World world) {
        if (!world.isRemote) {
            RcmcTrackData data = RcmcTrackData.get(world);
            data.markNetworkDirty();
            // Record the post-edit state for undo. markTrackDirty is the one choke point every edit
            // passes through, so hooking it here covers all edits — track, colour, style, elements,
            // transit — without touching a single call site, and future edit types the day they
            // are written. Skipped while a restore is being applied (its own dirty-mark would else
            // corrupt the stacks).
            if (history != null && !history.isRestoring()) {
                history.record(data.snapshot());
            }
        }
    }

    /**
     * Marks the save dirty because the set of trains changed, without touching the undo history.
     *
     * <p>Distinct from {@link #markTrackDirty} on purpose: spawning or withdrawing a train is not a
     * building decision, so recording an undo snapshot for it would push real edits off the end of
     * the history while adding entries that undo to an identical authored state.</p>
     *
     * <p>The per-tick mark in the tick hook covers a train that merely <em>moved</em>. This exists
     * for the case that one cannot cover: removing the last train, after which the tick hook
     * returns early and would never mark anything again — leaving the deleted train on disk, to
     * reappear on the next load.</p>
     */
    public void markTrainsDirty(World world) {
        if (world != null && !world.isRemote) {
            RcmcTrackData.get(world).markNetworkDirty();
        }
    }

    /**
     * Steps the authored state back one edit. Returns false if there is nothing to undo.
     *
     * <p>Restores the authored state (track, elements, stations, lines, signals). Trains are not
     * part of it and stay where they are; their line services are carried over onto the restored
     * lines ({@code TransitSystem.adoptServices}). An undo that removes a section a train sits on
     * leaves the train safely skipped, exactly as deleting the section by hand does, and an undo
     * that removes a line leaves that line's trains parked.</p>
     */
    public boolean undo(World world) {
        return applyRestore(world, history == null ? null : history.undo());
    }

    /** Mirror of {@link #undo} for redo. */
    public boolean redo(World world) {
        return applyRestore(world, history == null ? null : history.redo());
    }

    public boolean canUndo() {
        return history != null && history.canUndo();
    }

    public boolean canRedo() {
        return history != null && history.canRedo();
    }

    /**
     * Installs a restored snapshot into the live world state and the save data together, then
     * broadcasts the result to clients. Both sides of the state must be the same instances — a save
     * writes {@link RcmcTrackData}'s copy while the world runs this one — so they are set from the
     * one parse.
     */
    private boolean applyRestore(World world, net.minecraft.nbt.NBTTagCompound snapshot) {
        if (snapshot == null || world.isRemote) {
            return false;
        }
        history.beginRestore();
        try {
            TrackNetwork restoredNetwork =
                com.micatechnologies.minecraft.rcmc.track.storage.TrackCodec.readNetwork(snapshot);
            com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet restoredElements =
                com.micatechnologies.minecraft.rcmc.track.storage.ElementCodec.read(snapshot);
            com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem restoredTransit =
                com.micatechnologies.minecraft.rcmc.track.storage.TransitCodec.read(snapshot);

            // A snapshot is authored state only, so the restored transit arrives with no services.
            // Carry the running ones across, or every undo stops the whole network.
            restoredTransit.adoptServices(this.transit, trains, restoredNetwork,
                com.micatechnologies.minecraft.rcmc.RcmcConstants.SECONDS_PER_TICK);

            this.network = restoredNetwork;
            this.elements = restoredElements;
            this.transit = restoredTransit;
            RcmcTrackData.get(world).install(restoredNetwork, restoredElements, restoredTransit);

            int dimension = world.provider.getDimension();
            RcmcNetwork.sendToAllIn(new PacketTrackSync(restoredNetwork), dimension);
            RcmcNetwork.sendToAllIn(new PacketElementSync(restoredElements), dimension);
            RcmcNetwork.sendToAllIn(new com.micatechnologies.minecraft.rcmc.net.PacketTransitSync(
                restoredTransit), dimension);
        }
        finally {
            history.endRestore();
        }
        return true;
    }

    /** Forge event hooks. Registered once from {@code Rcmc.preInit}. */
    public static final class Hooks {

        /**
         * Ticks between train-state corrections sent to clients.
         *
         * <p>Four per second. The client is running the same integrator, so this is a correction
         * rate, not an update rate — the gap between corrections is covered by prediction rather
         * than by interpolation, which is the whole reason it can be this sparse at speeds where
         * vanilla tracking cannot cope.</p>
         */
        private static final int SYNC_INTERVAL_TICKS = 5;

        private int tickCounter;

        /** True when no player is connected to the server at all, in any dimension. */
        private static boolean nobodyOnline(World world) {
            net.minecraft.server.MinecraftServer server = world.getMinecraftServer();
            return server == null || server.getPlayerList().getCurrentPlayerCount() == 0;
        }

        /**
         * Brings a server world's state up as it loads, rather than whenever something first asks
         * for it.
         *
         * <p>{@link #of} is lazy, and before trains persisted that was fine: nothing existed until
         * a player ran a command, which created the state on the way. A world that loads with
         * trains already in it has no such trigger, so nothing would resume its services or
         * reconcile its cars until something happened to ask for the state — for a dimension nobody
         * is standing in, potentially never.</p>
         *
         * <p>Note this does not make trains <em>run</em> with nobody online; the tick hook pauses
         * them in place for that. It makes them <em>ready</em>, so the first tick after someone
         * arrives is a normal one rather than a cold start.</p>
         */
        @SubscribeEvent
        public void onWorldLoad(WorldEvent.Load event) {
            if (event.getWorld() != null && !event.getWorld().isRemote) {
                of(event.getWorld());
            }
        }

        @SubscribeEvent
        public void onWorldUnload(WorldEvent.Unload event) {
            STATES.remove(event.getWorld());
            TrackSupports.invalidate(event.getWorld());
        }

        /**
         * Releases a departing player's unfinished build sessions.
         *
         * <p>Both session maps are keyed by player UUID and were never cleared, so on a
         * long-running server they accumulated one entry per player who ever picked up a build
         * tool. Small, but unbounded — and it also meant a player who disconnected mid-layout came
         * back to a half-built chain they had no memory of starting.</p>
         */
        @SubscribeEvent
        public void onPlayerLeave(
            net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.player == null) {
                return;
            }
            java.util.UUID id = event.player.getUniqueID();
            com.micatechnologies.minecraft.rcmc.builder.TrackBuildSession.clear(id);
            com.micatechnologies.minecraft.rcmc.builder.PieceBuildSession.clear(id);
            com.micatechnologies.minecraft.rcmc.builder.TransitBuildSession.clear(id);
            RideOperations.forget(id);
        }

        /** New arrivals need the track before any train state can mean anything. */
        @SubscribeEvent
        public void onPlayerJoin(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) {
            sendFullState(event.player);
        }

        /**
         * A player changing dimension needs the whole state again, exactly as on login.
         *
         * <p>The client keeps its RCMC state per {@code World} instance, and a dimension change hands
         * it a brand-new client world — so on the way back from the Nether it starts from nothing.
         * Found in play: after one round trip through a portal every arrival board read
         * {@code NO SERVICE} until the player relogged, because the only full send was at login.</p>
         */
        @SubscribeEvent
        public void onPlayerChangedDimension(
            net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent event) {
            sendFullState(event.player);
        }

        /**
         * Respawning can also give the client a new world (leaving the End always does), so it gets
         * the full state too. Harmless when it does not: the packets replace state with the same.
         */
        @SubscribeEvent
        public void onPlayerRespawn(
            net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent event) {
            sendFullState(event.player);
        }

        private static void sendFullState(net.minecraft.entity.player.EntityPlayer eventPlayer) {
            if (!(eventPlayer instanceof net.minecraft.entity.player.EntityPlayerMP)) {
                return;
            }
            net.minecraft.entity.player.EntityPlayerMP player =
                (net.minecraft.entity.player.EntityPlayerMP) eventPlayer;
            RcmcWorldState state = of(player.world);
            if (state == null) {
                return;
            }
            RcmcNetwork.sendTo(new PacketTrackSync(state.network), player);
            RcmcNetwork.sendTo(new PacketElementSync(state.elements), player);
            RcmcNetwork.sendTo(new com.micatechnologies.minecraft.rcmc.net.PacketTransitSync(
                state.transit), player);
            for (Map.Entry<Integer, com.micatechnologies.minecraft.rcmc.physics.Train> entry
                : state.trains.asMap().entrySet()) {
                RcmcNetwork.sendTo(new PacketTrainSync(entry.getKey(), entry.getValue()), player);
            }
        }

        /**
         * Advances every train once per tick, at {@code END} so ride elements and block logic that
         * run during the tick have already set up this tick's conditions.
         */
        @SubscribeEvent
        public void onWorldTick(TickEvent.WorldTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            RcmcWorldState state = STATES.get(event.world);
            if (state == null || state.trains.isEmpty()) {
                return;
            }
            if (!event.world.isRemote && nobodyOnline(event.world)) {
                // Nobody on the server: freeze, in place, exactly as it stands. Not a shutdown —
                // skipping the tick advances nothing at all, so a train holds its position and
                // velocity, a dwell timer holds its remaining ticks, and a service stays a service.
                // When someone joins it continues from that state as though no time had passed.
                //
                // SERVER-wide, not per dimension — the owner's call. A metro is a public service:
                // with one player in the Nether, the overworld's lines still run, so a player
                // coming back finds the trains where the timetable says, not where they were left.
                // (A non-overworld dimension that Forge has unloaded for being empty gets no tick
                // at all, so its lines still hold until someone enters it.)
                return;
            }
            if (!event.world.isRemote && --state.carReconcileCountdown <= 0) {
                // Standing reconciliation, not a one-off at load. A car is a rendering of a train
                // that the world is free to discard — an unloaded chunk takes its cars with it, and
                // a spawn into a chunk that has not arrived yet is silently refused. Both are
                // answered by simply checking again. See TrainEntities.spawnMissingCars.
                state.carReconcileCountdown = CAR_RECONCILE_INTERVAL_TICKS;
                TrainEntities.spawnMissingCars(event.world, state);
            }
            // Trains move every tick, so the saved copy is stale the moment it is written.
            // WorldSavedData has no change detection and clears its own flag after each write, so
            // without re-marking here an autosave would persist one position and then never update
            // it — a train would reload wherever it happened to be at the last edit. markDirty is a
            // boolean set; the serialisation itself still happens once per save.
            state.markTrainsDirty(event.world);
            try {
                // Only the server drives ride hardware; see the `elements` field javadoc.
                com.micatechnologies.minecraft.rcmc.physics.TrainManager.ExternalAcceleration control
                    = null;
                if (!state.remote) {
                    // Every tick rather than once: an undo swaps in a fresh element set, and a gate
                    // installed only at load would silently fall back to "always dispatch".
                    state.elements.setDispatchGates(state.rides::gateFor);
                    if (state.blocks.isEmpty()) {
                        control = state.elements;
                    }
                    else {
                        // Occupancy first, and for every section before any train moves: a hold
                        // decision must read one consistent snapshot rather than depend on the
                        // order TrainManager happens to iterate in.
                        state.blocks.updateOccupancy(state.trains, state.network);
                        control = new com.micatechnologies.minecraft.rcmc.physics.block
                            .BlockSignaledElementSet(state.elements, state.blocks);
                    }
                    if (!state.rides.isEmpty()) {
                        // The operator's e-stop overrides the hardware and the block signals.
                        control = new com.micatechnologies.minecraft.rcmc.physics.ride.RideControlLayer(
                            control, state.rides, state.network, RcmcConstants.SECONDS_PER_TICK);
                    }
                    if (state.transit.hasServices()) {
                        // Trains in metro service are driven by their LineService; everything
                        // else falls through to the coaster control built above.
                        state.transit.beginTick(state.trains, state.network);
                        control = state.transit.composedWith(control);
                    }
                }
                state.trains.tick(state.network, control,
                    RcmcConfig.physicsSubSteps, RcmcConstants.SECONDS_PER_TICK);

                // After the tick, so the phases the sounds react to are this tick's. Server only;
                // the client hears what the server broadcasts rather than deciding for itself,
                // which keeps a chime from firing twice on an integrated server.
                if (!event.world.isRemote) {
                    state.transitSounds.tick(event.world, state.transit, state.trains,
                        state.network);
                }
            }
            catch (RuntimeException e) {
                // A geometry or traversal fault must not take the world tick down with it. Log
                // loudly and keep the server alive; the offending train will be visibly stuck,
                // which is a far better failure mode than a crash loop on world load.
                Rcmc.LOGGER.error("Train simulation failed this tick; trains may be stuck", e);
            }

            // Both sides simulate; the server periodically corrects.
            if (!event.world.isRemote && ++tickCounter >= SYNC_INTERVAL_TICKS) {
                tickCounter = 0;
                int dimension = event.world.provider.getDimension();
                for (Map.Entry<Integer, com.micatechnologies.minecraft.rcmc.physics.Train> entry
                    : state.trains.asMap().entrySet()) {
                    RcmcNetwork.sendToAllIn(new PacketTrainSync(entry.getKey(), entry.getValue()),
                        dimension);
                }
                // Arrival-board data rides the same cadence: a snapshot only changes when a
                // service passes a stop or cycles its doors, so this is already generous. Sent
                // when empty too, so a board blanks when the last service is withdrawn.
                if (!state.transit.isEmpty()) {
                    RcmcNetwork.sendToAllIn(new com.micatechnologies.minecraft.rcmc.net
                        .PacketServiceSync(state.transit.serviceSnapshots()), dimension);
                }
            }
        }
    }
}
