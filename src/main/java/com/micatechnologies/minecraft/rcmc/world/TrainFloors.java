package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.physics.CarSeating;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * Solid floors for metro cars whose doors are open, so a passenger can walk aboard.
 *
 * <h2>Why this is not one bounding box</h2>
 *
 * <p>An entity has exactly one axis-aligned box, and {@code setSize} makes it square in plan — so a
 * metro car nearly 20 blocks long gets a 3.8 × 3.8 box at its centre. A floor derived from that box
 * covers about a fifth of the car, in the middle, nowhere near a doorway. Walking in off a platform
 * put a player over the gap and they fell straight through, which is precisely what was reported.
 * The box was never wrong for what it is used for — tracking, culling, mouse-over — it just cannot
 * describe a long thing that is not aligned to an axis.</p>
 *
 * <p>So the floor is built as a <b>chain of short boxes along the car's own length</b>, each one the
 * tight axis-aligned bound of a small <em>oriented</em> slice. A slice is short enough that its
 * bound hugs the real footprint however the car is turned, and the union of them approximates a
 * rotated rectangle as closely as a walking surface needs. Forge's {@code GetCollisionBoxesEvent}
 * is what allows more than one box; {@link TrackCollisionHandler} already uses it to make support
 * columns solid, for the same reason and in the same way.</p>
 *
 * <h2>Why the cache</h2>
 *
 * <p>{@code getCollisionBoxes} is called several times per moving entity per tick. Rebuilding a
 * train's floor for each of those would mean recomputing car frames dozens of times a tick for a
 * berthed, motionless train. The boxes are therefore built once per world tick and reused, keyed on
 * the world time — which also makes this correct on <b>both sides</b>: a client predicting its own
 * movement must collide with the same floor the server does, and the client has everything needed
 * to build it (synced trains, synced track, door state from the service snapshot).</p>
 */
public final class TrainFloors {

    /**
     * Length of one floor slice, in blocks.
     *
     * <p>The trade is staircase error at the car's edges against the number of boxes: shorter
     * slices hug a diagonal car more closely and cost more of them. At 2 blocks a ~20-block car is
     * ten boxes and the worst-case error at 45° is a few tenths of a block, under a doorway that is
     * 1.8 wide.</p>
     */
    private static final double SLICE_LENGTH = 2.0D;

    /** Thickness of the floor slab. Deep enough that a stepping player lands on it, not through. */
    private static final double SLAB_THICKNESS = 0.5D;

    /** Kept just inside the body sides, so the floor does not stick out past the car it belongs to. */
    private static final double EDGE_INSET = 0.05D;

    private long cachedAtTime = Long.MIN_VALUE;
    private List<AxisAlignedBB> cached = Collections.emptyList();

    /**
     * Every open-door car floor in this world, rebuilt at most once per tick.
     *
     * <p>Empty — and cheap — whenever no train has its doors open, which is almost always.</p>
     */
    public List<AxisAlignedBB> floors(World world, RcmcWorldState state) {
        if (world == null || state == null) {
            return Collections.emptyList();
        }
        long now = world.getTotalWorldTime();
        if (now == cachedAtTime) {
            return cached;
        }
        cachedAtTime = now;
        cached = build(world, state);
        return cached;
    }

    private static List<AxisAlignedBB> build(World world, RcmcWorldState state) {
        List<AxisAlignedBB> boxes = null;
        for (Map.Entry<Integer, Train> entry : state.trains().asMap().entrySet()) {
            Train train = entry.getValue();
            TrainSpec spec = train.spec();
            if (spec.carStyle() != TrainSpec.CarStyle.METRO) {
                continue;
            }
            if (!MetroDoors.areOpen(world, entry.getKey())) {
                // A shut car keeps its ordinary solid box; only an open one needs a floor, because
                // only an open one can be walked into.
                continue;
            }
            if (!state.network().hasSection(train.reference().sectionId())) {
                continue;
            }
            if (boxes == null) {
                boxes = new ArrayList<>();
            }
            for (int car = 0; car < spec.carCount(); car++) {
                addCarFloor(boxes, train.bodyFrameOfCar(state.network(), car), spec);
            }
        }
        return boxes == null ? Collections.<AxisAlignedBB>emptyList() : boxes;
    }

    /** Slices one car's floor into axis-aligned boxes along its length. */
    private static void addCarFloor(List<AxisAlignedBB> boxes, TrackFrame frame, TrainSpec spec) {
        double halfLength = CarSeating.bodyLength(spec) * 0.5D;
        double halfWidth = CarSeating.METRO_BODY_HALF_WIDTH - EDGE_INSET;
        double top = CarSeating.METRO_FLOOR_HEIGHT;

        int slices = Math.max(1, (int) Math.ceil(halfLength * 2.0D / SLICE_LENGTH));
        double sliceHalf = halfLength / slices;
        for (int i = 0; i < slices; i++) {
            double centre = -halfLength + sliceHalf * (2 * i + 1);
            // The tight bound of this slice's four floor corners, in world space. Taking the corners
            // rather than padding an axis-aligned guess is what keeps the chain hugging a car that
            // sits at any angle — including a station on a curve.
            double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (int sa = -1; sa <= 1; sa += 2) {
                for (int sw = -1; sw <= 1; sw += 2) {
                    double along = centre + sa * sliceHalf;
                    double across = sw * halfWidth;
                    double x = frame.position.x + frame.forward.x * along + frame.right.x * across
                        + frame.up.x * top;
                    double y = frame.position.y + frame.forward.y * along + frame.right.y * across
                        + frame.up.y * top;
                    double z = frame.position.z + frame.forward.z * along + frame.right.z * across
                        + frame.up.z * top;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z);
                }
            }
            boxes.add(new AxisAlignedBB(minX, maxY - SLAB_THICKNESS, minZ, maxX, maxY, maxZ));
        }
    }
}
