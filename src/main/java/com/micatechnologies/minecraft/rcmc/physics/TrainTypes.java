package com.micatechnologies.minecraft.rcmc.physics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every train type the server knows: the mod's own, then any from its files, which may add new ones
 * or replace a built-in by using its id.
 *
 * <p>Server-wide, like the config it comes from. Only the server reads it: a train is built from a
 * type, and from then on carries its whole spec to clients and into the save.</p>
 */
public final class TrainTypes {

    /** The id a ride builds from when it has not been given one. */
    public static final String DEFAULT_COASTER = "coaster";

    private static final Map<String, TrainType> BUILT_IN = new LinkedHashMap<>();
    private static volatile Map<String, TrainType> all;

    static {
        // The mod's six, proportioned as the presets always were. Colours are TrackPalette ordinals:
        // 0 steel, 1 graphite, 2 white, 3 red, 4 orange, 5 yellow, 8 blue.
        add(new TrainType("coaster", "Sit-down, lap bars", TrainType.Body.SIT_DOWN, 3.0D, 0.5D, 4, 5, 12, 3, 4, 1, false));
        add(new TrainType("shoulder", "Over-the-shoulder", TrainType.Body.SHOULDER, 3.0D, 0.5D, 4, 5, 12, 3, 4, 1, false));
        add(new TrainType("wooden", "Wooden classic", TrainType.Body.WOODEN, 3.0D, 0.5D, 4, 5, 12, 3, 4, 1, false));
        add(new TrainType("metro", "Metro", TrainType.Body.METRO, 14.3D, 6.2D, 10, 3, 8, 0, 4, 1, false));
        add(new TrainType("metrocompact", "Metro, compact", TrainType.Body.METRO, 11.3D, 5.1D, 8, 3, 10, 0, 1, 5, false));
        add(new TrainType("metrolong", "Metro, long", TrainType.Body.METRO, 16.5D, 7.1D, 12, 3, 8, 2, 3, 8, false));
        all = Collections.unmodifiableMap(new LinkedHashMap<>(BUILT_IN));
    }

    private TrainTypes() {
    }

    private static void add(TrainType type) {
        BUILT_IN.put(type.id, type);
    }

    /** Installs the types read from files, over the built-ins. Replaces whatever was loaded before. */
    public static void setCustom(Collection<TrainType> custom) {
        Map<String, TrainType> merged = new LinkedHashMap<>(BUILT_IN);
        for (TrainType type : custom) {
            merged.put(type.id, type);
        }
        all = Collections.unmodifiableMap(merged);
    }

    /** The type with {@code id}, case-insensitively, or {@code null}. */
    public static TrainType get(String id) {
        return id == null ? null : all.get(id.toLowerCase(Locale.ROOT));
    }

    /** {@link #get}, or the default coaster when there is no such type any more. */
    public static TrainType coasterOrDefault(String id) {
        TrainType type = get(id);
        return type != null && type.isCoaster() ? type : all.get(DEFAULT_COASTER);
    }

    public static Collection<TrainType> all() {
        return all.values();
    }

    /** The coaster types, in order — what a ride's operator panel steps through. */
    public static List<TrainType> coasters() {
        List<TrainType> out = new ArrayList<>();
        for (TrainType type : all.values()) {
            if (type.isCoaster()) {
                out.add(type);
            }
        }
        return out;
    }

    /** The coaster type after {@code id}, wrapping round. */
    public static TrainType nextCoaster(String id) {
        List<TrainType> coasters = coasters();
        for (int i = 0; i < coasters.size(); i++) {
            if (coasters.get(i).id.equalsIgnoreCase(id)) {
                return coasters.get((i + 1) % coasters.size());
            }
        }
        return coasters.get(0);
    }
}
