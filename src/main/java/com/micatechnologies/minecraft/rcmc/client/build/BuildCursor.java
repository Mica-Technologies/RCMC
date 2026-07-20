package com.micatechnologies.minecraft.rcmc.client.build;

import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Where the next track node would land, and what shape of track that implies.
 *
 * <p>Shared by the preview and the HUD so they cannot disagree about the pending placement, and
 * so both match {@code ItemTrackTool.onItemUse} exactly. Three copies of "block above the face
 * hit, centred, plus the height offset" would drift, and a preview that differs from what the
 * click produces is worse than no preview.</p>
 */
@SideOnly(Side.CLIENT)
public final class BuildCursor {

    /** Grade beyond which a placement is worth warning about, in degrees. */
    public static final double STEEP_GRADE = 55.0D;

    private BuildCursor() {
        throw new AssertionError("No instances.");
    }

    /** World position a click would place a node at, or {@code null} if not aimed at a block. */
    public static Vec3 candidate(Minecraft mc) {
        RayTraceResult hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.getBlockPos() == null) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        return new Vec3(pos.getX() + 0.5D,
            pos.getY() + 1.0D + ClientBuildSession.heightOffset(),
            pos.getZ() + 0.5D);
    }

    /**
     * The climb from the last placed node to the candidate.
     *
     * <p>This is the number that explains the behaviour people find surprising: the height offset
     * is relative to the <em>terrain under the cursor</em>, not to the previous node, and it
     * persists across placements. Scroll it up by ten and place one node, and the track has ten
     * blocks to climb between two adjacent nodes — level, then a wall. Nothing is ignoring the
     * height; there are simply no nodes in between to climb through.</p>
     *
     * <p>Showing rise, run and the resulting grade turns that from a surprise into a decision.</p>
     */
    public static final class Segment {

        /** Horizontal distance from the previous node, in blocks. */
        public final double run;

        /** Vertical change from the previous node, in blocks. */
        public final double rise;

        /** Angle of climb in degrees; 90 is vertical. */
        public final double gradeDegrees;

        Segment(double run, double rise) {
            this.run = run;
            this.rise = rise;
            this.gradeDegrees = run < 1.0e-6D
                ? (Math.abs(rise) < 1.0e-6D ? 0.0D : 90.0D)
                : Math.toDegrees(Math.atan2(Math.abs(rise), run));
        }

        public boolean isSteep() {
            return gradeDegrees > STEEP_GRADE;
        }
    }

    /** The segment a click would create, or {@code null} if there is no previous node to measure from. */
    public static Segment pendingSegment(Minecraft mc) {
        List<TrackNode> nodes = ClientBuildSession.nodes();
        if (nodes.isEmpty()) {
            return null;
        }
        Vec3 candidate = candidate(mc);
        if (candidate == null) {
            return null;
        }
        Vec3 previous = nodes.get(nodes.size() - 1).position();
        double dx = candidate.x - previous.x;
        double dz = candidate.z - previous.z;
        return new Segment(Math.sqrt(dx * dx + dz * dz), candidate.y - previous.y);
    }
}
