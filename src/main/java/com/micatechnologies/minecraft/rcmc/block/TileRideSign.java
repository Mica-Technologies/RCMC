package com.micatechnologies.minecraft.rcmc.block;

import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideController;
import com.micatechnologies.minecraft.rcmc.world.CoasterStations;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import com.micatechnologies.minecraft.rcmc.world.RideRatings;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;

/**
 * What a ride sign shows: the ride's name, whether it is open, and its rating.
 *
 * <p>Linked to the nearest coaster station, like an operator panel. Coaster ride state lives only
 * on the server, so the server works out what the sign says every couple of seconds — the rating
 * from {@link RideRatings}, cached until the ride changes — and sends it to clients only when it
 * has changed. The renderer draws whatever arrived.</p>
 */
public class TileRideSign extends TileEntity implements ITickable {

    public static final int UNLINKED = -1;

    /** How far a sign looks for its station, in blocks. */
    public static final double LINK_RANGE = 32.0D;

    /** Ticks between refreshes. */
    private static final int REFRESH = 40;

    private int linkedSection = UNLINKED;
    private float facingDegrees;
    private String name = "";
    private String status = "";
    private boolean rated;
    private double excitement;
    private double intensity;
    private double nausea;
    private double topSpeed;
    private double length;
    private int inversions;
    private boolean safe;
    private int countdown;

    public int linkedSection() {
        return linkedSection;
    }

    public float facingDegrees() {
        return facingDegrees;
    }

    public void setFacingDegrees(float degrees) {
        facingDegrees = degrees;
        push();
    }

    public String name() {
        return name;
    }

    public String status() {
        return status;
    }

    public boolean isRated() {
        return rated;
    }

    public double excitement() {
        return excitement;
    }

    public double intensity() {
        return intensity;
    }

    public double nausea() {
        return nausea;
    }

    public double topSpeed() {
        return topSpeed;
    }

    public double length() {
        return length;
    }

    public int inversions() {
        return inversions;
    }

    public boolean isSafe() {
        return safe;
    }

    /** Links to the nearest coaster station, and shows it at once. Server side. */
    public void linkToNearestStation() {
        RcmcWorldState state = world == null || world.isRemote ? null : RcmcWorldState.of(world);
        if (state == null) {
            return;
        }
        StationPlatform station = CoasterStations.nearest(state, pos, LINK_RANGE);
        linkedSection = station == null ? UNLINKED : station.sectionId();
        markDirty();
        refresh(state);
    }

    @Override
    public void update() {
        if (world == null || world.isRemote || ++countdown < REFRESH) {
            return;
        }
        countdown = 0;
        RcmcWorldState state = RcmcWorldState.of(world);
        if (state == null) {
            return;
        }
        if (linkedSection == UNLINKED || CoasterStations.stationOf(state, linkedSection) == null) {
            linkToNearestStation();
            return;
        }
        refresh(state);
    }

    /** Works out what the sign says, and sends it on if it changed. */
    private void refresh(RcmcWorldState state) {
        String newName = "";
        String newStatus = "NO RIDE";
        boolean newRated = false;
        double e = 0.0D;
        double i = 0.0D;
        double n = 0.0D;
        double speed = 0.0D;
        double len = 0.0D;
        int inv = 0;
        boolean ok = false;
        if (linkedSection != UNLINKED && CoasterStations.stationOf(state, linkedSection) != null) {
            RideController ride = state.rides().get(state.rides().home(linkedSection));
            newName = ride == null || ride.name().isEmpty() ? "Coaster #" + linkedSection : ride.name();
            newStatus = ride == null ? "OPEN"
                : ride.isEmergencyStopped() ? "STOPPED"
                : ride.state() == RideController.State.OPEN ? "OPEN"
                : ride.state() == RideController.State.TESTING ? "TESTING" : "CLOSED";
            RideRatings.Entry rating = RideRatings.of(state, linkedSection);
            if (rating != null) {
                newRated = true;
                e = rating.rating.excitement;
                i = rating.rating.intensity;
                n = rating.rating.nausea;
                speed = rating.stats.maxSpeedBlocksPerSecond;
                len = rating.stats.totalLengthBlocks;
                inv = rating.stats.inversionCount;
                ok = rating.rating.safety.safe;
            }
        }
        boolean changed = !newName.equals(name) || !newStatus.equals(status) || newRated != rated
            || e != excitement || i != intensity || n != nausea || speed != topSpeed || len != length
            || inv != inversions || ok != safe;
        if (changed) {
            name = newName;
            status = newStatus;
            rated = newRated;
            excitement = e;
            intensity = i;
            nausea = n;
            topSpeed = speed;
            length = len;
            inversions = inv;
            safe = ok;
            push();
        }
    }

    private void push() {
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState blockState = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, blockState, blockState, 3);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        linkedSection = compound.hasKey("Section") ? compound.getInteger("Section") : UNLINKED;
        facingDegrees = compound.getFloat("Facing");
        name = compound.getString("Name");
        status = compound.getString("Status");
        rated = compound.getBoolean("Rated");
        excitement = compound.getDouble("Excitement");
        intensity = compound.getDouble("Intensity");
        nausea = compound.getDouble("Nausea");
        topSpeed = compound.getDouble("TopSpeed");
        length = compound.getDouble("Length");
        inversions = compound.getInteger("Inversions");
        safe = compound.getBoolean("Safe");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("Section", linkedSection);
        compound.setFloat("Facing", facingDegrees);
        compound.setString("Name", name);
        compound.setString("Status", status);
        compound.setBoolean("Rated", rated);
        compound.setDouble("Excitement", excitement);
        compound.setDouble("Intensity", intensity);
        compound.setDouble("Nausea", nausea);
        compound.setDouble("TopSpeed", topSpeed);
        compound.setDouble("Length", length);
        compound.setInteger("Inversions", inversions);
        compound.setBoolean("Safe", safe);
        return compound;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(super.getUpdateTag());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }
}
