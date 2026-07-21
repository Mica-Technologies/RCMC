package com.micatechnologies.minecraft.rcmc.track.storage;

import com.micatechnologies.minecraft.rcmc.physics.block.BlockSection;
import com.micatechnologies.minecraft.rcmc.physics.transit.LineSignals;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Converts a {@link TransitSystem}'s <em>authored</em> content — stations, lines, and each line's
 * block signalling — to and from NBT. Same single-seam philosophy as {@link TrackCodec}: the
 * transit types themselves stay free of Minecraft imports.
 *
 * <p>Signals are authored content, not runtime state, which is why they persist: a block layout is
 * a decision the builder made about the line, and losing it on restart would silently drop every
 * running service back to unlimited authority — a safety system that quietly disappears is worse
 * than one that was never installed. Live occupancy is <em>not</em> written; it is recomputed from
 * scratch every tick by {@code updateOccupancy}, so there is nothing there worth saving.</p>
 *
 * <p>Services (trains in service) are deliberately not persisted: trains themselves are runtime
 * state that does not survive a restart, so a persisted service would point at a train that no
 * longer exists. When train persistence lands, service persistence belongs beside it.</p>
 *
 * <p>Lines store their stations by value (a {@link TransitLine} snapshots its stations), so the
 * codec writes each line's own station list rather than references into the station registry —
 * a line survives its stations being renamed or deleted, exactly as it does at runtime.</p>
 */
public final class TransitCodec {

    /**
     * Transit NBT format version, written on every save.
     *
     * <p>v1 was stations and lines only, and was written with <em>no</em> version key at all —
     * which is why absent reads as 1 rather than as corrupt. v2 adds per-line block signalling.
     *
     * <p>Unlike {@link TrackCodec}, a future version is <b>not</b> refused here. Transit content is
     * additive decoration on a track that {@code TrackCodec} already version-guards: if a newer
     * save is opened by an older mod, that codec refuses first and this one never runs. Duplicating
     * the refusal would only add a second, less informative failure path.</p>
     */
    static final int DATA_VERSION = 2;

    private static final String KEY_VERSION = "TransitVersion";
    private static final String KEY_STATIONS = "TransitStations";
    private static final String KEY_LINES = "TransitLines";

    private static final String KEY_NAME = "Name";
    private static final String KEY_SECTION = "Section";
    private static final String KEY_DISTANCE = "Distance";
    private static final String KEY_LOOP = "Loop";
    private static final String KEY_IN_LABEL = "InboundLabel";
    private static final String KEY_OUT_LABEL = "OutboundLabel";
    private static final String KEY_STOPS = "Stops";

    private static final String KEY_SIGNALS = "TransitSignals";
    private static final String KEY_LINE = "Line";
    private static final String KEY_MARGIN = "Margin";
    private static final String KEY_HORIZON = "Horizon";
    private static final String KEY_BLOCKS = "Blocks";
    private static final String KEY_ID = "Id";
    private static final String KEY_START = "Start";
    private static final String KEY_END = "End";

    private TransitCodec() {
        throw new AssertionError("No instances.");
    }

    public static void write(TransitSystem transit, NBTTagCompound root) {
        root.setInteger(KEY_VERSION, DATA_VERSION);
        NBTTagList stationList = new NBTTagList();
        for (TransitStation station : transit.stations()) {
            stationList.appendTag(writeStation(station));
        }
        root.setTag(KEY_STATIONS, stationList);

        NBTTagList lineList = new NBTTagList();
        for (TransitLine line : transit.lines()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString(KEY_NAME, line.name());
            tag.setBoolean(KEY_LOOP, line.isLoop());
            tag.setString(KEY_IN_LABEL, line.inboundLabel());
            tag.setString(KEY_OUT_LABEL, line.outboundLabel());
            NBTTagList stops = new NBTTagList();
            for (TransitStation station : line.stations()) {
                stops.appendTag(writeStation(station));
            }
            tag.setTag(KEY_STOPS, stops);
            lineList.appendTag(tag);
        }
        root.setTag(KEY_LINES, lineList);

        NBTTagList signalList = new NBTTagList();
        for (Map.Entry<String, LineSignals> entry : transit.signals().entrySet()) {
            LineSignals signals = entry.getValue();
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString(KEY_LINE, entry.getKey());
            tag.setDouble(KEY_MARGIN, signals.margin());
            tag.setDouble(KEY_HORIZON, signals.horizon());
            NBTTagList blocks = new NBTTagList();
            for (BlockSection block : signals.blocks()) {
                NBTTagCompound b = new NBTTagCompound();
                b.setString(KEY_ID, block.id());
                b.setInteger(KEY_SECTION, block.sectionId());
                b.setDouble(KEY_START, block.startDistance());
                b.setDouble(KEY_END, block.endDistance());
                blocks.appendTag(b);
            }
            tag.setTag(KEY_BLOCKS, blocks);
            signalList.appendTag(tag);
        }
        root.setTag(KEY_SIGNALS, signalList);
    }

