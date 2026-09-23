package com.micatechnologies.minecraft.rcmc.physics.ride;

import com.micatechnologies.minecraft.rcmc.physics.element.DispatchGate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Every ride controller in one world, by the section its ride runs on.
 *
 * <p>A controller exists only once someone has operated the ride — linked a panel to it, or changed
 * a setting. A ride without one behaves as a fresh controller would (open, automatic), so there is
 * nothing to create for every coaster up front and nothing to clean up for one never touched.</p>
 */
public final class RideControllers {

    private final Map<Integer, RideController> bySection = new LinkedHashMap<>();

    /**
     * Sections that are part of another section's ride, mapped to that ride's home section.
     *
     * <p>A ride is usually one section. Split it in the track editor and it is two sections that are
     * still one ride — one station, one operator, one e-stop — so the new half joins the ride rather
     * than getting a controller of its own, closed, that a train crossing onto it would obey. Every
     * lookup goes through here, so a panel or a train on either half finds the same controller.</p>
     */
    private final Map<Integer, Integer> homes = new LinkedHashMap<>();

    /** The section whose controller runs {@code sectionId}'s ride: itself, unless it joined another. */
    public int home(int sectionId) {
        int home = sectionId;
        // Bounded, so a cycle in a hand-edited save cannot hang the server.
        for (int i = 0; i < 64 && homes.containsKey(home); i++) {
            home = homes.get(home);
        }
        return home;
    }

    /** Every section that is part of {@code sectionId}'s ride, its home included. */
    public Set<Integer> members(int sectionId) {
        int home = home(sectionId);
        Set<Integer> out = new TreeSet<>();
        out.add(home);
        for (int member : homes.keySet()) {
            if (home(member) == home) {
                out.add(member);
            }
        }
        return out;
    }

    /** Makes {@code member} part of {@code rideOf}'s ride — the new half of a split section. */
    public void join(int member, int rideOf) {
        int home = home(rideOf);
        if (home != member) {
            homes.put(member, home);
        }
    }

    /**
     * {@code removed} was merged into {@code survivor}: the two are one ride from now on. The ride
     * that had been operated keeps its controller — if only the removed section's had been, its
     * controller runs the merged section. No controller is discarded, so undoing the merge finds
     * each section's own again.
     */
    public void absorb(int removed, int survivor) {
        int keep = home(survivor);
        int other = home(removed);
        if (keep == other) {
            return;
        }
        if (!bySection.containsKey(keep) && bySection.containsKey(other)) {
            homes.put(survivor, other);
        }
        else {
            homes.put(removed, keep);
        }
    }

    /** The member-to-home map, for saving. */
    public Map<Integer, Integer> homes() {
        return Collections.unmodifiableMap(homes);
    }

    /** Replaces the member-to-home map — when loading, or when an undo restores the track. */
    public void setHomes(Map<Integer, Integer> restored) {
        homes.clear();
        homes.putAll(restored);
    }

    /** The ride's controller, or {@code null} if nobody has operated it. */
    public RideController get(int sectionId) {
        return bySection.get(home(sectionId));
    }

    /** The ride's controller, created with today's defaults if it does not exist yet. */
    public RideController getOrCreate(int sectionId) {
        return bySection.computeIfAbsent(home(sectionId), RideController::new);
    }

    /** Puts a controller in place, replacing any for the same section. Used when loading. */
    public void put(RideController controller) {
        bySection.put(controller.sectionId(), controller);
    }

    /**
     * {@code sectionId}'s track was deleted. It leaves its ride, and the ride's controller goes only
     * if no other section still belongs to it.
     */
    public RideController remove(int sectionId) {
        int oldHome = home(sectionId);
        homes.remove(sectionId);
        if (referenced(sectionId)) {
            // Others still run under it: the controller stays, keyed by a section that is gone,
            // until the last of them goes too.
            departed.add(sectionId);
            return null;
        }
        RideController removed = bySection.remove(sectionId);
        if (oldHome != sectionId && departed.contains(oldHome) && !referenced(oldHome)) {
            departed.remove(oldHome);
            bySection.remove(oldHome);
        }
        return removed;
    }

    /** Homes whose own section was deleted while others still belonged to the ride. */
    private final Set<Integer> departed = new TreeSet<>();

    private boolean referenced(int home) {
        for (int member : homes.keySet()) {
            if (home(member) == home) {
                return true;
            }
        }
        return false;
    }

    public Collection<RideController> all() {
        return Collections.unmodifiableCollection(bySection.values());
    }

    public boolean isEmpty() {
        return bySection.isEmpty();
    }

    public void clear() {
        bySection.clear();
        homes.clear();
        departed.clear();
    }

    /** The gate a station on {@code sectionId} should ask: its controller, or always-yes. */
    public DispatchGate gateFor(int sectionId) {
        RideController controller = bySection.get(home(sectionId));
        return controller == null ? DispatchGate.ALWAYS : controller;
    }

    /** Whether any train on {@code sectionId} is under an emergency stop. */
    public boolean isEmergencyStopped(int sectionId) {
        RideController controller = bySection.get(home(sectionId));
        return controller != null && controller.isEmergencyStopped();
    }
}
