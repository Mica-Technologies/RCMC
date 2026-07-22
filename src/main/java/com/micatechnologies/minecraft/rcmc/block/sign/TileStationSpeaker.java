package com.micatechnologies.minecraft.rcmc.block.sign;

import com.micatechnologies.minecraft.rcmc.net.PacketStationAnnounce;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.physics.transit.ArrivalEstimator;
import com.micatechnologies.minecraft.rcmc.physics.transit.ServiceSnapshot;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSignText;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

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
     * How close (track blocks) a train must be to its stop point here before "now approaching"
     * speaks. Without this the call fires the instant this station becomes the train's next stop —
     * a full inter-station gap away, right as it leaves the previous platform. Held below this the
     * train stays in the "one stop away" band, so the approach call lands when it is genuinely
     * about to enter, not when it is merely next in line.
     */
    private static final double APPROACH_DISTANCE = 30.0D;

    /** How far a player may be from the speaker and still hear it, in blocks. */
    private static final double AUDIBLE_RANGE = 20.0D;

    /**
     * A phase below zero for a train berthing here, so "now arriving" is a step nearer than "now
     * approaching" (phase 0) and still triggers the decrease that fires an announcement.
     */
    private static final int PHASE_ARRIVING = -1;

    /** Last announced closeness per train, so only a genuine approach speaks. Server-side scratch. */
    private final Map<Integer, Integer> lastPhase = new HashMap<>();

    @Override
    public void update() {
        // Relink while unlinked (and nothing else) — the base returns early once linked.
        super.update();
        if (world == null || world.isRemote || !isLinked()) {
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
                // The train is this station's next stop (raw == 0) but still well down the line:
                // hold it in the "one stop away" band so "now approaching" waits until it is close.
                boolean farHold = raw == 0 && !snapshot.atPlatform()
                    && snapshot.distanceToNextStop() > APPROACH_DISTANCE;
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
                    String text = TransitSignText.announcement(line, snapshot.serviceDirection(),
                        raw, snapshot.atPlatform());
                    if (text != null) {
                        broadcast(text);
                    }
                }
            }
        }

        // Forget trains that have gone out of service, so the map cannot grow without bound and a
        // returning train re-announces from a clean slate.
        lastPhase.keySet().retainAll(present);
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
}