    /** Reads authored transit into a fresh system. Malformed entries are skipped, not fatal. */
    public static TransitSystem read(NBTTagCompound root) {
        TransitSystem transit = new TransitSystem();
        if (root == null) {
            return transit;
        }
        NBTTagList stationList = root.getTagList(KEY_STATIONS, 10);
        for (int i = 0; i < stationList.tagCount(); i++) {
            TransitStation station = readStation(stationList.getCompoundTagAt(i));
            if (station != null) {
                transit.addStation(station);
            }
        }
        NBTTagList lineList = root.getTagList(KEY_LINES, 10);
        for (int i = 0; i < lineList.tagCount(); i++) {
            NBTTagCompound tag = lineList.getCompoundTagAt(i);
            NBTTagList stops = tag.getTagList(KEY_STOPS, 10);
            List<TransitStation> stations = new ArrayList<>(stops.tagCount());
            for (int j = 0; j < stops.tagCount(); j++) {
                TransitStation station = readStation(stops.getCompoundTagAt(j));
                if (station != null) {
                    stations.add(station);
                }
            }
            // Same policy as a dangling join: skip what cannot be rebuilt, keep the rest.
            if (!tag.getString(KEY_NAME).isEmpty() && stations.size() >= 2) {
                transit.addLine(new TransitLine(tag.getString(KEY_NAME), stations,
                    tag.getBoolean(KEY_LOOP),
                    orDefault(tag.getString(KEY_IN_LABEL), "INBOUND"),
                    orDefault(tag.getString(KEY_OUT_LABEL), "OUTBOUND")));
            }
        }

        // v1 saves have no signals tag; getTagList returns an empty list, so this loop simply
        // does nothing and the world loads as it always did.
        NBTTagList signalList = root.getTagList(KEY_SIGNALS, 10);
        for (int i = 0; i < signalList.tagCount(); i++) {
            NBTTagCompound tag = signalList.getCompoundTagAt(i);
            NBTTagList blocks = tag.getTagList(KEY_BLOCKS, 10);
            List<BlockSection> sections = new ArrayList<>(blocks.tagCount());
            for (int j = 0; j < blocks.tagCount(); j++) {
                NBTTagCompound b = blocks.getCompoundTagAt(j);
                String id = b.getString(KEY_ID);
                if (id.isEmpty() || b.getDouble(KEY_END) < b.getDouble(KEY_START)) {
                    continue;
                }
                sections.add(new BlockSection(id, b.getInteger(KEY_SECTION),
                    b.getDouble(KEY_START), b.getDouble(KEY_END)));
            }
            // Signals for a line that did not survive its own read would be unreachable, and
            // LineSignals refuses an empty block list outright — skip both rather than throw.
            if (sections.isEmpty() || transit.line(tag.getString(KEY_LINE)) == null) {
                continue;
            }
            transit.setSignals(tag.getString(KEY_LINE),
                new LineSignals(sections, tag.getDouble(KEY_MARGIN), tag.getDouble(KEY_HORIZON)));
        }
        return transit;
    }

    private static NBTTagCompound writeStation(TransitStation station) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(KEY_NAME, station.name());
        tag.setInteger(KEY_SECTION, station.stopPoint().sectionId());
        tag.setDouble(KEY_DISTANCE, station.stopPoint().distance());
        return tag;
    }

    private static TransitStation readStation(NBTTagCompound tag) {
        String name = tag.getString(KEY_NAME);
        if (name.isEmpty()) {
            return null;
        }
        return new TransitStation(name,
            new TrackRef(tag.getInteger(KEY_SECTION), tag.getDouble(KEY_DISTANCE)));
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
