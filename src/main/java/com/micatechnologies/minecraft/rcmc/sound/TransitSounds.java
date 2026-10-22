package com.micatechnologies.minecraft.rcmc.sound;

import com.micatechnologies.minecraft.rcmc.entity.EntityCoasterCar;
import com.micatechnologies.minecraft.rcmc.net.PacketStationAnnounce;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.transit.ArrivalEstimator;
import com.micatechnologies.minecraft.rcmc.physics.transit.DoorSide;
import com.micatechnologies.minecraft.rcmc.physics.transit.LineService;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSignText;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitStopController;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;

/**
 * Plays a metro's door and brake sounds, and speaks its in-car announcements, by watching its stop
 * controller change phase.
 *
 * <p><b>Why an observer rather than a call from the controller.</b> {@code TransitStopController}
 * is pure Java with no Minecraft types — that is what makes the whole door cycle unit-testable on a
 * bare JVM — so it cannot play a sound, and giving it a listener interface just to route one back
 * out would put a callback in the middle of the simulation for the sake of decoration. Watching the
 * phase from outside costs one enum comparison per service per tick and keeps the physics
 * untouched.</p>
 *
 * <p>Server-side only. Sound <em>effects</em> (the chimes, door close, brake release) are emitted at
 * every car of the train through {@code World.playSound(null, ...)}, which broadcasts to every client
 * in range and lets each one attenuate it — the sounds are mono precisely so that works. Spoken
 * <em>announcements</em> ("Next stop: …", "This is …") go the same way the platform speaker sends
 * its calls: a {@link PacketStationAnnounce} of text to each nearby player, which the client turns
 * into TTS (or a subtitle). A single ding — {@link RcmcSounds#METRO_ANNOUNCE_CHIME}, the door
 * chime's own voice struck once — precedes each announcement.</p>
 */
public final class TransitSounds {

    /**
     * How long before the doors start closing the warning chime plays, in ticks.
     *
     * <p>The chime is 1.75 s long and is a warning, so it has to finish — or nearly — before the
     * leaves actually move; a "doors closing" tone that lands after they have shut is just noise.
     * 35 ticks is the chime's own length plus a moment.</p>
     */
    private static final int CHIME_LEAD_TICKS = 35;

    /**
     * Ticks after departure before the "Next stop" announcement fires — two seconds, as the owner
     * asked. Measured from the moment the doors finish closing and the service rolls back to
     * {@link TransitStopController.Phase#APPROACHING} the next stop.
     */
    private static final int DEPART_ANNOUNCE_DELAY_TICKS = 40;

    /**
     * Ticks between the announcement chime starting and the speech beginning, so the single ding
     * has rung out first — the chime is 0.85 s, this is that plus a breath.
     */
    private static final int CHIME_TO_SPEECH_TICKS = 18;

    /** How far a player may be from a train and still hear its in-car announcements, in blocks. */
    private static final double ANNOUNCE_RANGE = 24.0D;

    private static final float VOLUME = 0.7F;

    /**
     * trainId -> what has been played for the stop visit it is at, or last berthed at.
     *
     * <p><b>State, not transitions.</b> These used to fire only on an exact phase edge — door close
     * on entering DOORS_CLOSING, the arrival call only on APPROACHING to DOORS_OPENING — and any
     * other route through the cycle skipped them: a service rebuilt after a restart or chunk reload
     * has no previous phase, and a hold or turnback reaches the berth another way. Each event is now
     * owed once per visit, the visit being the controller's {@code stopsServed} count, and plays on
     * the first tick it is seen to be owed.</p>
     */
    private final Map<Integer, Visit> visits = new HashMap<>();

    /**
     * trainId -> the stop index its "Entering …" call has already been made for.
     *
     * <p>Keyed by stop rather than by a boolean so the call fires once per approach and rearms
     * itself for the next station without needing to spot a departure.</p>
     */
    private final Map<Integer, Integer> enteringAnnounced = new HashMap<>();

    /**
     * How long before berthing the "Entering …" call goes out, in seconds.
     *
     * <p>Long enough that a standing passenger can cross the saloon to the door before it opens,
     * which is the entire point of telling them which side it will be. Timed against the estimated
     * arrival rather than a distance, for the reason set out in {@code ArrivalEstimator}: a
     * distance is not an amount of time.</p>
     */
    private static final double ENTERING_LEAD_SECONDS = 9.0D;

    /** In-car announcements waiting on their timer — see {@link PendingAnnouncement}. */
    private final List<PendingAnnouncement> pending = new ArrayList<>();

