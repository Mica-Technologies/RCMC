package com.micatechnologies.minecraft.rcmc.physics.element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Copies of ride hardware moved along its track — what an edit to a section's nodes needs, so the
 * hardware stays on the spans it was laid on.
 *
 * <p>Elements are immutable, and each keeps its own settings; this is the one place that knows how
 * to rebuild each kind with only its distances changed.</p>
 */
public final class RideElements {

    private RideElements() {
    }

    /**
     * {@code element} with every distance on {@code sectionId} passed through {@code map} — its
     * span, a station's stop point, a transfer track's storage offset when the storage is the
     * section edited. {@code element} itself when nothing of it is on that section.
     */
    public static RideElement moved(RideElement element, int sectionId, DoubleUnaryOperator map,
                                    double tickSeconds) {
        if (element instanceof TransferTrack) {
            TransferTrack t = (TransferTrack) element;
            boolean onTable = t.sectionId() == sectionId;
            boolean onStorage = t.isLinked() && t.storageSectionId() == sectionId;
            if (!onTable && !onStorage) {
                return element;
            }
            return new TransferTrack(t.sectionId(),
                onTable ? map.applyAsDouble(t.startDistance()) : t.startDistance(),
                onTable ? map.applyAsDouble(t.endDistance()) : t.endDistance(),
                t.tyreSpeed(), t.maxAcceleration(), tickSeconds, t.storageSectionId(),
                onStorage ? map.applyAsDouble(t.storageOffset()) : t.storageOffset());
        }
        if (element.sectionId() != sectionId) {
            return element;
        }
        double from = map.applyAsDouble(element.startDistance());
        double to = map.applyAsDouble(element.endDistance());
        if (element instanceof ChainLift) {
            ChainLift e = (ChainLift) element;
            return new ChainLift(sectionId, from, to, e.chainSpeed(), e.maxAcceleration(), tickSeconds);
        }
        if (element instanceof LaunchTrack) {
            LaunchTrack e = (LaunchTrack) element;
            return new LaunchTrack(sectionId, from, to, e.targetSpeed(), e.constantAcceleration());
        }
        if (element instanceof BrakeRun) {
            BrakeRun e = (BrakeRun) element;
            return new BrakeRun(sectionId, from, to, e.targetSpeed(), e.deceleration(), e.mode(), tickSeconds);
        }
        if (element instanceof DriveTyres) {
            DriveTyres e = (DriveTyres) element;
            return new DriveTyres(sectionId, from, to, e.driveSpeed(), e.maxAcceleration(), tickSeconds);
        }
        if (element instanceof StationPlatform) {
            StationPlatform e = (StationPlatform) element;
            double stop = Math.max(from, Math.min(to, map.applyAsDouble(e.stopDistance())));
            return new StationPlatform(sectionId, from, to, stop, e.brakeDeceleration(), e.dwellTicks(),
                e.dispatchAcceleration(), e.dispatchSpeed(), tickSeconds, e.passThroughs());
        }
        if (element instanceof StorageBerth) {
            return new StorageBerth(sectionId, from, to, tickSeconds);
        }
        // An element with no copy here keeps its distances: better stale than silently dropped.
        return element;
    }

    /** Shorter than this, blocks, and what is left of an element either side of a cut is dropped. */
    static final double MIN_PIECE = 1.0D;

    /**
     * What is left of {@code element} once {@code [from, to)} of section {@code sectionId} is given
     * over to something else: the parts before and after it, each keeping its settings.
     *
     * <p>Retyping one span of a lift three spans long leaves the other two spans a lift — it does
     * not take the whole lift away. {@code element} alone when it does not overlap the cut.</p>
     *
     * <p>A station keeps one piece, the one its stop point is on, or the longer when the stop was
     * in the cut: a platform in two halves would be two stations. Transfer tracks and storage
     * berths go whole, because the table and its berth are laid to match and cannot be trimmed
     * apart.</p>
     */
    public static List<RideElement> cutAround(RideElement element, int sectionId, double from,
                                              double to, double tickSeconds) {
        boolean overlaps = element.sectionId() == sectionId
            && element.endDistance() > from && element.startDistance() < to;
        if (!overlaps) {
            return Collections.singletonList(element);
        }
        if (element instanceof TransferTrack || element instanceof StorageBerth) {
            return Collections.emptyList();
        }
        RideElement before = element.startDistance() < from - MIN_PIECE
            ? moved(element, sectionId, d -> Math.min(d, from), tickSeconds) : null;
        RideElement after = element.endDistance() > to + MIN_PIECE
            ? moved(element, sectionId, d -> Math.max(d, to), tickSeconds) : null;
        if (element instanceof StationPlatform && before != null && after != null) {
            double stop = ((StationPlatform) element).stopDistance();
            if (stop < from) {
                after = null;
            }
            else if (stop >= to) {
                before = null;
            }
            else if (length(before) >= length(after)) {
                after = null;
            }
            else {
                before = null;
            }
        }
        List<RideElement> pieces = new ArrayList<>(2);
        if (before != null) {
            pieces.add(before);
        }
        if (after != null) {
            pieces.add(after);
        }
        return pieces;
    }

    private static double length(RideElement element) {
        return element.endDistance() - element.startDistance();
    }
}
