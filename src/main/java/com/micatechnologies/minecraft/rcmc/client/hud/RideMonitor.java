package com.micatechnologies.minecraft.rcmc.client.hud;

import com.micatechnologies.minecraft.rcmc.RcmcConfig;
import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.entity.EntityCoasterCar;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Central per-tick source of truth for "what is the rider feeling right now". {@link RideHud} and
 * {@link GForceEffects} both read from one shared instance of this rather than each re-deriving
 * it, so the two consumers can never disagree with each other and the curvature-direction estimate
 * in {@link RideTelemetry} only runs once per tick regardless of how many things want it.
 *
 * <p><b>Why a tick handler, not a per-frame one.</b> The train physics — and therefore speed,
 * curvature, and the derived G-forces — only change once per client tick (20/s;
 * {@link com.micatechnologies.minecraft.rcmc.client.ClientTrainTicker} advances it no faster).
 * Sampling inside a render callback would recompute an unchanged value every frame and, worse,
 * would measure {@code dv/dt} over a framerate-dependent interval — exactly the kind of thing
 * that would make longitudinal G read differently at 60 FPS than at 144. Ticking at the fixed
 * {@link RcmcConstants#SECONDS_PER_TICK} keeps both the acceleration derivative and the
 * {@link GForceSmoother}s frame-rate independent.</p>
 *
 * <p>Client-only, reached exclusively through {@code RcmcClientProxy}.</p>
 */
@SideOnly(Side.CLIENT)
public final class RideMonitor {

    private boolean riding;
    private int carIndex;
    private double previousVelocity;
    private double minHeight;
    private int elapsedTicks;

    private GForceSmoother verticalSmoother;
    private GForceSmoother longitudinalSmoother;

    private RideTelemetry.Reading latestReading;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) {
            reset();
            return;
        }

        // Mirrors RiderCamera's own lookup of "what is the player riding" — kept as its own check
        // here rather than shared, since the two classes have no other coupling and this is a
        // three-line condition, not worth a shared utility for.
        Entity viewer = mc.getRenderViewEntity();
        if (viewer == null || !(viewer.getRidingEntity() instanceof EntityCoasterCar)) {
            reset();
            return;
        }
        EntityCoasterCar car = (EntityCoasterCar) viewer.getRidingEntity();

        RcmcWorldState state = RcmcWorldState.of(mc.world);
        TrainManager trains = state == null ? null : state.trains();
        TrackNetwork network = state == null ? null : state.network();
        Train train = trains == null ? null : trains.train(car.trainId());

        if (train == null || network == null || !network.hasSection(train.reference().sectionId())) {
            // Same transient EntityCoasterCar.onUpdate() tolerates: track and train state arrive
            // in separate sync packets and can land a tick apart. Hold the last good reading
            // rather than clearing it, so the HUD does not flash blank for one tick.
            return;
        }

        if (!riding) {
            beginRide(car.carIndex(), train, network);
        }
        elapsedTicks++;

        double gravity = Math.max(RcmcConfig.gravity, 0.01D);
        RideTelemetry.Reading reading = RideTelemetry.compute(
            train, network, carIndex, previousVelocity, RcmcConstants.SECONDS_PER_TICK, gravity);
        // The train has one velocity for the whole rigid train regardless of which car the rider
        // sits in (see Train's class javadoc: one degree of freedom), so this derivative applies
        // equally to every seat.
        previousVelocity = train.velocity();

        if (reading == null) {
            return;
        }
        latestReading = reading;
        minHeight = Math.min(minHeight, reading.frame.position.y);

        verticalSmoother.update(reading.gForces.vertical, RcmcConstants.SECONDS_PER_TICK);
        longitudinalSmoother.update(reading.gForces.longitudinal, RcmcConstants.SECONDS_PER_TICK);
    }

    private void beginRide(int newCarIndex, Train train, TrackNetwork network) {
        riding = true;
        carIndex = newCarIndex;
        elapsedTicks = 0;
        previousVelocity = train.velocity();
        minHeight = train.frameOfCar(network, newCarIndex).position.y;
        latestReading = null;

        double tau = Math.max(RcmcConfig.gForceSmoothingSeconds, 0.05D);
        verticalSmoother = new GForceSmoother(tau);
        longitudinalSmoother = new GForceSmoother(tau);
    }

    private void reset() {
        riding = false;
        latestReading = null;
    }

    public boolean isRiding() {
        return riding;
    }

    /** The most recently computed reading, or {@code null} if not currently riding. */
    public RideTelemetry.Reading latestReading() {
        return latestReading;
    }

    public double elapsedSeconds() {
        return elapsedTicks * RcmcConstants.SECONDS_PER_TICK;
    }

    /**
     * How far above the lowest point <em>seen so far this ride</em> the car currently is.
     *
     * <p>Deliberately not "lowest point of the whole ride": that would need walking the entire
     * track graph reachable from the train's section, which is unbounded work for a closed circuit
     * and not something this class has any business doing on the render thread. Tracking the
     * running minimum since boarding is cheap, always correct for anything already ridden, and
     * converges to the true ride minimum by the time a circuit completes its first lap.</p>
     */
    public double heightAboveLowestPoint() {
        return latestReading == null ? 0.0D : latestReading.frame.position.y - minHeight;
    }

    /** Vertical G, smoothed over {@code RcmcConfig.gForceSmoothingSeconds} — see {@link GForceSmoother}. */
    public double smoothedVerticalG() {
        return verticalSmoother == null ? 0.0D : verticalSmoother.value();
    }

    /** Longitudinal G, smoothed the same way as {@link #smoothedVerticalG()}. */
    public double smoothedLongitudinalG() {
        return longitudinalSmoother == null ? 0.0D : longitudinalSmoother.value();
    }
}
