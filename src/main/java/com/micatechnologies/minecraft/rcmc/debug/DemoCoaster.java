package com.micatechnologies.minecraft.rcmc.debug;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a complete demonstration coaster: brake run, station, lift hill, a first drop that curves
 * through the far turnaround, a camelback on the return straight, and a banked climbing turn back
 * into the brakes.
 *
 * <p>The layout is an oval in plan. Along the near side lie the brakes, the platform and the
 * lift, in the order a train meets them. The rest of the ride goes round the far end and back.</p>
 *
 * <p>An earlier version placed every node, height and bank by hand. It ran, but it failed its own
 * ride check: up to 6 g sideways, because a bank chosen by eye matches the speed through a turn only
 * by luck. It also only worked at the size it was tuned at: a bigger layout stalled, and a taller
 * lift on the same footprint pulled impossible G. So this version doesn't guess. Two numbers come
 * from the physics:</p>
 * <ul>
 *   <li><b>Heights.</b> Each hill after the lift is a fraction of the energy the train still has
 *       there, estimated by marching the same rolling resistance and air drag the integrator
 *       applies. Hills get lower as the ride goes on, and a long layout gets gentler hills rather
 *       than one the train cannot climb.</li>
 *   <li><b>Banks.</b> Every node is rolled so that the load the rider feels, from the curve at the
 *       speed the train has there plus gravity, points straight down through the seat. That is what
 *       a real designer does to a turn, and it is why the check finds nothing sideways.</li>
 * </ul>
 *
 * <p>The build returns the element spans alongside the geometry, so the caller places the station,
 * lift and brakes exactly where the layout intends.</p>
 */
public final class DemoCoaster {

    /** A built demo, and where its ride hardware belongs. */
    public static final class Result {

        public final TrackSection section;

        /** Distance along the section at which each element starts and ends, in blocks. */
        public final double stationStart;
        public final double stationEnd;
        public final double stationStop;
        public final double liftStart;
        public final double liftEnd;
        public final double brakeStart;
        public final double brakeEnd;

        /** The size the layout was built at: the scale asked for, held to what suits the lift. */
        public final double scale;

        Result(TrackSection section, double stationStart, double stationEnd, double stationStop,
               double liftStart, double liftEnd, double brakeStart, double brakeEnd, double scale) {
            this.section = section;
            this.stationStart = stationStart;
            this.stationEnd = stationEnd;
            this.stationStop = stationStop;
            this.liftStart = liftStart;
            this.liftEnd = liftEnd;
            this.brakeStart = brakeStart;
            this.brakeEnd = brakeEnd;
            this.scale = scale;
        }
    }

    /** The lift the layout's proportions were drawn for; scale 1.0 is right for it. */
    private static final double REFERENCE_LIFT = 34.0D;

    /**
     * How far the scale may stray from what suits the lift, either way. G in a hill or turn goes as
     * the square of height over size: a tall lift on a small layout is violent, and a big layout on
     * a low lift runs out of energy before it gets home.
     */
    private static final double MIN_SCALE_PER_LIFT = 1.0D;
    private static final double MAX_SCALE_PER_LIFT = 1.4D;

    /** Radius of the two turnarounds at scale 1, in blocks. */
    private static final double TURN_RADIUS = 36.0D;

    /** Length of the return straight at scale 1. The near side is at least as long as the
     *  brakes, platform and lift need. */
    private static final double STRAIGHT_LENGTH = 105.0D;

    /** Platform length: holds a five-car train with room to stop short of the lift. */
    private static final double PLATFORM_LENGTH = 26.0D;

    /** Horizontal run of the lift per block of rise — a climb of about 29°. */
    private static final double LIFT_RUN_PER_RISE = 1.8D;

    /** Horizontal run of the first drop per block of fall. Long enough that the pull-out at the
     *  bottom, at the ride's top speed, stays within what a rider can take. */
    private static final double DROP_RUN_PER_FALL = 2.1D;

