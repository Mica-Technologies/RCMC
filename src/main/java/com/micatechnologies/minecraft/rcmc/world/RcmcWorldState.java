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
    private final TrainManager trains = new TrainManager();

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
            // Seed history with the loaded state so the first edit is undoable.
            created.history = new EditHistory(data.snapshot(), EditHistory.DEFAULT_DEPTH);
        }
        STATES.put(world, created);
        return created;
    }

    public TrackNetwork network() {
        return network;
    }

    public TrainManager trains() {
        return trains;
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
     * Steps the authored state back one edit. Returns false if there is nothing to undo.
     *
     * <p>Affects only the authored, persisted state (track, elements, transit) — never running
     * trains, which are runtime and unpersisted. An undo that removes a section a train sits on
     * leaves the train safely skipped, exactly as deleting the section by hand does.</p>
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
        }

        /** New arrivals need the track before any train state can mean anything. */
        @SubscribeEvent
        public void onPlayerJoin(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) {
            if (!(event.player instanceof net.minecraft.entity.player.EntityPlayerMP)) {
                return;
            }
            net.minecraft.entity.player.EntityPlayerMP player =
                (net.minecraft.entity.player.EntityPlayerMP) event.player;
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
            try {
                // Only the server drives ride hardware; see the `elements` field javadoc.
                com.micatechnologies.minecraft.rcmc.physics.TrainManager.ExternalAcceleration control
                    = null;
                if (!state.remote) {
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
