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

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        this.network = TrackCodec.readNetwork(nbt);
        this.elements = ElementCodec.read(nbt);
        this.transit = TransitCodec.read(nbt);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagCompound written = TrackCodec.writeNetwork(network);
        for (String key : written.getKeySet()) {
            compound.setTag(key, written.getTag(key));
        }
        NBTTagCompound elementTag = ElementCodec.write(elements);
        for (String key : elementTag.getKeySet()) {
            compound.setTag(key, elementTag.getTag(key));
        }
        TransitCodec.write(transit, compound);
        return compound;
    }
}
