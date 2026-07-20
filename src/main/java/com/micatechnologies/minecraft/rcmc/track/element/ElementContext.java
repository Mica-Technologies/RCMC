package com.micatechnologies.minecraft.rcmc.track.element;

import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;

/**
 * The state at the end of existing track, handed to a {@link TrackElement} so it knows where and which
 * way to start building.
 *
 * <p>Carries exactly what a builder placing the next piece of track already has in hand: the geometric
 * frame (position, forward, up) at the point the new element picks up, plus the authored bank in effect
 * there. Bank is not part of {@link TrackFrame} — it lives on {@code TrackNode}/{@code TrackSection} as a
 * separate authored value applied on top of the transported frame — so an element needs it passed in
 * alongside the frame to know what it is easing away from.</p>
 *
 * <p>{@link #nodeSpacing} is the one authoring knob every element shares: roughly how many blocks apart,
 * along the path, generated nodes should land. Elements that walk a circular or helical path convert it
 * to an angular step via their own radius; elements that walk a straight or graded path use it directly.
 * Sharing one knob across every element type — rather than each element inventing its own "node density"
 * parameter — is what lets a caller set "I want fine detail" once and apply it uniformly to a whole
 * palette of elements.</p>
 */
public final class ElementContext {

    /** A reasonable default node spacing: dense enough that a tight curve still reads as smooth once
     * Catmull-Rom fills in between nodes, sparse enough that a long straight does not carry thousands of
     * redundant collinear nodes. */
    public static final double DEFAULT_NODE_SPACING = 4.0D;

    public final TrackFrame entryFrame;
    public final double entryBankDegrees;
    public final double nodeSpacing;

    public ElementContext(TrackFrame entryFrame, double entryBankDegrees, double nodeSpacing) {
        if (entryFrame == null) {
            throw new IllegalArgumentException("entryFrame must not be null");
        }
        ElementGeometry.requireFinite(entryBankDegrees, "entryBankDegrees");
        ElementGeometry.requirePositive(nodeSpacing, "nodeSpacing");
        this.entryFrame = entryFrame;
        this.entryBankDegrees = entryBankDegrees;
        this.nodeSpacing = nodeSpacing;
    }

    public ElementContext(TrackFrame entryFrame, double entryBankDegrees) {
        this(entryFrame, entryBankDegrees, DEFAULT_NODE_SPACING);
    }
}
