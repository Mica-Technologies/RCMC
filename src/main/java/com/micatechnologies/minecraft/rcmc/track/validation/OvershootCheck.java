package com.micatechnologies.minecraft.rcmc.track.validation;

import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects the curve sagging below, or bulging above, the nodes it runs between.
 *
 * <p><b>What this catches.</b> An interpolating spline passes through its control points but is not
 * confined between them. Catmull-Rom derives the tangent at each node from its <em>neighbours</em>,
 * so a node sitting between a level run and a steep climb gets a sharply upward tangent — and the
 * segment <em>arriving</em> at that node has to finish with that tangent while still passing
 * through both endpoints. The only way to do both is to dip below first. The result is track that
 * sags into the ground before a climb, which reads as the builder's height being ignored when in
 * fact it is being honoured rather too enthusiastically.</p>
 *
 * <p>Reported as a {@code WARNING} rather than an error: the geometry is valid, and a builder may
 * genuinely want a dip. What it is not is a <em>surprise</em>, which is the whole problem — this
 * makes it visible.</p>
 *
 * <p><b>This is a detector, not a cure.</b> The real fix is to stop the curve overshooting at all,
 * by clamping node tangents for vertical monotonicity — the Fritsch–Carlson condition, as used by
 * monotone cubic interpolation. That belongs in {@code CatmullRomSpline} and is a more invasive
 * change than a check; see the master plan. Until then, the remedy available to a builder is to
 * place an intermediate node, which shortens the span the curve has to bend through.</p>
 */
public final class OvershootCheck {

    /** How far past its endpoints a span may stray before it is worth mentioning, in blocks. */
    public static final double DEFAULT_TOLERANCE = 0.75D;

    /** Samples per span. Overshoot peaks near the middle, so a modest count finds it reliably. */
    private static final int SAMPLES_PER_SPAN = 12;

    private final double toleranceBlocks;

    public OvershootCheck() {
        this(DEFAULT_TOLERANCE);
    }

    public OvershootCheck(double toleranceBlocks) {
        this.toleranceBlocks = toleranceBlocks;
    }

    /**
     * Checks every node-to-node span for vertical excursion beyond its endpoints.
     *
     * <p>Only the vertical axis is checked. Horizontal overshoot is what makes a curve a curve —
     * flagging it would fire on every corner ever built — whereas vertical overshoot is nearly
     * always unwanted, because the ground is down there.</p>
     */
    public List<TrackIssue> check(TrackSection section) {
        List<TrackIssue> issues = new ArrayList<>();
        int nodeCount = section.nodes().size();
        if (nodeCount < 2) {
            return issues;
        }

        int spans = section.isClosed() ? nodeCount : nodeCount - 1;
        for (int i = 0; i < spans; i++) {
            double from = section.nodeDistance(i);
            double to = i + 1 < nodeCount
                ? section.nodeDistance(i + 1)
                : section.totalLength();
            if (to <= from) {
                continue;
            }

            double startY = section.positionAtDistance(from).y;
            double endY = section.positionAtDistance(to).y;
            double low = Math.min(startY, endY);
            double high = Math.max(startY, endY);

            double worstBelow = 0.0D;
            double worstAbove = 0.0D;
            double worstAt = from;
            for (int sample = 1; sample < SAMPLES_PER_SPAN; sample++) {
                double at = from + (to - from) * sample / (double) SAMPLES_PER_SPAN;
                Vec3 point = section.positionAtDistance(at);
                double below = low - point.y;
                double above = point.y - high;
                if (below > worstBelow) {
                    worstBelow = below;
                    worstAt = at;
                }
                if (above > worstAbove) {
                    worstAbove = above;
                    worstAt = at;
                }
            }

            double worst = Math.max(worstBelow, worstAbove);
            if (worst > toleranceBlocks) {
                boolean sagging = worstBelow >= worstAbove;
                issues.add(new TrackIssue(TrackIssue.Severity.WARNING,
                    "TRACK_VERTICAL_OVERSHOOT",
                    String.format("Track %s %.1f blocks %s the nodes either side of it. "
                            + "Place a node partway to shorten the span it bends through.",
                        sagging ? "sags" : "bulges", worst, sagging ? "below" : "above"),
                    worstAt, worst));
            }
        }
        return issues;
    }
}
