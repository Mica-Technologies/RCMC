package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A station is a place, and a place can have more than one track through it.
 *
 * <p>The property that matters most here is that adding platforms did not change what a
 * single-platform station is: every station authored before platforms existed, and every station on
 * a single-track line, has to behave exactly as it did. The convenience accessors are what the bulk
 * of the mod still reads, so they are pinned rather than left to drift.</p>
 */
class TransitStationPlatformTest {

    private static TransitPlatform platform(double distance, DoorSide side, String label) {
        return new TransitPlatform(new TrackRef(1, distance), side, label);
    }

    @Test
    @DisplayName("a station built from one stop point is a one-platform station")
    void singleStopPointFoldsIntoOnePlatform() {
        TransitStation station = new TransitStation("Union", new TrackRef(1, 60.0D), DoorSide.RIGHT);

        assertEquals(1, station.platformCount());
        assertEquals(new TrackRef(1, 60.0D).distance(), station.stopPoint().distance(), 1e-9D);
        assertEquals(DoorSide.RIGHT, station.doorSide());
        assertEquals("", station.primary().label(), "nothing to disambiguate, so no label");
    }

    @Test
    @DisplayName("the convenience accessors read the primary platform")
    void accessorsReadThePrimary() {
        TransitStation station = new TransitStation("Central", Arrays.asList(
            platform(180.0D, DoorSide.RIGHT, "Inbound"),
            platform(184.0D, DoorSide.LEFT, "Outbound")));

        assertEquals(2, station.platformCount());
        assertEquals(180.0D, station.stopPoint().distance(), 1e-9D);
        assertEquals(DoorSide.RIGHT, station.doorSide());
        assertSame(station.platform(0), station.primary());
    }

    @Test
    @DisplayName("platforms are addressable by label, case-insensitively")
    void platformsAreAddressableByLabel() {
        TransitStation station = new TransitStation("Central", Arrays.asList(
            platform(180.0D, DoorSide.RIGHT, "Inbound"),
            platform(184.0D, DoorSide.LEFT, "Outbound")));

        assertEquals(DoorSide.LEFT, station.platform("outbound").doorSide());
        assertNull(station.platform("Northbound"), "no such berth");
        assertNull(station.platform(""), "an empty label matches nothing rather than the first");
    }

    @Test
    @DisplayName("authoring a door side applies to every platform")
    void doorSideAppliesToEveryPlatform() {
        TransitStation station = new TransitStation("Central", Arrays.asList(
            platform(180.0D, DoorSide.BOTH, "Inbound"),
            platform(184.0D, DoorSide.BOTH, "Outbound")))
            .withDoorSide(DoorSide.LEFT);

        for (TransitPlatform platform : station.platforms()) {
            assertEquals(DoorSide.LEFT, platform.doorSide(),
                "authoring a side on a one-platform station has to mean what it always did, so it "
                    + "cannot quietly apply to only the first of several");
        }
    }

    @Test
    @DisplayName("a station cannot exist with nowhere to berth")
    void aStationNeedsAPlatform() {
        assertThrows(IllegalArgumentException.class,
            () -> new TransitStation("Nowhere", Collections.<TransitPlatform>emptyList()));
    }

    @Test
    @DisplayName("the platform list is not modifiable through the accessor")
    void platformsAreImmutable() {
        TransitStation station = new TransitStation("Union", new TrackRef(1, 60.0D));
        assertThrows(UnsupportedOperationException.class,
            () -> station.platforms().add(platform(70.0D, DoorSide.BOTH, "Sneaky")));
    }

    @Test
    @DisplayName("adding a platform leaves the original station alone")
    void withPlatformIsCopyOnWrite() {
        TransitStation original = new TransitStation("Union", new TrackRef(1, 60.0D));
        TransitStation extended = original.withPlatform(platform(64.0D, DoorSide.LEFT, "2"));

        assertEquals(1, original.platformCount(), "immutable, so the original is untouched");
        assertEquals(2, extended.platformCount());
        assertTrue(extended.platform("2").hasLabel());
    }
}
