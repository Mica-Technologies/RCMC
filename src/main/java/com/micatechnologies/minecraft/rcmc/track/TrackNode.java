package com.micatechnologies.minecraft.rcmc.track;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;

/**
 * One control point placed by a builder: where the track goes, how banked it is there, and
 * optionally which style it switches to.
 *
 * <p>Immutable. Editing a node produces a new one and a new {@link TrackSection}, which is what
 * makes the section's cached derived geometry safe to hold — there is no way to mutate a node out
 * from under a cached {@code ArcLengthTable}.</p>
 *
 * <p>Position is a full-precision {@link Vec3} in world coordinates, deliberately <em>not</em> a
 * {@code BlockPos} plus offset. Track is continuous; quantising node positions to the block grid
 * and carrying a separate sub-block offset would be two representations of one quantity, and the
 * pair would drift. The anchor block for a node is simply the block containing it
 * ({@link #blockX()} and friends), derived on demand.</p>
 */
public final class TrackNode {

    private final Vec3 position;
    private final double bankDegrees;
    private final String styleId;

    /**
     * @param position    world position of the track centreline at this node
     * @param bankDegrees authored roll about the direction of travel, in degrees. Positive banks
     *                    to the right (the outside of a left-hand curve rises). Interpolated
     *                    smoothly to neighbouring nodes by {@link TrackSection}.
     * @param styleId     track style from this node onward, or {@code null} to inherit the
     *                    section's style
     */
    public TrackNode(Vec3 position, double bankDegrees, String styleId) {
        if (position == null) {
            throw new IllegalArgumentException("position must not be null");
        }
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
            throw new IllegalArgumentException("node position must be finite, got " + position);
        }
        if (!Double.isFinite(bankDegrees)) {
            throw new IllegalArgumentException("bankDegrees must be finite, got " + bankDegrees);
        }
        this.position = position;
        this.bankDegrees = bankDegrees;
        this.styleId = styleId;
    }

    /** A node at {@code position} with no bank and no style override. */
    public TrackNode(Vec3 position) {
        this(position, 0.0D, null);
    }

    public Vec3 position() {
        return position;
    }

    public double bankDegrees() {
        return bankDegrees;
    }

    public double bankRadians() {
        return Math.toRadians(bankDegrees);
    }

    /** Style override from this node onward, or {@code null} to inherit the section's. */
    public String styleId() {
        return styleId;
    }

    public TrackNode withPosition(Vec3 newPosition) {
        return new TrackNode(newPosition, bankDegrees, styleId);
    }

    public TrackNode withBank(double newBankDegrees) {
        return new TrackNode(position, newBankDegrees, styleId);
    }

    public TrackNode withStyle(String newStyleId) {
        return new TrackNode(position, bankDegrees, newStyleId);
    }

    /** X of the block containing this node — the anchor block position. */
    public int blockX() {
        return (int) Math.floor(position.x);
    }

    public int blockY() {
        return (int) Math.floor(position.y);
    }

    public int blockZ() {
        return (int) Math.floor(position.z);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TrackNode)) {
            return false;
        }
        TrackNode o = (TrackNode) obj;
        return position.equals(o.position)
            && Double.compare(bankDegrees, o.bankDegrees) == 0
            && (styleId == null ? o.styleId == null : styleId.equals(o.styleId));
    }

    @Override
    public int hashCode() {
        int result = position.hashCode();
        result = 31 * result + Double.hashCode(bankDegrees);
        result = 31 * result + (styleId == null ? 0 : styleId.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "TrackNode{" + position + ", bank=" + bankDegrees + "deg"
            + (styleId == null ? "" : ", style=" + styleId) + '}';
    }
}
