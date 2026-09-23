package com.micatechnologies.minecraft.rcmc.physics.transit;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import java.util.Locale;

/**
 * What a train in service is doing, in words — for {@code /rcmc line trains} and the line control
 * desk, which show the same thing and must never disagree about it.
 */
public final class ServiceStatus {

    /** Its direction label on its line: OUTBOUND, INBOUND, or the line's own names for them. */
    public final String direction;
    /** "to Harbor, 40 blocks", "at Harbor, boarding, 6 s left", "at Harbor, held". */
    public final String doing;
    /** Blocks/s, unsigned. */
    public final double speed;
    /** The train it is running at nose to nose on one track, or -1. */
    public final int headOnWith;
    /** The train it is stopping short of, or -1. */
    public final int stoppingFor;
    /** Held at its platform by an operator. */
    public final boolean held;

    private ServiceStatus(String direction, String doing, double speed, int headOnWith, int stoppingFor,
                          boolean held) {
        this.direction = direction;
        this.doing = doing;
        this.speed = speed;
        this.headOnWith = headOnWith;
        this.stoppingFor = stoppingFor;
        this.held = held;
    }

    public static ServiceStatus of(TransitSystem transit, int trainId, LineService service,
                                   TrainManager trains, TrackNetwork network) {
        TransitLine line = service.line();
        String next = line.station(service.currentStopIndex()).name();
        TransitStopController controller = service.controller();
        boolean held = transit.isHeld(trainId);
        String doing;
        switch (controller.phase()) {
            case APPROACHING:
                doing = "to " + next + ", " + whole(Math.max(0.0D, service.distanceToNextStop())) + " blocks";
                break;
            case BOARDING:
                if (controller.phaseTicksRemaining() == 0) {
                    doing = "at " + next + (held ? ", held" : ", held for headway");
                }
                else {
                    doing = "at " + next + ", boarding, "
                        + whole(controller.phaseTicksRemaining() / 20.0D) + " s left";
                }
                break;
            case DOORS_OPENING:
                doing = "at " + next + ", doors opening";
                break;
            default:
                doing = "at " + next + ", doors closing";
                break;
        }
        Train train = trains.train(trainId);
        return new ServiceStatus(
            service.serviceDirection() > 0 ? line.outboundLabel() : line.inboundLabel(),
            doing,
            train == null ? 0.0D : Math.abs(train.velocity()),
            transit.headOnWith(trainId, trains, network),
            transit.stoppingFor(trainId),
            held);
    }

    /** Why it is not simply running, if it is not: ", HEAD-ON with train #4 …", or empty. */
    public String why() {
        if (headOnWith >= 0) {
            return ", HEAD-ON with train #" + headOnWith + " on the same track";
        }
        if (stoppingFor >= 0) {
            return ", stopping for train #" + stoppingFor + " ahead";
        }
        return "";
    }

    private static String whole(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }
}
