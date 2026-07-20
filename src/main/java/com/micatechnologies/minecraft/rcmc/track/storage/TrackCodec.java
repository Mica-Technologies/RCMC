package com.micatechnologies.minecraft.rcmc.track.storage;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/**
 * Converts a {@link TrackNetwork} to and from NBT.
 *
 * <p>Deliberately separate from the track types themselves. {@code track} and {@code track.math}
 * contain no Minecraft types so they stay unit-testable on a bare JVM; this class is the single
 * seam where that rule is crossed. Adding a {@code writeToNBT} method to {@link TrackSection}
 * would have been shorter and would have cost the whole geometry pipeline its testability.</p>
 *
 * <p><b>Versioning.</b> Every payload carries {@link #DATA_VERSION}. Migration is a switch on that
 * int in {@link #readNetwork}. This costs almost nothing now and is impossible to retrofit once
 * players have built parks — a save written by a version that didn't record its own format is
 * unreadable without guessing.</p>
 */
public final class TrackCodec {

    /**
     * Format version of the NBT written by this class.
     *
     * <p>Bump on any change to the shape of the data, and add a migration branch in
     * {@link #readNetwork}. Never reuse a number.</p>
     *
     * <p>History:</p>
     * <ul>
     *   <li>1 — initial: sections with nodes (position, bank, style), closed flag, section style,
     *       and symmetric end joins.</li>
     * </ul>
     */
    public static final int DATA_VERSION = 1;

    private static final String KEY_VERSION = "DataVersion";
    private static final String KEY_SECTIONS = "Sections";
    private static final String KEY_JOINS = "Joins";

    private static final String KEY_ID = "Id";
    private static final String KEY_NODES = "Nodes";
    private static final String KEY_CLOSED = "Closed";
    private static final String KEY_STYLE = "Style";
    private static final String KEY_PALETTE = "Palette";

    private static final String KEY_X = "X";
    private static final String KEY_Y = "Y";
    private static final String KEY_Z = "Z";
    private static final String KEY_BANK = "Bank";

    private static final String KEY_FROM_SECTION = "FromSection";
    private static final String KEY_FROM_END = "FromEnd";
    private static final String KEY_TO_SECTION = "ToSection";
    private static final String KEY_TO_END = "ToEnd";

    private TrackCodec() {
        throw new AssertionError("No instances.");
    }

    public static NBTTagCompound writeNetwork(TrackNetwork network) {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger(KEY_VERSION, DATA_VERSION);

        NBTTagList sectionList = new NBTTagList();
        for (TrackSection section : network.sections()) {
            sectionList.appendTag(writeSection(section));
        }
        root.setTag(KEY_SECTIONS, sectionList);

        // Joins are stored symmetrically in the network (both directions), so write each pair
        // once and let the reader re-establish symmetry via connect().
        NBTTagList joinList = new NBTTagList();
        for (Map.Entry<TrackNetwork.SectionEnd, TrackNetwork.SectionEnd> entry : network.joins().entrySet()) {
            TrackNetwork.SectionEnd from = entry.getKey();
            TrackNetwork.SectionEnd to = entry.getValue();
            if (!isCanonical(from, to)) {
                continue;
            }
            NBTTagCompound join = new NBTTagCompound();
            join.setInteger(KEY_FROM_SECTION, from.sectionId);
            join.setString(KEY_FROM_END, from.end.name());
            join.setInteger(KEY_TO_SECTION, to.sectionId);
            join.setString(KEY_TO_END, to.end.name());
            joinList.appendTag(join);
        }
        root.setTag(KEY_JOINS, joinList);

        return root;
    }

    /**
     * Picks one of the two mirrored entries for a join so it is written exactly once. Ordering by
     * section id, then by end, is arbitrary but stable — which is what matters, since an unstable
     * choice would make otherwise-identical saves differ byte for byte.
     */
    private static boolean isCanonical(TrackNetwork.SectionEnd from, TrackNetwork.SectionEnd to) {
        if (from.sectionId != to.sectionId) {
            return from.sectionId < to.sectionId;
        }
        return from.end.ordinal() <= to.end.ordinal();
    }

