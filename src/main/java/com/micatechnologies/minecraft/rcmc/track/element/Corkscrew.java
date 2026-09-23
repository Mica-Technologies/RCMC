package com.micatechnologies.minecraft.rcmc.track.element;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * A corkscrew: one full roll, the train rising as it turns over and floating across the top, its
 * path swinging off to the side the way a corkscrew's does.
 *
 * <p>Shaped the way {@link InversionPath} shapes the planar elements — from the load the riders feel
 * rather than from a curve — but in three dimensions, because the roll itself steers the path. The
 * rider rolls smoothly through 360°; the load along their up is
 * {@code n = 1 + A·cos(roll)·sin²(πf)}, {@code f} the fraction of the way through. So they are pressed
 * in (up to {@code 1 + A} g) while upright and pulling up, weightless near the top where they are
 * inverted, and pushed sideways by nothing, ever: the path curves exactly as that load demands, and
 * at the ends the load is 1 g, the same as the track either side. The side of the roll carries the
 * path sideways, which is the corkscrew's offset.</p>
 *
 * <p>{@code A} is solved for the design speed so the train leaves level. The heading it leaves on
 * drifts by a degree or two, and the element reports it exactly, so what follows joins without a
 * kink.</p>
 *
 * <p>It was once a roll about the rail along a sideways S-bend: 3 to 9 g sideways at the riders'
 * hearts. Then a helix about the line of travel, whose felt load swung almost entirely sideways at
 * both ends and snapped upright in the last half block.</p>
 */
public final class Corkscrew implements TrackElement {

    /** Element length per block/s of design speed: about a second and a quarter end to end. */
    static final double LENGTH_PER_SPEED = 1.25D;

    private static final double DS = 0.02D;
    private static final double GRAVITY = InversionPath.GRAVITY;

    private final double entrySpeed;
    private final RollDirection rollDirection;

    public Corkscrew(double entrySpeed, RollDirection rollDirection) {
        ElementGeometry.requirePositive(entrySpeed, "entrySpeed");
        if (rollDirection == null) {
            throw new IllegalArgumentException("rollDirection must not be null");
        }
        this.entrySpeed = entrySpeed;
        this.rollDirection = rollDirection;
    }

    @Override
    public String id() {
        return "corkscrew";
    }

    @Override
    public String displayName() {
        return "Corkscrew";
    }

    /** The heart's path and the rider's up along it. */
    private static final class Path {
        final List<Vec3> hearts = new ArrayList<>();
        final List<Vec3> ups = new ArrayList<>();
        final List<Double> rolls = new ArrayList<>();
        final List<Double> arc = new ArrayList<>();
        Vec3 tangent;
    }

    @Override
    public ElementResult generate(ElementContext context) {
        // More pull climbs higher and leaves pitched up; less leaves pitched down. Find level.
        double lo = 0.2D;
        double hi = 2.0D;
        Path path = null;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2.0D;
            Path trial;
            try {
                trial = integrate(context, mid);
            }
            catch (IllegalArgumentException e) {
                hi = mid;
                continue;
            }
            path = trial;
            if (trial.tangent.dot(context.entryFrame.up) > 0.0D) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        if (path == null) {
            throw new IllegalArgumentException("too slow for a corkscrew: the train would stall");
        }

        // Nodes close where the rider rolls, gradually sparser where not — see
        // InversionPath.nodeIndices for why a roll needs them close, and evenly graded.
        int count = path.hearts.size();
        double[] arc = new double[count];
        double[] turn = new double[count];
        for (int k = 0; k < count; k++) {
            arc[k] = path.arc.get(k);
            turn[k] = path.rolls.get(k);
        }
        List<Vec3> hearts = new ArrayList<>();
        List<Vec3> ups = new ArrayList<>();
        for (int k : InversionPath.nodeIndices(arc, turn, InversionPath.NODE_SPACING)) {
            hearts.add(path.hearts.get(k));
            ups.add(path.ups.get(k));
        }
        return HeartlineShaper.shape(context, hearts, ups, path.tangent);
    }

    /** The path load amplitude {@code amp} produces, for the stock train entering at the design
     *  speed. */
    private Path integrate(ElementContext context, double amp) {
        double length = LENGTH_PER_SPEED * entrySpeed;
        double sign = rollDirection == RollDirection.POSITIVE ? 1.0D : -1.0D;
        Vec3 worldUp = context.entryFrame.up;
        Vec3 heart = context.entryFrame.position.add(worldUp.scale(HeartlineShaper.HEART_HEIGHT));
        Vec3 origin = heart;
        Vec3 tangent = context.entryFrame.forward;
        Vec3 level = worldUp;
        Path path = new Path();
        List<Double> heights = new ArrayList<>();
        double lost = 0.0D;
        int steps = (int) Math.ceil(length / DS);
        for (int k = 0; k <= steps; k++) {
            double s = k * DS;
            double f = Math.min(1.0D, s / length);
            double roll = 360.0D * InversionPath.smootherstep(f);
            double r = Math.toRadians(roll) * sign;
            Vec3 up = level.scale(Math.cos(r)).add(tangent.cross(level).scale(Math.sin(r)));
            heights.add(heart.subtract(origin).dot(worldUp));
            path.hearts.add(heart);
            path.ups.add(up);
            path.rolls.add(roll);
            path.arc.add(s);
            if (k == steps) {
                break;
            }

            double speedSquared = entrySpeed * entrySpeed - lost - 2.0D * GRAVITY * trainHeight(heights, k);
            if (speedSquared < InversionPath.MIN_SPEED * InversionPath.MIN_SPEED) {
                throw new IllegalArgumentException("too slow for a corkscrew: the train would stall");
            }
            double n = 1.0D + amp * Math.cos(Math.toRadians(roll)) * Math.pow(Math.sin(Math.PI * f), 2.0D);
            // The rider feels n along their up; the track supplies that, less gravity.
            Vec3 accel = up.scale(n * GRAVITY).subtract(worldUp.scale(GRAVITY));
            Vec3 across = accel.subtract(tangent.scale(accel.dot(tangent)));
            Vec3 turned = tangent.add(across.scale(DS / speedSquared)).normalize();
            // Carry the reference up along with the heading: parallel transport.
            level = level.subtract(turned.scale(level.dot(turned))).normalize();
            tangent = turned;
            heart = heart.add(tangent.scale(DS));
            lost += InversionPath.frictionLoss(speedSquared, DS);
        }
        path.tangent = tangent;
        return path;
    }

    /** Average height of the stock train's cars, its front car at step {@code k}, the run before the
     *  element taken as level — see {@link InversionPath#CAR_OFFSETS}. */
    private static double trainHeight(List<Double> heights, int k) {
        double sum = 0.0D;
        for (double offset : InversionPath.CAR_OFFSETS) {
            int at = k - (int) Math.round(offset / DS);
            sum += at <= 0 ? 0.0D : heights.get(at);
        }
        return sum / InversionPath.CAR_OFFSETS.length;
    }
}
