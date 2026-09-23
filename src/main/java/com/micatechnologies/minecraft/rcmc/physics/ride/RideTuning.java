package com.micatechnologies.minecraft.rcmc.physics.ride;

import com.micatechnologies.minecraft.rcmc.physics.element.BrakeRun;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.DriveTyres;
import com.micatechnologies.minecraft.rcmc.physics.element.LaunchTrack;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The ride hardware an operator can adjust: lift and tyre speeds, launch speed and force, brake
 * targets, station dwell.
 *
 * <p>Elements are immutable, so a new value is applied by building a replacement element that
 * differs in that one value and putting it where the old one was — position matters, since the first
 * matching element wins where spans overlap. The values were hard-coded before this existed (a 5
 * blocks/s lift, a 3 s dwell), with nothing a player could change.</p>
 *
 * <p>Every value is clamped to a range a ride can actually run on; the GUI steps within it.</p>
 */
public final class RideTuning {

    /** A value an operator can set, with its range and the step the GUI moves it by. */
    public enum Parameter {
        LIFT_SPEED("Lift speed", "b/s", 1.0D, 15.0D, 0.5D),
        LAUNCH_SPEED("Launch speed", "b/s", 5.0D, 60.0D, 1.0D),
        LAUNCH_FORCE("Launch force", "b/s²", 1.0D, 30.0D, 0.5D),
        BRAKE_SPEED("Brake target", "b/s", 0.0D, 30.0D, 0.5D),
        TYRE_SPEED("Tyre speed", "b/s", 1.0D, 20.0D, 0.5D),
        STATION_DWELL("Station dwell", "s", 0.0D, 60.0D, 1.0D),
        STATION_PASSES("Pass-throughs", "", 0.0D, 5.0D, 1.0D);

        public final String label;
        public final String unit;
        public final double min;
        public final double max;
        public final double step;

        Parameter(String label, String unit, double min, double max, double step) {
            this.label = label;
            this.unit = unit;
            this.min = min;
            this.max = max;
            this.step = step;
        }

        public double clamp(double value) {
            return Math.max(min, Math.min(max, value));
        }
    }

    /** One adjustable value on one element of a ride, as the operator panel lists it. */
    public static final class Setting {
        /** The element's position in the world's element set — how a change addresses it. */
        public final int elementIndex;
        public final Parameter parameter;
        public final double value;

        public Setting(int elementIndex, Parameter parameter, double value) {
            this.elementIndex = elementIndex;
            this.parameter = parameter;
            this.value = value;
        }

        @Override
        public String toString() {
            return parameter.label + " #" + elementIndex + " = " + value + " " + parameter.unit;
        }
    }

    private RideTuning() {
    }

