package com.micatechnologies.minecraft.rcmc.track.element;

/**
 * A prefab, parameterised generator for one standard rollercoaster maneuver — a straight, a banked
 * curve, a loop, a corkscrew — expressed as a node sequence a builder can drop onto the end of existing
 * track instead of hand-placing every control point of a spline editor.
 *
 * <p><b>Why this package exists.</b> {@code track.math} and {@code track.TrackSection} give a builder a
 * perfectly general tool: place nodes anywhere, the centripetal Catmull-Rom spline through them is
 * guaranteed not to cusp (see {@code TRACK_GEOMETRY.md}). What that generality does not give you is
 * <em>good defaults</em> — the difference between "a curve that is geometrically valid" and "a curve
 * banked at the angle that balances lateral G at a sensible speed, eased in and out so the roll rate
 * never steps" is exactly the difference between building a coaster being fun and it being a fight with a
 * spline editor. Every class in this package encodes one of those defaults so a builder gets it for free.</p>
 *
 * <p><b>Pure Java, same as {@code track.math} and {@code physics}.</b> No Minecraft type appears anywhere
 * in this package, in main code or in tests — see {@code CLAUDE.md}'s load-bearing rules. That is what
 * makes an element's geometry (does a 90° curve of radius R actually land R away, perpendicular to where
 * it started?) checkable on a bare JVM instead of only by eyeballing it in a dev client.</p>
 *
 * <p><b>This package does not modify {@code track.math} or {@code track}.</b> Anywhere an element needs a
 * capability those packages do not expose as {@code public} (a general-axis Rodrigues rotation, parallel
 * transport of an arbitrary vector, the smoothstep used for bank easing), it is re-derived locally in
 * {@link ElementGeometry} rather than changing a shared class's visibility or adding a method to it. A
 * few extra lines of duplicated algebra is a much smaller risk than a merge conflict against another agent
 * editing those same files concurrently.</p>
 *
 * @see ElementContext
 * @see ElementResult
 */
public interface TrackElement {

    /** Stable, machine-readable identifier — e.g. {@code "curve_left_medium"}, {@code "vertical_loop"}.
     * Meant for save data and palette lookup, so it should not change once shipped. */
    String id();

    /** Human-readable name for a builder-facing UI, e.g. {@code "Medium Left Curve"}. */
    String displayName();

    /**
     * Builds the node sequence for this element, continuing from {@code context}.
     *
     * <p>The returned nodes do not repeat {@code context.entryFrame.position} — the caller is expected to
     * already have a node there (the end of whatever precedes this element) and to append these nodes
     * after it, exactly as {@code TrackSection.withNodeAppended} would be used repeatedly.</p>
     */
    ElementResult generate(ElementContext context);
}
