package com.micatechnologies.minecraft.rcmc.sound;

import com.micatechnologies.minecraft.rcmc.net.PacketStationAnnounce;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
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
 * the train's lead car through {@code World.playSound(null, ...)}, which broadcasts to every client
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

    /** trainId -> the phase it was in last tick, so transitions can be spotted. */
    private final Map<Integer, TransitStopController.Phase> lastPhase = new HashMap<>();

    /** trainId -> whether its warning chime has already played for the stop it is at. */
    private final Map<Integer, Boolean> chimed = new HashMap<>();

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
            TransitStopController.Phase previous = lastPhase.put(trainId, phase);

            Train train = trains.train(trainId);
            if (train == null) {
                continue;
            }

            if (phase == TransitStopController.Phase.BOARDING) {
                // Chime once, timed so it finishes as the doors begin to move.
                if (!Boolean.TRUE.equals(chimed.get(trainId))
                    && controller.phaseTicksRemaining() <= CHIME_LEAD_TICKS) {
                    play(world, train, network, RcmcSounds.METRO_DOOR_CHIME);
                    chimed.put(trainId, Boolean.TRUE);
                }
            } else if (phase != TransitStopController.Phase.DOORS_CLOSING) {
                chimed.remove(trainId);
            }

            if (previous == null || previous == phase) {
                continue;
            }
            if (phase == TransitStopController.Phase.DOORS_OPENING
                && previous == TransitStopController.Phase.APPROACHING) {
                // Arrived: the doors are starting to open. Ding now, then say "This is <here>".
                // currentStopIndex is still the station being berthed at.
                play(world, train, network, RcmcSounds.METRO_ANNOUNCE_CHIME);
                pending.add(new PendingAnnouncement(trainId, CHIME_TO_SPEECH_TICKS,
                    TransitSignText.arrivalAnnouncement(stopName(service, service.currentStopIndex())),
                    false));
            }
            if (phase == TransitStopController.Phase.DOORS_CLOSING) {
                play(world, train, network, RcmcSounds.METRO_DOOR_CLOSE);
            } else if (previous == TransitStopController.Phase.DOORS_CLOSING
                && phase == TransitStopController.Phase.APPROACHING) {
                // Doors shut, brakes off, and away: the release is the sound of departure.
                play(world, train, network, RcmcSounds.METRO_BRAKE_RELEASE);
                // Two seconds later, announce the stop now being run to. The service has already
                // advanced its target this tick, so currentStopIndex names the next stop. Capture
                // the name now — the chime-then-speech is scheduled, not spoken here.
                pending.add(new PendingAnnouncement(trainId, DEPART_ANNOUNCE_DELAY_TICKS,
                    TransitSignText.nextStopAnnouncement(stopName(service, service.currentStopIndex())),
                    true));
            }
        }
        // Services that ended take their sound state with them.
        for (Iterator<Integer> it = lastPhase.keySet().iterator(); it.hasNext(); ) {
            Integer id = it.next();
            if (transit.serviceFor(id) == null) {
                it.remove();
                chimed.remove(id);
            }
        }
        pending.removeIf(p -> transit.serviceFor(p.trainId) == null);
    }

    /** The human-readable name of a line's station by index, defensive against a bad index. */
    private static String stopName(LineService service, int stopIndex) {
        TransitLine line = service.line();
        if (stopIndex < 0 || stopIndex >= line.stationCount()) {
            return "the next station";
        }
        return line.station(stopIndex).name();
    }

    /** Counts down queued announcements and, when one comes due, chimes and/or speaks it. */
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
                if (reschedule == null) {
                    reschedule = new ArrayList<>();
                }
                reschedule.add(new PendingAnnouncement(p.trainId, CHIME_TO_SPEECH_TICKS, p.text, false));
            } else {
                announce(world, train, network, p.text);
            }
        }
        if (reschedule != null) {
            pending.addAll(reschedule);
        }
    }

    private static void play(World world, Train train, TrackNetwork network, SoundEvent sound) {
        TrackFrame frame = train.frameOfCar(network, 0);
        if (frame == null) {
            return;
        }
        // null player: heard by everyone in range including whoever is aboard.
        world.playSound(null, frame.position.x, frame.position.y, frame.position.z,
            sound, SoundCategory.NEUTRAL, VOLUME, 1.0F);
    }

    /** Sends an in-car announcement to every player riding or near the train. */
    private static void announce(World world, Train train, TrackNetwork network, String text) {
        TrackFrame frame = train.frameOfCar(network, 0);
        if (frame == null) {
            return;
        }
        double rangeSq = ANNOUNCE_RANGE * ANNOUNCE_RANGE;
        for (EntityPlayer player : world.playerEntities) {
            if (!(player instanceof EntityPlayerMP)) {
                continue;
            }
            double dx = player.posX - frame.position.x;
            double dy = player.posY - frame.position.y;
            double dz = player.posZ - frame.position.z;
            if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                // Empty voice: the client uses the TTS engine's default, as the platform speaker does.
                RcmcNetwork.sendTo(new PacketStationAnnounce(text, ""), (EntityPlayerMP) player);
            }
        }
    }

    /**
     * An in-car announcement on a timer. {@code chimeFirst} distinguishes the two stages: queued
     * with it set, the entry fires the ding and re-queues itself (without it) for the speech; queued
     * without it, the entry speaks. Arrival goes straight to the speech stage because its ding has
     * already played by the time the entry is queued.
     */
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