    /** Every adjustable value of the elements on {@code sectionId}, in element order. */
    public static List<Setting> settingsFor(RideElementSet elements, int sectionId,
                                            double tickSeconds) {
        List<Setting> out = new ArrayList<>();
        List<RideElement> all = elements.elements();
        for (int i = 0; i < all.size(); i++) {
            RideElement element = all.get(i);
            if (element.sectionId() != sectionId) {
                continue;
            }
            if (element instanceof ChainLift) {
                out.add(new Setting(i, Parameter.LIFT_SPEED, ((ChainLift) element).chainSpeed()));
            }
            else if (element instanceof LaunchTrack) {
                LaunchTrack launch = (LaunchTrack) element;
                // The magnitude: a backward launch is still set by how fast, not by a minus sign.
                out.add(new Setting(i, Parameter.LAUNCH_SPEED, Math.abs(launch.targetSpeed())));
                out.add(new Setting(i, Parameter.LAUNCH_FORCE, launch.constantAcceleration()));
            }
            else if (element instanceof BrakeRun) {
                out.add(new Setting(i, Parameter.BRAKE_SPEED, ((BrakeRun) element).targetSpeed()));
            }
            else if (element instanceof DriveTyres) {
                out.add(new Setting(i, Parameter.TYRE_SPEED, ((DriveTyres) element).driveSpeed()));
            }
            else if (element instanceof StationPlatform) {
                out.add(new Setting(i, Parameter.STATION_DWELL,
                    ((StationPlatform) element).dwellTicks() * tickSeconds));
                out.add(new Setting(i, Parameter.STATION_PASSES,
                    ((StationPlatform) element).passThroughs()));
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Sets {@code parameter} on the element at {@code elementIndex} to {@code value}, clamped.
     *
     * @return the value actually applied, or {@code NaN} if that element does not exist, is not on
     *         {@code sectionId}, or has no such parameter — a stale GUI addressing an element that
     *         has since changed is refused rather than retuning the wrong hardware
     */
    public static double apply(RideElementSet elements, int sectionId, int elementIndex,
                               Parameter parameter, double value, double tickSeconds) {
        List<RideElement> all = elements.elements();
        if (elementIndex < 0 || elementIndex >= all.size()) {
            return Double.NaN;
        }
        RideElement old = all.get(elementIndex);
        if (old.sectionId() != sectionId) {
            return Double.NaN;
        }
        double v = parameter.clamp(value);
        RideElement replacement = withValue(old, parameter, v, tickSeconds);
        if (replacement == null) {
            return Double.NaN;
        }
        elements.replace(old, replacement);
        return v;
    }

    /** A copy of {@code e} with {@code parameter} set to {@code v}, or {@code null} if it has none. */
    static RideElement withValue(RideElement e, Parameter parameter, double v, double tickSeconds) {
        if (e instanceof ChainLift && parameter == Parameter.LIFT_SPEED) {
            ChainLift lift = (ChainLift) e;
            return new ChainLift(lift.sectionId(), lift.startDistance(), lift.endDistance(), v,
                lift.maxAcceleration(), tickSeconds);
        }
        if (e instanceof LaunchTrack && parameter == Parameter.LAUNCH_SPEED) {
            LaunchTrack launch = (LaunchTrack) e;
            double sign = launch.targetSpeed() < 0.0D ? -1.0D : 1.0D;
            return new LaunchTrack(launch.sectionId(), launch.startDistance(), launch.endDistance(),
                sign * v, launch.constantAcceleration());
        }
        if (e instanceof LaunchTrack && parameter == Parameter.LAUNCH_FORCE) {
            LaunchTrack launch = (LaunchTrack) e;
            return new LaunchTrack(launch.sectionId(), launch.startDistance(), launch.endDistance(),
                launch.targetSpeed(), v);
        }
        if (e instanceof BrakeRun && parameter == Parameter.BRAKE_SPEED) {
            BrakeRun brake = (BrakeRun) e;
            // A trim brake that stops a train is a block brake; it cannot be tuned to zero.
            double target = brake.mode() == BrakeRun.Mode.TRIM
                ? Math.max(Parameter.BRAKE_SPEED.step, v) : v;
            return new BrakeRun(brake.sectionId(), brake.startDistance(), brake.endDistance(), target,
                brake.deceleration(), brake.mode(), tickSeconds);
        }
        if (e instanceof DriveTyres && parameter == Parameter.TYRE_SPEED) {
            DriveTyres tyres = (DriveTyres) e;
            return new DriveTyres(tyres.sectionId(), tyres.startDistance(), tyres.endDistance(), v,
                tyres.maxAcceleration(), tickSeconds);
        }
        if (e instanceof StationPlatform && parameter == Parameter.STATION_DWELL) {
            StationPlatform s = (StationPlatform) e;
            return new StationPlatform(s.sectionId(), s.startDistance(), s.endDistance(),
                s.stopDistance(), s.brakeDeceleration(), (int) Math.round(v / tickSeconds),
                s.dispatchAcceleration(), s.dispatchSpeed(), tickSeconds, s.passThroughs());
        }
        if (e instanceof StationPlatform && parameter == Parameter.STATION_PASSES) {
            StationPlatform s = (StationPlatform) e;
            return new StationPlatform(s.sectionId(), s.startDistance(), s.endDistance(),
                s.stopDistance(), s.brakeDeceleration(), s.dwellTicks(),
                s.dispatchAcceleration(), s.dispatchSpeed(), tickSeconds, (int) Math.round(v));
        }
        return null;
    }
}
