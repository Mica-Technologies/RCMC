package com.micatechnologies.minecraft.rcmc.track;

/**
 * How well two section ends actually meet.
 *
 * <p>Joining two sections in the network graph does not, by itself, make the track continuous —
 * the two ends have independent geometry and can be metres apart pointing in unrelated directions.
 * This measures the two ways that can go wrong:</p>
 *
 * <ul>
 *   <li>{@link #positionGap} — how far apart the endpoints are, in blocks. A gap means a train
 *       <em>teleports</em> across the join. This is degenerate, not merely ugly.</li>
 *   <li>{@link #tangentAngleDegrees} — the angle between the direction of travel leaving one
 *       section and entering the next. A non-zero angle is a kink, which the physics reads as an
 *       instantaneous direction change and a rider feels as an impact.</li>
 * </ul>
 *
 * <p>The two are treated differently, matching the project's warn-don't-forbid stance: a position
 * gap is rejected outright by {@link TrackNetwork#connect}, because a teleporting train is broken
 * rather than unpleasant. A tangent kink is reported and left to the editor to surface, because a
 * builder may legitimately want a slightly imperfect join while iterating — and because "your
 * transition is rough" is exactly the kind of feedback the ride-rating system should be punishing
 * later, not something to refuse up front.</p>
 */
public final class JoinAlignment {

    /** Distance between the two endpoints, in blocks. */
    public final double positionGap;

    /**
     * Angle between the outgoing and incoming directions of travel, in degrees. Zero is a
     * perfectly smooth (G¹-continuous) join; 180° means the track doubles back on itself.
     */
    public final double tangentAngleDegrees;

    public JoinAlignment(double positionGap, double tangentAngleDegrees) {
        this.positionGap = positionGap;
        this.tangentAngleDegrees = tangentAngleDegrees;
    }

    /**
     * True if this join is smooth enough that a rider would not notice it.
     *
     * <p>The 2° default is an admitted judgement call rather than a measured figure: it is roughly
     * the point at which, at coaster speeds, the lateral impulse from a kink stops being
     * distinguishable from ordinary track noise. Tighten it if joins start feeling rough.</p>
     */
    public boolean isSmooth() {
        return isSmooth(2.0D);
    }

    public boolean isSmooth(double toleranceDegrees) {
        return tangentAngleDegrees <= toleranceDegrees;
    }

    @Override
    public String toString() {
        return "JoinAlignment{gap=" + String.format("%.4f", positionGap)
            + " blocks, kink=" + String.format("%.2f", tangentAngleDegrees) + " deg}";
    }
}
