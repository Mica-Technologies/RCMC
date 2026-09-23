package com.micatechnologies.minecraft.rcmc.physics.transit;

/**
 * Which end of a train leads — the end whose cab shows headlights, while the other shows red.
 *
 * <p>A metro train is double-ended and never turns round; at a terminus it simply starts running
 * the other way, and the driver changes ends. So the leading end is not a property of the train
 * but of where it is going, and the question has three answers depending on what is known:</p>
 *
 * <ul>
 *   <li><b>In service</b>, the line's own facing decides. It flips the moment a terminus reversal
 *       is decided, while the train still stands at the platform — which is exactly when a real
 *       train's lights change over, and before any motion could show it.</li>
 *   <li><b>Out of service but moving</b>, the direction of motion decides.</li>
 *   <li><b>Out of service and standing</b>, the lights stay as they were: a train that stopped
 *       does not swap ends by stopping.</li>
 * </ul>
 *
 * <p>The head is car 0's front end, the end the train's position is measured at; it leads when the
 * train runs, or is about to run, toward higher distances along its section.</p>
 */
public final class LeadingEnd {

    /** Above this speed, blocks/s, the direction of motion is trusted over what was shown before. */
    static final double MOVING_SPEED = 0.1D;

    private LeadingEnd() {
    }

    /**
     * Whether the head end leads.
     *
     * @param service  the train's service, or {@code null} when it is not running one
     * @param velocity the train's velocity along its section
     * @param previous what this said last time, for a train standing out of service
     */
    public static boolean headLeads(ServiceSnapshot service, double velocity, boolean previous) {
        if (service != null) {
            return service.facing() > 0;
        }
        if (Math.abs(velocity) > MOVING_SPEED) {
            return velocity > 0.0D;
        }
        return previous;
    }
}
