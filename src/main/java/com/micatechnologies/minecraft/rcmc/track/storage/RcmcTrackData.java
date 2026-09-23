package com.micatechnologies.minecraft.rcmc.track.storage;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/**
 * Per-dimension persistent storage for the track network.
 *
 * <p><b>Why {@link WorldSavedData} and not tile entities.</b> A coaster spans hundreds of blocks
 * across many chunks. Tile-entity storage would make a section's geometry available only while
 * every chunk it crosses is loaded — but a train must keep moving through unloaded chunks, and
 * block-section safety logic must stay coherent park-wide regardless of who is standing where.
 * {@code WorldSavedData} loads once with the world and is then unconditionally available. This is
 * the structural decision that makes the "physics never touches the world" rule achievable, and
 * it is why entities freezing in unloaded chunks is a rendering concern here rather than a
 * simulation one.</p>
 *
 * <p>Anchor blocks placed at nodes hold only a section-id back-reference — never geometry.</p>
 *
 * <p>Server-side only. The client keeps its own {@link TrackNetwork} built from sync packets, not
 * from this.</p>
 */
public class RcmcTrackData extends WorldSavedData {

    /**
     * Storage key. Namespaced because {@link MapStorage} is a flat per-dimension namespace shared
     * with vanilla and every other mod.
     */
    public static final String DATA_NAME = RcmcConstants.MOD_NAMESPACE + "_track";

    private TrackNetwork network = new TrackNetwork();

    /**
     * Ride hardware on that network. Stored here rather than in its own WorldSavedData because
     * elements are meaningless without the track they sit on — splitting them across two saved
     * blobs would let one load and the other fail, leaving a park half-configured.
     */
    private com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet elements =
        new com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet();

    /**
     * Authored transit (stations, lines) on that network. Stored here for the same reason
     * elements are: a station is a point on a section, meaningless without it.
     */
    private com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem transit =
        new com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem();

    /**
     * Trains running on that network.
     *
     * <p>The one piece of <em>runtime</em> state in here, and it is here for the same reason as the
     * rest: a train is an address into a section, meaningless without it, and splitting it into its
     * own saved blob would let the track load while the trains failed to. See
     * {@link #writeAuthored} for the consequence that has to be handled — the undo history is built
     * from this same serialisation, and a train is not an edit.</p>
     */
    private com.micatechnologies.minecraft.rcmc.physics.TrainManager trains =
        new com.micatechnologies.minecraft.rcmc.physics.TrainManager();

    /**
     * Services read from the save, held until the world state can resume them.
     *
     * <p>{@link #readFromNBT} cannot resume them itself: putting a train into service needs the
     * track network to walk, and the network is still being parsed in the same call. So the raw tag
     * is kept and {@code RcmcWorldState} resumes from it once everything is loaded.</p>
     */
    private NBTTagCompound pendingServices;

    /** Each coaster's block sections. Authored layout: saved, and part of the undo snapshot. */
    private com.micatechnologies.minecraft.rcmc.physics.block.BlockSystems blocks =
        new com.micatechnologies.minecraft.rcmc.physics.block.BlockSystems();

    public com.micatechnologies.minecraft.rcmc.physics.block.BlockSystems blocks() {
        return blocks;
    }

    /** Each ride's operating state. Saved beside the trains, outside the undo snapshot. */
    private com.micatechnologies.minecraft.rcmc.physics.ride.RideControllers rides =
        new com.micatechnologies.minecraft.rcmc.physics.ride.RideControllers();

    public com.micatechnologies.minecraft.rcmc.physics.ride.RideControllers rides() {
        return rides;
    }

    /** Required by {@link WorldSavedData}'s reflective instantiation on load. */
    public RcmcTrackData() {
        super(DATA_NAME);
    }

    public RcmcTrackData(String name) {
        super(name);
    }

