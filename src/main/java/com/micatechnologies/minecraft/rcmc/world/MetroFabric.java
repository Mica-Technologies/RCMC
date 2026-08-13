package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.block.RcmcBlocks;
import com.micatechnologies.minecraft.rcmc.debug.DemoUnderground;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Turns a {@link DemoUnderground.Plan} into actual blocks: the tunnel it runs through, the decking
 * passengers stand on, and the signage that tells them what is coming.
 *
 * <h2>Why it carves before it lines</h2>
 *
 * <p>The Circle Line's two tracks run eight blocks apart, so a bore around each overlaps the other.
 * Lining each bore as it is carved would leave the first one's wall standing down the middle of the
 * second — a wall through the middle of the tunnel. So this collects <em>every</em> interior block
 * of every bore first, empties them all, and only then lines whatever still borders solid ground.
 * The two bores merge into one wide running tunnel with no seam, and the turning loops merge with
 * themselves the same way, for free.</p>
 *
 * <h2>Heights</h2>
 *
 * <p>Rail level is the plan's origin. The trackbed is the block below it; decking is the block
 * above, so a deck's top face is level with the car floor two blocks up — which is exactly where
 * {@link PlatformSide} looks for it, and why a platform laid here is detected without being
 * declared. The ceiling clears the decking by four, which is headroom to walk under and enough for
 * a board to hang.</p>
 */
public final class MetroFabric {

    /**
     * Half the tunnel's width. Two bores eight blocks apart still overlap at this, so they merge
     * into one running tunnel — the point at which narrowing it further would leave a spine of rock
     * between the tracks.
     */
    private static final int BORE_HALF_WIDTH = 5;

    /** Interior height above rail level; the ceiling is the block above this. */
    private static final int BORE_HEIGHT = DemoUnderground.INTERIOR_HEIGHT;

    /** Sampling step along the alignment. Half a block never leaves a gap on the turnbacks. */
    private static final double STEP = 0.5D;

    /** One sea lantern every this many blocks of ceiling, along the track. */
    private static final int LIGHT_SPACING = 6;

    private MetroFabric() {
        throw new AssertionError("No instances.");
    }

    /** Builds the whole network's fabric. Returns how many blocks were placed. */
    public static int build(World world, TrackNetwork network, DemoUnderground.Plan plan,
                            Vec3 origin) {
        Set<Long> interior = new HashSet<>();
        for (TrackSection section : plan.sections) {
            collectBore(network, section, interior);
        }

        int placed = 0;
        placed += carve(world, interior);
        placed += line(world, interior, origin);
        placed += decks(world, plan, origin);
        placed += signage(world, plan, origin);
        return placed;
    }

    /** Every block inside the running tunnel around one section. */
    private static void collectBore(TrackNetwork network, TrackSection section, Set<Long> interior) {
        double total = section.totalLength();
        for (double s = 0.0D; s <= total; s += STEP) {
            TrackFrame frame;
            try {
                frame = network.frameAt(new TrackRef(section.id(), s));
            }
            catch (RuntimeException e) {
                continue;  // sampled off the end of an open section; nothing to carve there
            }
            for (int across = -BORE_HALF_WIDTH; across <= BORE_HALF_WIDTH; across++) {
                double x = frame.position.x + frame.right.x * across;
                double z = frame.position.z + frame.right.z * across;
                int bx = (int) Math.floor(x);
                int bz = (int) Math.floor(z);
                int base = (int) Math.floor(frame.position.y);
                for (int dy = 0; dy <= BORE_HEIGHT; dy++) {
                    interior.add(new BlockPos(bx, base + dy, bz).toLong());
                }
            }
        }
    }

    private static int carve(World world, Set<Long> interior) {
        int placed = 0;
        for (long packed : interior) {
            BlockPos pos = BlockPos.fromLong(packed);
            if (!world.isAirBlock(pos)) {
                set(world, pos, Blocks.AIR.getDefaultState());
                placed++;
            }
        }
        return placed;
    }

    /**
     * Lines whatever borders the void: trackbed below, walls around, ceiling above.
     *
     * <p>Driven off the interior set rather than off the alignment, so wherever two bores merged
     * there is simply no boundary left to line — which is what removes the seam between the two
     * running tracks without any special case for it.</p>
     */
    private static int line(World world, Set<Long> interior, Vec3 origin) {
        int railLevel = (int) Math.floor(origin.y);
        int placed = 0;
        for (long packed : interior) {
            BlockPos pos = BlockPos.fromLong(packed);
            for (net.minecraft.util.EnumFacing face : net.minecraft.util.EnumFacing.values()) {
                BlockPos neighbour = pos.offset(face);
                if (interior.contains(neighbour.toLong())) {
                    continue;
                }
                IBlockState material;
                if (face == net.minecraft.util.EnumFacing.DOWN) {
                    material = Blocks.GRAVEL.getDefaultState();
                }
                else if (face == net.minecraft.util.EnumFacing.UP) {
                    // Lit along the running line, so the tunnel reads as a tunnel and the door-side
                    // detector is not fooled by darkness (it is not, but a rider would be).
                    material = lit(neighbour, railLevel)
                        ? Blocks.SEA_LANTERN.getDefaultState()
                        : Blocks.STONEBRICK.getDefaultState();
                }
                else {
                    material = Blocks.STONEBRICK.getDefaultState();
                }
                set(world, neighbour, material);
                placed++;
            }
        }
        return placed;
    }

