package com.micatechnologies.minecraft.rcmc.rating;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.element.ElementContext;
import com.micatechnologies.minecraft.rcmc.track.element.ElementResult;
import com.micatechnologies.minecraft.rcmc.track.element.TrackElement;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared helpers for building small test coasters out of {@code track.element} pieces, for the
 * {@code rating} package's tests.
 *
 * <p>Not a {@code @Test} class itself — just the plumbing every rating test needs to turn a chain of
 * {@link TrackElement}s (a curve, a slope, a loop) into a real {@link TrackNetwork} the way a builder
 * would, without hand-deriving control-point coordinates for every test track.</p>
 */
final class TestTracks {

    static final double DEFAULT_NODE_SPACING = 4.0D;

    private TestTracks() {
    }

    /** A single-section, open (non-closed) network containing exactly {@code nodes}. */
    static TrackNetwork singleSectionNetwork(int sectionId, List<TrackNode> nodes) {
        TrackNetwork network = new TrackNetwork();
        network.addSection(new TrackSection(sectionId, nodes, false, "steel"));
        return network;
    }

    /**
     * Chains {@code elements} one after another starting from {@code startFrame}, and returns the
     * full node list a {@link TrackSection} needs — including the entry node the elements themselves
     * deliberately omit (see {@link TrackElement#generate}'s javadoc).
     */
    static List<TrackNode> chain(TrackFrame startFrame, double startBankDegrees, TrackElement... elements) {
        List<TrackNode> nodes = new ArrayList<>();
        nodes.add(new TrackNode(startFrame.position, startBankDegrees, null));

        ElementContext context = new ElementContext(startFrame, startBankDegrees, DEFAULT_NODE_SPACING);
        for (TrackElement element : elements) {
            ElementResult result = element.generate(context);
            nodes.addAll(result.nodes);
            context = result.asNextContext(DEFAULT_NODE_SPACING);
        }
        return nodes;
    }

    /** Same node positions, every authored bank forced to zero — for building an "unbanked" twin of a
     *  track that would otherwise bank into its turns, so the two can be rated against each other. */
    static List<TrackNode> zeroBank(List<TrackNode> nodes) {
        List<TrackNode> result = new ArrayList<>(nodes.size());
        for (TrackNode node : nodes) {
            result.add(new TrackNode(node.position(), 0.0D, node.styleId()));
        }
        return result;
    }

    /** Same node positions, every authored bank sign flipped. See {@code RideRaterTest}'s javadoc
     *  note on {@code track.element.Curve}'s bank sign for why a rating-package test needs this
     *  instead of trusting a generated curve's own bank directly. */
    static List<TrackNode> negateBank(List<TrackNode> nodes) {
        List<TrackNode> result = new ArrayList<>(nodes.size());
        for (TrackNode node : nodes) {
            result.add(new TrackNode(node.position(), -node.bankDegrees(), node.styleId()));
        }
        return result;
    }
}
