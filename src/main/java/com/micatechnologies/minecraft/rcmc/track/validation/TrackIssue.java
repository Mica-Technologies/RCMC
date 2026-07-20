package com.micatechnologies.minecraft.rcmc.track.validation;

/**
 * One finding reported by {@link TrackValidator} about a {@code TrackSection}.
 *
 * <p>Immutable. See the class javadoc on {@link TrackValidator} for the reasoning behind
 * {@link Severity} — in short, {@code ERROR} is reserved for geometry that would break the
 * simulation outright; anything that is merely unpleasant or dangerous to ride is a
 * {@code WARNING}.</p>
 */
public final class TrackIssue {

    /**
     * How seriously to take an issue.
     *
     * <p>{@code ERROR} means the simulation cannot be trusted at that point — coincident nodes,
     * a non-finite number, a tangent that reverses. {@code WARNING} means the geometry is valid
     * and will simulate correctly, but a rider would find it uncomfortable or dangerous: too
     * much lateral G, too steep a drop, a bank angle changing too fast, track passing close to
     * itself. {@code INFO} is reserved for future observations that are not a problem by
     * themselves; no current check produces one.</p>
     */
    public enum Severity {
        INFO, WARNING, ERROR
    }

    private final Severity severity;
    private final String code;
    private final String message;
    private final double distanceBlocks;
    private final double value;

    /**
     * @param severity       how seriously to take this finding
     * @param code           stable, machine-readable identifier (e.g.
     *                       {@code "TRACK_EXCESSIVE_LATERAL_G"}). Stable across releases so
     *                       tooling and tests can key off it instead of parsing {@code message},
     *                       which is free to reword.
     * @param message        human-readable explanation, safe to show a builder directly
     * @param distanceBlocks distance along the section, in blocks, where the issue occurs
     * @param value          the measured quantity that triggered the issue, in whatever unit is
     *                       natural for the check that produced it (g, degrees, degrees/block,
     *                       blocks — see the check's javadoc in {@link TrackValidator})
     */
    public TrackIssue(Severity severity, String code, String message, double distanceBlocks, double value) {
        if (severity == null) {
            throw new IllegalArgumentException("severity must not be null");
        }
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
        this.severity = severity;
        this.code = code;
        this.message = message;
        this.distanceBlocks = distanceBlocks;
        this.value = value;
    }

    public Severity severity() {
        return severity;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public double distanceBlocks() {
        return distanceBlocks;
    }

    public double value() {
        return value;
    }

    @Override
    public String toString() {
        return "TrackIssue{" + severity + ' ' + code + " at s=" + String.format("%.2f", distanceBlocks)
            + ", value=" + String.format("%.3f", value) + ": " + message + '}';
    }
}