    /**
     * Call once per tick, after {@code TransitSystem.beginTick} and the train tick, so the phases
     * read are the ones that have just been advanced.
     */
    public void tick(World world, TransitSystem transit, TrainManager trains, TrackNetwork network) {
        if (world == null || world.isRemote || network == null) {
            return;
        }
        // Fire anything whose timer has come due first, so an announcement scheduled this tick
        // waits a full delay rather than being counted down the moment it is queued.
        firePending(world, trains, network);

        for (Map.Entry<Integer, LineService> entry : transit.services().entrySet()) {
            int trainId = entry.getKey();
            LineService service = entry.getValue();
            TransitStopController controller = service.controller();
            TransitStopController.Phase phase = controller.phase();
            Train train = trains.train(trainId);
            if (train == null) {
                continue;
            }

            int visitIndex = controller.stopsServed();
            Visit visit = visits.get(trainId);
            if (phase != TransitStopController.Phase.APPROACHING) {
                if (visit == null || visit.index != visitIndex) {
                    visit = new Visit(visitIndex);
                    visits.put(trainId, visit);
                }
                if (!visit.arrived) {
                    visit.arrived = true;
                    // Berthed: the "Entering …" call has served its purpose, and clearing it here
                    // is what rearms it for the next station.
                    enteringAnnounced.remove(trainId);
                    // Ding now, then say "This is <here>". currentStopIndex is still the station
                    // being berthed at.
                    play(world, train, network, RcmcSounds.METRO_ANNOUNCE_CHIME);
                    String arrival = TransitSignText.arrivalAnnouncement(
                        stopName(service, service.currentStopIndex()));
                    TtsTrace.stage("CHIME", "train#" + trainId, "", arrival);
                    pending.add(new PendingAnnouncement(trainId, CHIME_TO_SPEECH_TICKS, arrival, false));
                }
                // Chime once, timed so it finishes as the doors begin to move.
                if (!visit.chimed && phase == TransitStopController.Phase.BOARDING
                    && controller.phaseTicksRemaining() <= CHIME_LEAD_TICKS) {
                    visit.chimed = true;
                    play(world, train, network, RcmcSounds.METRO_DOOR_CHIME);
                }
                if (!visit.closed && phase == TransitStopController.Phase.DOORS_CLOSING) {
                    visit.closed = true;
                    if (!visit.chimed) {
                        TtsTrace.stage("SKIP", "train#" + trainId, "reason=no-boarding-window",
                            "door chime");
                    }
                    play(world, train, network, RcmcSounds.METRO_DOOR_CLOSE);
                }
            } else if (visit != null && !visit.departed && visit.index < visitIndex) {
                visit.departed = true;
                // Doors shut, brakes off, and away: the release is the sound of departure.
                play(world, train, network, RcmcSounds.METRO_BRAKE_RELEASE);
                // Two seconds later, announce the stop now being run to. The service has already
                // advanced its target, so currentStopIndex names the next stop. Capture the name
                // now — the chime-then-speech is scheduled, not spoken here.
                pending.add(new PendingAnnouncement(trainId, DEPART_ANNOUNCE_DELAY_TICKS,
                    TransitSignText.nextStopAnnouncement(stopName(service, service.currentStopIndex())),
                    true));
            }

            announceEntering(world, transit, trainId, service, train, network);
        }
        // Services that ended take their sound state with them.
        visits.keySet().removeIf(id -> transit.serviceFor(id) == null);
        pending.removeIf(p -> transit.serviceFor(p.trainId) == null);
    }

    /** The human-readable name of a line's station by index, defensive against a bad index. */
    private static String stopName(LineService service, int stopIndex) {
        TransitLine line = service.line();
        if (stopIndex < 0 || stopIndex >= line.stationCount()) {
            return "the next station";
        }
        return line.station(stopIndex).spokenName();
    }

    /** Counts down queued announcements and, when one comes due, chimes and/or speaks it. */
    /**
     * Says "Entering X. The doors will open on the left." as a train runs into a station.
     *
     * <p>Before arrival, not on it: a passenger told which door to use as it opens has been told
     * too late to walk to it. Fired once per approach, on the same arrival-time estimate the
     * platform speaker uses, so it lands with time to cross the car.</p>
     *
     * <p>The side is converted to the <em>rider's</em> left and right here — this is the one place
     * that matters, because it is the only one talking to a person. The renderer and the snapshot
     * both keep it track-relative.</p>
     */
    private void announceEntering(World world, TransitSystem transit, int trainId,
                                  LineService service, Train train, TrackNetwork network) {
        if (service.controller().phase() != TransitStopController.Phase.APPROACHING) {
            return;
        }
        int stopIndex = service.currentStopIndex();
        if (Integer.valueOf(stopIndex).equals(enteringAnnounced.get(trainId))) {
            return;
        }
        double seconds = ArrivalEstimator.secondsToArrival(service.distanceToNextStop(),
            train.velocity(), service.controller().serviceBrakeDeceleration());
        if (seconds > ENTERING_LEAD_SECONDS) {
            return;
        }
        enteringAnnounced.put(trainId, stopIndex);
        DoorSide side = transit.doorSideFor(service).asSeenFrom(service.facing());
        play(world, train, network, RcmcSounds.METRO_ANNOUNCE_CHIME);
        String entering = TransitSignText.enteringAnnouncement(stopName(service, stopIndex), side);
        TtsTrace.stage("CHIME", "train#" + trainId, "", entering);
        pending.add(new PendingAnnouncement(trainId, CHIME_TO_SPEECH_TICKS, entering, false));
    }

