package com.micatechnologies.minecraft.rcmc.block.sign;

import com.micatechnologies.minecraft.rcmc.net.PacketStationAnnounce;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.transit.ArrivalEstimator;
import com.micatechnologies.minecraft.rcmc.physics.transit.LineService;
import com.micatechnologies.minecraft.rcmc.physics.transit.ServiceSnapshot;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSignText;
import com.micatechnologies.minecraft.rcmc.sound.RcmcSounds;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.SoundCategory;

/**
 * A station speaker's brain: it links itself to the nearest station (inherited from
 * {@link TileTransitSignBase}) and, once linked, watches the trains approaching that station and
 * speaks an announcement each time one draws closer.
 *
 * <h3>What it announces, and when</h3>
 *
 * <p>Only trains <b>within three stations</b> — the window the owner asked for — and only on the
 * <em>tick a service crosses into a nearer band</em>, never every tick. A service's "closeness" is
 * an integer: how many stops away it is ({@link ArrivalEstimator}), or a step nearer than zero
 * while it is berthing here. Because that integer changes only when a train actually passes a stop,
 * detecting a decrease fires exactly once per stop — "two stops away", then "one stop away", then
 * "now approaching", then "now arriving" — with no spam in between. When a train departs and its
 * count climbs again, the next approach re-announces from scratch.</p>
 *
 * <p>The words come from {@link TransitSignText#announcement}, the same source the platform boards
 * and in-car signs read, so a speaker can never contradict a screen. The text is sent to every
 * player within earshot; the client turns it into speech (CSM's TTS) or a subtitle.</p>
 */
public class TileStationSpeaker extends TileTransitSignBase {

    /** Trains at most this many stops away are announced — "within three stations". */
    private static final int WINDOW = 2;

    /**
     * A last-resort distance floor, in track blocks.
     *
     * <p>The approach call is timed rather than placed — see {@link #announcementLeadSeconds}. This
     * catches the one case the timing cannot: a train creeping the last few metres into its berth
     * has a tiny speed, so its estimated arrival time is large even though it is plainly about to
     * arrive. Without a floor a slow approach would never announce at all and the first anyone
     * heard would be the "now arriving" call, which is the bug this replaces.</p>
     */
    private static final double NEAR_DISTANCE = 12.0D;

    /**
     * Seconds of speech assumed per character of announcement text.
     *
     * <p>A rough model of a normal speaking rate (about 13 characters a second). The synthesiser
     * will not tell us how long a line takes until it has said it, and the whole point is to start
     * <em>before</em> that — so the length of the sentence is the best predictor available, and it
     * is a good one: "The next inbound Ring train to Parkside is now approaching" genuinely does
     * take longer to say than a short one.</p>
     */
    private static final double SECONDS_PER_CHARACTER = 1.0D / 13.0D;

    /** Bounds on the estimate above, so a freak name cannot make the lead absurd either way. */
    private static final double MIN_SPEECH_SECONDS = 2.0D;
    private static final double MAX_SPEECH_SECONDS = 7.0D;

    /**
     * Extra seconds so the words finish a moment BEFORE the train pulls in, rather than exactly as
     * it does. Announcements that end on the doors opening feel late even when they are not.
     */
    private static final double SETTLE_SECONDS = 1.0D;

    /** How far a player may be from the speaker and still hear it, in blocks. */
    private static final double AUDIBLE_RANGE = 20.0D;

    /**
     * Ticks between the platform chime and the spoken announcement it precedes, so the ding rings
     * out first — the station chime is 0.92 s, this is that plus a breath.
     */
    private static final int CHIME_TO_SPEECH_TICKS = 20;

    private static final float CHIME_VOLUME = 0.8F;

    /**
     * A phase below zero for a train berthing here, so "now arriving" is a step nearer than "now
     * approaching" (phase 0) and still triggers the decrease that fires an announcement.
     */
    private static final int PHASE_ARRIVING = -1;

    /** Last announced closeness per train, so only a genuine approach speaks. Server-side scratch. */
    private final Map<Integer, Integer> lastPhase = new HashMap<>();

    /** Announcements chimed and waiting on their timer before the words are sent. Server-side scratch. */
    private final List<Pending> pending = new ArrayList<>();

    @Override
    public void update() {
        // Relink while unlinked (and nothing else) — the base returns early once linked.
        super.update();
        if (world == null || world.isRemote) {
            return;
        }
        // Drain queued announcements even if the speaker has since unlinked — a chime already rang.
        firePending();
        if (!isLinked()) {
            return;
        }
        announceApproachingTrains();
    }

