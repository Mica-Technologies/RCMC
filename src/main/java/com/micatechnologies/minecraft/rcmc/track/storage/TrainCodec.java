package com.micatechnologies.minecraft.rcmc.track.storage;

import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.transit.LineService;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitDrives;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Converts running trains, and the services they are working, to and from NBT.
 *
 * <p>Before this existed, trains were the one thing in a park that did not survive a restart. Track,
 * ride hardware, stations, lines and signalling all persisted; the trains running on them vanished,
 * taking every metro service with them. A saved line that is not there tomorrow does not read as
 * "trains are runtime state" — it reads as the save being broken.</p>
 *
 * <h2>What is written, and what is deliberately not</h2>
 *
 * <p><b>Written:</b> the train's identity, its spec (including paint), where it is, and how fast it
 * is going. Plus, per train in service, the line it works and its cruise speed.</p>
 *
 * <p><b>Not written, because it is re-derived within one tick of loading:</b></p>
 *
 * <ul>
 *   <li><b>Fault status.</b> {@code DEAD_END} and {@code VALLEYED} are functions of where the train
 *       is, how fast it is going, and what track is under it — all of which <em>are</em> saved. A
 *       valleyed train reloads at rest in the same dip and re-latches on its first tick. Persisting
 *       the flag would mean adding a setter to {@link Train} that exists only for this codec, and
 *       would let a stale fault outlive the condition that caused it.</li>
 *   <li><b>Held.</b> Set every tick by whatever is holding the train — a station element, a stop
 *       controller. A restored train is held again the moment that thing next runs.</li>
 *   <li><b>The integrator.</b> Built from {@code RcmcConfig}, which is server-authoritative and
 *       loaded independently. Writing gravity into a train would let a train restore with different
 *       physics from the one beside it after a config change.</li>
 *   <li><b>Stop-controller phase and timers.</b> See {@link #readServices} — a service resumes by
 *       re-entering service from where the train stands, not by restoring a half-finished door
 *       cycle.</li>
 * </ul>
 *
 * <p>The seam is the same one {@link TrackCodec} and {@link TransitCodec} use: NBT lives here, and
 * {@code physics} stays free of Minecraft imports so the simulation remains testable on a bare
 * JVM.</p>
 */
public final class TrainCodec {

    /**
     * Train NBT format version.
     *
     * <p>v1 is the first format — before it, trains were simply absent from the save, so an older
     * world reads as "no trains", which is exactly what it had. There is no migration to write.</p>
     *
     * <p>A future version is not refused here, for {@link TransitCodec}'s reason: {@link TrackCodec}
     * version-guards the track these trains run on and refuses first.</p>
     */
    static final int DATA_VERSION = 1;

    private static final String KEY_VERSION = "TrainVersion";
    private static final String KEY_TRAINS = "Trains";
    private static final String KEY_SERVICES = "TrainServices";

    private static final String KEY_ID = "Id";
    private static final String KEY_SECTION = "Section";
    private static final String KEY_DISTANCE = "Distance";
    private static final String KEY_VELOCITY = "Velocity";

    private static final String KEY_CARS = "Cars";
    private static final String KEY_CAR_LENGTH = "CarLength";
    private static final String KEY_COUPLING_GAP = "CouplingGap";
    private static final String KEY_SEATS = "Seats";
    private static final String KEY_CAR_STYLE = "CarStyle";
    private static final String KEY_BODY_COLOUR = "BodyColour";
    private static final String KEY_TRIM_COLOUR = "TrimColour";
    private static final String KEY_SEAT_COLOUR = "SeatColour";

    private static final String KEY_LINE = "Line";
    private static final String KEY_CRUISE = "Cruise";

    private TrainCodec() {
        throw new AssertionError("No instances.");
    }

    /** Writes every train, and every service, into {@code root}. */
    public static void write(TrainManager trains, TransitSystem transit, NBTTagCompound root) {
        root.setInteger(KEY_VERSION, DATA_VERSION);

        NBTTagList trainList = new NBTTagList();
        for (Map.Entry<Integer, Train> entry : trains.asMap().entrySet()) {
            trainList.appendTag(writeTrain(entry.getKey(), entry.getValue()));
        }
        root.setTag(KEY_TRAINS, trainList);

        NBTTagList serviceList = new NBTTagList();
        if (transit != null) {
            for (Map.Entry<Integer, LineService> entry : transit.services().entrySet()) {
                // A service whose train is gone is not written: it would restore as a service with
                // no train, which the reader would have to skip anyway. Dropping it here means the
                // save never contains a state the loader has to defend against.
                if (trains.train(entry.getKey()) == null) {
                    continue;
                }
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger(KEY_ID, entry.getKey());
                tag.setString(KEY_LINE, entry.getValue().line().name());
                tag.setDouble(KEY_CRUISE, entry.getValue().controller().cruiseSpeed());
                serviceList.appendTag(tag);
            }
        }
        root.setTag(KEY_SERVICES, serviceList);
    }

    private static NBTTagCompound writeTrain(int trainId, Train train) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(KEY_ID, trainId);
        tag.setInteger(KEY_SECTION, train.reference().sectionId());
        // Double, not float, throughout — the same reason TrackCodec gives. Distance accumulates
        // along a section thousands of samples long, and a float would shift a train's position by
        // a visible amount on every reload.
        tag.setDouble(KEY_DISTANCE, train.reference().distance());
        tag.setDouble(KEY_VELOCITY, train.velocity());

        TrainSpec spec = train.spec();
        tag.setInteger(KEY_CARS, spec.carCount());
        tag.setDouble(KEY_CAR_LENGTH, spec.carLength());
        tag.setDouble(KEY_COUPLING_GAP, spec.couplingGap());
        tag.setInteger(KEY_SEATS, spec.seatsPerCar());
        tag.setInteger(KEY_CAR_STYLE, spec.carStyle().ordinal());
        tag.setInteger(KEY_BODY_COLOUR, spec.bodyColour());
        tag.setInteger(KEY_TRIM_COLOUR, spec.trimColour());
        tag.setInteger(KEY_SEAT_COLOUR, spec.seatColour());
        return tag;
    }

    /**
     * Reads every train back into a fresh manager.
     *
     * @param integrator the physics to give each restored train — built from the server's config,
     *                   not from the save
     */
    public static TrainManager read(NBTTagCompound root, PhysicsIntegrator integrator) {
        TrainManager trains = new TrainManager();
        if (root == null || !root.hasKey(KEY_TRAINS)) {
            return trains;
        }
        NBTTagList list = root.getTagList(KEY_TRAINS, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            TrainSpec spec = new TrainSpec(
                Math.max(1, tag.getInteger(KEY_CARS)),
                tag.getDouble(KEY_CAR_LENGTH),
                tag.getDouble(KEY_COUPLING_GAP),
                tag.getInteger(KEY_SEATS),
                tag.getInteger(KEY_BODY_COLOUR),
                tag.getInteger(KEY_TRIM_COLOUR),
                tag.getInteger(KEY_SEAT_COLOUR),
                TrainSpec.CarStyle.byOrdinal(tag.getInteger(KEY_CAR_STYLE)));
            Train train = new Train(spec, integrator,
                new TrackRef(tag.getInteger(KEY_SECTION), tag.getDouble(KEY_DISTANCE)),
                tag.getDouble(KEY_VELOCITY));
            trains.add(tag.getInteger(KEY_ID), train);
        }
        return trains;
    }

    /**
     * Puts every saved service back into service, and returns how many resumed.
     *
     * <p><b>A service resumes rather than being restored.</b> Only the line and the cruise speed are
     * saved; everything else — which stop is next, which way the train faces, where it is in the
     * door cycle — comes from {@code enterService}, which derives all of it by walking the track
     * from wherever the train actually stands. That is not a shortcut around serialising a
     * controller's timers: it is more correct than doing so. The track may have been edited while
     * the world was closed, and a restored "next stop is index 4, facing +1" could be pointing at a
     * station that has moved, been renamed, or been deleted. Re-deriving cannot go stale.</p>
     *
     * <p>The visible consequence is small and self-correcting: a train saved berthed with its doors
     * open reloads berthed with them shut, and immediately opens them again. A train saved
     * mid-dwell serves a full dwell. Both are indistinguishable from a train that has just
     * arrived, which is what it now is.</p>
     *
     * <p>A service naming a line that no longer exists, or a train that cannot reach any of that
     * line's stations, is dropped with the train left parked on the track. That is the honest
     * outcome — the line it worked is gone — and it is recoverable with one command.</p>
     */
    public static int readServices(NBTTagCompound root, TrainManager trains, TransitSystem transit,
                                   TrackNetwork network, double tickSeconds) {
        if (root == null || transit == null || network == null || !root.hasKey(KEY_SERVICES)) {
            return 0;
        }
        int resumed = 0;
        NBTTagList list = root.getTagList(KEY_SERVICES, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int trainId = tag.getInteger(KEY_ID);
            Train train = trains.train(trainId);
            if (train == null || transit.line(tag.getString(KEY_LINE)) == null) {
                continue;
            }
            double cruise = tag.getDouble(KEY_CRUISE);
            if (cruise <= 0.0D) {
                continue;
            }
            try {
                transit.enterService(trainId, train, network, tag.getString(KEY_LINE),
                    TransitDrives.metro(cruise, tickSeconds));
                resumed++;
            }
            catch (IllegalArgumentException e) {
                // The train can no longer reach its line — the track under it was changed, or the
                // line's stations were. Leaving it parked and un-serviced is recoverable;
                // aborting the world load over it is not.
            }
        }
        return resumed;
    }
}
