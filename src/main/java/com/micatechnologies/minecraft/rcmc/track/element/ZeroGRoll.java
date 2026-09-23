package com.micatechnologies.minecraft.rcmc.track.element;

/**
 * A zero-g roll: up over a low hill, rolling right over while the riders float, and down again.
 *
 * <p>Shaped by {@link InversionPath}: the train pulls up at {@link #PULL} g until it is climbing at
 * {@link #CLIMB}, eases to weightless, flies the weightless arc over the top while it rolls through
 * 360°, then pulls out level. Riders feel nothing during the roll —
 * that is the point of the element, and why the roll is timed to the weightless stretch exactly —
 * and the train leaves level, on its original heading and height, a little slower.</p>
 *
 * <p>Built for one speed. Taken faster the top turns to airtime; taken slower, to a light push
 * into the seat. Either way the ride check says so.</p>
 */
public final class ZeroGRoll implements TrackElement {

    static final double PULL = 2.0D;
    static final double CLIMB = Math.toRadians(32.0D);

    private final double entrySpeed;
    private final RollDirection direction;

    public ZeroGRoll(double entrySpeed, RollDirection direction) {
        ElementGeometry.requirePositive(entrySpeed, "entrySpeed");
        this.entrySpeed = entrySpeed;
        this.direction = direction == null ? RollDirection.POSITIVE : direction;
    }

    @Override
    public String id() {
        return "zero_g_roll";
    }

    @Override
    public String displayName() {
        return "Zero-G Roll";
    }

    @Override
    public ElementResult generate(ElementContext context) {
        double ease = 0.3D * entrySpeed;
        InversionPath path = new InversionPath(entrySpeed);
        path.ease(PULL, ease);
        path.holdUntil(() -> path.alpha() >= CLIMB);
        path.ease(0.0D, ease);
        double top = path.alpha();
        double from = path.length();
        path.holdUntil(() -> path.alpha() <= -top);
        double to = path.length();
        path.settle(0.0D, PULL, ease);
        path.roll(from, to, direction == RollDirection.POSITIVE ? 360.0D : -360.0D);
        return path.shape(context, InversionPath.NODE_SPACING, context.entryFrame.forward);
    }
}