    private void announceApproachingTrains() {
        RcmcWorldState state = RcmcWorldState.of(world);
        if (state == null) {
            return;
        }
        List<TransitLine> serving = state.transit().linesServing(stationName());
        if (serving.isEmpty()) {
            return;
        }
        List<ServiceSnapshot> snapshots = state.transit().serviceSnapshots();
        Set<Integer> present = new HashSet<>();

        for (TransitLine line : serving) {
            int stationIndex = line.indexOfStation(stationName());
            if (stationIndex < 0) {
                continue;
            }
            for (ServiceSnapshot snapshot : snapshots) {
                if (!snapshot.lineName().equalsIgnoreCase(line.name())) {
                    continue;
                }
                int raw = ArrivalEstimator.stopsAway(line, snapshot.serviceDirection(),
                    snapshot.nextStopIndex(), stationIndex);
                if (raw < 0) {
                    continue;
                }
                present.add(snapshot.trainId());
                // The train is this station's next stop (raw == 0) but not yet due: hold it in the
                // "one stop away" band so "now approaching" waits until the call would land at the
                // right moment. Held for TIME, not distance — see announcementLeadSeconds.
                boolean farHold = raw == 0 && !snapshot.atPlatform()
                    && !isDue(state, line, snapshot);
                int phase;
                if (raw == 0 && snapshot.atPlatform()) {
                    phase = PHASE_ARRIVING;
                } else if (farHold) {
                    phase = 1;
                } else {
                    phase = raw;
                }
                Integer previous = lastPhase.put(snapshot.trainId(), phase);
                // Announce only when a train has moved to a nearer band this tick, inside the
                // three-station window, and never while it is being held short of the approach.
                if (!farHold && phase <= WINDOW && (previous == null || phase < previous)) {
                    // Announced in the direction it will arrive in: a train due here after turning
                    // back at a terminus is the OUTBOUND train to riders on this platform, even
                    // while it is still running inbound to the end of the line.
                    int arriving = ArrivalEstimator.arrivalDirection(line,
                        snapshot.serviceDirection(), snapshot.nextStopIndex(), stationIndex);
                    String text = TransitSignText.announcement(line,
                        arriving == 0 ? snapshot.serviceDirection() : arriving,
                        raw, snapshot.atPlatform(), raw == 0 && hasSeveralPlatforms(state)
                            ? snapshot.platformLabel() : "");
                    if (text != null) {
                        announce(text);
                    }
                }
            }
        }

        // Forget trains that have gone out of service, so the map cannot grow without bound and a
        // returning train re-announces from a clean slate.
        lastPhase.keySet().retainAll(present);
    }

    /** Whether this speaker's station has more than one berth, so an arrival must say which. */
    private boolean hasSeveralPlatforms(RcmcWorldState state) {
        com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation station =
            state.transit().station(stationName());
        return station != null && station.platforms().size() > 1;
    }

    /**
     * Whether the approach call should go out now, so that it <em>ends</em> as the train pulls in.
     *
     * <p>The old test was a fixed 30 blocks. That is not an amount of time — it is however long the
     * train takes to cover it — so the margin it bought depended entirely on the braking rate, and
     * it could not account for how long the sentence takes to say. On the stock preset it was
     * adequate but marginal; a firmer brake or a longer station name broke it. What matters is how
     * long the train has left, and both inputs to that are known here.</p>
     *
     * <p><b>Not the cause of the reported "only fires on arrival".</b> That was the service
     * direction bug: with the wrong direction, {@code stopsAway} did not report this station as the
     * train's next stop at all until it berthed. This is the separate improvement asked for on the
     * back of it — that the call should <em>end</em> as the train pulls in.</p>
     *
     * <p>The lead is the whole announcement: the chime, the pause after it, and the words
     * themselves, plus a moment so the last syllable is not landing on the doors opening.</p>
     */
    private boolean isDue(RcmcWorldState state, TransitLine line, ServiceSnapshot snapshot) {
        double distance = snapshot.distanceToNextStop();
        if (distance <= NEAR_DISTANCE) {
            return true;
        }
        Train train = state.trains().train(snapshot.trainId());
        LineService service = state.transit().serviceFor(snapshot.trainId());
        if (train == null || service == null) {
            return false;
        }
        double seconds = ArrivalEstimator.secondsToArrival(distance, train.velocity(),
            service.controller().serviceBrakeDeceleration());
        return seconds <= announcementLeadSeconds(line, snapshot);
    }

    /** How long this station's approach call takes to deliver, chime and all. */
    private static double announcementLeadSeconds(TransitLine line, ServiceSnapshot snapshot) {
        String text = TransitSignText.announcement(line, snapshot.serviceDirection(), 0, false);
        double speech = text == null ? MIN_SPEECH_SECONDS : text.length() * SECONDS_PER_CHARACTER;
        speech = Math.max(MIN_SPEECH_SECONDS, Math.min(MAX_SPEECH_SECONDS, speech));
        return CHIME_TO_SPEECH_TICKS / 20.0D + speech + SETTLE_SECONDS;
    }

    /**
     * Chimes now and queues the words to follow once the ding has rung out. The chime is a
     * positional sound every client in range attenuates for itself; the words go out as text a
     * short while later, so a listener hears "ding … <announcement>" rather than both at once.
     */
    private void announce(String text) {
        world.playSound(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
            RcmcSounds.METRO_STATION_CHIME, SoundCategory.NEUTRAL, CHIME_VOLUME, 1.0F);
        pending.add(new Pending(text, CHIME_TO_SPEECH_TICKS));
    }

    /** Counts down queued announcements and sends each once its chime has finished. */
    private void firePending() {
        if (pending.isEmpty()) {
            return;
        }
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (--p.ticks > 0) {
                continue;
            }
            it.remove();
            broadcast(p.text);
        }
    }

    /** Sends the announcement text to every player within earshot of this speaker. */
    private void broadcast(String text) {
        double rangeSq = AUDIBLE_RANGE * AUDIBLE_RANGE;
        for (EntityPlayer player : world.playerEntities) {
            if (!(player instanceof EntityPlayerMP)) {
                continue;
            }
            double dx = player.posX - (pos.getX() + 0.5D);
            double dy = player.posY - (pos.getY() + 0.5D);
            double dz = player.posZ - (pos.getZ() + 0.5D);
            if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                // Empty voice: the client uses the TTS engine's default. Announcements are one
                // voice for now; a per-line voice could ride the packet later.
                RcmcNetwork.sendTo(new PacketStationAnnounce(text, ""), (EntityPlayerMP) player);
            }
        }
    }

    /** A station announcement whose chime has played and whose words are waiting on a short timer. */
    private static final class Pending {
        final String text;
        int ticks;

        Pending(String text, int ticks) {
            this.text = text;
            this.ticks = ticks;
        }
    }
}