    /** The camelback's crest and the turnaround's rise, as fractions of the energy left there. */
    private static final double CAMELBACK = 0.75D;
    private static final double TURNAROUND_RISE = 0.5D;

    /** How far out of their seats the camelback's crest lifts riders, in g: enough to feel, well
     *  inside what the ride check allows. Throwing riders up is what a camelback is for. */
    private static final double CAMELBACK_AIRTIME = 0.3D;

    /** How much sharper a hill is at its top than at its foot — see {@code Plan.height}. */
    private static final double HILL_SHAPE = 1.4D;

    /** What the brakes are built for: the trim speed they hold and how hard they slow. These
     *  match what {@code /rcmc demo} installs. */
    private static final double BRAKE_SPEED = 6.0D;
    private static final double BRAKE_DECELERATION = 6.0D;
    private static final double MIN_BRAKE_LENGTH = 12.0D;

    /** Speed the chain carries the train over the crest at; what {@code /rcmc demo} installs. */
    private static final double CHAIN_SPEED = 5.0D;

    /** The integrator's defaults, which the energy estimate marches — see {@code RcmcConfig}. The
     *  losses are overstated a little, so a hill is never quite as tall as the train could climb. */
    private static final double GRAVITY = 9.81D;
    private static final double ROLLING_RESISTANCE = 0.01D;
    private static final double AIR_DRAG = 0.0015D;
    private static final double LOSS_MARGIN = 1.25D;

    /** Steepest bank the builder will give a node, in degrees. */
    private static final double MAX_BANK = 75.0D;

    private DemoCoaster() {
        throw new AssertionError("No instances.");
    }

    /** Sensible defaults: a mid-sized layout with a 34-block lift. */
    public static Result build(int sectionId, Vec3 origin) {
        return build(sectionId, origin, 1.0D, REFERENCE_LIFT);
    }

    /**
     * @param origin     the station's position — where the player is standing
     * @param scale      overall size multiplier, held to a range that suits {@code liftHeight}
     * @param liftHeight height of the lift crest above the station, in blocks
     */
    public static Result build(int sectionId, Vec3 origin, double scale, double liftHeight) {
        double h = liftHeight;
        double s = Math.max(h / REFERENCE_LIFT * MIN_SCALE_PER_LIFT,
            Math.min(h / REFERENCE_LIFT * MAX_SCALE_PER_LIFT, scale));

        // The brakes' length depends on how fast the train comes home, which depends on the layout,
        // whose near side includes the brakes. A few rounds settle it.
        Plan plan = null;
        double brakeLength = MIN_BRAKE_LENGTH;
        for (int round = 0; round < 4; round++) {
            plan = new Plan(h, s, brakeLength);
            double home = Math.max(0.0D, plan.headAt(plan.total));
            double arrival = 2.0D * GRAVITY * home;
            brakeLength = Math.max(MIN_BRAKE_LENGTH,
                (arrival - BRAKE_SPEED * BRAKE_SPEED) / (2.0D * BRAKE_DECELERATION) + 4.0D);
        }

        List<Double> stations = plan.nodeStations();
        List<TrackNode> nodes = new ArrayList<>();
        for (double u : stations) {
            nodes.add(new TrackNode(plan.position(u, origin), 0.0D, null));
        }
        TrackSection flat = new TrackSection(sectionId, nodes, true, null);

        // Bank each node for the load felt there. Brakes, platform and lift stay level. The bank
        // turns about the riders' hearts, not the rail: the heart stays where it would be over a
        // level rail and the rail moves, or every roll into a turn would throw riders sideways.
        List<TrackNode> banked = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            double u = stations.get(i);
            double bank = u <= plan.crest ? 0.0D : idealBank(flat, flat.nodeDistance(i),
                plan.speedSquaredAt(u));
            TrackFrame level = flat.unbankedFrameAtDistance(flat.nodeDistance(i));
            Vec3 bankedUp = level.withBank(Math.toRadians(bank)).up;
            Vec3 rail = nodes.get(i).position().add(level.up.subtract(bankedUp)
                .scale(com.micatechnologies.minecraft.rcmc.track.element.HeartlineShaper.HEART_HEIGHT));
            banked.add(new TrackNode(rail, bank, null));
        }
        TrackSection section = new TrackSection(sectionId, banked, true, null);

