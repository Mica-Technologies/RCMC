package com.micatechnologies.minecraft.rcmc.client.render.track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * Where a section's third rail runs, which side of the track it is on, and where it breaks.
 *
 * <p>A real conductor rail is not one continuous bar. It sits on the side away from each
 * platform, so it changes sides on the way into a station and back out, and every change leaves a
 * gap between the ramped-off end of one length and the start of the next. It also stops short of
 * switches, so the rail cannot foul the diverging wheels. A train coasts its shoes across the gaps;
 * cars carry shoegear both sides for exactly this reason.</p>
 *
 * <p>The plan is pure data — a default side, the stretches where some other side applies, and the
 * stretches to leave bare — and {@link #runs} turns it into the lengths of rail to draw. It is
 * worked out by the renderer from the stations and switches it can see, and handed to
 * {@link TrackMeshBuilder} like the support columns are, so the builder stays free of world
 * state and this stays testable.</p>
 */
public final class ThirdRailPlan {

    /** Length of the break where the rail changes sides, in blocks. */
    static final double SIDE_CHANGE_GAP = 3.0D;

    /** Shorter than this, a length of rail is not worth drawing. */
    private static final double MIN_RUN = 0.5D;

    /** {@code +1} right, {@code -1} left: the side away from platforms. */
    final int defaultSide;
    /** {@code {from, to, side}}: stretches on another side, latest wins where they overlap. */
    final List<double[]> overrides;
    /** {@code {from, to}}: stretches with no rail. */
    final List<double[]> gaps;

    public ThirdRailPlan(int defaultSide, List<double[]> overrides, List<double[]> gaps) {
        this.defaultSide = defaultSide < 0 ? -1 : 1;
        this.overrides = Collections.unmodifiableList(new ArrayList<>(overrides));
        this.gaps = Collections.unmodifiableList(new ArrayList<>(gaps));
    }

    /** One length of conductor rail. */
    public static final class Run {
        public final double from;
        public final double to;
        public final int side;

        Run(double from, double to, int side) {
            this.from = from;
            this.to = to;
            this.side = side;
        }

        @Override
        public String toString() {
            return String.format("[%.1f..%.1f %s]", from, to, side < 0 ? "L" : "R");
        }
    }

    /**
     * The lengths of rail to draw on a section {@code total} long, in order along it.
     *
     * <p>On a closed section a stretch may run past either end; it is wrapped onto the other.</p>
     */
    public List<Run> runs(double total, boolean closed) {
        if (total <= 0.0D) {
            return Collections.emptyList();
        }
        List<double[]> sided = new ArrayList<>();
        for (double[] o : overrides) {
            for (double[] piece : clip(o[0], o[1], total, closed)) {
                sided.add(new double[] { piece[0], piece[1], o[2] < 0 ? -1 : 1 });
            }
        }

        // Elementary intervals between every boundary; each takes the last override covering it.
        TreeSet<Double> cuts = new TreeSet<>();
        cuts.add(0.0D);
        cuts.add(total);
        for (double[] s : sided) {
            cuts.add(s[0]);
            cuts.add(s[1]);
        }
        List<Run> merged = new ArrayList<>();
        Double previous = null;
        for (double cut : cuts) {
            if (previous != null && cut > previous) {
                double mid = (previous + cut) * 0.5D;
                int side = defaultSide;
                for (double[] s : sided) {
                    if (mid >= s[0] && mid <= s[1]) {
                        side = (int) s[2];
                    }
                }
                Run last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
                if (last != null && last.side == side) {
                    merged.set(merged.size() - 1, new Run(last.from, cut, side));
                }
                else {
                    merged.add(new Run(previous, cut, side));
                }
            }
            previous = cut;
        }

        // A break wherever the rail changes side, then the authored breaks.
        List<double[]> bare = new ArrayList<>();
        for (int i = 1; i < merged.size(); i++) {
            double at = merged.get(i).from;
            bare.add(new double[] { at - SIDE_CHANGE_GAP * 0.5D, at + SIDE_CHANGE_GAP * 0.5D });
        }
        if (closed && merged.size() > 1 && merged.get(0).side != merged.get(merged.size() - 1).side) {
            bare.add(new double[] { -SIDE_CHANGE_GAP * 0.5D, SIDE_CHANGE_GAP * 0.5D });
            bare.add(new double[] { total - SIDE_CHANGE_GAP * 0.5D, total + SIDE_CHANGE_GAP * 0.5D });
        }
        for (double[] g : gaps) {
            bare.addAll(clip(g[0], g[1], total, closed));
        }

        List<Run> out = new ArrayList<>();
        for (Run run : merged) {
            List<double[]> pieces = new ArrayList<>();
            pieces.add(new double[] { run.from, run.to });
            for (double[] g : bare) {
                List<double[]> next = new ArrayList<>();
                for (double[] p : pieces) {
                    if (g[1] <= p[0] || g[0] >= p[1]) {
                        next.add(p);
                        continue;
                    }
                    if (g[0] > p[0]) {
                        next.add(new double[] { p[0], g[0] });
                    }
                    if (g[1] < p[1]) {
                        next.add(new double[] { g[1], p[1] });
                    }
                }
                pieces = next;
            }
            for (double[] p : pieces) {
                if (p[1] - p[0] >= MIN_RUN) {
                    out.add(new Run(p[0], p[1], run.side));
                }
            }
        }
        return out;
    }

    /** {@code [from, to]} within {@code [0, total]}, wrapped round a closed section. */
    private static List<double[]> clip(double from, double to, double total, boolean closed) {
        List<double[]> pieces = new ArrayList<>();
        if (to <= from) {
            return pieces;
        }
        if (closed) {
            if (from < 0.0D) {
                pieces.add(new double[] { Math.max(0.0D, total + from), total });
            }
            if (to > total) {
                pieces.add(new double[] { 0.0D, Math.min(total, to - total) });
            }
        }
        double a = Math.max(0.0D, from);
        double b = Math.min(total, to);
        if (b > a) {
            pieces.add(new double[] { a, b });
        }
        return pieces;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ThirdRailPlan)) {
            return false;
        }
        ThirdRailPlan other = (ThirdRailPlan) obj;
        return defaultSide == other.defaultSide && sameRows(overrides, other.overrides)
            && sameRows(gaps, other.gaps);
    }

    @Override
    public int hashCode() {
        int hash = defaultSide;
        for (double[] row : overrides) {
            hash = hash * 31 + java.util.Arrays.hashCode(row);
        }
        for (double[] row : gaps) {
            hash = hash * 31 + java.util.Arrays.hashCode(row);
        }
        return hash;
    }

    private static boolean sameRows(List<double[]> a, List<double[]> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!java.util.Arrays.equals(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "ThirdRailPlan{side=" + defaultSide + ", overrides=" + overrides.size() + ", gaps="
            + gaps.size() + "}";
    }
}
