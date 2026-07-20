package com.micatechnologies.minecraft.rcmc.physics.element;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every {@link RideElement} in one world, and the {@link TrainManager.ExternalAcceleration}
 * implementation that feeds them into the simulation.
 *
 * <p>This is the concrete answer to the thing {@code docs/design/PHYSICS.md} deliberately leaves
 * as "supplied by the caller": this class is that caller. Given a train, it finds which element
 * (if any) contains the train's current position and delegates to it, and returns zero — meaning
 * pure gravity and drag, exactly as if this set didn't exist — for every stretch of track that
 * isn't covered by one.</p>
 *
 * <p><b>Overlapping elements.</b> Elements should not normally share track, but nothing at this
 * layer prevents it — most plausibly a short trim brake authored inside the tail of a longer
 * launch run, or a station's drive tyres overlapping the very end of a brake run feeding it.
 * Resolution is <b>first match wins</b>, in the order elements were added to this set. That is a
 * park-authoring decision, not a physics one: whoever builds the ride adds the element that should
 * take precedence (typically the safety-critical one — a block brake pre-empting a launch, say)
 * first. Insertion order was chosen over trying to infer "which element is more specific" from the
 * spans themselves, because span geometry alone doesn't reliably encode intent (a short span isn't
 * always the higher-priority one), whereas author-controlled ordering is simple, predictable, and
 * — the property that actually matters here — deterministic.</p>
 */
public final class RideElementSet implements TrainManager.ExternalAcceleration {

    private final List<RideElement> elements = new ArrayList<>();

    public void add(RideElement element) {
        if (element == null) {
            throw new IllegalArgumentException("element must not be null");
        }
        elements.add(element);
    }

    public boolean remove(RideElement element) {
        return elements.remove(element);
    }

    /** The first element (in insertion order) whose span contains {@code ref}, or {@code null}. */
    public RideElement find(TrackRef ref) {
        for (RideElement element : elements) {
            if (element.contains(ref)) {
                return element;
            }
        }
        return null;
    }

    public List<RideElement> elements() {
        return Collections.unmodifiableList(elements);
    }

    public int count() {
        return elements.size();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Looks up the element (if any) covering {@code train}'s current position and delegates to
     * it — see the class javadoc for what happens when more than one element could match.</p>
     */
    @Override
    public double forTrain(int trainId, Train train) {
        RideElement element = find(train.reference());
        return element == null ? 0.0D : element.accelerationFor(train);
    }

    @Override
    public String toString() {
        return "RideElementSet{" + elements.size() + " elements}";
    }
}