    /**
     * The network for {@code world}, creating and registering empty storage on first use.
     *
     * <p>Call only on the logical server. On a client world {@code getPerWorldStorage()} exists but
     * is never populated from disk, so this would hand back a permanently empty network and the
     * caller would silently see no track at all.</p>
     */
    public static RcmcTrackData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        RcmcTrackData data = (RcmcTrackData) storage.getOrLoadData(RcmcTrackData.class, DATA_NAME);
        if (data == null) {
            data = new RcmcTrackData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public TrackNetwork network() {
        return network;
    }

    public com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet elements() {
        return elements;
    }

    public com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem transit() {
        return transit;
    }

    /**
     * Marks the network dirty so it is written on the next world save.
     *
     * <p>Must be called after <em>every</em> mutation. {@link WorldSavedData} has no change
     * detection: an unmarked edit is simply lost on restart, silently and with no error.</p>
     */
    public void markNetworkDirty() {
        markDirty();
    }

    public com.micatechnologies.minecraft.rcmc.physics.TrainManager trains() {
        return trains;
    }

    /**
     * The services read from disk, for the world state to resume once the network is available.
     * Cleared by {@link #takePendingServices} so a resume can only ever happen once.
     */
    public NBTTagCompound takePendingServices() {
        NBTTagCompound taken = pendingServices;
        pendingServices = null;
        return taken;
    }

    /**
     * A complete snapshot of the <em>authored</em> state, for the undo history.
     *
     * <p>Shares {@link #writeAuthored} with {@link #writeToNBT}, so a snapshot can never capture
     * less authored state than a save does — that is what lets "whatever persists, undoes" hold
     * without a second codec to keep in step.</p>
     *
     * <p><b>Trains are excluded, and that is the point of the split.</b> Undo steps back through
     * building decisions; a train's position is not one. Folding trains into the snapshot would
     * make undoing a track edit also yank every running train back to wherever it happened to be
     * several edits ago — teleporting riders backwards along the track as a side effect of an
     * unrelated undo. The contract {@code RcmcWorldState.undo} documents ("affects only the
     * authored state — never running trains") is enforced here, structurally, rather than by
     * remembering it at each call site.</p>
     */
    public NBTTagCompound snapshot() {
        return writeAuthored(new NBTTagCompound());
    }

    /**
     * Replaces the authored state with the network/elements/transit already parsed from a snapshot,
     * and marks the data dirty so the restored state is what next persists.
     *
     * <p>Takes parsed objects rather than the raw NBT because the caller ({@code RcmcWorldState})
     * must install the very same instances into its own live references — the two must not diverge,
     * or a save would write one and the world would run the other.</p>
     */
    public void install(TrackNetwork network,
                        com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet elements,
                        com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem transit,
                        com.micatechnologies.minecraft.rcmc.physics.block.BlockSystems blocks) {
        this.network = network;
        this.elements = elements;
        this.transit = transit;
        this.blocks = blocks;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        this.network = TrackCodec.readNetwork(nbt);
        this.elements = ElementCodec.read(nbt);
        this.transit = TransitCodec.read(nbt);
        this.blocks = BlockCodec.read(nbt);
        // The integrator comes from the server's own config rather than the save: physics values
        // are server-authoritative, so a train must restore with the physics this server runs, not
        // the physics it was saved under.
        this.trains = TrainCodec.read(nbt, new com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator(
            com.micatechnologies.minecraft.rcmc.RcmcConfig.gravity,
            com.micatechnologies.minecraft.rcmc.RcmcConfig.rollingResistance,
            com.micatechnologies.minecraft.rcmc.RcmcConfig.airDrag,
            com.micatechnologies.minecraft.rcmc.RcmcConfig.maxSpeed));
        this.pendingServices = nbt;
        this.rides = RideCodec.read(nbt);
        this.rides.setHomes(RideCodec.readHomes(nbt));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        writeAuthored(compound);
        TrainCodec.write(trains, transit, compound);
        RideCodec.write(rides, compound);
        return compound;
    }

    /**
     * Writes everything a builder authored: track, ride hardware, stations, lines, signalling.
     *
     * <p>Split out so {@link #snapshot} and {@link #writeToNBT} cannot disagree about what
     * "authored" means. The difference between them is exactly one line — the trains — and it is
     * visible in both places rather than implied.</p>
     */
    private NBTTagCompound writeAuthored(NBTTagCompound compound) {
        NBTTagCompound written = TrackCodec.writeNetwork(network);
        for (String key : written.getKeySet()) {
            compound.setTag(key, written.getTag(key));
        }
        NBTTagCompound elementTag = ElementCodec.write(elements);
        for (String key : elementTag.getKeySet()) {
            compound.setTag(key, elementTag.getTag(key));
        }
        TransitCodec.write(transit, compound);
        BlockCodec.write(blocks, compound);
        RideCodec.writeHomes(rides, compound);
        return compound;
    }
}
