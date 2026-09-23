package com.micatechnologies.minecraft.rcmc.physics.element;

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
}
