package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Headlights belong at the end a train is leaving by, and must not swap for any other reason. */
class LeadingEndTest {

    private static ServiceSnapshot service(int facing) {
        return new ServiceSnapshot(1, "Red", 1, 0, true, true, 1.0D, 0.0D, DoorSide.BOTH, "",
            new double[0], facing);
    }

    @Test
    @DisplayName("a train standing at a terminus shows its lights at the end it is about to leave by")
    void terminusReversalChangesEndsBeforeTheTrainMoves() {
        // Stationary, and it came in head first; the line has already flipped its facing.
        assertFalse(LeadingEnd.headLeads(service(-1), 0.0D, true));
        assertTrue(LeadingEnd.headLeads(service(1), 0.0D, false));
    }

    @Test
    @DisplayName("out of service, the end it is moving toward leads")
    void motionDecidesOutOfService() {
        assertTrue(LeadingEnd.headLeads(null, 4.0D, false));
        assertFalse(LeadingEnd.headLeads(null, -4.0D, true));
    }

    @Test
    @DisplayName("a train that stops out of service keeps its lights where they were")
    void stoppingDoesNotSwapEnds() {
        assertFalse(LeadingEnd.headLeads(null, 0.0D, false));
        assertTrue(LeadingEnd.headLeads(null, 0.05D, true));
        assertFalse(LeadingEnd.headLeads(null, 0.05D, false), "creeping is not a direction");
    }
}
