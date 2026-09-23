package com.micatechnologies.minecraft.rcmc.physics;

import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;

/**
 * Where a rider's heart is, and the curve it follows — which is what a rider feels.
 *
 * <p>A rider sits above the rail, not on it. On level track and in ordinary turns the difference
 * hardly matters, but in a roll it is everything: roll a car about the rail and the rider's chest
 * swings round a circle a block across, which is a violent sideways throw the rail itself never
 * makes. That is why real coasters roll about the <em>heartline</em> — the path of the riders'
 * chests — and let the rail spiral round it instead. It is also why G has to be measured here: G
 * measured on the rail says a heartline roll is violent (the rail is the thing spiralling) and a
 * roll about the rail is smooth (the rail is straight), which is exactly backwards.</p>
 *
 * <p>The heart is {@link #HEIGHT} above the track frame, along its {@code up}. Its curvature is
 * found by sampling that point either side of the car, since nothing gives it in closed form.</p>
 */
public final class Heartline {

    /**
     * Height of a seated rider's chest above the track frame, in blocks: the seat is
     * {@code CoasterCarLayout.SEAT_HEIGHT} up, and a seated chest about 0.6 above that. The same
     * height the track builder shapes rolls about — see {@code HeartlineShaper}.
     */
    public static final double HEIGHT =
        com.micatechnologies.minecraft.rcmc.track.element.HeartlineShaper.HEART_HEIGHT;

    /**
     * Spacing, in blocks of track, of the points the heart's curvature is sampled from.
     *
     * <p>A block either side, not less. The track is a Catmull-Rom spline, which is smooth in
     * direction but steps in curvature at every node; where the rail spirals round the riders'
     * hearts in a roll, its curvature is large, and sampled finer than the node spacing those steps
     * read as half a g of sideways noise flickering node to node. A rider's body, the seat and the
     * car's wheels do not feel centimetre ripples either — ride accelerometer data is filtered for
     * the same reason. Every real feature of a ride, a clothoid, a crest or a roll, is several blocks
     * long and still shows in full.</p>
     */
    public static final double HALF_STEP = 1.0D;

    /** The heart's path at one point: how sharply it curves, toward where, and how fast it moves
     *  for each block the train travels. */
    public static final class Sample {
        public final double curvature;
        /** Unit vector toward the centre of curvature; {@code null} where the path is straight. */
        public final Vec3 direction;
        /** Heart speed over train speed: above 1 where the heart swings round the rail. */
        public final double speedFactor;
        /** True when the sample was cut short by an open section's end. */
        public final boolean clipped;

        Sample(double curvature, Vec3 direction, double speedFactor, boolean clipped) {
            this.curvature = curvature;
            this.direction = direction;
            this.speedFactor = speedFactor;
            this.clipped = clipped;
        }
    }

    private Heartline() {
    }

    /** The heart's position above {@code frame}. */
    public static Vec3 heart(TrackFrame frame) {
        return frame.position.add(frame.up.scale(HEIGHT));
    }

    /** The heart's path at distance {@code s} along {@code section}. */
    public static Sample at(TrackSection section, double s) {
        double total = section.totalLength();
        double lo = s - HALF_STEP;
        double hi = s + HALF_STEP;
        boolean clipped = false;
        if (!section.isClosed()) {
            if (lo < 0.0D) {
                hi = Math.min(total, hi - lo);
                lo = 0.0D;
                clipped = true;
            }
            if (hi > total) {
                lo = Math.max(0.0D, lo - (hi - total));
                hi = total;
                clipped = true;
            }
        }
        double mid = (lo + hi) / 2.0D;
        double step = (hi - lo) / 2.0D;
        if (step < 1.0e-6D) {
            return new Sample(0.0D, null, 1.0D, true);
        }
        Vec3 before = heart(section.frameAtDistance(wrap(lo, total, section.isClosed())));
        Vec3 here = heart(section.frameAtDistance(wrap(mid, total, section.isClosed())));
        Vec3 after = heart(section.frameAtDistance(wrap(hi, total, section.isClosed())));
        Vec3 in = here.subtract(before);
        Vec3 out = after.subtract(here);
        double inLength = in.length();
        double outLength = out.length();
        double speedFactor = (inLength + outLength) / (2.0D * step);
        if (inLength < 1.0e-9D || outLength < 1.0e-9D) {
            return new Sample(0.0D, null, speedFactor, clipped);
        }
        Vec3 turn = out.scale(1.0D / outLength).subtract(in.scale(1.0D / inLength));
        double curvature = turn.length() / ((inLength + outLength) / 2.0D);
        Vec3 direction = curvature < 1.0e-9D ? null : turn.normalize();
        return new Sample(curvature, direction, speedFactor, clipped);
    }

    /**
     * What a rider feels at distance {@code s}: the heart's own curve at the heart's own speed.
     *
     * @param trainSpeed            the train's speed along the track, blocks/s
     * @param alongTrackAcceleration the train's acceleration along the track, blocks/s²
     */
    public static GForces forces(TrackSection section, double s, double trainSpeed,
                                 double alongTrackAcceleration, double gravity) {
        TrackFrame frame = section.frameAtDistance(s);
        Sample sample = at(section, s);
        double speed = Math.abs(trainSpeed) * sample.speedFactor;
        return GForces.at(frame, speed, sample.curvature, sample.direction, alongTrackAcceleration, gravity);
    }

    private static double wrap(double s, double total, boolean closed) {
        if (!closed) {
            return Math.max(0.0D, Math.min(total, s));
        }
        double w = s % total;
        return w < 0.0D ? w + total : w;
    }
}
