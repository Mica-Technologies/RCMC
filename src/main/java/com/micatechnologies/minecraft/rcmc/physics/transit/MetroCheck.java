package com.micatechnologies.minecraft.rcmc.physics.transit;

import com.micatechnologies.minecraft.rcmc.rating.RideWarning;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a metro builder should know about the track their lines run on: where the curves make
 * trains crawl, where a grade is too steep, and where a platform is not level or not straight.
 *
 * <p>The coaster ride check simulates a lap, because a coaster's speed is whatever the track gives
 * it. A metro's is not — the driver holds line speed, slows for curves ({@link CurveSpeed}) and stops
 * at stations — so everything here is read straight off the geometry, and runs fast enough to
 * redraw live while the transit tool is held.</p>
 */
public final class MetroCheck {

    /** A curve that holds trains under this is worth knowing about, blocks/s. */
    public static final double SLOW_CURVE = 5.0D;

    /** Under this a train is crawling, blocks/s. */
    public static final double CRAWL_CURVE = 3.0D;

    /** Grades steeper than this are slow to climb and hard on the brakes. */
    public static final double STEEP_GRADE = 0.06D;

    /**
     * On this grade a train stopped facing uphill can barely start: gravity along it is most of the
     * stock's 1.2 blocks/s² of traction.
     */
    public static final double TOO_STEEP_GRADE = 0.10D;

    /** A platform should be level to within this: a steeper one rolls a pushchair off it. */
    public static final double PLATFORM_GRADE = 0.015D;

    /** A platform on a curve tighter than this leaves a gap between the doors and the edge, blocks. */
    public static final double PLATFORM_RADIUS = 150.0D;

    /** How far either side of a berth's stop point its platform is checked, blocks. */
    public static final double PLATFORM_REACH = 30.0D;

    private static final double STEP = 1.0D;

    private MetroCheck() {
    }

    /** Everything worth flagging on the track every line in {@code transit} stops on. */
    public static List<RideWarning> check(TrackNetwork network, TransitSystem transit) {
        return check(network, transit, transit.lines());
    }

    /** As above, for just {@code lines}. */
    public static List<RideWarning> check(TrackNetwork network, TransitSystem transit,
                                          java.util.Collection<TransitLine> lines) {
        List<RideWarning> out = new ArrayList<>();
        Set<Integer> sections = new LinkedHashSet<>();
        List<TransitPlatform> berths = new ArrayList<>();
        for (TransitLine line : lines) {
            for (TransitStation station : line.stations()) {
                TransitStation live = transit.station(station.name());
                for (TransitPlatform platform : (live == null ? station : live).platforms()) {
                    if (network.hasSection(platform.stopPoint().sectionId()) && !berths.contains(platform)) {
                        berths.add(platform);
                        sections.add(platform.stopPoint().sectionId());
                    }
                }
            }
        }
        for (int id : sections) {
            track(network.section(id), out);
        }
        for (TransitPlatform berth : berths) {
            platform(network.section(berth.stopPoint().sectionId()), berth.stopPoint().distance(), out);
        }
        return out;
    }

    /** Slow curves and steep grades along a section, each as runs of track. */
    private static void track(TrackSection section, List<RideWarning> out) {
        Run curve = new Run(RideWarning.Kind.TIGHT_CURVE, section.id(), false);
        Run grade = new Run(RideWarning.Kind.STEEP_GRADE, section.id(), true);
        double length = section.totalLength();
        for (double s = 0.0D; s <= length; s += STEP) {
            double limit = CurveSpeed.limitAt(section, s);
            curve.at(s, limit < SLOW_CURVE, limit < CRAWL_CURVE, limit, out);
            double g = gradeAt(section, s);
            grade.at(s, Math.abs(g) > STEEP_GRADE, Math.abs(g) > TOO_STEEP_GRADE, g * 100.0D, out);
        }
        curve.close(out);
        grade.close(out);
    }

    /** A platform that is on a grade or a curve. */
    private static void platform(TrackSection section, double stop, List<RideWarning> out) {
        double length = section.totalLength();
        double from = section.isClosed() ? stop - PLATFORM_REACH : Math.max(0.0D, stop - PLATFORM_REACH);
        double to = section.isClosed() ? stop + PLATFORM_REACH : Math.min(length, stop + PLATFORM_REACH);
        double steepest = 0.0D;
        double tightest = Double.POSITIVE_INFINITY;
        for (double s = from; s <= to; s += STEP) {
            double at = section.isClosed() ? ((s % length) + length) % length : s;
            double g = gradeAt(section, at);
            if (Math.abs(g) > Math.abs(steepest)) {
                steepest = g;
            }
            double k = CurveSpeed.curvature(section, at);
            tightest = Math.min(tightest, k < 1e-6D ? Double.POSITIVE_INFINITY : 1.0D / k);
        }
        if (Math.abs(steepest) > PLATFORM_GRADE) {
            out.add(new RideWarning(RideWarning.Kind.PLATFORM_GRADE, RideWarning.Severity.CAUTION,
                section.id(), from, to, steepest * 100.0D));
        }
        if (tightest < PLATFORM_RADIUS) {
            out.add(new RideWarning(RideWarning.Kind.PLATFORM_CURVE, RideWarning.Severity.CAUTION,
                section.id(), from, to, tightest));
        }
    }

    /** Rise over run at {@code s}: 0.05 is a 5% grade, climbing along +s. */
    static double gradeAt(TrackSection section, double s) {
        Vec3 t = section.tangentAtDistance(s);
        double run = Math.sqrt(t.x * t.x + t.z * t.z);
        return run < 1e-9D ? Double.POSITIVE_INFINITY : t.y / run;
    }

    /** One kind of finding along a section, opened and closed as the track goes in and out of it. */
    private static final class Run {
        final RideWarning.Kind kind;
        final int sectionId;
        /** Whether the worst is the largest magnitude (a grade) or the smallest value (a curve's limit). */
        final boolean largest;
        double from = -1.0D;
        double last;
        double peak;
        boolean danger;

        Run(RideWarning.Kind kind, int sectionId, boolean largest) {
            this.kind = kind;
            this.sectionId = sectionId;
            this.largest = largest;
        }

        void at(double s, boolean over, boolean wellOver, double value, List<RideWarning> out) {
            if (!over) {
                close(out);
                return;
            }
            if (from < 0.0D) {
                from = s;
                peak = value;
                danger = false;
            }
            last = s;
            danger |= wellOver;
            if (largest ? Math.abs(value) > Math.abs(peak) : value < peak) {
                peak = value;
            }
        }

        void close(List<RideWarning> out) {
            if (from < 0.0D) {
                return;
            }
            out.add(new RideWarning(kind, danger ? RideWarning.Severity.DANGER : RideWarning.Severity.CAUTION,
                sectionId, from, last, peak));
            from = -1.0D;
        }
    }
}
