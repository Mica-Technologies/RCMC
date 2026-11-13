package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.physics.CarSeating;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.TrainStrike;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * Solid bodies for standing trains, and solid floors for metro cars whose doors are open, so a
 * passenger can walk aboard. A moving train has neither; see {@link TrainStrikes}.
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
    private List<TrainBoxes> cached = Collections.emptyList();

    /**
     * Each standing train's boxes from the tick they were last built, by train id.
     *
     * <p>A standing train is exactly the one whose boxes do not change: one held at a red or
     * berthed for a minute had them rebuilt, every car's frame and all, twenty times a second.
     * They are reused while it stands where it stood, on the same track; see {@link TrainBoxes#key}.</p>
     */
    private final Map<Integer, TrainBoxes> byTrain = new java.util.HashMap<>();

    /** One train's boxes and the box around them all, so a query far from it costs one test. */
    private static final class TrainBoxes {
        final List<AxisAlignedBB> boxes;
        final AxisAlignedBB around;
        /**
         * What the boxes were built from: the lead and tail references, the section objects they
         * lie on (an edit replaces a section, so a changed one is a different object), and whether
         * the doors were open.
         */
        Object[] key;

        TrainBoxes(List<AxisAlignedBB> boxes) {
            this.boxes = boxes;
            AxisAlignedBB union = boxes.get(0);
            for (int i = 1; i < boxes.size(); i++) {
                union = union.union(boxes.get(i));
            }
            this.around = union;
        }
    }

    /**
     * Adds every open-door floor and standing-train body box that meets {@code query} to
     * {@code out}. The boxes are rebuilt at most once per tick.
     *
     * <p>Grouped by train: every entity's collision query in the whole world comes through here,
     * and a park with dozens of trains held in stations would otherwise test each against every
     * slice of every one of them.</p>
     */
    public void collect(World world, RcmcWorldState state, AxisAlignedBB query, List<AxisAlignedBB> out) {
        for (TrainBoxes train : floors(world, state)) {
            if (!train.around.intersects(query)) {
                continue;
            }
            for (AxisAlignedBB box : train.boxes) {
                if (box.intersects(query)) {
                    out.add(box);
                }
            }
        }
    }

    private List<TrainBoxes> floors(World world, RcmcWorldState state) {
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

    private List<TrainBoxes> build(World world, RcmcWorldState state) {
        List<TrainBoxes> trains = null;
        byTrain.keySet().retainAll(state.trains().asMap().keySet());
        for (Map.Entry<Integer, Train> entry : state.trains().asMap().entrySet()) {
            Train train = entry.getValue();
            TrainSpec spec = train.spec();
            if (!state.network().hasSection(train.reference().sectionId())) {
                continue;
            }
            boolean open = spec.carStyle() == TrainSpec.CarStyle.METRO && MetroDoors.areOpen(world, entry.getKey());
            // A standing train is solid along its whole body, so nobody walks into the end of a
            // long car. A moving one is not solid at all: a box sweeping over someone would pin them
            // inside it. TrainStrikes throws them clear instead.
            boolean standing = Math.abs(train.velocity()) < TrainStrikes.STANDING_SPEED;
            if (!open && !standing) {
                byTrain.remove(entry.getKey());
                continue;
            }
            com.micatechnologies.minecraft.rcmc.track.TrackRef lead = train.reference();
            com.micatechnologies.minecraft.rcmc.track.TrackRef tail =
                train.refOfCar(state.network(), spec.carCount() - 1);
            com.micatechnologies.minecraft.rcmc.physics.transit.DoorSide side = open
                ? MetroDoors.openSide(world, entry.getKey()) : null;
            Object[] key = {lead, tail, state.network().section(lead.sectionId()),
                state.network().section(tail.sectionId()), open, side};
            TrainBoxes reused = byTrain.get(entry.getKey());
            if (reused != null && java.util.Arrays.equals(reused.key, key)) {
                if (trains == null) {
                    trains = new ArrayList<>();
                }
                trains.add(reused);
                continue;
            }
            List<AxisAlignedBB> boxes = new ArrayList<>();
            for (int car = 0; car < spec.carCount(); car++) {
                TrackFrame frame = train.bodyFrameOfCar(state.network(), car);
                if (open) {
                    addCarFloor(boxes, frame, spec);
                    addGapPlates(boxes, world, frame, spec, side);
                }
                else {
                    TrainStrike.Body body = TrainStrike.bodyOf(spec);
                    addSlices(boxes, frame, body.halfLength, body.halfWidth, body.bottom, body.top);
                }
            }
            if (boxes.isEmpty()) {
                continue;
            }
            if (trains == null) {
                trains = new ArrayList<>();
            }
            TrainBoxes built = new TrainBoxes(boxes);
            built.key = key;
            byTrain.put(entry.getKey(), built);
            trains.add(built);
        }
        return trains == null ? Collections.<TrainBoxes>emptyList() : trains;
    }

    /** Slices a car's whole body, {@code bottom} to {@code top} above the track, into boxes. */
    private static void addSlices(List<AxisAlignedBB> boxes, TrackFrame frame, double halfLength,
                                  double halfWidth, double bottom, double top) {
        int slices = Math.max(1, (int) Math.ceil(halfLength * 2.0D / SLICE_LENGTH));
        double sliceHalf = halfLength / slices;
        for (int i = 0; i < slices; i++) {
            double centre = -halfLength + sliceHalf * (2 * i + 1);
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (int sa = -1; sa <= 1; sa += 2) {
                for (int sw = -1; sw <= 1; sw += 2) {
                    for (double u : new double[] {bottom, top}) {
                        double along = centre + sa * sliceHalf;
                        double across = sw * halfWidth;
                        double x = frame.position.x + frame.forward.x * along + frame.right.x * across + frame.up.x * u;
                        double y = frame.position.y + frame.forward.y * along + frame.right.y * across + frame.up.y * u;
                        double z = frame.position.z + frame.forward.z * along + frame.right.z * across + frame.up.z * u;
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                        minZ = Math.min(minZ, z);
                        maxZ = Math.max(maxZ, z);
                    }
                }
            }
            boxes.add(new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ));
        }
    }

    /**
     * Carries the floor out across the gap at each doorway on the side the doors opened, as far as
     * the platform that is there. See {@link DoorwayLandings}: the car's own floor stops at its
     * side, the platform starts wherever the block grid put its edge, and a passenger walking
     * between the two went down whatever was left.
     */
    private static void addGapPlates(List<AxisAlignedBB> boxes, World world, TrackFrame frame, TrainSpec spec,
                                     com.micatechnologies.minecraft.rcmc.physics.transit.DoorSide side) {
        double top = CarSeating.METRO_FLOOR_HEIGHT;
        double inner = CarSeating.METRO_BODY_HALF_WIDTH - EDGE_INSET - 0.05D;
        for (int sign = -1; sign <= 1; sign += 2) {
            if (side != null && (sign > 0 ? !side.opensRight() : !side.opensLeft())) {
                continue;
            }
            for (double centre : CarSeating.doorCentreOffsets(spec)) {
                DoorwayLandings.Landing landing = DoorwayLandings.find(world, frame, centre, sign);
                if (landing == null) {
                    continue;
                }
                double outer = CarSeating.METRO_BODY_HALF_WIDTH + landing.gap + DoorwayLandings.OVERLAP;
                double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
                double maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
                double maxY = -Double.MAX_VALUE;
                for (int sa = -1; sa <= 1; sa += 2) {
                    for (double across : new double[] {inner, outer}) {
                        double along = centre + sa * CarSeating.DOOR_HALF_WIDTH;
                        double x = frame.position.x + frame.forward.x * along + frame.right.x * across * sign
                            + frame.up.x * top;
                        double y = frame.position.y + frame.forward.y * along + frame.right.y * across * sign
                            + frame.up.y * top;
                        double z = frame.position.z + frame.forward.z * along + frame.right.z * across * sign
                            + frame.up.z * top;
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                        minZ = Math.min(minZ, z);
                        maxZ = Math.max(maxZ, z);
                    }
                }
                boxes.add(new AxisAlignedBB(minX, maxY - GAP_PLATE_THICKNESS, minZ, maxX, maxY, maxZ));
            }
        }
    }

    /** Thinner than the floor slab: a plate, which must not become a kerb to anything under it. */
    private static final double GAP_PLATE_THICKNESS = 0.2D;

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
