package com.micatechnologies.minecraft.rcmc.rating;

import java.util.Locale;

/**
 * One thing wrong with how a ride actually runs, and where: a stretch of track where riders feel
 * too much, or the place a train cannot get past.
 *
 * <p>Found by {@link RideCheck} from a simulated lap, so it is about the speed a train really has
 * there — a curve that is fine at the bottom of a lift can be brutal at the bottom of the drop.</p>
 */
public final class RideWarning {

    /** What is wrong. */
    public enum Kind {
        /** Pressed into the seat harder than is safe. */
        HIGH_G,
        /** Thrown up out of the seat harder than is safe. */
        NEGATIVE_G,
        /** Thrown sideways harder than is safe: a curve too tight, or not banked enough, for its speed. */
        LATERAL_G,
        /** Thrown forward or back harder than is safe: a launch or brake too sharp. */
        LONGITUDINAL_G,
        /** The train cannot get past here: a hill it cannot climb, or hardware that stops it. */
        STALL
    }

    /** How bad. */
    public enum Severity {
        /** Over the safe limit. */
        CAUTION,
        /** Well over it, or the ride does not run at all. */
        DANGER
    }

    public final Kind kind;
    public final Severity severity;
    public final int sectionId;
    public final double from;
    public final double to;
    /** The worst value over the stretch: G for a G-force, blocks/s for a stall (the speed there). */
    public final double peak;

    public RideWarning(Kind kind, Severity severity, int sectionId, double from, double to, double peak) {
        this.kind = kind;
        this.severity = severity;
        this.sectionId = sectionId;
        this.from = Math.min(from, to);
        this.to = Math.max(from, to);
        this.peak = peak;
    }

    /** What to tell the builder. */
    public String message() {
        switch (kind) {
            case HIGH_G:
                return String.format(Locale.ROOT, "%.1f g pressing riders into their seats", peak);
            case NEGATIVE_G:
                return String.format(Locale.ROOT, "%.1f g lifting riders out of their seats", peak);
            case LATERAL_G:
                return String.format(Locale.ROOT, "%.1f g sideways: too tight, or banked too little, for this speed",
                    Math.abs(peak));
            case LONGITUDINAL_G:
                return String.format(Locale.ROOT, "%.1f g %s: too sharp", Math.abs(peak),
                    peak > 0 ? "forwards" : "backwards");
            case STALL:
            default:
                return "The train cannot get past here";
        }
    }

    /** Short label for an on-track marker. */
    public String label() {
        switch (kind) {
            case HIGH_G:
            case NEGATIVE_G:
                return String.format(Locale.ROOT, "%.1f g", peak);
            case LATERAL_G:
                return String.format(Locale.ROOT, "%.1f g sideways", Math.abs(peak));
            case LONGITUDINAL_G:
                return String.format(Locale.ROOT, "%.1f g %s", Math.abs(peak), peak > 0 ? "forward" : "back");
            case STALL:
            default:
                return "Stalls here";
        }
    }

    @Override
    public String toString() {
        return severity + " " + kind + " #" + sectionId + " " + from + ".." + to + " " + peak;
    }
}
