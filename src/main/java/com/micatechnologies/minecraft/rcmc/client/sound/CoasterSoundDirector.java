package com.micatechnologies.minecraft.rcmc.client.sound;

import com.micatechnologies.minecraft.rcmc.Rcmc;
import com.micatechnologies.minecraft.rcmc.RcmcConfig;
import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.entity.EntityCoasterCar;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.sound.CoasterAudioMix;
import com.micatechnologies.minecraft.rcmc.sound.CoasterAudioMix.Channel;
import com.micatechnologies.minecraft.rcmc.sound.CoasterAudioMix.Level;
import com.micatechnologies.minecraft.rcmc.sound.RcmcSounds;
import com.micatechnologies.minecraft.rcmc.track.ElementSpan;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.entity.Entity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Gives every nearby coaster train its sound: rolling on the rails, the chain on a lift, brakes
 * biting, drive tyres, a launch firing, and the wind for whoever is riding.
 *
 * <p>Entirely client-side. The client already predicts every train's position and speed each tick
 * ({@code ClientTrainTicker}) and holds the ride hardware's spans ({@code PacketElementSync}), which
 * is everything the mix needs, so nothing goes over the network. What each loop should sound like
 * is decided by {@link CoasterAudioMix}, which is pure and unit-tested; this class only keeps one
 * {@link TrainLoopSound} per (train, channel, on board or not) playing with those levels.</p>
 *
 * <p>A rider hears their own train from inside it — no position, no falloff — while everyone else
 * hears it from where the train is. Metro trains are left alone; they have their own sounds.</p>
 */
@SideOnly(Side.CLIENT)
public final class CoasterSoundDirector {

    /** No sound is started for a train further away than this. Loops already playing fade out. */
    private static final double EARSHOT = 56.0D;