    private void firePending(World world, TrainManager trains, TrackNetwork network) {
        if (pending.isEmpty()) {
            return;
        }
        List<PendingAnnouncement> reschedule = null;
        for (Iterator<PendingAnnouncement> it = pending.iterator(); it.hasNext(); ) {
            PendingAnnouncement p = it.next();
            if (--p.ticks > 0) {
                continue;
            }
            it.remove();
            Train train = trains.train(p.trainId);
            if (train == null) {
                continue; // train gone; nothing to announce and nowhere to play it
            }
            if (p.chimeFirst) {
                // The delay has elapsed: ding now, then speak once the ding has rung out.
                play(world, train, network, RcmcSounds.METRO_ANNOUNCE_CHIME);
                TtsTrace.stage("CHIME", "train#" + p.trainId, "", p.text);
                if (reschedule == null) {
                    reschedule = new ArrayList<>();
                }
                reschedule.add(new PendingAnnouncement(p.trainId, CHIME_TO_SPEECH_TICKS, p.text, false));
            } else {
                announce(world, train, network, p.trainId, p.text);
            }
        }
        if (reschedule != null) {
            pending.addAll(reschedule);
        }
    }

    /**
     * Plays a sound at every car, not only the lead one: a five-car train is a hundred blocks long,
     * and a rider in the last car or a player at the far end of the platform heard nothing from a
     * sound placed at the front.
     */
    private static void play(World world, Train train, TrackNetwork network, SoundEvent sound) {
        for (int car = 0; car < train.spec().carCount(); car++) {
            TrackFrame frame = train.frameOfCar(network, car);
            if (frame == null) {
                continue;
            }
            // null player: heard by everyone in range including whoever is aboard.
            world.playSound(null, frame.position.x, frame.position.y, frame.position.z,
                sound, SoundCategory.NEUTRAL, VOLUME, 1.0F);
        }
    }

    /**
     * Sends an in-car announcement to every player riding the train or near any of its cars —
     * measured from the nearest car, not the lead one, so the back of a long train hears it too.
     */
    private static void announce(World world, Train train, TrackNetwork network, int trainId,
                                 String text) {
        List<TrackFrame> frames = new ArrayList<>();
        for (int car = 0; car < train.spec().carCount(); car++) {
            TrackFrame frame = train.frameOfCar(network, car);
            if (frame != null) {
                frames.add(frame);
            }
        }
        double rangeSq = ANNOUNCE_RANGE * ANNOUNCE_RANGE;
        for (EntityPlayer player : world.playerEntities) {
            if (!(player instanceof EntityPlayerMP)) {
                continue;
            }
            boolean hears = player.getRidingEntity() instanceof EntityCoasterCar
                && ((EntityCoasterCar) player.getRidingEntity()).trainId() == trainId;
            for (int i = 0; !hears && i < frames.size(); i++) {
                TrackFrame frame = frames.get(i);
                double dx = player.posX - frame.position.x;
                double dy = player.posY - frame.position.y;
                double dz = player.posZ - frame.position.z;
                hears = dx * dx + dy * dy + dz * dz <= rangeSq;
            }
            if (hears) {
                // Empty voice: the client uses the TTS engine's default, as the platform speaker does.
                RcmcNetwork.sendTo(new PacketStationAnnounce(text, ""), (EntityPlayerMP) player);
                TtsTrace.stage("SEND", "train#" + trainId, "to=" + player.getName(), text);
            }
        }
    }

    /**
     * An in-car announcement on a timer. {@code chimeFirst} distinguishes the two stages: queued
     * with it set, the entry fires the ding and re-queues itself (without it) for the speech; queued
     * without it, the entry speaks. Arrival goes straight to the speech stage because its ding has
     * already played by the time the entry is queued.
     */
    /** What has been played for one stop visit; see {@code visits}. */
    private static final class Visit {
        final int index;
        boolean arrived;
        boolean chimed;
        boolean closed;
        boolean departed;

        Visit(int index) {
            this.index = index;
        }
    }

    private static final class PendingAnnouncement {
        final int trainId;
        int ticks;
        final String text;
        final boolean chimeFirst;

        PendingAnnouncement(int trainId, int ticks, String text, boolean chimeFirst) {
            this.trainId = trainId;
            this.ticks = ticks;
            this.text = text;
            this.chimeFirst = chimeFirst;
        }
    }
}