    /** A regular grid of ceiling lights, so spacing does not depend on which bore got there first. */
    private static boolean lit(BlockPos pos, int railLevel) {
        return Math.floorMod(pos.getX(), LIGHT_SPACING) == 0
            && Math.floorMod(pos.getZ(), LIGHT_SPACING) == 0;
    }

    /**
     * Lays each station's decking, with an edge strip along every face that looks at a track.
     *
     * <p>The edge is found rather than declared: a deck block with open space beside it at car-floor
     * height is looking at a running line, and a deck block boxed in by more deck is not. That keeps
     * an island's two edges and a side platform's single edge correct without either being spelled
     * out per station.</p>
     */
    private static int decks(World world, DemoUnderground.Plan plan, Vec3 origin) {
        int ox = (int) Math.floor(origin.x);
        int oy = (int) Math.floor(origin.y);
        int oz = (int) Math.floor(origin.z);
        Set<Long> deckBlocks = new HashSet<>();
        for (DemoUnderground.Stop stop : plan.stops) {
            for (DemoUnderground.Deck deck : stop.decks) {
                for (int x = deck.minX; x <= deck.maxX; x++) {
                    for (int z = deck.minZ; z <= deck.maxZ; z++) {
                        deckBlocks.add(new BlockPos(ox + x, oy + deck.y, oz + z).toLong());
                    }
                }
            }
        }

        int placed = 0;
        for (long packed : deckBlocks) {
            BlockPos pos = BlockPos.fromLong(packed);
            boolean edge = false;
            for (net.minecraft.util.EnumFacing face : net.minecraft.util.EnumFacing.HORIZONTALS) {
                if (!deckBlocks.contains(pos.offset(face).toLong())) {
                    edge = true;
                    break;
                }
            }
            set(world, pos, edge
                ? RcmcBlocks.platformEdge.getDefaultState()
                : RcmcBlocks.platform.getDefaultState());
            placed++;
            // Clear what a passenger stands in, so the deck reads as walkable to the detector and
            // to a person. The bore already emptied most of this; a deck laid at its edge may not.
            for (int up = 1; up <= 3; up++) {
                BlockPos above = pos.up(up);
                if (!world.isAirBlock(above)) {
                    set(world, above, Blocks.AIR.getDefaultState());
                    placed++;
                }
            }
        }
        return placed;
    }

    /** Hangs an arrival board over each platform, with a nameplate and a speaker beside it. */
    private static int signage(World world, DemoUnderground.Plan plan, Vec3 origin) {
        int ox = (int) Math.floor(origin.x);
        int oy = (int) Math.floor(origin.y);
        int oz = (int) Math.floor(origin.z);
        int placed = 0;
        for (DemoUnderground.Stop stop : plan.stops) {
            BlockPos board = new BlockPos(ox + stop.signX, oy + stop.signY, oz + stop.signZ);
            // Placed directly rather than through the item, so the board is exactly where the
            // layout wants it. That means onBlockPlacedBy never runs, so it stays a single block
            // and links itself on its own retry tick — the behaviour every /setblock sign relies on.
            set(world, board, RcmcBlocks.arrivalBoard.getDefaultState());
            placed++;

            BlockPos plate = board.down(3);
            if (world.isAirBlock(plate)) {
                set(world, plate, RcmcBlocks.stationSign.getDefaultState());
                placed++;
            }
            BlockPos speaker = board.north(2);
            if (world.isAirBlock(speaker)) {
                set(world, speaker, RcmcBlocks.stationSpeaker.getDefaultState());
                placed++;
            }
        }
        return placed;
    }

    /**
     * Flag 2 on purpose: send the change to clients, but skip the neighbour-notification cascade.
     *
     * <p>This places on the order of a hundred thousand blocks in one command, and the default flag
     * 3 would fire a block update for each of them — a cascade that costs far more than the placing
     * does and that nothing here needs, since the fabric is inert stone, gravel and decking.</p>
     */
    private static void set(World world, BlockPos pos, IBlockState state) {
        if (pos.getY() < 0 || pos.getY() >= world.getHeight()) {
            return;
        }
        world.setBlockState(pos, state, 2);
    }
}