    private final Map<Key, TrainLoopSound> loops = new HashMap<>();
    private final Map<Integer, Double> lastSpeed = new HashMap<>();
    private final Map<Integer, Double> lastAcceleration = new HashMap<>();
    private final Map<Integer, String> lastSpan = new HashMap<>();
    private World lastWorld;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        if (world != lastWorld) {
            // Minecraft stops every sound itself on a world change; forget ours to match.
            loops.clear();
            lastSpeed.clear();
            lastAcceleration.clear();
            lastSpan.clear();
            lastWorld = world;
        }
        if (world == null || mc.player == null || mc.isGamePaused()) {
            return;
        }
        try {
            direct(mc, world);
        }
        catch (RuntimeException e) {
            // Sound is decoration: a fault here must never take the client down.
            Rcmc.LOGGER.error("Coaster sound update failed this tick", e);
        }
    }

    private void direct(Minecraft mc, World world) {
        float master = (float) RcmcConfig.coasterSoundVolume;
        RcmcWorldState state = RcmcWorldState.of(world);
        if (state == null || master <= 0.0F) {
            releaseAll();
            return;
        }
        int ridingTrain = ridingTrainId(mc.player);
        Set<Key> wanted = new HashSet<>();

        for (Map.Entry<Integer, Train> entry : state.trains().asMap().entrySet()) {
            int trainId = entry.getKey();
            Train train = entry.getValue();
            if (train.spec().carStyle() != TrainSpec.CarStyle.COASTER) {
                continue;
            }
            int middle = train.spec().carCount() / 2;
            TrackFrame frame;
            TrackRef where;
            try {
                frame = train.frameOfCar(state.network(), middle);
                where = train.refOfCar(state.network(), middle);
            }
            catch (RuntimeException e) {
                continue;   // Track edited from under the train this tick; next tick settles it.
            }
            boolean onBoard = trainId == ridingTrain;
            double x = frame.position.x;
            double y = frame.position.y;
            double z = frame.position.z;
            if (!onBoard && mc.player.getDistanceSq(x, y, z) > EARSHOT * EARSHOT) {
                forget(trainId);
                continue;
            }

            double speed = train.speed();
            double previous = lastSpeed.getOrDefault(trainId, speed);
            // Lightly smoothed: client prediction snaps to the server's value now and then, and a
            // one-tick jump would read as a spike of acceleration.
            double raw = (speed - previous) / RcmcConstants.SECONDS_PER_TICK;
            double acceleration = 0.6D * lastAcceleration.getOrDefault(trainId, 0.0D) + 0.4D * raw;
            String span = spanAt(state.elementSpans(), where);

            if (CoasterAudioMix.launchFires(lastSpan.get(trainId),
                lastAcceleration.getOrDefault(trainId, 0.0D), span, acceleration)) {
                playLaunch(mc.getSoundHandler(), RcmcSounds.COASTER_LAUNCH, onBoard, 2.5F * master, x, y, z);
            }
            if (lastSpeed.containsKey(trainId) && CoasterAudioMix.dispatchFires(previous, speed, span)) {
                playLaunch(mc.getSoundHandler(), RcmcSounds.COASTER_DISPATCH, onBoard, master, x, y, z);
            }
            lastSpeed.put(trainId, speed);
            lastAcceleration.put(trainId, acceleration);
            lastSpan.put(trainId, span);

            for (Channel channel : Channel.values()) {
                Level level = CoasterAudioMix.level(channel, speed, acceleration, span, onBoard);
                Key key = new Key(trainId, channel, onBoard);
                TrainLoopSound loop = loops.get(key);
                float volume = level.volume * master;
                // Restarted if Minecraft dropped it (a sound reload, the channel limit) — but only
                // once it has had time to start; see TrainLoopSound.age.
                if (loop == null || loop.isReleased()
                    || (loop.age() > 20 && !mc.getSoundHandler().isSoundPlaying(loop))) {
                    if (loop != null) {
                        loop.release();
                    }
                    if (volume <= 0.0F) {
                        continue;
                    }
                    loop = new TrainLoopSound(eventFor(channel), onBoard,
                        CoasterAudioMix.bystanderRange(speed), volume, level.pitch, x, y, z);
                    loops.put(key, loop);
                    mc.getSoundHandler().playSound(loop);
                }
                loop.target(volume, level.pitch, x, y, z);
                wanted.add(key);
            }
        }

        // Anything not asked for this tick — a train that left, a rider who got off, a channel whose
        // hardware is behind the train — fades out and ends.
        Iterator<Map.Entry<Key, TrainLoopSound>> it = loops.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, TrainLoopSound> entry = it.next();
            if (!wanted.contains(entry.getKey())) {
                entry.getValue().release();
                it.remove();
            }
        }
        lastSpeed.keySet().retainAll(state.trains().asMap().keySet());
        lastAcceleration.keySet().retainAll(state.trains().asMap().keySet());
        lastSpan.keySet().retainAll(state.trains().asMap().keySet());
    }

    /**
     * A one-shot from the train: heard inside it by a rider, from where it is by everyone else.
     * {@code volume} above 1 only widens a bystander's range (16 blocks a unit); the gain clamps at 1.
     */
    private static void playLaunch(SoundHandler handler, SoundEvent event, boolean onBoard, float volume,
                                   double x, double y, double z) {
        if (onBoard) {
            handler.playSound(new PositionedSoundRecord(event.getSoundName(), SoundCategory.BLOCKS,
                Math.min(1.0F, volume), 1.0F, false, 0, ISound.AttenuationType.NONE, 0.0F, 0.0F, 0.0F));
        }
        else {
            handler.playSound(new PositionedSoundRecord(event, SoundCategory.BLOCKS,
                volume, 1.0F, (float) x, (float) y, (float) z));
        }
    }

    /** The ride hardware under a point on the track, or {@code null} for plain track. */
    private static String spanAt(List<ElementSpan> spans, TrackRef where) {
        for (ElementSpan span : spans) {
            if (span.sectionId == where.sectionId() && span.contains(where.distance())) {
                return span.type;
            }
        }
        return null;
    }

    private static int ridingTrainId(Entity player) {
        Entity ridden = player.getRidingEntity();
        return ridden instanceof EntityCoasterCar ? ((EntityCoasterCar) ridden).trainId() : -1;
    }

    private static SoundEvent eventFor(Channel channel) {
        switch (channel) {
            case CHAIN:
                return RcmcSounds.COASTER_CHAIN;
            case WIND:
                return RcmcSounds.COASTER_WIND;
            case BRAKE:
                return RcmcSounds.COASTER_BRAKE;
            case TYRES:
                return RcmcSounds.COASTER_TYRES;
            case ROLL:
            default:
                return RcmcSounds.COASTER_ROLL;
        }
    }

    private void forget(int trainId) {
        Iterator<Map.Entry<Key, TrainLoopSound>> it = loops.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, TrainLoopSound> entry = it.next();
            if (entry.getKey().trainId == trainId) {
                entry.getValue().release();
                it.remove();
            }
        }
    }

    private void releaseAll() {
        for (TrainLoopSound loop : loops.values()) {
            loop.release();
        }
        loops.clear();
    }

    private static final class Key {
        final int trainId;
        final Channel channel;
        final boolean onBoard;

        Key(int trainId, Channel channel, boolean onBoard) {
            this.trainId = trainId;
            this.channel = channel;
            this.onBoard = onBoard;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key)) {
                return false;
            }
            Key k = (Key) o;
            return trainId == k.trainId && channel == k.channel && onBoard == k.onBoard;
        }

        @Override
        public int hashCode() {
            return Objects.hash(trainId, channel, onBoard);
        }
    }
}
