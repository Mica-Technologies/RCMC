package com.micatechnologies.minecraft.rcmc.track.element;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;

/**
 * A dive loop: up and over with a half roll, then half a loop down, and away the way the train came,
 * lower. An Immelmann ridden backwards.
 *
 * <p>Shaped by {@link InversionPath}. The train pulls up to a climb, eases to weightless and rolls
 * the riders over the ballistic crest, then dives through half a loop, pulling up to {@link #PULL} g,
 * and leaves level, heading back the way it came.</p>
 */
public final class DiveLoop implements TrackElement {

    static final double PULL = 3.2D;
    static final double PULL_UP = 2.0D;
    static final double CLIMB = Math.toRadians(35.0D);

    /** How far to the side the train comes back, in blocks: it returns the way it went in, and
     *  must pass beside the track it came in on, not through it: past the track validator's
     *  4-block clearance even where the pull-out passes under or over the way in. */
    static final double SIDEWAYS = 6.5D;

    private final double entrySpeed;
    private final RollDirection direction;

    public DiveLoop(double entrySpeed, RollDirection direction) {
        ElementGeometry.requirePositive(entrySpeed, "entrySpeed");
        this.entrySpeed = entrySpeed;
        this.direction = direction == null ? RollDirection.POSITIVE : direction;
    }

    @Override
    public String id() {
        return "dive_loop";
    }

    @Override
    public String displayName() {
        return "Dive Loop";
    }

    @Override
    public ElementResult generate(ElementContext context) {
        double ease = 0.3D * entrySpeed;
        InversionPath path = new InversionPath(entrySpeed);
        path.ease(PULL_UP, ease);
        path.holdUntil(() -> path.alpha() >= CLIMB);
        path.ease(0.0D, ease);
        double top = path.alpha();
        double from = path.length();
        path.holdUntil(() -> path.alpha() <= -top);
        double to = path.length();
        path.turnOver();
        // Down through half a loop: the pull peaks at PULL with the train diving straight down,
        // and settles to 1 g as it comes level, heading back the way it came.
        path.settle(-Math.PI, PULL, ease);
        path.roll(from, to, direction == RollDirection.POSITIVE ? 180.0D : -180.0D);
        Vec3 back = context.entryFrame.forward.scale(-1.0D);
        return path.shape(context, InversionPath.NODE_SPACING, back,
            direction == RollDirection.POSITIVE ? SIDEWAYS : -SIDEWAYS);
    }
}
