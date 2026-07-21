package com.micatechnologies.minecraft.rcmc.track.storage;

import com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Converts a {@link TransitSystem}'s <em>authored</em> content — stations and lines — to and
 * from NBT. Same single-seam philosophy as {@link TrackCodec}: the transit types themselves stay
 * free of Minecraft imports.
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

    private static final String KEY_STATIONS = "TransitStations";
    private static final String KEY_LINES = "TransitLines";

    private static final String KEY_NAME = "Name";
    private static final String KEY_SECTION = "Section";
    private static final String KEY_DISTANCE = "Distance";
    private static final String KEY_LOOP = "Loop";
    private static final String KEY_IN_LABEL = "InboundLabel";
    private static final String KEY_OUT_LABEL = "OutboundLabel";
    private static final String KEY_STOPS = "Stops";

    private TransitCodec() {
        throw new AssertionError("No instances.");
    }

    public static void write(TransitSystem transit, NBTTagCompound root) {
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
