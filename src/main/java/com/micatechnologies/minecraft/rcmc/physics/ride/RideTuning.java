package com.micatechnologies.minecraft.rcmc.physics.ride;

import com.micatechnologies.minecraft.rcmc.physics.element.BrakeRun;
import com.micatechnologies.minecraft.rcmc.physics.element.ChainLift;
import com.micatechnologies.minecraft.rcmc.physics.element.DriveTyres;
import com.micatechnologies.minecraft.rcmc.physics.element.LaunchTrack;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElementSet;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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
        return settingsFor(elements, Collections.singleton(sectionId), null, tickSeconds);
    }

    /**
     * Every adjustable value of a ride spread over {@code sections}, one per piece of hardware.
     *
     * <p>A piece of hardware can be more than one element. A lift cut by a split, or running over a
     * circuit's seam, is two elements laid end to end with the same settings — one lift to anyone
     * looking at it, and listed once, addressed by its first element. {@link #apply} sets the rest
     * with it. {@code network} decides what "end to end" means across sections and seams; without
     * it only elements meeting on one section count.</p>
     */
    public static List<Setting> settingsFor(RideElementSet elements, Set<Integer> sections,
                                            TrackNetwork network, double tickSeconds) {
        List<Setting> out = new ArrayList<>();
        List<RideElement> all = elements.elements();
        Set<Integer> listed = new TreeSet<>();
        for (int i = 0; i < all.size(); i++) {
            RideElement element = all.get(i);
            if (!sections.contains(element.sectionId()) || listed.contains(i)) {
                continue;
            }
            listed.addAll(sameHardware(all, i, network));
            out.addAll(settingsOf(element, i, tickSeconds));
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
        return apply(elements, Collections.singleton(sectionId), null, elementIndex, parameter, value,
            tickSeconds);
    }

    /**
     * As above, for a ride over {@code sections}: sets the piece of hardware {@code elementIndex}
     * belongs to — every element of it, so a lift in two pieces stays one lift at one speed.
     */
    public static double apply(RideElementSet elements, Set<Integer> sections, TrackNetwork network,
                               int elementIndex, Parameter parameter, double value, double tickSeconds) {
        List<RideElement> all = elements.elements();
        if (elementIndex < 0 || elementIndex >= all.size()) {
            return Double.NaN;
        }
        RideElement old = all.get(elementIndex);
        if (!sections.contains(old.sectionId())) {
            return Double.NaN;
        }
        double v = parameter.clamp(value);
        if (withValue(old, parameter, v, tickSeconds) == null) {
            return Double.NaN;
        }
        List<RideElement> pieces = new ArrayList<>();
        for (int index : sameHardware(all, elementIndex, network)) {
            pieces.add(all.get(index));
        }
        for (RideElement piece : pieces) {
            elements.replace(piece, withValue(piece, parameter, v, tickSeconds));
        }
        return v;
    }

    /**
     * The indices of every element that is one piece of hardware with element {@code index}: the
     * same kind, with the same settings, each laid end to end with the next — on one section, over
     * a circuit's seam, or across an end-to-start join.
     */
    static Set<Integer> sameHardware(List<RideElement> all, int index, TrackNetwork network) {
        Set<Integer> group = new TreeSet<>();
        List<Integer> frontier = new ArrayList<>();
        group.add(index);
        frontier.add(index);
        while (!frontier.isEmpty()) {
            RideElement at = all.get(frontier.remove(frontier.size() - 1));
            for (int j = 0; j < all.size(); j++) {
                if (group.contains(j)) {
                    continue;
                }
                RideElement other = all.get(j);
                if (sameSettings(at, other) && (continues(at, other, network) || continues(other, at, network))) {
                    group.add(j);
                    frontier.add(j);
                }
            }
        }
        return group;
    }

    private static boolean sameSettings(RideElement a, RideElement b) {
        if (a.getClass() != b.getClass()) {
            return false;
        }
        // Settings list a launch's speed without its sign and a brake's target without its mode;
        // a forward and a backward launch, or a trim and a block brake, are never one piece.
        if (a instanceof LaunchTrack
            && Math.signum(((LaunchTrack) a).targetSpeed()) != Math.signum(((LaunchTrack) b).targetSpeed())) {
            return false;
        }
        if (a instanceof BrakeRun && ((BrakeRun) a).mode() != ((BrakeRun) b).mode()) {
            return false;
        }
        List<Setting> sa = settingsOf(a, 0, 1.0D);
        List<Setting> sb = settingsOf(b, 0, 1.0D);
        if (sa.isEmpty() || sa.size() != sb.size()) {
            return false;
        }
        for (int i = 0; i < sa.size(); i++) {
            if (Math.abs(sa.get(i).value - sb.get(i).value) > 1.0e-9D) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code b} starts where {@code a} ends, in the direction of travel. */
    private static boolean continues(RideElement a, RideElement b, TrackNetwork network) {
        final double eps = 1.0e-6D;
        if (a.sectionId() == b.sectionId() && Math.abs(a.endDistance() - b.startDistance()) < eps) {
            return true;
        }
        if (network == null || b.startDistance() > eps) {
            return false;
        }
        TrackSection from = network.section(a.sectionId());
        if (from == null || Math.abs(a.endDistance() - from.totalLength()) > eps) {
            return false;
        }
        if (a.sectionId() == b.sectionId()) {
            return from.isClosed();
        }
        TrackNetwork.SectionEnd joined = network.joinedTo(
            new TrackNetwork.SectionEnd(a.sectionId(), TrackNetwork.End.END));
        return joined != null && joined.sectionId == b.sectionId() && joined.end == TrackNetwork.End.START;
    }

    /** The adjustable values of one element; see {@link #settingsFor}. */
    private static List<Setting> settingsOf(RideElement element, int i, double tickSeconds) {
        List<Setting> out = new ArrayList<>();
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
        return out;
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
