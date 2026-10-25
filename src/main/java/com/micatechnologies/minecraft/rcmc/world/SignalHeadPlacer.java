package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.Rcmc;
import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.block.RcmcBlocks;
import com.micatechnologies.minecraft.rcmc.block.signal.BlockSignalHead;
import com.micatechnologies.minecraft.rcmc.block.signal.SignalHeadMount;
import com.micatechnologies.minecraft.rcmc.block.signal.TileSignalHead;
import com.micatechnologies.minecraft.rcmc.physics.CarSeating;
import com.micatechnologies.minecraft.rcmc.physics.transit.LineSignals;
import com.micatechnologies.minecraft.rcmc.physics.transit.SignalHeads;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Finds a spot for, and places, the signal head at one block boundary of a signalled line — what
 * {@code /rcmc signal heads} does for every boundary of a line.
 *
 * <p><b>Where a head goes.</b> First choice is {@value #PREFERRED_OFFSET} blocks out from the
 * track centre on the driver's side, at rail level. Real tunnels rarely leave exactly that free —
 * a walkway, a platform edge or the tunnel wall is often there — so when it is taken the placer
 * tries one block up, nearer the track (as a bracket on the wall that took it), further out,
 * higher (hung from a ceiling), a block or two along the track, and finally the other side. Every
 * candidate must keep the head clear of a metro car body, and must have something to stand on,
 * bolt to or hang from. Nothing a builder placed is ever replaced.</p>
 *
 * <p><b>Unloaded chunks.</b> A boundary in a chunk that is not loaded is queued rather than
 * skipped: the queue is saved with the world and each head is placed the next time its chunk is
 * loaded, by anyone, for any reason. Loading chunks on demand would stall a server for a city
 * line's worth of chunks, and is not ours to do.</p>
 */
public final class SignalHeadPlacer {

    /** How far to the side of the track centre line a head stands by preference, blocks. */
    public static final double PREFERRED_OFFSET = 2.8D;

    /** Lateral offsets tried, in order of preference. */
    private static final double[] OFFSETS = {PREFERRED_OFFSET, 2.4D, 3.3D, 3.8D};

    /** Heights above rail level tried, in order. */
    private static final int[] HEIGHTS = {0, 1, 2, 3};

    /** Shifts along the track tried, in blocks, in order. */
    private static final double[] ALONG = {0.0D, 1.0D, -1.0D, 2.0D, -2.0D};

    /**
     * How far a head's drawn body reaches from its block centre, blocks. A candidate's centre must
     * stand at least this much further out than the car body, along the track's sideways axis.
     */
    private static final double HEAD_REACH = 0.3D;

    /** Clearance kept between a car body and a head, blocks. */
    private static final double BODY_CLEARANCE = 0.05D;

    private SignalHeadPlacer() {
    }

    /** What happened at one boundary. */
    public enum Outcome {
        PLACED, ALREADY_THERE, QUEUED, NO_ROOM, NO_TRACK
    }

    /** A chosen spot: block, mount and, for a wall bracket, which wall. */
    public static final class Spot {
        public final BlockPos pos;
        public final SignalHeadMount mount;
        public final int wallSide;
        public final float facing;

        Spot(BlockPos pos, SignalHeadMount mount, int wallSide, float facing) {
            this.pos = pos;
            this.mount = mount;
            this.wallSide = wallSide;
            this.facing = facing;
        }

        public String describe() {
            return pos.getX() + " " + pos.getY() + " " + pos.getZ() + " (" + mount.getName() + ")";
        }
    }

    /** The result at one boundary, with where the head is or would go when there is one. */
    public static final class Result {
        public final Outcome outcome;
        public final Spot spot;
        /** The first-choice position, for reporting a boundary left without a head. */
        public final BlockPos preferred;

        Result(Outcome outcome, Spot spot, BlockPos preferred) {
            this.outcome = outcome;
            this.spot = spot;
            this.preferred = preferred;
        }
    }

    /**
     * Places (or, with {@code dry}, only plans) the head at one boundary of {@code lineName}.
     * Queues it when its chunk is not loaded, unless {@code dry}.
     */
    public static Result place(World world, RcmcWorldState state, String lineName,
                               SignalHeads.Boundary boundary, boolean dry) {
        TrackNetwork network = state.network();
        if (!network.hasSection(boundary.sectionId)) {
            return new Result(Outcome.NO_TRACK, null, null);
        }
        TrackFrame frame = network.frameAt(new TrackRef(boundary.sectionId, boundary.distance));
        BlockPos preferred = blockAt(frame.position.add(
            frame.right.scale(boundary.direction * PREFERRED_OFFSET)));
        if (!world.isAreaLoaded(preferred, 6)) {
            if (!dry) {
                Queue.of(world).add(lineName, boundary);
            }
            return new Result(Outcome.QUEUED, null, preferred);
        }
        if (existingHead(world, preferred, lineName, boundary) != null) {
            return new Result(Outcome.ALREADY_THERE, null, preferred);
        }
        Spot spot = findSpot(world, network, boundary);
        if (spot == null) {
            return new Result(Outcome.NO_ROOM, null, preferred);
        }
        if (!dry) {
            world.setBlockState(spot.pos, RcmcBlocks.signalHead.getDefaultState()
                .withProperty(BlockSignalHead.MOUNT, spot.mount));
            TileEntity tile = world.getTileEntity(spot.pos);
            if (tile instanceof TileSignalHead) {
                TileSignalHead head = (TileSignalHead) tile;
                head.setFacingDegrees(spot.facing);
                if (spot.mount == SignalHeadMount.WALL) {
                    head.setWallSide(spot.wallSide);
                }
                head.bindTo(lineName, boundary.sectionId, boundary.distance, boundary.direction);
            }
        }
        return new Result(Outcome.PLACED, spot, preferred);
    }

    /** The nearest free, clear, supported spot for this boundary's head, or {@code null}. */
    static Spot findSpot(World world, TrackNetwork network, SignalHeads.Boundary boundary) {
        double length = network.section(boundary.sectionId).totalLength();
        for (int side : new int[] {1, -1}) {
            for (double along : ALONG) {
                double at = boundary.distance + along * boundary.direction;
                if (at < 0.0D || at > length) {
                    continue;
                }
                TrackFrame frame = network.frameAt(new TrackRef(boundary.sectionId, at));
                Vec3 travel = frame.forward.scale(boundary.direction);
                Vec3 outward = frame.right.scale(boundary.direction * side);
                float facing = TileSignalHead.yawOf(travel);
                for (int height : HEIGHTS) {
                    for (double offset : OFFSETS) {
                        BlockPos pos = blockAt(frame.position.add(outward.scale(offset)))
                            .up(height);
                        Spot spot = trySpot(world, frame, outward, pos, facing);
                        if (spot != null) {
                            return spot;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Spot trySpot(World world, TrackFrame frame, Vec3 outward, BlockPos pos,
                                float facing) {
        if (!world.isAirBlock(pos)) {
            return null;
        }
        // Clear of the car: the block's centre, less the head's own reach, outside the body.
        double cx = pos.getX() + 0.5D - frame.position.x;
        double cz = pos.getZ() + 0.5D - frame.position.z;
        double lateral = cx * outward.x + cz * outward.z;
        if (lateral - HEAD_REACH < CarSeating.METRO_BODY_HALF_WIDTH + BODY_CLEARANCE) {
            return null;
        }
        if (world.isSideSolid(pos.down(), EnumFacing.UP, false)) {
            return new Spot(pos, SignalHeadMount.POST, -1, facing);
        }
        EnumFacing wall = EnumFacing.getFacingFromVector((float) outward.x, 0.0F, (float) outward.z);
        if (wall.getAxis().isHorizontal()
            && world.isSideSolid(pos.offset(wall), wall.getOpposite(), false)) {
            return new Spot(pos, SignalHeadMount.WALL, wall.getHorizontalIndex(), facing);
        }
        if (world.isSideSolid(pos.up(), EnumFacing.DOWN, false)) {
            return new Spot(pos, SignalHeadMount.CEILING, -1, facing);
        }
        return null;
    }

    /** A head already bound to this boundary near its first-choice spot, or {@code null}. */
    private static TileSignalHead existingHead(World world, BlockPos preferred, String lineName,
                                               SignalHeads.Boundary boundary) {
        int reach = 5;
        for (BlockPos pos : BlockPos.getAllInBoxMutable(preferred.add(-reach, -2, -reach),
            preferred.add(reach, 4, reach))) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileSignalHead) {
                TileSignalHead head = (TileSignalHead) tile;
                if (head.isLinked() && head.lineName().equalsIgnoreCase(lineName)
                    && head.sectionId() == boundary.sectionId
                    && head.direction() == boundary.direction
                    && Math.abs(head.distance() - boundary.distance) < 1.0E-3D) {
                    return head;
                }
            }
        }
        return null;
    }

    private static BlockPos blockAt(Vec3 at) {
        return new BlockPos(Math.floor(at.x), Math.floor(at.y + 0.01D), Math.floor(at.z));
    }

    /** "section #19 at 1234.5, 58 64 -120" — how a boundary is named in replies and the log. */
    public static String describe(SignalHeads.Boundary boundary, BlockPos at) {
        return "section #" + boundary.sectionId + " at "
            + String.format(Locale.ROOT, "%.1f", boundary.distance)
            + (boundary.direction > 0 ? " (forward)" : " (reverse)")
            + (at == null ? "" : ", " + at.getX() + " " + at.getY() + " " + at.getZ());
    }

    /**
     * Heads waiting for their chunk to load, saved with the world. Checked twice a second; a
     * check is a chunk-loaded test per queued head until its chunk arrives.
     */
    public static final class Queue extends WorldSavedData {

        public static final String DATA_NAME = RcmcConstants.MOD_NAMESPACE + "_signal_heads";

        private final List<Entry> entries = new ArrayList<>();

        public Queue(String name) {
            super(name);
        }

        public Queue() {
            this(DATA_NAME);
        }

        public static Queue of(World world) {
            MapStorage storage = world.getPerWorldStorage();
            Queue queue = (Queue) storage.getOrLoadData(Queue.class, DATA_NAME);
            if (queue == null) {
                queue = new Queue();
                storage.setData(DATA_NAME, queue);
            }
            return queue;
        }

        void add(String line, SignalHeads.Boundary boundary) {
            for (Entry entry : entries) {
                if (entry.matches(line, boundary)) {
                    return;
                }
            }
            entries.add(new Entry(line, boundary.sectionId, boundary.distance, boundary.direction));
            markDirty();
        }

        /** How many heads are waiting, for {@code line} or for every line when {@code null}. */
        public int count(String line) {
            int n = 0;
            for (Entry entry : entries) {
                if (line == null || entry.line.equalsIgnoreCase(line)) {
                    n++;
                }
            }
            return n;
        }

        /** Drops the waiting heads of {@code line}, or of every line when {@code null}. */
        public int clear(String line) {
            int before = entries.size();
            entries.removeIf(entry -> line == null || entry.line.equalsIgnoreCase(line));
            if (entries.size() != before) {
                markDirty();
            }
            return before - entries.size();
        }

        void tick(World world) {
            if (entries.isEmpty()) {
                return;
            }
            RcmcWorldState state = RcmcWorldState.of(world);
            boolean changed = false;
            for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
                Entry entry = it.next();
                SignalHeads.Boundary boundary = entry.liveBoundary(state);
                if (boundary == null) {
                    // The line was re-signalled or removed: this head has nothing to repeat.
                    it.remove();
                    changed = true;
                    continue;
                }
                Result result = place(world, state, entry.line, boundary, true);
                if (result.outcome == Outcome.QUEUED) {
                    continue;
                }
                if (result.outcome == Outcome.PLACED) {
                    result = place(world, state, entry.line, boundary, false);
                }
                if (result.outcome == Outcome.NO_ROOM) {
                    Rcmc.LOGGER.warn("No room for a signal head on line {} at {}", entry.line,
                        describe(boundary, result.preferred));
                }
                it.remove();
                changed = true;
            }
            if (changed) {
                markDirty();
            }
        }

        @Override
        public void readFromNBT(NBTTagCompound compound) {
            entries.clear();
            NBTTagList list = compound.getTagList("Heads", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                entries.add(new Entry(tag.getString("Line"), tag.getInteger("Section"),
                    tag.getDouble("Distance"), tag.getByte("Direction") < 0 ? -1 : 1));
            }
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound compound) {
            NBTTagList list = new NBTTagList();
            for (Entry entry : entries) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("Line", entry.line);
                tag.setInteger("Section", entry.sectionId);
                tag.setDouble("Distance", entry.distance);
                tag.setByte("Direction", (byte) entry.direction);
                list.appendTag(tag);
            }
            compound.setTag("Heads", list);
            return compound;
        }

        private static final class Entry {
            final String line;
            final int sectionId;
            final double distance;
            final int direction;

            Entry(String line, int sectionId, double distance, int direction) {
                this.line = line;
                this.sectionId = sectionId;
                this.distance = distance;
                this.direction = direction;
            }

            boolean matches(String otherLine, SignalHeads.Boundary boundary) {
                return line.equalsIgnoreCase(otherLine) && sectionId == boundary.sectionId
                    && direction == boundary.direction
                    && Math.abs(distance - boundary.distance) < 1.0E-3D;
            }

            /** This entry's boundary on the line as signalled now, or {@code null} if it is gone. */
            SignalHeads.Boundary liveBoundary(RcmcWorldState state) {
                LineSignals signals = state.transit().signalsFor(line);
                if (signals == null) {
                    return null;
                }
                for (SignalHeads.Boundary boundary : SignalHeads.boundaries(signals.blocks())) {
                    if (matches(line, boundary)) {
                        return boundary;
                    }
                }
                return null;
            }
        }
    }

    /** Works the queue on every server world. Registered on the Forge event bus. */
    public static final class Hooks {

        /** Ticks between queue checks. */
        private static final int INTERVAL = 10;

        @SubscribeEvent
        public void onWorldTick(TickEvent.WorldTickEvent event) {
            if (event.phase != TickEvent.Phase.END || event.world.isRemote
                || event.world.getTotalWorldTime() % INTERVAL != 0L) {
                return;
            }
            Queue.of(event.world).tick(event.world);
        }
    }
}
