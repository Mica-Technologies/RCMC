package com.micatechnologies.minecraft.rcmc.physics.ride;

import com.micatechnologies.minecraft.rcmc.physics.element.DispatchGate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every ride controller in one world, by the section its ride runs on.
 *
 * <p>A controller exists only once someone has operated the ride — linked a panel to it, or changed
 * a setting. A ride without one behaves as a fresh controller would (open, automatic), so there is
 * nothing to create for every coaster up front and nothing to clean up for one never touched.</p>
 */
public final class RideControllers {

    private final Map<Integer, RideController> bySection = new LinkedHashMap<>();

    /** The ride's controller, or {@code null} if nobody has operated it. */
    public RideController get(int sectionId) {
        return bySection.get(sectionId);
    }

    /** The ride's controller, created with today's defaults if it does not exist yet. */
    public RideController getOrCreate(int sectionId) {
        return bySection.computeIfAbsent(sectionId, RideController::new);
    }

    /** Puts a controller in place, replacing any for the same section. Used when loading. */
    public void put(RideController controller) {
        bySection.put(controller.sectionId(), controller);
    }

    /** Forgets a ride's controller — its track was deleted. */
    public RideController remove(int sectionId) {
        return bySection.remove(sectionId);
    }

    public Collection<RideController> all() {
        return Collections.unmodifiableCollection(bySection.values());
    }

    public boolean isEmpty() {
        return bySection.isEmpty();
    }

    public void clear() {
        bySection.clear();
    }

    /** The gate a station on {@code sectionId} should ask: its controller, or always-yes. */
    public DispatchGate gateFor(int sectionId) {
        RideController controller = bySection.get(sectionId);
        return controller == null ? DispatchGate.ALWAYS : controller;
    }

    /** Whether any train on {@code sectionId} is under an emergency stop. */
    public boolean isEmergencyStopped(int sectionId) {
        RideController controller = bySection.get(sectionId);
        return controller != null && controller.isEmergencyStopped();
    }
}
