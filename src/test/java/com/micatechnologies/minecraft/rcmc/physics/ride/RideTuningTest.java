package com.micatechnologies.minecraft.rcmc.physics.ride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.element.BrakeRun;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.LaunchTrack;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideTuning.Parameter;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideTuning.Setting;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RideTuningTest {

    private static final double TICK = 1.0D / 20.0D;

    /** Two rides: section 1 with a station, lift and brake; section 2 with a launch. */
    private static RideElementSet park() {
        RideElementSet set = new RideElementSet();
        set.add(new StationPlatform(1, 0.0D, 50.0D, 45.0D, 6.0D, 60, 4.0D, 6.0D, TICK));
        set.add(new LaunchTrack(2, 0.0D, 40.0D, 22.0D, 8.0D));
        set.add(new ChainLift(1, 50.0D, 120.0D, 5.0D, 12.0D, TICK));
        set.add(new BrakeRun(1, 400.0D, 450.0D, 6.0D, 6.0D, BrakeRun.Mode.TRIM, TICK));
        return set;
    }

    @Test
    @DisplayName("a ride lists every adjustable value of its own hardware, and nobody else's")
    void listsTheRidesSettings() {
        List<Setting> settings = RideTuning.settingsFor(park(), 1, TICK);
        assertEquals(3, settings.size(), settings.toString());
        assertEquals(Parameter.STATION_DWELL, settings.get(0).parameter);
        assertEquals(3.0D, settings.get(0).value, 1e-9, "60 ticks is a 3 s dwell");
        assertEquals(Parameter.LIFT_SPEED, settings.get(1).parameter);
        assertEquals(5.0D, settings.get(1).value, 1e-9);
        assertEquals(Parameter.BRAKE_SPEED, settings.get(2).parameter);

        List<Setting> launch = RideTuning.settingsFor(park(), 2, TICK);
        assertEquals(2, launch.size(), "a launch has a speed and a force");
    }

    @Test
    @DisplayName("changing the lift speed changes the lift, and keeps it where it was in the list")
    void retunesInPlace() {
        RideElementSet set = park();
        assertEquals(7.0D, RideTuning.apply(set, 1, 2, Parameter.LIFT_SPEED, 7.0D, TICK), 1e-9);
        assertTrue(set.elements().get(2) instanceof ChainLift, "same position — it is its priority");
        ChainLift lift = (ChainLift) set.elements().get(2);
        assertEquals(7.0D, lift.chainSpeed(), 1e-9);
        assertEquals(12.0D, lift.maxAcceleration(), 1e-9, "everything else unchanged");
        assertEquals(50.0D, lift.startDistance(), 1e-9);
    }

    @Test
    @DisplayName("a dwell in seconds becomes ticks on the station")
    void dwellIsSeconds() {
        RideElementSet set = park();
        RideTuning.apply(set, 1, 0, Parameter.STATION_DWELL, 10.0D, TICK);
        assertEquals(200, ((StationPlatform) set.elements().get(0)).dwellTicks());
    }

    @Test
    @DisplayName("values are held to a range a ride can actually run on")
    void valuesAreClamped() {
        RideElementSet set = park();
        assertEquals(15.0D, RideTuning.apply(set, 1, 2, Parameter.LIFT_SPEED, 400.0D, TICK), 1e-9);
        assertEquals(1.0D, RideTuning.apply(set, 1, 2, Parameter.LIFT_SPEED, -3.0D, TICK), 1e-9);
    }

    @Test
    @DisplayName("a change aimed at the wrong ride or the wrong kind of hardware is refused")
    void staleOrWrongChangesAreRefused() {
        RideElementSet set = park();
        assertTrue(Double.isNaN(RideTuning.apply(set, 2, 2, Parameter.LIFT_SPEED, 7.0D, TICK)),
            "element 2 belongs to ride 1, not 2");
        assertTrue(Double.isNaN(RideTuning.apply(set, 1, 2, Parameter.LAUNCH_SPEED, 30.0D, TICK)),
            "a lift has no launch speed");
        assertTrue(Double.isNaN(RideTuning.apply(set, 1, 99, Parameter.LIFT_SPEED, 7.0D, TICK)),
            "no such element");
        assertEquals(5.0D, ((ChainLift) set.elements().get(2)).chainSpeed(), 1e-9, "untouched");
    }
}
