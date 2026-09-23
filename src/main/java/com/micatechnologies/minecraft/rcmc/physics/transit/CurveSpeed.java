package com.micatechnologies.minecraft.rcmc.physics.transit;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Speed limits from the track's own curves: a metro train slows for a bend the way it slows for
 * a station.
 *
 * <p>Before this a service ran every curve at line speed — 15 blocks/s round a 30-block radius is
 * 0.75 g sideways, which no metro would ask of a standing passenger. Every point of track now has
 * a limit, {@code sqrt(A / curvature)}: the speed at which the curve asks {@link #LATERAL} of a
 * rider. Taken from the curvature of the whole path, it limits crests and dips the same way.</p>
 *
 * <p>The driver is given one number, {@link #allowed}: the fastest speed from which it can still
 * be at or under every limit ahead by the time it reaches it, braking at the service rate — and
 * under the limit of any curve the train is already in, for its whole length, because the last
 * car takes the bend at the same speed as the first. That is the same braking law the driver
 * already uses for a station, so a curve ahead reads to it exactly like a stop that is not quite
 * a stop.</p>
 *
 * <p>Curvature is measured from tangents {@link #HALF_SPAN} either side rather than at a point:
 * a centripetal Catmull-Rom spline's curvature steps at every node, and a limit that followed
 * those steps would have trains braking for corners that are not there.</p>
 */
public final class CurveSpeed {

    /** Sideways acceleration a limit allows, blocks/s² — about an eighth of a g, a firm metro curve. */
    public static final double LATERAL = 1.2D;

    /** No curve limits a train below this, blocks/s: past this it is a buffer stop, not a bend. */
    public static final double FLOOR = 2.0D;

    /** Spacing of a section's precomputed limits, in blocks. */
    static final double STEP = 1.0D;

    /** Tangents are compared this far either side of a point to measure its curvature. */
    static final double HALF_SPAN = 2.0D;

    private static final int MAX_HOPS = 64;

    /** Limits per section, by identity: sections are immutable, and an edit makes a new one. */
    private static final Map<TrackSection, double[]> PROFILES = new WeakHashMap<>();

    private CurveSpeed() {
    }

    /** The curve limit at {@code s} along {@code section}, blocks/s; infinite on straight track. */
    public static double limitAt(TrackSection section, double s) {
        double[] profile = profile(section);
        int i = (int) Math.round(s / STEP);
        return profile[Math.max(0, Math.min(profile.length - 1, i))];
    }

    /**
     * The fastest a train may go now and still meet every curve limit, blocks/s — infinite when
     * none is in reach.
     *
     * @param lead      the train's lead reference
     * @param facing    the way it is travelling, along the lead section's axis
     * @param length    how far the train reaches back (toward -s) from its lead reference, blocks:
     *                  all of it is held to the limits under it
     * @param brake     the deceleration it plans to slow at, blocks/s²
     * @param lookAhead how far ahead to look, blocks: at least a stop from line speed
     */
    public static double allowed(TrackNetwork network, TrackRef lead, double facing, double length,
                                 double brake, double lookAhead) {
        // The cars trail the lead reference toward -s whichever way the train runs, so the body is
        // always that stretch; running -s it is the tail that leads, and the look ahead starts there.
        double underTrain = scan(network, lead, -1.0D, length, brake, false);
        TrackRef front = lead;
        double dir = 1.0D;
        if (facing < 0.0D) {
            TrackNetwork.Traversal tail = network.advance(lead, -length);
            front = tail.ref;
            // Past a join that flips the axis, carrying on the same way is +s on the tail's section.
            dir = tail.reversed ? 1.0D : -1.0D;
        }
        double ahead = scan(network, front, dir, lookAhead, brake, true);
        return Math.min(underTrain, ahead);
    }

    /**
     * Walks {@code horizon} blocks from {@code from} in {@code dir}, returning the lowest limit met
     * — raised, when {@code braking}, by what braking over the distance to it buys.
     */
    private static double scan(TrackNetwork network, TrackRef from, double dir, double horizon,
                               double brake, boolean braking) {
        double lowest = Double.POSITIVE_INFINITY;
        int sectionId = from.sectionId();
        double position = from.distance();
        double walked = 0.0D;
        for (int hops = 0; hops <= MAX_HOPS && walked <= horizon; hops++) {
            TrackSection section = network.section(sectionId);
            if (section == null) {
                break;
            }
            double[] profile = profile(section);
            double length = section.totalLength();
            double toEnd = dir > 0.0D ? length - position : position;
            double reach = Math.min(toEnd, horizon - walked);
            // Every sample between here and as far as this section takes us, nearest first.
            int first = (int) (dir > 0.0D ? Math.ceil(position / STEP) : Math.floor(position / STEP));
            for (int i = first; ; i += dir > 0.0D ? 1 : -1) {
                if (i < 0 || i >= profile.length) {
                    break;
                }
                double x = Math.abs(i * STEP - position);
                if (x > reach) {
                    break;
                }
                double limit = profile[i];
                if (Double.isInfinite(limit)) {
                    continue;
                }
                double permitted = braking
                    ? Math.sqrt(limit * limit + 2.0D * brake * (walked + x))
                    : limit;
                lowest = Math.min(lowest, permitted);
            }
            walked += toEnd;
            if (walked > horizon) {
                break;
            }
            if (section.isClosed()) {
                // Round the seam and on along the same section.
                position = dir > 0.0D ? 0.0D : length;
                continue;
            }
            TrackNetwork.SectionEnd arriving = network.linkedTo(new TrackNetwork.SectionEnd(
                sectionId, dir > 0.0D ? TrackNetwork.End.END : TrackNetwork.End.START));
            if (arriving == null || network.section(arriving.sectionId) == null) {
                break;
            }
            sectionId = arriving.sectionId;
            if (arriving.end == TrackNetwork.End.START) {
                position = 0.0D;
                dir = 1.0D;
            }
            else {
                position = network.section(sectionId).totalLength();
                dir = -1.0D;
            }
        }
        return lowest;
    }

    /** The section's limits every {@link #STEP} blocks, computed once per section instance. */
    static double[] profile(TrackSection section) {
        synchronized (PROFILES) {
            double[] cached = PROFILES.get(section);
            if (cached != null) {
                return cached;
            }
        }
        double length = section.totalLength();
        int count = (int) Math.floor(length / STEP) + 1;
        double[] limits = new double[count];
        for (int i = 0; i < count; i++) {
            limits[i] = limitFromCurvature(curvature(section, i * STEP));
        }
        synchronized (PROFILES) {
            PROFILES.put(section, limits);
        }
        return limits;
    }

    /** Curvature at {@code s}, blocks⁻¹, from the turn of the tangent across {@link #HALF_SPAN} either side. */
    static double curvature(TrackSection section, double s) {
        double length = section.totalLength();
        double a = s - HALF_SPAN;
        double b = s + HALF_SPAN;
        if (section.isClosed()) {
            a = Math.floorMod((long) Math.round(a * 1000.0D), Math.round(length * 1000.0D)) / 1000.0D;
            b = Math.floorMod((long) Math.round(b * 1000.0D), Math.round(length * 1000.0D)) / 1000.0D;
        }
        else {
            a = Math.max(0.0D, a);
            b = Math.min(length, b);
        }
        double span = section.isClosed() ? 2.0D * HALF_SPAN : Math.max(1e-6D, (b - a));
        Vec3 ta = section.tangentAtDistance(a).normalize();
        Vec3 tb = section.tangentAtDistance(b).normalize();
        double dot = Math.max(-1.0D, Math.min(1.0D, ta.dot(tb)));
        return Math.acos(dot) / span;
    }

    static double limitFromCurvature(double curvature) {
        if (curvature < 1e-5D) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(FLOOR, Math.sqrt(LATERAL / curvature));
    }
}
