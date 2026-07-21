package com.micatechnologies.minecraft.rcmc.sound;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.physics.transit.LineService;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitStopController;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;

/**
 * Plays a metro's door and brake sounds by watching its stop controller change phase.
 *
 * <p><b>Why an observer rather than a call from the controller.</b> {@code TransitStopController}
 * is pure Java with no Minecraft types — that is what makes the whole door cycle unit-testable on a
 * bare JVM — so it cannot play a sound, and giving it a listener interface just to route one back
 * out would put a callback in the middle of the simulation for the sake of decoration. Watching the
 * phase from outside costs one enum comparison per service per tick and keeps the physics
 * untouched.</p>
 *
 * <p>Server-side only. Sounds are emitted at the train's lead car through
 * {@code World.playSound(null, ...)}, which broadcasts to every client in range and lets each one
 * attenuate it — the sounds are mono precisely so that works.</p>
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

    private static final float VOLUME = 0.7F;

    /** trainId -> the phase it was in last tick, so transitions can be spotted. */
    private final Map<Integer, TransitStopController.Phase> lastPhase = new HashMap<>();

    /** trainId -> whether its warning chime has already played for the stop it is at. */
    private final Map<Integer, Boolean> chimed = new HashMap<>();

    /**
     * Call once per tick, after {@code TransitSystem.beginTick} and the train tick, so the phases
     * read are the ones that have just been advanced.
     */
    public void tick(World world, TransitSystem transit, TrainManager trains, TrackNetwork network) {
        if (world == null || world.isRemote || network == null) {
            return;
        }
        for (Map.Entry<Integer, LineService> entry : transit.services().entrySet()) {
            int trainId = entry.getKey();
            TransitStopController controller = entry.getValue().controller();
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
            if (phase == TransitStopController.Phase.DOORS_CLOSING) {
                play(world, train, network, RcmcSounds.METRO_DOOR_CLOSE);
            } else if (previous == TransitStopController.Phase.DOORS_CLOSING
                && phase == TransitStopController.Phase.APPROACHING) {
                // Doors shut, brakes off, and away: the release is the sound of departure.
                play(world, train, network, RcmcSounds.METRO_BRAKE_RELEASE);
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
}
