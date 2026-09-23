package com.micatechnologies.minecraft.rcmc.physics.element;

import com.micatechnologies.minecraft.rcmc.physics.Train;

/**
 * A transfer table: a stretch of the circuit that can slide sideways, train and all, onto a parallel
 * storage track — how a real coaster takes a train out of service, or brings one back.
 *
 * <p>Most of the time it is drive tyres: trains cross it at a walking pace, which is why it belongs
 * just before the station. While a store is pending ({@link #setHolding}) the tyres stop the next
 * train on it instead, and once that train is wholly on the table and at rest the world tick slides
 * it across. The move itself is not this element's: it moves trains between sections, and elements
 * only ever push the one they are given.</p>
 *
 * <p>The storage side is a {@link StorageBerth} on its own section, and {@code storageOffset} is the
 * point on it level with this element's start. Unlinked — no storage yet — it is simply tyres.</p>
 */
public final class TransferTrack extends RideElementSpan {

    /** No storage track linked. */
    public static final int UNLINKED = -1;

    private final double tyreSpeed;
    private final double maxAcceleration;
    private final double tickSeconds;
    private final int storageSectionId;
    private final double storageOffset;

    /** Runtime, set every tick from the ride: whether a store is waiting for a train. */
    private boolean holding;

    public TransferTrack(int sectionId, double startDistance, double endDistance, double tyreSpeed,
                         double maxAcceleration, double tickSeconds, int storageSectionId,
                         double storageOffset) {
        super(sectionId, startDistance, endDistance);
        if (maxAcceleration <= 0.0D || tickSeconds <= 0.0D) {
            throw new IllegalArgumentException("maxAcceleration and tickSeconds must be positive");
        }
        this.tyreSpeed = tyreSpeed;
        this.maxAcceleration = maxAcceleration;
        this.tickSeconds = tickSeconds;
        this.storageSectionId = storageSectionId;
        this.storageOffset = storageOffset;
    }

    @Override
    public double accelerationFor(Train train) {
        if (!holding) {
            return VelocityServo.accelerationToHold(train.velocity(), tyreSpeed, maxAcceleration,
                tickSeconds);
        }
        // Creep on to the far end and stop there, so the whole train ends up on the table: stopped
        // the moment it arrived, it would stand half on and could never be slid across.
        // Braking is a brake's, not a tyre's: a train can reach the table well above creep speed.
        double remaining = endDistance() - STOP_SHORT - train.reference().distance();
        double target = remaining <= 0.0D ? 0.0D
            : Math.signum(tyreSpeed) * Math.min(Math.abs(tyreSpeed),
                Math.sqrt(2.0D * HOLD_DECELERATION * remaining));
        double rate = Math.abs(train.velocity()) > Math.abs(target) ? HOLD_DECELERATION : maxAcceleration;
        return VelocityServo.accelerationToHold(train.velocity(), target, rate, tickSeconds);
    }

    /** How far short of the table's end a train being stored is stopped, blocks. */
    static final double STOP_SHORT = 0.5D;

    /** How hard the table brakes a train it is stopping for storage, blocks/s². */
    static final double HOLD_DECELERATION = 6.0D;

    /** A train stopped here for a transfer is being held, not stalled. */
    @Override
    public boolean isHolding() {
        return holding;
    }

    public void setHolding(boolean holding) {
        this.holding = holding;
    }

    public boolean isLinked() {
        return storageSectionId != UNLINKED;
    }

    /** This transfer, linked to a storage track: {@code offset} is level with this one's start. */
    public TransferTrack linkedTo(int storageSection, double offset) {
        return new TransferTrack(sectionId(), startDistance(), endDistance(), tyreSpeed,
            maxAcceleration, tickSeconds, storageSection, offset);
    }

    public double tyreSpeed() {
        return tyreSpeed;
    }

    public double maxAcceleration() {
        return maxAcceleration;
    }

    public int storageSectionId() {
        return storageSectionId;
    }

    public double storageOffset() {
        return storageOffset;
    }
}
