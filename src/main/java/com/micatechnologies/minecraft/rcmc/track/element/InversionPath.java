package com.micatechnologies.minecraft.rcmc.track.element;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Shapes an inversion from the load its riders should feel, rather than from a curve.
 *
 * <p>A zero-g roll, an Immelmann and a dive loop are all the same three moves: pull up (or down)
 * with the riders pressed into their seats, go weightless over the top while the train rolls, and
 * pull out. What makes one ride well is the load at each moment, so that is what an element
 * prescribes: {@code n}, the load along the rider's up in g. This integrates the path that load
 * produces, in the vertical plane of the entry:</p>
 *
 * <pre>  dα/ds = g (σ·n − cos α) / v²</pre>
 *
 * <p>where {@code α} is the heading's pitch in that plane (0 level ahead, 180° level back), and
 * {@code σ} is +1 while the rider's up is the plane's up and −1 once a half roll has turned them
 * over. With {@code n = 0} the rider is weightless and the train can roll through any angle without
 * a sideways jolt, which is why every roll here happens there.</p>
 *
 * <p><b>Whose speed.</b> A train is not a point: over a crest its back cars are still climbing when
 * the front one tops out, so the whole train is slower than a lone car would be there. The speed
 * used is the stock five-car train's ({@link #CAR_OFFSETS}) — from the average height of its cars,
 * entered at the design speed — and the path is the one its front car rides; the ride rating
 * measures the front car too.</p>
 *
 * <p><b>Leaving level.</b> Every pull-out ends on a feedback law, {@code n = 1 + K sin(error)}
 * capped at the element's pull, eased in from wherever the load was. Near level it pulls the pitch
 * error down to nothing, with the load settling to exactly 1 g, so each element leaves precisely
 * level without any solving — whatever the speed. Rolling resistance and drag are charged as the
 * integrator charges them, so the real train arrives at each point no slower than designed.</p>
 */
final class InversionPath {

    static final double GRAVITY = 9.81D;

    /** How hard a pull-out closes on level: load above 1 g per unit of sin(pitch error). */
    private static final double SETTLE_GAIN = 8.0D;

    /** Distance behind the front car of each car's centre in the stock coaster train: five cars
     *  3 blocks long with half-block gaps. */
    static final double[] CAR_OFFSETS = {0.0D, 3.5D, 7.0D, 10.5D, 14.0D};

    /** The integrator's rolling resistance and air drag (see {@code RcmcConfig}), overstated a
     *  little: a train that loses more than the design expects stalls upside down. */
    private static final double ROLLING_RESISTANCE = 0.01D;
    private static final double AIR_DRAG = 0.0015D;
    private static final double LOSS_MARGIN = 1.3D;

    /** Node spacing along the riders' hearts through an inversion, in blocks — see
     *  {@link #nodeIndices}. Close enough for the rail's spiral round the heart, with G measured
     *  over a block either side of the rider (see {@code physics.Heartline}); and wide enough that
     *  the rail's nodes, closer together where the rail runs inside the hearts' curve, stay above
     *  the block the track validator asks for. */
    static final double NODE_SPACING = 0.5D;

    /** Slowest the design lets the train get anywhere in an element, in blocks/s. Well above
     *  stopping: the real train differs from the design by a little, and a little too slow at the
     *  top of an Immelmann is a train stopped upside down. */
    static final double MIN_SPEED = 5.0D;

    /** Integration step, in blocks of heart path. */
    private static final double DS = 0.05D;

    /** One point of the heart's path. */
    static final class Point {
        final double s;
        final double x;
        final double y;
        final double alpha;
        double roll;

        Point(double s, double x, double y, double alpha) {
            this.s = s;
            this.x = x;
            this.y = y;
            this.alpha = alpha;
        }
    }

    private final List<Point> points = new ArrayList<>();
    private final double entrySpeedSquared;
    private double s;
    private double x;
    private double y;
    private double alpha;
    private double sigma = 1.0D;
    private double load = 1.0D;
    /** Speed squared lost to friction so far. */
    private double lost;

    InversionPath(double entrySpeed) {
        entrySpeedSquared = entrySpeed * entrySpeed;
        points.add(new Point(0.0D, 0.0D, 0.0D, 0.0D));
    }

