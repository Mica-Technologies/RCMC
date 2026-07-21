package com.micatechnologies.minecraft.rcmc.track;

/**
 * Measures distance along the network — the read-only sibling of {@link TrackNetwork#advance}.
 *
 * <p>{@code advance} answers "I moved this far, where am I now?"; this answers "how far until I
 * reach <em>there</em>?" — the question every piece of transit control (a stopping curve into a
 * station, a movement authority up to an occupied block) fundamentally asks. It honours the same
 * topology {@code advance} does: plain joins, switches via their current selection
 * ({@link TrackNetwork#linkedTo}), direction flips across END-to-END joins, and closed-circuit
 * wrap.</p>
 *
 * <p>Pure static functions over pure-Java types, deterministic, no Minecraft imports — the usual
 * contract this deep in the stack.</p>
 */
public final class TrackWalk {

    /** Mirrors {@link TrackNetwork}'s own bound, and exists for the same reason: terminate, don't hang. */
    private static final int MAX_HOPS = 64;

    /** Tolerance for "the target is exactly where we are standing". */
    private static final double EPSILON = 1e-9D;

    private TrackWalk() {
    }

    /**
     * Distance travelled, in blocks, from {@code from} moving in {@code direction} until reaching
     * {@code target} — or {@link Double#POSITIVE_INFINITY} if the walk dead-ends (unconnected
     * track, a switch lined against the move), leaves {@code horizon} behind, or runs out of hops
     * first.
     *
     * <p>Infinity as the "not reachable" value is a deliberate fit for how callers use this: a
     * stop target that is unreachable composes with {@code Math.min} into "cruise", and an
     * unlimited movement authority simply <em>is</em> infinity. A target behind the train is not
     * special-cased — the walk keeps going forward, and on a looping layout it legitimately finds
     * the target the long way around, which is exactly what a train that cannot reverse would have
     * to do.</p>
     *
     * @param direction travel direction along {@code from}'s section axis: positive for increasing
     *                  distance, negative for decreasing — only the sign is used
     * @param horizon   give up beyond this many blocks — keeps the cost of an unreachable target
     *                  bounded, and callers honest about how far ahead they actually need to see
     */
    public static double distanceTo(TrackNetwork network, TrackRef from, double direction,
                                    TrackRef target, double horizon) {
        if (network == null || from == null || target == null) {
            throw new IllegalArgumentException("network, from and target are all required");
        }
        double dir = direction >= 0.0D ? 1.0D : -1.0D;
        int sectionId = from.sectionId();
        double position = from.distance();
        double accumulated = 0.0D;

        for (int hops = 0; hops <= MAX_HOPS; hops++) {
            TrackSection section = network.section(sectionId);
            if (section == null) {
                return Double.POSITIVE_INFINITY;
            }
            double length = section.totalLength();

            if (sectionId == target.sectionId()) {
                double delta = (target.distance() - position) * dir;
                if (section.isClosed()) {
                    // On a ring, "behind" just means "most of a lap ahead".
                    if (delta < 0.0D) {
                        delta += length;
                    }
                    double total = accumulated + delta;
                    return total <= horizon ? total : Double.POSITIVE_INFINITY;
                }
                if (delta >= -EPSILON) {
                    double total = accumulated + Math.max(0.0D, delta);
                    return total <= horizon ? total : Double.POSITIVE_INFINITY;
                }
                // Behind us on an open section: keep walking — a loop of joined sections can
                // still bring us back around to it from the other side.
            }

            if (section.isClosed()) {
                // A closed section has no ends to leave through; the target either was on it
                // (handled above) or can never be reached from it.
                return Double.POSITIVE_INFINITY;
            }

            double toEnd = dir > 0.0D ? length - position : position;
            accumulated += toEnd;
            if (accumulated > horizon) {
                return Double.POSITIVE_INFINITY;
            }

            TrackNetwork.SectionEnd leaving = new TrackNetwork.SectionEnd(
                sectionId, dir > 0.0D ? TrackNetwork.End.END : TrackNetwork.End.START);
            TrackNetwork.SectionEnd arriving = network.linkedTo(leaving);
            if (arriving == null) {
                return Double.POSITIVE_INFINITY;
            }
            TrackSection next = network.section(arriving.sectionId);
            if (next == null) {
                return Double.POSITIVE_INFINITY;
            }
            sectionId = arriving.sectionId;
            // Same rule as TrackNetwork.advance: which way we travel through the new section
            // depends only on which end we arrive at.
            if (arriving.end == TrackNetwork.End.START) {
                position = 0.0D;
                dir = 1.0D;
            } else {
                position = next.totalLength();
                dir = -1.0D;
            }
        }
        return Double.POSITIVE_INFINITY;
    }
}
