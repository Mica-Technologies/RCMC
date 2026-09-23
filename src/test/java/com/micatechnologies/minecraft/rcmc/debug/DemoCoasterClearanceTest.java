package com.micatechnologies.minecraft.rcmc.debug;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The demo coaster never runs below its own station.
 *
 * <p>Found in review as "gaps in the track near the crests": {@code /rcmc demo} puts the station at
 * the player's feet, and the layout dug its first valley in below that — so on any ground at all,
 * every dip ran through the dirt and the terrain hid it. From above, the track looked broken.</p>
 */
class DemoCoasterClearanceTest {

    @ParameterizedTest(name = "scale {0}, lift {1}")
    @CsvSource({"1.0, 34", "0.4, 16", "0.4, 120", "4.0, 16", "4.0, 120", "2.0, 60"})
    @DisplayName("no stretch of the demo dips below the station it is built from")
    void staysAboveTheStation(double scale, double lift) {
        Vec3 origin = new Vec3(0, 64, 0);
        TrackSection section = DemoCoaster.build(1, origin, scale, lift).section;
        double lowest = Double.MAX_VALUE;
        double at = 0;
        for (double s = 0; s < section.totalLength(); s += 0.25D) {
            double y = section.frameAtDistance(s).position.y;
            if (y < lowest) {
                lowest = y;
                at = s;
            }
        }
        // A few hundredths of a block is allowed: a banked rail steps outward about the riders'
        // hearts (see HeartlineShaper), and the spline between such nodes can sag by that much —
        // well inside the rail's own thickness. What this guards against is whole dips underground.
        assertTrue(lowest >= origin.y - 0.1D,
            "track at s=" + at + " is " + (origin.y - lowest) + " blocks below the station");
    }
}
