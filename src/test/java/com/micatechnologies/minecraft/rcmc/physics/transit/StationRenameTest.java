package com.micatechnologies.minecraft.rcmc.physics.transit;

import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.flatNetwork;
import static com.micatechnologies.minecraft.rcmc.physics.transit.TransitTestSupport.realistic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A station can be renamed, or given a display name with spaces, while its line runs: every line
 * serving it and every train on those lines see the new station at once, and signs bound to the old
 * id can find the new one.
 */
class StationRenameTest {

    private static TransitSystem system(TrackNetwork network) {
        TransitSystem transit = new TransitSystem();
        TransitStation a = new TransitStation("Haywood50th", new TrackRef(1, 100.0D));
        TransitStation b = new TransitStation("SinIsland", new TrackRef(1, 600.0D));
        transit.addStation(a);
        transit.addStation(b);
        transit.addLine(new TransitLine("Blue", Arrays.asList(a, b), false, true, "IN", "OUT"));
        return transit;
    }

    @Test
    @DisplayName("a rename reaches the registry, the line, a running train, and leaves a forwarding address")
    void renameReachesEverything() {
        TrackNetwork network = flatNetwork(1000.0D);
        TransitSystem transit = system(network);
        Train train = new Train(TrainSpec.metroTrain(3), realistic(), new TrackRef(1, 300.0D), 0.0D);
        LineService service = transit.enterService(1, train, network, "Blue", TransitDrives.metro(15.0D, 0.05D));
        int stop = service.currentStopIndex();

        transit.replaceStation("Haywood50th", transit.station("Haywood50th").withName("GrandCentral"));

        assertNull(transit.station("Haywood50th"));
        assertEquals("GrandCentral", transit.station("GrandCentral").name());
        assertEquals("GrandCentral", transit.line("Blue").station(0).name());
        assertSame(transit.line("Blue"), service.line(), "the running service is on the updated line");
        assertEquals(stop, service.currentStopIndex(), "and still running to the same stop");
        assertEquals("GrandCentral", transit.renamedTo("Haywood50th"));
    }

    @Test
    @DisplayName("a display name with spaces is shown, the spoken name is said, and the id is unchanged")
    void displayAndSpokenNames() {
        TransitSystem transit = system(flatNetwork(1000.0D));
        transit.replaceStation("SinIsland", transit.station("SinIsland")
            .withDisplayName("Sin Island").withSpokenName("Sin Island Terminal"));

        TransitStation station = transit.line("Blue").station(1);
        assertEquals("SinIsland", station.name());
        assertEquals("Sin Island", station.displayName());
        assertEquals("Sin Island Terminal", station.spokenName());
        assertEquals("Sin Island", transit.line("Blue").terminusName(1), "boards show the display name");
        assertNull(transit.renamedTo("SinIsland"), "no rename happened");
    }

    @Test
    @DisplayName("a rename onto an existing station's id is refused")
    void renameOntoExistingIsRefused() {
        TransitSystem transit = system(flatNetwork(1000.0D));
        assertThrows(IllegalArgumentException.class, () ->
            transit.replaceStation("Haywood50th", transit.station("Haywood50th").withName("SinIsland")));
    }
}
