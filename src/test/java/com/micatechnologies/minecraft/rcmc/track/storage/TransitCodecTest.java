package com.micatechnologies.minecraft.rcmc.track.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.block.BlockSection;
import com.micatechnologies.minecraft.rcmc.physics.transit.LineSignals;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitLine;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import java.util.Arrays;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for the transit NBT codec.
 *
 * <p>The design note behind {@code PacketTransitSync} claims that reusing this codec means "wire
 * and save formats can't drift". That claim was asserted nowhere until this file existed — the
 * codec had no test at all, which is exactly the situation where a save-corrupting bug lives
 * undetected, because a codec bug does not throw, it silently loses a player's line.</p>
 */
class TransitCodecTest {

    private static TransitStation station(String name, int section, double distance) {
        return new TransitStation(name, new TrackRef(section, distance));
    }

    private static TransitSystem sample() {
        TransitSystem transit = new TransitSystem();
        TransitStation north = station("North", 1, 10.0D);
        TransitStation centre = station("Centre", 1, 220.5D);
        TransitStation south = station("South", 2, 47.25D);
        transit.addStation(north);
        transit.addStation(centre);
        transit.addStation(south);
        transit.addLine(new TransitLine("Metro", Arrays.asList(north, centre, south), false));
        return transit;
    }

    private static TransitSystem roundTrip(TransitSystem transit) {
        NBTTagCompound root = new NBTTagCompound();
        TransitCodec.write(transit, root);
        return TransitCodec.read(root);
    }

    @Test
    @DisplayName("stations and lines survive a save/load round trip")
    void stationsAndLinesRoundTrip() {
        TransitSystem read = roundTrip(sample());

        assertEquals(3, read.stations().size());
        assertNotNull(read.station("North"));
        assertEquals(220.5D, read.station("Centre").stopPoint().distance(), 1e-9D);
        assertEquals(2, read.station("South").stopPoint().sectionId());

        TransitLine line = read.line("Metro");
        assertNotNull(line);
        assertEquals(3, line.stationCount());
        assertEquals("South", line.station(2).name());
    }

    @Test
    @DisplayName("a line's signal blocks survive a save/load round trip")
    void signalsRoundTrip() {
        TransitSystem transit = sample();
        transit.setSignals("Metro", new LineSignals(Arrays.asList(
            new BlockSection("s1-b1", 1, 0.0D, 150.0D),
            new BlockSection("s1-b2", 1, 150.0D, 300.0D),
            new BlockSection("s2-b1", 2, 0.0D, 90.5D)), 0.75D, 400.0D));

        LineSignals read = roundTrip(transit).signalsFor("Metro");

        assertNotNull(read, "signalling must survive a restart — a safety system that silently "
            + "disappears is worse than one never installed");
        assertEquals(3, read.blocks().size());
        assertEquals(0.75D, read.margin(), 1e-9D);
        assertEquals(400.0D, read.horizon(), 1e-9D);
        assertEquals("s2-b1", read.blocks().get(2).id());
        assertEquals(2, read.blocks().get(2).sectionId());
        assertEquals(90.5D, read.blocks().get(2).endDistance(), 1e-9D);
    }

    @Test
    @DisplayName("signals are looked up by the same case-insensitive name as the line")
    void signalsAreKeyedLikeLines() {
        TransitSystem transit = sample();
        transit.setSignals("Metro", new LineSignals(
            Arrays.asList(new BlockSection("b1", 1, 0.0D, 100.0D)), 0.5D, 500.0D));

        assertNotNull(roundTrip(transit).signalsFor("METRO"));
    }

    @Test
    @DisplayName("a v1 save — no version key, no signals tag — still loads")
    void v1SaveLoads() {
        NBTTagCompound root = new NBTTagCompound();
        TransitCodec.write(sample(), root);
        root.removeTag("TransitVersion");
        root.removeTag("TransitSignals");

        TransitSystem read = TransitCodec.read(root);

        assertEquals(3, read.stations().size());
        assertNotNull(read.line("Metro"));
        assertNull(read.signalsFor("Metro"));
    }

    @Test
    @DisplayName("the current version is stamped on every write")
    void versionIsWritten() {
        NBTTagCompound root = new NBTTagCompound();
        TransitCodec.write(sample(), root);

        assertEquals(TransitCodec.DATA_VERSION, root.getInteger("TransitVersion"));
    }

    @Test
    @DisplayName("signals for a line that failed to load are dropped, not resurrected")
    void orphanedSignalsAreSkipped() {
        TransitSystem transit = sample();
        transit.setSignals("Metro", new LineSignals(
            Arrays.asList(new BlockSection("b1", 1, 0.0D, 100.0D)), 0.5D, 500.0D));
        NBTTagCompound root = new NBTTagCompound();
        TransitCodec.write(transit, root);
        // Simulate the line itself being unreadable: signals pointing at a line that is not there
        // must not create a phantom entry no command could ever address.
        root.removeTag("TransitLines");

        TransitSystem read = TransitCodec.read(root);

        assertTrue(read.lines().isEmpty());
        assertTrue(read.signals().isEmpty());
    }
}