    double alpha() {
        return alpha;
    }

    double length() {
        return s;
    }

    List<Point> points() {
        return points;
    }

    /** The rider has been turned over by a half roll: their up is now the plane's down. */
    void turnOver() {
        sigma = -sigma;
    }

    /** The train's speed squared with its front car here: from its cars' average height, the run
     *  before the element taken as level. */
    private double speedSquared() {
        double height = 0.0D;
        for (double offset : CAR_OFFSETS) {
            double at = s - offset;
            height += at <= 0.0D ? 0.0D : points.get(Math.min(points.size() - 1, (int) Math.round(at / DS))).y;
        }
        return entrySpeedSquared - lost - 2.0D * GRAVITY * height / CAR_OFFSETS.length;
    }

    /** One step at load {@code n}. */
    private void step(double n) {
        load = n;
        double speedSquared = speedSquared();
        if (speedSquared < MIN_SPEED * MIN_SPEED) {
            throw new IllegalArgumentException("too slow to get round: the train would stall");
        }
        double half = alpha + GRAVITY * (sigma * n - Math.cos(alpha)) / speedSquared * DS / 2.0D;
        x += Math.cos(half) * DS;
        y += Math.sin(half) * DS;
        alpha += GRAVITY * (sigma * n - Math.cos(half)) / speedSquared * DS;
        lost += frictionLoss(speedSquared, DS);
        s += DS;
        points.add(new Point(s, x, y, alpha));
    }

    /** Speed squared that rolling resistance and drag take over {@code ds} blocks at this speed,
     *  overstated by the margin. */
    static double frictionLoss(double speedSquared, double ds) {
        double v = Math.sqrt(Math.max(0.0D, speedSquared));
        return 2.0D * (ROLLING_RESISTANCE * v + AIR_DRAG * speedSquared) * ds * LOSS_MARGIN;
    }

    /** Eases the load from where it is to {@code to} over {@code length} blocks. */
    void ease(double to, double length) {
        double from = load;
        int steps = Math.max(1, (int) Math.round(length / DS));
        for (int i = 1; i <= steps; i++) {
            step(from + (to - from) * ElementGeometry.smoothstep((double) i / steps));
        }
    }

    /** Holds the load until {@code done} says stop. */
    void holdUntil(java.util.function.BooleanSupplier done) {
        double n = load;
        double end = s + 400.0D;
        while (!done.getAsBoolean()) {
            if (s > end) {
                throw new IllegalArgumentException("the element never reaches its shape at this speed");
            }
            step(n);
        }
    }

    /**
     * Eases the load from where it is to {@code to} as the pitch goes from where it is to
     * {@code untilAlpha} — by pitch, not distance, so a tight slow loop and a wide fast one both
     * arrive with the load exactly {@code to}. The pitch must be moving toward {@code untilAlpha}
     * at every load on the way.
     */
    void easeByPitch(double to, double untilAlpha) {
        double from = load;
        double start = alpha;
        double end = s + 400.0D;
        while ((untilAlpha - alpha) * (untilAlpha - start) > 0.0D) {
            if (s > end) {
                throw new IllegalArgumentException("the element never reaches its shape at this speed");
            }
            double t = (alpha - start) / (untilAlpha - start);
            step(from + (to - from) * ElementGeometry.smoothstep(Math.max(0.0D, Math.min(1.0D, t))));
        }
    }

    /**
     * Pulls out to leave level at pitch {@code target}: {@code n = 1 + SETTLE_GAIN·sin(error)} in
     * the rider's own terms, capped at {@code maxLoad}, eased in from the current load over
     * {@code length} blocks, and run until the pitch has settled.
     *
     * <p>Stiff and capped rather than gentle: pitch turns at {@code g(n - cos a)/v²} per block, so a
     * gentle law at speed takes hundreds of blocks to come level. This pulls the most the element
     * allows until nearly level, then closes the last few degrees in a few blocks.</p>
     */
    void settle(double target, double maxLoad, double length) {
        double from = load;
        double eased = s + length;
        double end = s + 400.0D;
        while (true) {
            // The error the rider's up turns them toward, whichever way up they are.
            double error = sigma * (target - alpha);
            double law = Math.max(0.3D, Math.min(maxLoad, 1.0D + SETTLE_GAIN * Math.sin(error)));
            double ramp = ElementGeometry.smoothstep(Math.max(0.0D, Math.min(1.0D, 1.0D - (eased - s) / length)));
            if (ramp >= 1.0D && Math.abs(target - alpha) < Math.toRadians(0.05D)) {
                return;
            }
            if (s > end) {
                throw new IllegalArgumentException("the element never levels out at this speed");
            }
            step(from + (law - from) * ramp);
        }
    }

