package com.micatechnologies.minecraft.rcmc.track.storage;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.physics.element.BrakeRun;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.DriveTyres;
import com.micatechnologies.minecraft.rcmc.physics.element.LaunchTrack;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * NBT persistence for ride elements.
 *
 * <p>Separate from {@link TrackCodec} because elements are separate data with their own lifetime —
 * a section can be re-cut without disturbing the lift on it, and an element can be retuned without
 * touching geometry — but it shares the same versioning discipline for the same reason.</p>
 *
 * <p>Elements are stored by an explicit type tag rather than by class name. A class name in a save
 * file makes every future rename a migration, and makes the save format an accidental part of the
 * package layout.</p>
 */
public final class ElementCodec {

    /**
     * Format version of element data.
     *
     * <p>History:</p>
     * <ul>
     *   <li>1 — initial: chain lift, launch, brake run, station, drive tyres.</li>
     * </ul>
     */
    public static final int DATA_VERSION = 1;

    private static final String KEY_VERSION = "ElementVersion";
    private static final String KEY_ELEMENTS = "Elements";
    private static final String KEY_TYPE = "Type";
    private static final String KEY_SECTION = "Section";
    private static final String KEY_START = "Start";
    private static final String KEY_END = "End";

    // Per-type parameters. Named generically where two types share a meaning, so a brake's target
    // and a launch's target read the same in a save file and in a debug dump.
    private static final String KEY_SPEED = "Speed";
    private static final String KEY_ACCEL = "Accel";
    private static final String KEY_MODE = "Mode";
    private static final String KEY_STOP = "Stop";
    private static final String KEY_DWELL = "Dwell";
    private static final String KEY_DISPATCH_SPEED = "DispatchSpeed";
    private static final String KEY_DISPATCH_ACCEL = "DispatchAccel";

    private ElementCodec() {
        throw new AssertionError("No instances.");
    }

    public static NBTTagCompound write(RideElementSet set) {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger(KEY_VERSION, DATA_VERSION);
        NBTTagList list = new NBTTagList();
        for (RideElement element : set.elements()) {
            NBTTagCompound tag = writeElement(element);
            if (tag != null) {
                list.appendTag(tag);
            }
        }
        root.setTag(KEY_ELEMENTS, list);
        return root;
    }

    public static RideElementSet read(NBTTagCompound root) {
        RideElementSet set = new RideElementSet();
        if (root == null || !root.hasKey(KEY_VERSION)) {
            return set;
        }
        int version = root.getInteger(KEY_VERSION);
        if (version > DATA_VERSION) {
            throw new IllegalStateException(
                "RCMC ride-element data is version " + version + " but this build understands up to "
                    + DATA_VERSION + ". Update the mod rather than loading this world.");
        }

        NBTTagList list = root.getTagList(KEY_ELEMENTS, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            RideElement element = readElement(list.getCompoundTagAt(i));
            if (element != null) {
                set.add(element);
            }
        }
        return set;
    }

    /**
     * The type tag used for {@code element} in save data, or {@code null} if it has none.
     *
     * <p>Public so the render-sync packet uses the same names as the save file. Two independent
     * lists of type strings would drift, and the symptom would be ride hardware that saves
     * correctly but draws as the wrong thing.</p>
     */
    public static String typeOf(RideElement element) {
        if (element instanceof ChainLift) {
            return "chain_lift";
        }
        if (element instanceof LaunchTrack) {
            return "launch";
        }
        if (element instanceof BrakeRun) {
            return "brake";
        }
        if (element instanceof StationPlatform) {
            return "station";
        }
        if (element instanceof DriveTyres) {
            return "drive_tyres";
        }
        return null;
    }

    private static NBTTagCompound writeElement(RideElement element) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(KEY_SECTION, element.sectionId());
        tag.setDouble(KEY_START, element.startDistance());
        tag.setDouble(KEY_END, element.endDistance());

