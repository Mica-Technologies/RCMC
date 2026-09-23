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

    /**
     * {@code element} laid on {@code [newStart, newEnd]} of section {@code newSectionId}, keeping its
     * settings — for track that has moved to another section, or been turned round.
     *
     * <p>Hardware acts along the direction of travel, so a lift on turned-round track still pulls
     * trains the way they now run. A station keeps its stop point the same distance short of the
     * platform's exit end: turned round, the exit is the other end, and a stop kept where it was
     * would leave the train hanging out of the platform it has just run into.
     * {@code stopMap} places the stop when the track was not turned round.</p>
     *
     * <p>{@code null} for a linked transfer track and for storage berths, which are laid to match
     * each other and cannot be moved one at a time.</p>
     */
    public static RideElement relocated(RideElement element, int newSectionId, double newStart,
                                        double newEnd, boolean reversed, DoubleUnaryOperator stopMap,
                                        double tickSeconds) {
        double from = Math.min(newStart, newEnd);
        double to = Math.max(newStart, newEnd);
        if (element instanceof ChainLift) {
            ChainLift e = (ChainLift) element;
            return new ChainLift(newSectionId, from, to, e.chainSpeed(), e.maxAcceleration(), tickSeconds);
        }
        if (element instanceof LaunchTrack) {
            LaunchTrack e = (LaunchTrack) element;
            return new LaunchTrack(newSectionId, from, to, e.targetSpeed(), e.constantAcceleration());
        }
        if (element instanceof BrakeRun) {
            BrakeRun e = (BrakeRun) element;
            return new BrakeRun(newSectionId, from, to, e.targetSpeed(), e.deceleration(), e.mode(), tickSeconds);
        }
        if (element instanceof DriveTyres) {
            DriveTyres e = (DriveTyres) element;
            return new DriveTyres(newSectionId, from, to, e.driveSpeed(), e.maxAcceleration(), tickSeconds);
        }
        if (element instanceof TransferTrack && !((TransferTrack) element).isLinked()) {
            // Unlinked, a transfer table is only its tyres; there is no storage to keep it in line with.
            TransferTrack e = (TransferTrack) element;
            return new TransferTrack(newSectionId, from, to, e.tyreSpeed(), e.maxAcceleration(), tickSeconds,
                TransferTrack.UNLINKED, 0.0D);
        }
        if (element instanceof StationPlatform) {
            StationPlatform e = (StationPlatform) element;
            double stop = reversed
                ? to - (e.endDistance() - e.stopDistance())
                : stopMap.applyAsDouble(e.stopDistance());
            stop = Math.max(from, Math.min(to, stop));
            return new StationPlatform(newSectionId, from, to, stop, e.brakeDeceleration(), e.dwellTicks(),
                e.dispatchAcceleration(), e.dispatchSpeed(), tickSeconds, e.passThroughs());
        }
        return null;
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
     * in the cut: a platform in two halves would be two stations. A linked transfer track and its
     * storage berth go whole, because the two are laid to match and cannot be trimmed apart.</p>
     */
    public static List<RideElement> cutAround(RideElement element, int sectionId, double from,
                                              double to, double tickSeconds) {
        boolean overlaps = element.sectionId() == sectionId
            && element.endDistance() > from && element.startDistance() < to;
        if (!overlaps) {
            return Collections.singletonList(element);
        }
        if ((element instanceof TransferTrack && ((TransferTrack) element).isLinked())
            || element instanceof StorageBerth) {
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
                // The stop was in the cut, and clamping it into this piece puts it at the piece's
                // entry: a train stopping there would hang back out of the platform. Stop it at
                // the exit end instead, as the piece before the cut already does.
                StationPlatform piece = (StationPlatform) after;
                after = new StationPlatform(sectionId, piece.startDistance(), piece.endDistance(),
                    piece.endDistance(), piece.brakeDeceleration(), piece.dwellTicks(),
                    piece.dispatchAcceleration(), piece.dispatchSpeed(), tickSeconds, piece.passThroughs());
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
