package com.micatechnologies.minecraft.rcmc.client.render;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.transit.LeadingEnd;
import com.micatechnologies.minecraft.rcmc.physics.transit.ServiceSnapshot;
import com.micatechnologies.minecraft.rcmc.world.RcmcWorldState;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Which lamps a metro car's cab ends show: white at the end the train leads with, red at the end
 * it trails. {@link LeadingEnd} decides which end that is; this remembers the answer per train, so
 * a train standing out of service keeps the lights it arrived with.
 */
@SideOnly(Side.CLIENT)
final class MetroCabs {

    /** Last answer per train. Tiny, and keyed by id, so a stale entry costs nothing. */
    private static final Map<Integer, Boolean> HEAD_LEADS = new HashMap<>();

    private MetroCabs() {
    }

    /**
     * The lamps at a car's front and rear ends, as {@code {front, rear}}.
     *
     * @param front whether the car's {@code +z} end is a cab end — car 0's, the train's head
     * @param rear  whether its {@code -z} end is — the last car's, the train's tail
     */
    static MetroCarModel.Lamp[] lamps(World world, int trainId, Train train, boolean front, boolean rear) {
        boolean headLeads = headLeads(world, trainId, train);
        MetroCarModel.Lamp lead = MetroCarModel.Lamp.HEAD;
        MetroCarModel.Lamp trail = MetroCarModel.Lamp.TAIL;
        return new MetroCarModel.Lamp[] {
            front ? (headLeads ? lead : trail) : MetroCarModel.Lamp.OFF,
            rear ? (headLeads ? trail : lead) : MetroCarModel.Lamp.OFF,
        };
    }

    private static boolean headLeads(World world, int trainId, Train train) {
        RcmcWorldState state = RcmcWorldState.of(world);
        ServiceSnapshot service = null;
        if (state != null) {
            for (ServiceSnapshot snapshot : state.serviceSnapshots()) {
                if (snapshot.trainId() == trainId) {
                    service = snapshot;
                    break;
                }
            }
        }
        boolean previous = HEAD_LEADS.getOrDefault(trainId, true);
        boolean now = LeadingEnd.headLeads(service, train == null ? 0.0D : train.velocity(), previous);
        HEAD_LEADS.put(trainId, now);
        return now;
    }
}
