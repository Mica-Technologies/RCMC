package com.micatechnologies.minecraft.rcmc.track.element;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Lays track so that it rolls about the riders' hearts rather than about the rail.
 *
 * <p>An element says where the riders' hearts should go and which way is up for them at each node;
 * this puts the rail {@link #HEART_HEIGHT} below each heart, along that up, and works out the bank
 * that makes the finished track's frame point that way. Rolling about the rail swings a rider's
 * chest round a circle almost a block across, a sideways throw real track design avoids by rolling
 * about the heartline and letting the rail move instead. A corkscrew's rail spirals round a nearly
 * straight heart path; a banked turn's rail steps outward and down as the bank comes on.</p>
 *
 * <p><b>Why the bank is measured, not computed.</b> A node's bank rolls the frame that parallel
 * transport gives the finished track, and transport along a rail that spirals picks up a twist of its
 * own. So the rail is built as a track section — entered the way the real track will enter it, from
 * the element's entry frame — and each node's bank is read off as the roll from that section's
 * unbanked frame to the wanted up. Unwrapped node to node, so a full roll comes out as 360°, not a
 * snap back through zero.</p>
 */
public final class HeartlineShaper {

    /**
     * Height of a seated rider's heart above the track frame, in blocks. The ride physics measures G
     * here ({@code physics.Heartline}), so track shaped about this height rides as it was designed.
     */
    public static final double HEART_HEIGHT = 0.9D;

    private HeartlineShaper() {
    }

    /**
     * @param context the element's entry: its frame is the rail's, unbanked, and its bank the
     *                rail's bank there
     * @param hearts  where each node's rider heart goes, in order along the element
     * @param ups     the rider's up at each heart
     */
    public static ElementResult shape(ElementContext context, List<Vec3> hearts, List<Vec3> ups) {
        return shape(context, hearts, ups, null);
    }

    /**
     * As above, leaving along {@code exitForward} when the element knows its exit direction
     * exactly. The local section only estimates it at its loose end, and whatever is laid next
     * starts along it: a small error there is a kink at the join.
     */
    public static ElementResult shape(ElementContext context, List<Vec3> hearts, List<Vec3> ups,
                                      Vec3 exitForward) {
        if (hearts.size() != ups.size() || hearts.isEmpty()) {
            throw new IllegalArgumentException("need one up per heart, and at least one");
        }
        List<Vec3> rails = new ArrayList<>(hearts.size());
        for (int i = 0; i < hearts.size(); i++) {
            rails.add(hearts.get(i).subtract(ups.get(i).normalize().scale(HEART_HEIGHT)));
        }

        // The rail as the finished track will frame it: entered along the entry frame, its up
        // transported from the entry's.
        TrackFrame entry = context.entryFrame;
        List<TrackNode> flat = new ArrayList<>(rails.size() + 1);
        flat.add(new TrackNode(entry.position));
        for (Vec3 rail : rails) {
            flat.add(new TrackNode(rail));
        }
        TrackSection section = new TrackSection(0, flat, false, null, null,
            entry.forward, exitForward, entry.up);

        List<TrackNode> nodes = new ArrayList<>(rails.size());
        double previous = context.entryBankDegrees;
        for (int i = 0; i < rails.size(); i++) {
            TrackFrame level = section.unbankedFrameAtDistance(section.nodeDistance(i + 1));
            Vec3 up = ups.get(i);
            // TrackFrame.withBank(a) turns up toward forward x up by a: read the angle back the same way.
            double radians = Math.atan2(up.dot(level.forward.cross(level.up)), up.dot(level.up));
            double degrees = Math.toDegrees(radians);
            degrees += 360.0D * Math.round((previous - degrees) / 360.0D);
            nodes.add(new TrackNode(rails.get(i), degrees, null));
            previous = degrees;
        }

        TrackFrame exit = section.unbankedFrameAtDistance(section.totalLength());
        Vec3 forward = exitForward == null ? exit.forward : exitForward.normalize();
        Vec3 up = exit.up.subtract(forward.scale(exit.up.dot(forward))).normalize();
        return new ElementResult(nodes, new TrackFrame(rails.get(rails.size() - 1), forward, up), previous);
    }

    /** The rider's up at a point heading {@code forward}, rolled by {@code bankDegrees} from
     *  {@code level} — the bank convention {@code TrackFrame.withBank} uses. */
    public static Vec3 bankedUp(Vec3 forward, Vec3 level, double bankDegrees) {
        Vec3 f = forward.normalize();
        Vec3 up = level.subtract(f.scale(level.dot(f))).normalize();
        double a = Math.toRadians(bankDegrees);
        return up.scale(Math.cos(a)).add(f.cross(up).scale(Math.sin(a)));
    }
}