        double stationStart = section.nodeDistance(plan.stationFirst);
        double platformEnd = section.nodeDistance(plan.platformLast);
        // The platform's far end is the lift's start, so there is no unpowered gap between them.
        // They touch rather than overlap: RideElementSet is first-match-wins, so an overlap would
        // let the station keep claiming a train the lift should already have. The chain runs right
        // to the crest; a train left to coast the last stretch rolls back into the station.
        return new Result(section, stationStart, platformEnd,
            // Stop short of the platform end so there is room to accelerate before the chain.
            platformEnd - 3.0D,
            platformEnd, section.nodeDistance(plan.crestNode),
            0.0D, stationStart, s);
    }

    /**
     * The bank, in degrees, that puts the felt load at {@code distance} straight through the seat:
     * the curve's centripetal pull at {@code speedSquared}, plus gravity, has no sideways part.
     */
    private static double idealBank(TrackSection section, double distance, double speedSquared) {
        double half = 1.5D;
        double total = section.totalLength();
        Vec3 before = section.tangentAtDistance((distance - half + total) % total);
        Vec3 after = section.tangentAtDistance((distance + half) % total);
        Vec3 curvature = after.subtract(before).scale(1.0D / (2.0D * half));
        Vec3 felt = curvature.scale(speedSquared).add(new Vec3(0.0D, GRAVITY, 0.0D));
        TrackFrame level = section.unbankedFrameAtDistance(distance);
        double across = felt.dot(level.right);
        double down = felt.dot(level.up);
        // Over a crest with airtime the load points up out of the seat, and "straight through the
        // seat" would be a bank of nearly 180°. Rolling a rider upside down to meet a light moment
        // is not what anyone wants; bank as if the seat still carried some weight.
        double radians = Math.atan2(across, Math.max(down, 0.5D * GRAVITY));
        // The sign of a roll is the frame's convention, not ours: take whichever leaves no
        // sideways load.
        if (Math.abs(felt.dot(level.withBank(-radians).right)) < Math.abs(felt.dot(level.withBank(radians).right))) {
            radians = -radians;
        }
        double degrees = Math.toDegrees(radians);
        return Math.max(-MAX_BANK, Math.min(MAX_BANK, degrees));
    }

    /**
     * The layout in plan and profile, as a function of {@code u}: distance round the oval in plan,
     * starting at the brakes. The spline is fitted through points on this afterwards.
     */
    private static final class Plan {
        final double h;
        final double r;
        final double brakeLength;
        final double halfStraight;
        final double liftStart;
        final double crest;
        final double total;

        /** Where the profile's hills and valleys are, in u, and their heights as fractions of the
         *  energy left there (crest and brakes are absolute). */
        final double[] keyU;
        final double[] keyFraction;
        final double[] keyHeight;

        int stationFirst;
        int platformLast;
        int crestNode;

        /** Energy head (height the train could reach) sampled every block of u from the crest. */
        private final double[] head;
        private final double[] fastHead;

        Plan(double h, double s, double brakeLength) {
            this.h = h;
            this.r = TURN_RADIUS * s;
            this.brakeLength = brakeLength;
            double lift = LIFT_RUN_PER_RISE * h;
            this.halfStraight = Math.max(brakeLength + PLATFORM_LENGTH + lift, STRAIGHT_LENGTH * s) / 2.0D;
            this.crest = 2.0D * halfStraight;
            this.liftStart = crest - lift;
            this.total = 4.0D * halfStraight + 2.0D * Math.PI * r;

            double farTurn = crest;
            double returnStraight = farTurn + Math.PI * r;
            double nearTurn = returnStraight + 2.0D * halfStraight;
            double dropBottom = Math.min(crest + DROP_RUN_PER_FALL * h, returnStraight);
            // The camelback sits in the middle of the return straight, level track either side;
            // the near turn climbs to its apex and falls to the brakes.
            double middle = (dropBottom + nearTurn) / 2.0D;
            double room = (nearTurn - dropBottom) / 2.0D;
            keyU = new double[] {crest, dropBottom, middle - room, middle, middle + room,
                nearTurn, nearTurn + Math.PI * r / 2.0D, total};
            keyFraction = new double[] {-1.0D, 0.0D, 0.0D, CAMELBACK, 0.0D, 0.0D, TURNAROUND_RISE, 0.0D};
            keyHeight = new double[keyU.length];
            keyHeight[0] = h;

            // Heights depend on the energy left, and the energy left on the heights before: settle
            // it by marching the profile a few times.
            int steps = (int) Math.ceil(total - crest) + 1;
            head = new double[steps];
            fastHead = new double[steps];
            for (int i = 1; i < keyU.length; i++) {
                keyHeight[i] = keyFraction[i] * h;
            }
            for (int round = 0; round < 6; round++) {
                march();
                for (int i = 1; i < keyU.length; i++) {
                    keyHeight[i] = keyFraction[i] * Math.max(0.0D, headAt(keyU[i]));
                }
                // The camelback is as long as it has to be for its crest to lift riders by
                // CAMELBACK_AIRTIME at the speed the train carries over it, and no shorter: at the
                // top of this profile the track curves by (pi^2 / 2) * shape^2 * rise / half^2.
                double rise = keyHeight[3];
                double overTheTop = speedSquaredAt(middle);
                // The riders' hearts, above the rail, go over a crest on a tighter curve than the
                // rail does — by the heart's height — so the rail's crest is that much wider.
                double crestRadius = overTheTop / ((1.0D + CAMELBACK_AIRTIME) * GRAVITY)
                    + com.micatechnologies.minecraft.rcmc.track.element.HeartlineShaper.HEART_HEIGHT;
                double half = Math.sqrt(Math.PI * Math.PI / 2.0D * HILL_SHAPE * HILL_SHAPE * rise * crestRadius);
                half = Math.max(8.0D, Math.min(room, half));
                keyU[2] = middle - half;
                keyU[4] = middle + half;
            }
            march();
        }

        /**
         * Energy head along the ride from the crest, losing what rolling resistance and drag take —
         * twice. {@code head} overstates the losses, and sets how tall a hill may be, so the train
         * always has the energy to climb it. {@code fastHead} takes them as they are, and gives the
         * speed the train really carries: what the G and the banks are worked out for. Sizing a
         * crest for the cautious speed makes it too sharp for the real one.
         */
        private void march() {
            march(head, LOSS_MARGIN);
            march(fastHead, 1.0D);
        }

        private void march(double[] into, double margin) {
            into[0] = h + CHAIN_SPEED * CHAIN_SPEED / (2.0D * GRAVITY);
            double previousY = height(crest);
            for (int i = 1; i < into.length; i++) {
                double u = Math.min(total, crest + i);
                double y = height(u);
                double run = Math.hypot(u - Math.min(total, crest + i - 1), y - previousY);
                double v = Math.sqrt(Math.max(0.0D, 2.0D * GRAVITY * (into[i - 1] - y)));
                double deceleration = ROLLING_RESISTANCE * v + AIR_DRAG * v * v;
                into[i] = into[i - 1] - run * deceleration / GRAVITY * margin;
                previousY = y;
            }
        }

        double headAt(double u) {
            if (u <= crest) {
                return h;
            }
            int i = (int) Math.min(head.length - 1, Math.round(u - crest));
            return head[i];
        }

        /** The square of the speed the train really has at {@code u}, from {@code fastHead}. */
        double speedSquaredAt(double u) {
            double energy = u <= crest ? h
                : fastHead[(int) Math.min(fastHead.length - 1, Math.round(u - crest))];
            return Math.max(0.0D, 2.0D * GRAVITY * (energy - height(u)));
        }

        /** Height above the station at {@code u}. */
        double height(double u) {
            if (u <= liftStart) {
                return 0.0D;
            }
            if (u <= crest) {
                return h * liftProfile((u - liftStart) / (crest - liftStart));
            }
            for (int i = 1; i < keyU.length; i++) {
                if (u <= keyU[i]) {
                    double t = (u - keyU[i - 1]) / (keyU[i] - keyU[i - 1]);
                    // Gentle at the low end, where the train is fast, and tighter at the high end,
                    // where it is slow: the same G either way needs a much wider valley than crest.
                    double w = keyHeight[i] >= keyHeight[i - 1]
                        ? Math.pow(t, HILL_SHAPE) : 1.0D - Math.pow(1.0D - t, HILL_SHAPE);
                    return keyHeight[i - 1] + (keyHeight[i] - keyHeight[i - 1]) * (1.0D - Math.cos(Math.PI * w)) / 2.0D;
                }
            }
            return 0.0D;
        }

        /** A lift's climb: easing into a steady slope and out of it again at the top. */
        private static double liftProfile(double t) {
            double ease = 0.2D;
            double slope = 1.0D / (1.0D - ease);
            if (t < ease) {
                return slope * t * t / (2.0D * ease);
            }
            if (t < 1.0D - ease) {
                return slope * (ease / 2.0D + (t - ease));
            }
            double rest = 1.0D - t;
            return 1.0D - slope * rest * rest / (2.0D * ease);
        }

        /** The point at {@code u}, in the world. The near side runs along +X, the oval off to +Z. */
        Vec3 position(double u, Vec3 origin) {
            double a = halfStraight;
            double x;
            double z;
            double farTurn = crest;
            double returnStraight = farTurn + Math.PI * r;
            double nearTurn = returnStraight + 2.0D * a;
            if (u <= farTurn) {
                x = -a + u;
                z = -r;
            } else if (u <= returnStraight) {
                double phi = (u - farTurn) / r;
                x = a + r * Math.sin(phi);
                z = -r * Math.cos(phi);
            } else if (u <= nearTurn) {
                x = a - (u - returnStraight);
                z = r;
            } else {
                double phi = (u - nearTurn) / r;
                x = -a - r * Math.sin(phi);
                z = r * Math.cos(phi);
            }
            // The station sits at the player's feet: shift so the platform's middle is the origin.
            double platformMiddle = -a + (brakeLength + liftStart) / 2.0D;
            return new Vec3(origin.x + x - platformMiddle, origin.y + height(u), origin.z + z + r);
        }

        /** Where the nodes go, in u: sparse on the level and the lift, closer where the ride moves. */
        List<Double> nodeStations() {
            List<Double> us = new ArrayList<>();
            us.add(0.0D);
            stationFirst = us.size();
            us.add(brakeLength);
            us.add((brakeLength + liftStart) / 2.0D);
            platformLast = us.size();
            us.add(liftStart);
            for (double t : new double[] {0.25D, 0.5D, 0.75D}) {
                us.add(liftStart + (crest - liftStart) * t);
            }
            crestNode = us.size();
            us.add(crest);
            // After the crest: every key point, and enough between them to hold the shape.
            double spacing = Math.min(12.0D, Math.PI * r / 10.0D);
            for (int k = 1; k < keyU.length; k++) {
                double from = keyU[k - 1];
                double to = keyU[k];
                // At least four to a piece, so the spline holds a short hill's shape and does
                // not sharpen its crest.
                int pieces = Math.max(4, (int) Math.ceil((to - from) / spacing));
                for (int p = 1; p <= pieces; p++) {
                    double u = from + (to - from) * p / pieces;
                    if (u < total - 1.0e-6D) {
                        us.add(u);
                    }
                }
            }
            return us;
        }
    }
}
