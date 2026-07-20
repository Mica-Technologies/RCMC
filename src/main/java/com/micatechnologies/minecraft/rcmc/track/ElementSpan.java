package com.micatechnologies.minecraft.rcmc.track;

/**
 * Where a piece of ride hardware sits, and what kind it is — with none of its behaviour.
 *
 * <p>Exists so the client can <em>draw</em> ride hardware without running it. A lift hill has to
 * look like a lift hill (chain down the middle), a brake run like a brake run, and that needs
 * nothing more than a span and a type name. Syncing the real {@code RideElement} would mean
 * syncing a station's dwell counter and a launch's phase every tick, for no visible gain — the
 * client learns their <em>effect</em> from the corrected train state it already receives.</p>
 *
 * <p>Deliberately in {@code track} rather than {@code physics.element}: this is a description of
 * track, not a participant in the simulation, and nothing here should ever grow an
 * {@code accelerationFor}.</p>
 */
public final class ElementSpan {

    /** Type tag, matching the one the save codec uses — {@code chain_lift}, {@code brake}, … */
    public final String type;

    public final int sectionId;
    public final double startDistance;
    public final double endDistance;

    public ElementSpan(String type, int sectionId, double startDistance, double endDistance) {
        this.type = type;
        this.sectionId = sectionId;
        this.startDistance = startDistance;
        this.endDistance = endDistance;
    }

    public boolean contains(double distance) {
        return distance >= startDistance && distance <= endDistance;
    }

    public boolean isLift() {
        return "chain_lift".equals(type);
    }

    @Override
    public String toString() {
        return "ElementSpan{" + type + " on " + sectionId + " ["
            + String.format("%.1f", startDistance) + ", " + String.format("%.1f", endDistance) + "]}";
    }
}
