package com.micatechnologies.minecraft.rcmc.physics.transit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Speed limits signed along the track: a stretch of a section, and the most a metro may run
 * through it, in blocks/s.
 *
 * <p>Curves already set their own limits ({@link CurveSpeed}); these are the ones a builder adds
 * where geometry says nothing — through a station, over points, at a tunnel portal. A service
 * treats them exactly like curve limits: it brakes in time to be at the limit when its front
 * reaches the stretch, and holds it until its last car has cleared it.</p>
 *
 * <p>Held by section id and distance, so an edit that reshapes a section may move a limit along
 * it; re-sign it after reshaping. Pure Java.</p>
 */
public final class SpeedLimits {

    /** One signed stretch. */
    public static final class Limit {
        public final double from;
        public final double to;
        public final double speed;

        public Limit(double from, double to, double speed) {
            if (speed <= 0.0D) {
                throw new IllegalArgumentException("a speed limit must be positive, got " + speed);
            }
            this.from = Math.min(from, to);
            this.to = Math.max(from, to);
            this.speed = speed;
        }
    }

    private final Map<Integer, List<Limit>> bySection = new LinkedHashMap<>();

    /** The lowest limit signed at {@code distance} along section {@code sectionId}; infinite if none. */
    public double at(int sectionId, double distance) {
        List<Limit> limits = bySection.get(sectionId);
        if (limits == null) {
            return Double.POSITIVE_INFINITY;
        }
        double lowest = Double.POSITIVE_INFINITY;
        for (Limit limit : limits) {
            if (distance >= limit.from && distance <= limit.to) {
                lowest = Math.min(lowest, limit.speed);
            }
        }
        return lowest;
    }

    /** Whether any limit is signed on {@code sectionId} — a cheap guard for the scan. */
    public boolean has(int sectionId) {
        return bySection.containsKey(sectionId);
    }

    public void add(int sectionId, Limit limit) {
        bySection.computeIfAbsent(sectionId, id -> new ArrayList<>()).add(limit);
    }

    /** Removes every limit on {@code sectionId}; returns how many there were. */
    public int clear(int sectionId) {
        List<Limit> removed = bySection.remove(sectionId);
        return removed == null ? 0 : removed.size();
    }

    public List<Limit> on(int sectionId) {
        List<Limit> limits = bySection.get(sectionId);
        return limits == null ? Collections.<Limit>emptyList() : Collections.unmodifiableList(limits);
    }

    public Map<Integer, List<Limit>> all() {
        return Collections.unmodifiableMap(bySection);
    }

    public void replaceWith(SpeedLimits other) {
        bySection.clear();
        for (Map.Entry<Integer, List<Limit>> entry : other.bySection.entrySet()) {
            bySection.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    public void clearAll() {
        bySection.clear();
    }
}
