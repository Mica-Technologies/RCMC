package com.micatechnologies.minecraft.rcmc.block;

import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideController;
import com.micatechnologies.minecraft.rcmc.world.CoasterStations;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Opens and shuts an air gate with its station.
 *
 * <p>Linked, like an operator panel, to the coaster station nearest where it was placed — by
 * section id rather than by reference, so it finds the station again after a reload or an edit.
 * Every tick it asks {@link CoasterStations#gatesOpen} whether it should stand open; while it is
 * open it holds the ride's dispatch ({@link RideController#holdForGates}), and the hold outlasts
 * it by {@link CoasterStations#GATES_SHUT_FOR} ticks after it shuts.</p>
 */
public class TileAirGate extends TileEntity implements ITickable {

    /** Not linked to any station. */
    public static final int UNLINKED = -1;

    /** How far a gate looks for its station, in blocks. */
    public static final double LINK_RANGE = 16.0D;

    private static final String KEY_SECTION = "Section";

    private int linkedSection = UNLINKED;

    public int linkedSection() {
        return linkedSection;
    }

    /** Links to {@code sectionId}'s ride directly — the platform builder knows which it is. */
    public void link(int sectionId) {
        if (linkedSection != sectionId) {
            linkedSection = sectionId;
            markDirty();
        }
    }

    /** Links to the coaster station nearest this gate. Server side. */
    public void linkToNearestStation() {
        RcmcWorldState state = world == null || world.isRemote ? null : RcmcWorldState.of(world);
        if (state == null) {
            return;
        }
        StationPlatform station = CoasterStations.nearest(state, pos, LINK_RANGE);
        link(station == null ? UNLINKED : station.sectionId());
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        RcmcWorldState state = RcmcWorldState.of(world);
        if (state == null) {
            return;
        }
        StationPlatform station = linkedSection == UNLINKED ? null
            : CoasterStations.stationOf(state, linkedSection);
        if (station == null && Math.floorMod(world.getTotalWorldTime() + pos.hashCode(), 40L) == 0L) {
            // Built before its track, or its station was removed: keep looking, slowly.
            linkToNearestStation();
            station = linkedSection == UNLINKED ? null : CoasterStations.stationOf(state, linkedSection);
        }
        boolean open = station != null && CoasterStations.gatesOpen(state, station);
        if (open) {
            RideController ride = state.rides().get(station.sectionId());
            if (ride != null) {
                ride.holdForGates(CoasterStations.GATE_HOLD);
            }
        }
        IBlockState here = world.getBlockState(pos);
        if (here.getBlock() instanceof BlockAirGate && here.getValue(BlockAirGate.OPEN) != open) {
            world.setBlockState(pos, here.withProperty(BlockAirGate.OPEN, open), 3);
            // One gate in four sounds: a platform's gates move together, and forty iron doors at
            // once would drown everything else and use up the sound channels.
            if (Math.floorMod(pos.getX() + pos.getZ(), 4) == 0) {
                world.playSound(null, pos, open ? net.minecraft.init.SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN
                        : net.minecraft.init.SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE,
                    net.minecraft.util.SoundCategory.BLOCKS, 0.7F, 0.85F);
            }
        }
    }

    /** Opening and shutting changes the state, not the block: the gate keeps its link. */
    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        linkedSection = compound.hasKey(KEY_SECTION) ? compound.getInteger(KEY_SECTION) : UNLINKED;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger(KEY_SECTION, linkedSection);
        return compound;
    }
}
