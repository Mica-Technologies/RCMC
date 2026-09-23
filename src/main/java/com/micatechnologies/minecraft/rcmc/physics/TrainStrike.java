package com.micatechnologies.minecraft.rcmc.physics;

import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;

/**
 * What happens to someone standing in the way of a moving train: whether a car's body touches them,
 * which way they are thrown, and how badly they are hurt.
 *
 * <p>The train itself is untouched. It has one degree of freedom, distance along the track, and a
 * car cannot be deflected or derailed by what it hits — so a collision is entirely something that
 * happens to the other party. That keeps it cheap and keeps the physics exact.</p>
 *
 * <p>Pure geometry in the car's own frame, so it holds for a car at any angle, banked or climbing:
 * a car is a box {@code halfLength} long each way, {@code halfWidth} each side and from
 * {@code bottom} to {@code top} above the track, and a body is an upright column.</p>
 */
public final class TrainStrike {

    /** Below this speed, blocks/s, a train shoves people clear without hurting them. */
    public static final double HARMLESS_SPEED = 3.0D;

    /**
     * Damage per block/s over {@link #HARMLESS_SPEED}, in half-hearts. A lift at 5 b/s bruises
     * (about a heart and a half); a metro at 15 b/s nearly kills; a coaster at 20 b/s kills.
     */
    public static final double DAMAGE_PER_SPEED = 1.6D;

    /** The solid body of a car, in its own frame. */
    public static final class Body {
        public final double halfLength;
        public final double halfWidth;
        public final double bottom;
        public final double top;

        public Body(double halfLength, double halfWidth, double bottom, double top) {
            this.halfLength = halfLength;
            this.halfWidth = halfWidth;
            this.bottom = bottom;
            this.top = top;
        }
    }

    private TrainStrike() {
    }

    /** The body of one car of {@code spec}. */
    public static Body bodyOf(TrainSpec spec) {
        if (spec.carStyle() == TrainSpec.CarStyle.METRO) {
            return new Body(CarSeating.bodyLength(spec) * 0.5D, CarSeating.METRO_BODY_HALF_WIDTH, 0.3D,
                CarSeating.METRO_ROOF_HEIGHT);
        }
        // A coaster car's tub, up to the height of its riders' heads.
        return new Body(spec.carLength() * 0.5D, CoasterCarLayout.BODY_HALF_WIDTH, 0.0D, 1.3D);
    }

    /**
     * Where a column standing on {@code feet}, {@code halfWidth} each way and {@code height} tall,
     * touches a car's body, as {@code {along, across}} offsets from the car's centre — or
     * {@code null} if it does not.
     */
    public static double[] contact(TrackFrame frame, Body body, Vec3 feet, double halfWidth, double height) {
        Vec3 d = feet.subtract(frame.position);
        double along = d.dot(frame.forward);
        double across = d.dot(frame.right);
        double up = d.dot(frame.up);
        if (Math.abs(along) > body.halfLength + halfWidth || Math.abs(across) > body.halfWidth + halfWidth) {
            return null;
        }
        if (up > body.top || up + height < body.bottom) {
            return null;
        }
        return new double[] {along, across};
    }

    /** Damage, in half-hearts, from being hit at {@code speed} blocks/s; {@code multiplier} scales it. */
    public static double damage(double speed, double multiplier) {
        if (multiplier <= 0.0D || speed <= HARMLESS_SPEED) {
            return 0.0D;
        }
        return (speed - HARMLESS_SPEED) * DAMAGE_PER_SPEED * multiplier;
    }

    /**
     * The velocity, in blocks per tick, a car moving at {@code velocity} along its frame gives
     * someone it hits at {@code across}: out to the side they were on — clear of the track, so the
     * next car does not hit them again — and forward with the train, harder the faster it is going.
     */
    public static Vec3 shove(TrackFrame frame, double velocity, double across) {
        double speed = Math.abs(velocity);
        double side = across >= 0.0D ? 1.0D : -1.0D;
        double forward = speed <= HARMLESS_SPEED ? 0.0D : Math.min(1.6D, speed * 0.05D);
        double sideways = 0.35D + Math.min(0.9D, speed * 0.03D);
        double lift = speed <= HARMLESS_SPEED ? 0.1D : Math.min(0.6D, 0.25D + speed * 0.015D);
        Vec3 flatForward = new Vec3(frame.forward.x, 0.0D, frame.forward.z);
        Vec3 flatRight = new Vec3(frame.right.x, 0.0D, frame.right.z);
        flatForward = flatForward.lengthSquared() < 1.0e-9D ? Vec3.ZERO : flatForward.normalize();
        flatRight = flatRight.lengthSquared() < 1.0e-9D ? Vec3.ZERO : flatRight.normalize();
        double direction = velocity >= 0.0D ? 1.0D : -1.0D;
        return flatForward.scale(direction * forward).add(flatRight.scale(side * sideways)).add(new Vec3(0.0D, lift, 0.0D));
    }
}
