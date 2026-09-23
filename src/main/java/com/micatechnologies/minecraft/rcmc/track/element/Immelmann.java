package com.micatechnologies.minecraft.rcmc.track.element;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;

/**
 * An Immelmann: half a loop up, a half roll over the top, and away the way the train came, higher.
 *
 * <p>Shaped by {@link InversionPath}. The train pulls up at {@link #PULL} g through the first half of
 * a loop until it is nearly level upside down, eases to weightless over the crest and rolls the
 * riders upright there, then pulls out level, heading back the way it came.</p>
 *
 * <p>Built for one entry speed; the half loop climbs as high as that speed allows. It needs a lot
 * of it — the train must still be moving over the top, upside down, after climbing the loop — so
 * the builder offers it from {@link #MIN_SPEED}. Slower than that it cannot be built at all.</p>
 */
public final class Immelmann implements TrackElement {

    static final double PULL = 3.8D;
    static final double PULL_OUT = 2.2D;
    /** How far short of level, upside down, the load is fully off: the weightless crest runs from
     *  here to as far past level, and the half roll happens across it. */
    static final double CREST = Math.toRadians(30.0D);
    /** Pitch at which the load starts coming off: past vertical, where the loop keeps turning over
     *  under gravity alone. */
    static final double RELEASE = Math.toRadians(110.0D);

    /** Slowest entry, in blocks/s, an Immelmann gets over at. */
    public static final double MIN_SPEED = 22.0D;

    /** How far to the side the train comes back, in blocks: it returns the way it went in, and
     *  must pass beside the track it came in on, not through it: past the track validator's
     *  4-block clearance even where the pull-out passes under or over the way in. */
    static final double SIDEWAYS = 6.5D;

    private final double entrySpeed;
    private final RollDirection direction;

    public Immelmann(double entrySpeed, RollDirection direction) {
        ElementGeometry.requirePositive(entrySpeed, "entrySpeed");
        this.entrySpeed = entrySpeed;
        this.direction = direction == null ? RollDirection.POSITIVE : direction;
    }

    @Override
    public String id() {
        return "immelmann";
    }

    @Override
    public String displayName() {
        return "Immelmann";
    }

    @Override
    public ElementResult generate(ElementContext context) {
        double ease = 0.3D * entrySpeed;
        InversionPath path = new InversionPath(entrySpeed);
        path.ease(PULL, ease);
        // Past the vertical, the loop keeps turning over even with the load off, so the load comes
        // off by pitch: weightless exactly CREST short of level upside down, at any speed.
        path.holdUntil(() -> path.alpha() >= RELEASE);
        path.easeByPitch(0.0D, Math.PI - CREST);
        double from = path.length();
        path.holdUntil(() -> path.alpha() >= Math.PI + CREST);
        double to = path.length();
        path.turnOver();
        path.settle(Math.PI, PULL_OUT, ease);
        path.roll(from, to, direction == RollDirection.POSITIVE ? 180.0D : -180.0D);
        Vec3 back = context.entryFrame.forward.scale(-1.0D);
        return path.shape(context, InversionPath.NODE_SPACING, back,
            direction == RollDirection.POSITIVE ? SIDEWAYS : -SIDEWAYS);
    }
}
