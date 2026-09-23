package com.micatechnologies.minecraft.rcmc.rating;

import com.micatechnologies.minecraft.rcmc.physics.GForces;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Checks a ride the way it actually runs, and says where it goes wrong.
 *
 * <p>The geometric validator judges each curve at one assumed design speed, which is right for
 * nothing: the same bend is gentle at the top of a lift and brutal at the bottom of the drop. This
 * runs the ride's own train through a simulated lap — the same simulation {@code /rcmc rate} uses —
 * and flags every stretch where riders feel more than {@link SafetyLimits} allows, and the place a
 * train that never gets round gave up.</p>
 *
 * <p>Warnings only. Like the rest of RCT-style building, a lethal ride can be built; the builder is
 * just told it is lethal, and where.</p>
 */
public final class RideCheck {

    /** A stretch shorter than this many ticks is a spike, not something a rider feels. */
    static final int MIN_TICKS = 3;

    /** Over the limit by this factor or more is {@link RideWarning.Severity#DANGER}. */
    static final double DANGER_FACTOR = 1.3D;

    /** Two stretches of the same kind this close together, in blocks, are reported as one. */
    static final double MERGE_GAP = 5.0D;

    private RideCheck() {
    }

    /**
     * Every warning for the ride whose train starts at {@code sectionId}'s station (or the start of
     * the section, without one), in the order the train meets them.
     */
    public static List<RideWarning> check(RideRater rater, TrackNetwork network, RideElementSet elements,
                                          int sectionId, TrainSpec spec, SafetyLimits limits) {
        Collector collector = new Collector(limits == null ? SafetyLimits.DEFAULT : limits, network);
        RideStatistics stats = rater.simulateRide(network, elements, sectionId, spec, collector);
        List<RideWarning> out = collector.finish();
        // Round the lap means net progress, not distance covered: a train rocking in a valley it
        // cannot climb out of covers a lap's worth of track without ever getting round.
        double lap = network.hasSection(sectionId) ? network.section(sectionId).totalLength() : 0.0D;
        boolean gotRound = stats.completedWithoutFault() && collector.furthestProgress >= lap * 0.95D;
        if (!gotRound && collector.furthest != null) {
            out.add(new RideWarning(RideWarning.Kind.STALL, RideWarning.Severity.DANGER,
                collector.furthest.sectionId(), collector.furthest.distance(),
                collector.furthest.distance(), collector.furthestSpeed));
        }
        return out;
    }

    /** Turns ticks into stretches, per kind. */
    private static final class Collector implements RideRater.Listener {
        private final SafetyLimits limits;
        private final Map<RideWarning.Kind, Run> open = new EnumMap<>(RideWarning.Kind.class);
        private final List<RideWarning> done = new ArrayList<>();
        private TrackRef previous;
        private double progress;
        double furthestProgress = Double.NEGATIVE_INFINITY;
        TrackRef furthest;
        double furthestSpeed;

        private final TrackNetwork network;

        Collector(SafetyLimits limits, TrackNetwork network) {
            this.limits = limits;
            this.network = network;
        }

        @Override
        public void sample(TrackRef where, double speed, GForces g) {
            // How far round the lap the train has got, counting back as back: where it got furthest
            // is where a train that never makes it round gave up.
            if (previous != null && previous.sectionId() == where.sectionId()) {
                double step = where.distance() - previous.distance();
                // Crossing a circuit's start line wraps the distance; it is one small step, not a lap.
                double length = network.hasSection(where.sectionId())
                    ? network.section(where.sectionId()).totalLength() : 0.0D;
                if (length > 0.0D && Math.abs(step) > length * 0.5D) {
                    step -= Math.signum(step) * length;
                }
                progress += step;
            }
            else if (previous != null) {
                progress += speed * 0.05D;
            }
            previous = where;
            if (progress > furthestProgress) {
                furthestProgress = progress;
                furthest = where;
                furthestSpeed = speed;
            }

            over(RideWarning.Kind.HIGH_G, g.vertical, limits.maxPositiveVerticalG, true, where);
            over(RideWarning.Kind.NEGATIVE_G, g.vertical, limits.maxNegativeVerticalG, false, where);
            over(RideWarning.Kind.LATERAL_G, Math.abs(g.lateral), limits.maxLateralG, true, where);
            over(RideWarning.Kind.LONGITUDINAL_G, Math.abs(g.longitudinal), limits.maxLongitudinalG, true, where);
        }

        /** Extends or closes the open stretch of {@code kind} with this tick's {@code value}. */
        private void over(RideWarning.Kind kind, double value, double limit, boolean above, TrackRef where) {
            boolean exceeds = above ? value > limit : value < limit;
            Run run = open.get(kind);
            if (exceeds) {
                if (run == null || run.sectionId != where.sectionId()) {
                    close(kind);
                    run = new Run(where.sectionId(), where.distance(), limit, above);
                    open.put(kind, run);
                }
                run.extend(where.distance(), value);
            }
            else {
                close(kind);
            }
        }

        private void close(RideWarning.Kind kind) {
            Run run = open.remove(kind);
            if (run == null || run.ticks < MIN_TICKS) {
                return;
            }
            double ratio = run.peak / run.limit;
            RideWarning.Severity severity = ratio >= DANGER_FACTOR
                ? RideWarning.Severity.DANGER : RideWarning.Severity.CAUTION;
            RideWarning warning = new RideWarning(kind, severity, run.sectionId, run.from, run.to, run.peak);
            // The same trouble twice in quick succession is one place to fix, not two.
            for (int i = done.size() - 1; i >= 0; i--) {
                RideWarning last = done.get(i);
                if (last.kind == kind && last.sectionId == run.sectionId
                    && warning.from - last.to <= MERGE_GAP && warning.to >= last.from) {
                    boolean worse = run.above ? run.peak > last.peak : run.peak < last.peak;
                    done.set(i, new RideWarning(kind,
                        severity == RideWarning.Severity.DANGER ? severity : last.severity, run.sectionId,
                        Math.min(last.from, warning.from), Math.max(last.to, warning.to),
                        worse ? run.peak : last.peak));
                    return;
                }
            }
            done.add(warning);
        }

        List<RideWarning> finish() {
            for (RideWarning.Kind kind : RideWarning.Kind.values()) {
                close(kind);
            }
            return done;
        }
    }

    /** One open stretch over a limit. */
    private static final class Run {
        final int sectionId;
        final double limit;
        final boolean above;
        double from;
        double to;
        double peak;
        int ticks;

        Run(int sectionId, double at, double limit, boolean above) {
            this.sectionId = sectionId;
            this.from = at;
            this.to = at;
            this.limit = limit;
            this.above = above;
            this.peak = limit;
        }

        void extend(double at, double value) {
            from = Math.min(from, at);
            to = Math.max(to, at);
            ticks++;
            if (above ? value > peak : value < peak) {
                peak = value;
            }
        }
    }
}
