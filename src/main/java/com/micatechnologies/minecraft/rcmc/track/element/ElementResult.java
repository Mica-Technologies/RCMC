package com.micatechnologies.minecraft.rcmc.track.element;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import java.util.Collections;
import java.util.List;

/**
 * What a {@link TrackElement} hands back: the nodes to append, and the state a following element needs to
 * pick up from where this one left off.
 *
 * <p><b>{@link #exitFrame} deliberately does not include the exit bank.</b> It mirrors the split
 * {@code TrackSection} itself uses — {@code TrackSection.frameAtDistance} composes a bank-free transported
 * frame with a separately-tracked authored bank via {@code TrackFrame.withBank} — rather than baking bank
 * into the frame here. Two reasons: first, that is exactly what {@code TrackSection} will independently
 * recompute anyway once these nodes are committed to a real section, via real parallel transport over the
 * whole section rather than one element in isolation, so reporting anything else here would just be a
 * second, possibly-diverging, opinion. Second, it keeps chaining composable — the next element's
 * {@link ElementContext} wants the same (frame, bank) split this class produces, so the output of one
 * element is directly the input shape of the next.</p>
 */
public final class ElementResult {

    public final List<TrackNode> nodes;
    public final TrackFrame exitFrame;
    public final double exitBankDegrees;

    public ElementResult(List<TrackNode> nodes, TrackFrame exitFrame, double exitBankDegrees) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("nodes must be non-null and non-empty");
        }
        if (exitFrame == null) {
            throw new IllegalArgumentException("exitFrame must not be null");
        }
        ElementGeometry.requireFinite(exitBankDegrees, "exitBankDegrees");
        this.nodes = Collections.unmodifiableList(nodes);
        this.exitFrame = exitFrame;
        this.exitBankDegrees = exitBankDegrees;
    }

    /** Convenience for chaining: the context the next element should be built with. */
    public ElementContext asNextContext(double nodeSpacing) {
        return new ElementContext(exitFrame, exitBankDegrees, nodeSpacing);
    }
}
