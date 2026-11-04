package com.micatechnologies.minecraft.rcmc.track;

import com.micatechnologies.minecraft.rcmc.physics.CarSeating;
import com.micatechnologies.minecraft.rcmc.physics.CoasterCarLayout;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sweeps a train's clearance envelope along a section and finds what in the world stands inside it.
 *
 * <p>Track is spline data, not blocks, so nothing stops a builder laying it through a tree or
 * beside a wall closer than a car is wide; the clash only shows when a train clips through. This
 * walks the section, builds the envelope's cross-section in the car's own frame at each step — so it
 * banks and inverts with the track, exactly as the car does — and asks an {@link Obstacles} what
 * stands at each point.</p>
 *
 * <p>Pure geometry: the world is behind {@link Obstacles}, so the sweep itself is tested on a bare
 * JVM like the rest of this package.</p>
 */
public final class ClearanceSweep {

    /** Along-track step between cross-sections, blocks. */
    public static final double ALONG_STEP = 0.5D;

    /** Spacing of the points across each cross-section, blocks. Thinner than a fence post. */
    public static final double ACROSS_STEP = 0.25D;

    /** Points this close under the envelope's top are taken as its top, so a face exactly at it is clear. */
    private static final double FACE_EPSILON = 1.0e-3D;

    private ClearanceSweep() {
        throw new AssertionError("No instances.");
    }

    /** The space a train needs, in the car frame: across either side of the rail, and up from it. */
    public static final class Envelope {
        /**
         * A coaster car: the tub ({@link CoasterCarLayout#BODY_HALF_WIDTH}) plus room for a rider's
         * arms either side, and a seated rider's head with a hand raised over it.
         */
        public static final Envelope COASTER = new Envelope("coaster",
            CoasterCarLayout.BODY_HALF_WIDTH + 0.28D, 0.25D, 2.3D);

        /** A metro car: its body ({@link CarSeating#METRO_BODY_HALF_WIDTH}) up to its roof. */
        public static final Envelope METRO = new Envelope("metro",
            CarSeating.METRO_BODY_HALF_WIDTH + 0.05D, 0.25D, CarSeating.METRO_ROOF_HEIGHT);

        public final String name;
        public final double halfWidth;
        /**
         * Where the envelope starts above the rail. Not zero: the block the track is laid on has
         * its top face at the rail, and the bogies run on the rails, not on the ground around them.
         */
        public final double bottom;
        public final double top;

        public Envelope(String name, double halfWidth, double bottom, double top) {
            this.name = name;
            this.halfWidth = halfWidth;
            this.bottom = bottom;
            this.top = top;
        }

        /** The envelope for track of style {@code styleId}: metro for transit styles, else coaster. */
        public static Envelope forStyle(String styleId) {
            return TrackStyleIds.isTransit(styleId) ? METRO : COASTER;
        }
    }

    /** What stands at a point of the world, as far as a passing train is concerned. */
    public enum Occupancy {
        CLEAR,
        BLOCKED,
        /** Not known without loading the world there; never treated as clear or as blocked. */
        UNKNOWN
    }

    /** The world, as the sweep asks it. */
    public interface Obstacles {
        /** What stands at {@code (x, y, z)}, which lies in block {@code (bx, by, bz)}. */
        Occupancy at(int bx, int by, int bz, double x, double y, double z);
    }

    /** One block standing in the envelope, at the first distance along the section it was met. */
    public static final class Hit {
        public final int x;
        public final int y;
        public final int z;
        public final double distance;

        Hit(int x, int y, int z, double distance) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.distance = distance;
        }
    }

    /** What a sweep found. */
    public static final class Result {
        /** Every intruding block, once each, in order along the section. */
        public final List<Hit> hits;
        /** Blocks the sweep could not see — in unloaded chunks — once each. */
        public final int unknownBlocks;
        public final double sweptLength;

        Result(List<Hit> hits, int unknownBlocks, double sweptLength) {
            this.hits = Collections.unmodifiableList(hits);
            this.unknownBlocks = unknownBlocks;
            this.sweptLength = sweptLength;
        }
    }

    /** Sweeps the whole of {@code section}, each stretch with the envelope its style needs. */
    public static Result sweep(TrackSection section, Obstacles obstacles) {
        return sweep(section, null, obstacles);
    }

    /**
     * Sweeps {@code section} with {@code envelope}, or with each stretch's own style's envelope when
     * it is {@code null}.
     */
    public static Result sweep(TrackSection section, Envelope envelope, Obstacles obstacles) {
        double length = section.totalLength();
        int steps = Math.max(1, (int) Math.ceil(length / ALONG_STEP));
        // A circuit's last step is its first; an open section's last is its end.
        int last = section.isClosed() ? steps - 1 : steps;
        Map<Long, Occupancy> seen = new HashMap<>();
        List<Hit> hits = new ArrayList<>();
        int unknown = 0;
        for (int i = 0; i <= last; i++) {
            double s = Math.min(length, i * length / steps);
            Envelope e = envelope != null ? envelope : Envelope.forStyle(section.styleAtDistance(s));
            TrackFrame frame = section.frameAtDistance(s);
            int across = (int) Math.ceil(2.0D * e.halfWidth / ACROSS_STEP);
            int up = (int) Math.ceil((e.top - e.bottom) / ACROSS_STEP);
            for (int a = 0; a <= across; a++) {
                double r = -e.halfWidth + 2.0D * e.halfWidth * a / across;
                for (int u = 0; u <= up; u++) {
                    double h = u == up ? e.top - FACE_EPSILON : e.bottom + (e.top - e.bottom) * u / up;
                    Vec3 p = frame.position.add(frame.right.scale(r)).add(frame.up.scale(h));
                    int bx = floor(p.x);
                    int by = floor(p.y);
                    int bz = floor(p.z);
                    long key = key(bx, by, bz);
                    Occupancy known = seen.get(key);
                    if (known == Occupancy.BLOCKED || known == Occupancy.UNKNOWN) {
                        continue;
                    }
                    // A block can be clear at one point and blocked at another (a slab, a fence),
                    // so CLEAR is asked again at each new point; the other two are final.
                    Occupancy now = obstacles.at(bx, by, bz, p.x, p.y, p.z);
                    if (now == Occupancy.BLOCKED) {
                        hits.add(new Hit(bx, by, bz, s));
                    }
                    else if (now == Occupancy.UNKNOWN) {
                        unknown++;
                    }
                    seen.put(key, now);
                }
            }
        }
        return new Result(hits, unknown, length);
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }
}
