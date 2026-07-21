package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.physics.transit.LineService;
import com.micatechnologies.minecraft.rcmc.physics.transit.ServiceSnapshot;
import net.minecraft.world.World;

/**
 * Whether a train's doors are open, asked the same way from either side.
 *
 * <p>The server owns the answer — it is a phase of the running {@code TransitStopController} — and
 * the client only has the synced {@link ServiceSnapshot}. Both callers want the same question
 * answered, and getting different answers on the two sides is the specific failure that makes
 * boarding feel broken: doors drawn open that refuse you, or doors drawn shut that let you in.
 * One helper, consulted by the model, the collision box and the boarding check alike.</p>
 *
 * <p>Lives in {@code world} rather than {@code physics.transit} because it needs a {@code World} to
 * find the state; the transit package itself stays free of Minecraft types.</p>
 */
public final class MetroDoors {

    private MetroDoors() {
        throw new AssertionError("No instances.");
    }

    /** Whether train {@code trainId} currently has its doors open. False if it is not in service. */
    public static boolean areOpen(World world, int trainId) {
        RcmcWorldState state = RcmcWorldState.of(world);
        if (state == null) {
            return false;
        }
        // Server side: the live controller is authoritative.
        LineService service = state.transit().serviceFor(trainId);
        if (service != null) {
            return service.controller().doorsOpen();
        }
        // Client side: services are never synced, only their snapshots.
        for (ServiceSnapshot snapshot : state.serviceSnapshots()) {
            if (snapshot.trainId() == trainId) {
                return snapshot.doorsOpen();
            }
        }
        return false;
    }

    /**
     * How far this train's doors are open, 0 to 1 — the animated value, distinct from
     * {@link #areOpen}, which is the boarding gate and stays shut while the leaves are moving.
     */
    public static double openFraction(World world, int trainId) {
        RcmcWorldState state = RcmcWorldState.of(world);
        if (state == null) {
            return 0.0D;
        }
        LineService service = state.transit().serviceFor(trainId);
        if (service != null) {
            return service.controller().doorFraction();
        }
        for (ServiceSnapshot snapshot : state.serviceSnapshots()) {
            if (snapshot.trainId() == trainId) {
                return snapshot.doorFraction();
            }
        }
        return 0.0D;
    }

    /**
     * Whether this world has any metro service running at all — a cheap guard for hot paths.
     *
     * <p>Deliberately checks <em>both</em> sides' notion of it. {@code TransitSystem.services} is
     * server truth and is never synced, so a client asking {@code hasServices()} always hears "no";
     * a guard written that way silently disables everything downstream of it on the very side that
     * runs player movement collision. The client's equivalent is the synced snapshot list.</p>
     */
    public static boolean anyServiceRunning(World world) {
        RcmcWorldState state = RcmcWorldState.of(world);
        if (state == null) {
            return false;
        }
        return state.transit().hasServices() || !state.serviceSnapshots().isEmpty();
    }

    /** Whether this train is in metro service at all — a coaster train has no doors to open. */
    public static boolean isInService(World world, int trainId) {
        RcmcWorldState state = RcmcWorldState.of(world);
        if (state == null) {
            return false;
        }
        if (state.transit().serviceFor(trainId) != null) {
            return true;
        }
        for (ServiceSnapshot snapshot : state.serviceSnapshots()) {
            if (snapshot.trainId() == trainId) {
                return true;
            }
        }
        return false;
    }
}