        if (element instanceof ChainLift) {
            tag.setString(KEY_TYPE, "chain_lift");
            tag.setDouble(KEY_SPEED, ((ChainLift) element).chainSpeed());
            tag.setDouble(KEY_ACCEL, ((ChainLift) element).maxAcceleration());
        }
        else if (element instanceof LaunchTrack) {
            LaunchTrack launch = (LaunchTrack) element;
            if (Double.isNaN(launch.constantAcceleration())) {
                // A custom acceleration profile is a function, and a function cannot be written to
                // NBT. Dropping it is the honest outcome — writing it back as a constant launch
                // would silently change how the ride behaves after a restart.
                return null;
            }
            tag.setString(KEY_TYPE, "launch");
            tag.setDouble(KEY_SPEED, launch.targetSpeed());
            tag.setDouble(KEY_ACCEL, launch.constantAcceleration());
        }
        else if (element instanceof BrakeRun) {
            tag.setString(KEY_TYPE, "brake");
            tag.setDouble(KEY_SPEED, ((BrakeRun) element).targetSpeed());
            tag.setDouble(KEY_ACCEL, ((BrakeRun) element).deceleration());
            tag.setString(KEY_MODE, ((BrakeRun) element).mode().name());
        }
        else if (element instanceof StationPlatform) {
            StationPlatform station = (StationPlatform) element;
            tag.setString(KEY_TYPE, "station");
            tag.setDouble(KEY_STOP, station.stopDistance());
            tag.setDouble(KEY_ACCEL, station.brakeDeceleration());
            tag.setInteger(KEY_DWELL, station.dwellTicks());
            tag.setDouble(KEY_DISPATCH_ACCEL, station.dispatchAcceleration());
            tag.setDouble(KEY_DISPATCH_SPEED, station.dispatchSpeed());
        }
        else if (element instanceof DriveTyres) {
            tag.setString(KEY_TYPE, "drive_tyres");
            tag.setDouble(KEY_SPEED, ((DriveTyres) element).driveSpeed());
            tag.setDouble(KEY_ACCEL, ((DriveTyres) element).maxAcceleration());
        }
        else {
            // An element type with no persistence mapping is dropped rather than silently written
            // as something else. Returning null makes that visible in the element count on reload
            // instead of producing a wrong element.
            return null;
        }
        return tag;
    }

    private static RideElement readElement(NBTTagCompound tag) {
        int section = tag.getInteger(KEY_SECTION);
        double start = tag.getDouble(KEY_START);
        double end = tag.getDouble(KEY_END);
        double tick = RcmcConstants.SECONDS_PER_TICK;

        switch (tag.getString(KEY_TYPE)) {
            case "chain_lift":
                return new ChainLift(section, start, end,
                    tag.getDouble(KEY_SPEED), tag.getDouble(KEY_ACCEL), tick);
            case "launch":
                return new LaunchTrack(section, start, end,
                    tag.getDouble(KEY_SPEED), tag.getDouble(KEY_ACCEL));
            case "brake":
                return new BrakeRun(section, start, end, tag.getDouble(KEY_SPEED),
                    tag.getDouble(KEY_ACCEL), BrakeRun.Mode.valueOf(tag.getString(KEY_MODE)), tick);
            case "station":
                return new StationPlatform(section, start, end, tag.getDouble(KEY_STOP),
                    tag.getDouble(KEY_ACCEL), tag.getInteger(KEY_DWELL),
                    tag.getDouble(KEY_DISPATCH_ACCEL), tag.getDouble(KEY_DISPATCH_SPEED), tick);
            case "drive_tyres":
                return new DriveTyres(section, start, end,
                    tag.getDouble(KEY_SPEED), tag.getDouble(KEY_ACCEL), tick);
            default:
                // Unknown type — from a newer version, or a removed one. Skip it rather than
                // aborting the whole load and taking the rest of the park with it.
                return null;
        }
    }
}