    /** Rolls the rider through {@code degrees} between arc lengths {@code from} and {@code to}. */
    void roll(double from, double to, double degrees) {
        for (Point p : points) {
            double t = (p.s - from) / (to - from);
            if (t >= 1.0D) {
                p.roll += degrees;
            } else if (t > 0.0D) {
                p.roll += degrees * smootherstep(t);
            }
        }
    }

    /**
     * Hands the path to {@link HeartlineShaper}, its nodes placed by {@link #nodeIndices}.
     */
    ElementResult shape(ElementContext context, double spacing, Vec3 exitForward) {
        return shape(context, spacing, exitForward, 0.0D);
    }

    /**
     * As above, the whole path eased {@code sideways} blocks to the right by its end, along
     * {@link #smootherstep} — for an element that comes back the way it went in and must pass beside
     * its own entry rather than through it.
     */
    ElementResult shape(ElementContext context, double spacing, Vec3 exitForward, double sideways) {
        Vec3 forward = context.entryFrame.forward;
        Vec3 right = context.entryFrame.right;
        Vec3 up = context.entryFrame.up;
        Vec3 origin = context.entryFrame.position.add(up.scale(HeartlineShaper.HEART_HEIGHT));
        double[] arc = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            arc[i] = points.get(i).s;
        }
        List<Vec3> hearts = new ArrayList<>();
        List<Vec3> ups = new ArrayList<>();
        for (int i : nodeIndices(arc, spacing)) {
            Point p = points.get(i);
            Vec3 tangent = forward.scale(Math.cos(p.alpha)).add(up.scale(Math.sin(p.alpha)));
            Vec3 planeUp = forward.scale(-Math.sin(p.alpha)).add(up.scale(Math.cos(p.alpha)));
            double r = Math.toRadians(p.roll);
            Vec3 riderUp = planeUp.scale(Math.cos(r)).add(tangent.cross(planeUp).scale(Math.sin(r)));
            hearts.add(origin.add(forward.scale(p.x)).add(up.scale(p.y))
                .add(right.scale(sideways * smootherstep(p.s / s))));
            ups.add(riderUp);
        }
        return HeartlineShaper.shape(context, hearts, ups, exitForward);
    }

    /**
     * Which samples become nodes: evenly, {@code spacing} blocks of heart path apart, and the last.
     *
     * <p>Evenly on purpose. The rail spirals round the riders' hearts less than a block away and the
     * spline has to follow that spiral from the nodes alone; nodes packed closer where the rider
     * rolls fast looked like the answer, but a spline through nodes whose spacing changes
     * overshoots where it changes, and at the heart a centimetre of overshoot is half a g
     * sideways.</p>
     *
     * @param arc arc length at each sample, ascending from 0
     * @return sample indices, ascending, excluding 0 and including the last
     */
    static List<Integer> nodeIndices(double[] arc, double spacing) {
        int count = arc.length;
        double total = arc[count - 1];
        int segments = Math.max(4, (int) Math.round(total / spacing));
        List<Integer> nodes = new ArrayList<>();
        int i = 1;
        for (int k = 1; k < segments; k++) {
            double target = total * k / segments;
            while (i < count - 1 && arc[i] < target) {
                i++;
            }
            nodes.add(i);
        }
        nodes.add(count - 1);
        return nodes;
    }

    /** Zero rate and zero acceleration at both ends: a roll that starts and stops without a jerk. */
    static double smootherstep(double t) {
        double c = Math.max(0.0D, Math.min(1.0D, t));
        return c * c * c * (c * (c * 6.0D - 15.0D) + 10.0D);
    }
}