    public static TrackNetwork readNetwork(NBTTagCompound root) {
        TrackNetwork network = new TrackNetwork();
        if (root == null || !root.hasKey(KEY_VERSION)) {
            return network;
        }

        int version = root.getInteger(KEY_VERSION);
        if (version > DATA_VERSION) {
            // Refuse rather than guess. Silently dropping data a newer version wrote would
            // present to the player as "my coaster vanished", with the save already overwritten
            // by the time they noticed.
            throw new IllegalStateException(
                "RCMC track data is version " + version + " but this build only understands up to "
                    + DATA_VERSION + ". Update the mod; do not load this world with this build.");
        }
        // Migration branches for older versions go here as `if (version < N) { ... }`.

        NBTTagList sectionList = root.getTagList(KEY_SECTIONS, 10);
        for (int i = 0; i < sectionList.tagCount(); i++) {
            network.addSection(readSection(sectionList.getCompoundTagAt(i)));
        }

        NBTTagList joinList = root.getTagList(KEY_JOINS, 10);
        for (int i = 0; i < joinList.tagCount(); i++) {
            NBTTagCompound join = joinList.getCompoundTagAt(i);
            TrackNetwork.SectionEnd from = new TrackNetwork.SectionEnd(
                join.getInteger(KEY_FROM_SECTION), TrackNetwork.End.valueOf(join.getString(KEY_FROM_END)));
            TrackNetwork.SectionEnd to = new TrackNetwork.SectionEnd(
                join.getInteger(KEY_TO_SECTION), TrackNetwork.End.valueOf(join.getString(KEY_TO_END)));
            // A join referencing a section that failed to load would otherwise throw and abort the
            // whole world load; skip it and keep the rest of the park.
            if (network.section(from.sectionId) != null && network.section(to.sectionId) != null) {
                network.connect(from, to);
            }
        }

        return network;
    }

    public static NBTTagCompound writeSection(TrackSection section) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(KEY_ID, section.id());
        tag.setBoolean(KEY_CLOSED, section.isClosed());
        if (section.styleId() != null) {
            tag.setString(KEY_STYLE, section.styleId());
        }

        if (!section.palette().isDefault()) {
            // Only written when painted, so an untouched park's save file does not grow a colour
            // record for every section that never used one.
            NBTTagCompound palette = new NBTTagCompound();
            for (com.micatechnologies.minecraft.rcmc.track.TrackPalette.Part part
                : com.micatechnologies.minecraft.rcmc.track.TrackPalette.Part.values()) {
                palette.setString(part.name(), section.palette().of(part).name());
            }
            tag.setTag(KEY_PALETTE, palette);
        }

        NBTTagList nodeList = new NBTTagList();
        for (TrackNode node : section.nodes()) {
            nodeList.appendTag(writeNode(node));
        }
        tag.setTag(KEY_NODES, nodeList);
        return tag;
    }

    public static TrackSection readSection(NBTTagCompound tag) {
        NBTTagList nodeList = tag.getTagList(KEY_NODES, 10);
        List<TrackNode> nodes = new ArrayList<>(nodeList.tagCount());
        for (int i = 0; i < nodeList.tagCount(); i++) {
            nodes.add(readNode(nodeList.getCompoundTagAt(i)));
        }
        com.micatechnologies.minecraft.rcmc.track.TrackPalette palette =
            com.micatechnologies.minecraft.rcmc.track.TrackPalette.DEFAULT;
        if (tag.hasKey(KEY_PALETTE, 10)) {
            NBTTagCompound stored = tag.getCompoundTag(KEY_PALETTE);
            for (com.micatechnologies.minecraft.rcmc.track.TrackPalette.Part part
                : com.micatechnologies.minecraft.rcmc.track.TrackPalette.Part.values()) {
                palette = palette.with(part,
                    com.micatechnologies.minecraft.rcmc.track.TrackPalette.Colour.byName(
                        stored.getString(part.name()), palette.of(part)));
            }
        }
        return new TrackSection(
            tag.getInteger(KEY_ID),
            nodes,
            tag.getBoolean(KEY_CLOSED),
            tag.hasKey(KEY_STYLE, 8) ? tag.getString(KEY_STYLE) : null,
            palette);
    }

    public static NBTTagCompound writeNode(TrackNode node) {
        NBTTagCompound tag = new NBTTagCompound();
        // Doubles, not floats. Node positions accumulate into arc length over hundreds of samples,
        // and float rounding at write time would shift a whole circuit's length on every reload.
        tag.setDouble(KEY_X, node.position().x);
        tag.setDouble(KEY_Y, node.position().y);
        tag.setDouble(KEY_Z, node.position().z);
        if (node.bankDegrees() != 0.0D) {
            tag.setDouble(KEY_BANK, node.bankDegrees());
        }
        if (node.styleId() != null) {
            tag.setTag(KEY_STYLE, new NBTTagString(node.styleId()));
        }
        return tag;
    }

    public static TrackNode readNode(NBTTagCompound tag) {
        return new TrackNode(
            new Vec3(tag.getDouble(KEY_X), tag.getDouble(KEY_Y), tag.getDouble(KEY_Z)),
            tag.getDouble(KEY_BANK),
            tag.hasKey(KEY_STYLE, 8) ? tag.getString(KEY_STYLE) : null);
    }
}
