package com.micatechnologies.minecraft.rcmc.builder;

import com.micatechnologies.minecraft.rcmc.track.element.AirtimeHill;
import com.micatechnologies.minecraft.rcmc.track.element.Corkscrew;
import com.micatechnologies.minecraft.rcmc.track.element.Curve;
import com.micatechnologies.minecraft.rcmc.track.element.Helix;
import com.micatechnologies.minecraft.rcmc.track.element.RollDirection;
import com.micatechnologies.minecraft.rcmc.track.element.Slope;
import com.micatechnologies.minecraft.rcmc.track.element.Straight;
import com.micatechnologies.minecraft.rcmc.track.element.TrackElement;
import com.micatechnologies.minecraft.rcmc.track.element.TurnDirection;
import com.micatechnologies.minecraft.rcmc.track.element.VerticalLoop;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The prefab pieces a builder can pick from, in the order the piece tool cycles through them.
 *
 * <p>{@code track.element} contains the generators; this is the <em>menu</em>. The distinction
 * matters: a {@link TrackElement} is fully specified at construction (a curve knows its radius, its
 * arc, its design speed and its bank ceiling), which is exactly what you want for a library and
 * exactly wrong for a tool — a builder holding a wand has not chosen six numbers, they have chosen
 * "curve left" and possibly nudged how tight it is. Each {@link Entry} therefore fixes every
 * parameter but one, names the survivor, and bounds it.</p>
 *
 * <p><b>One knob per piece, deliberately.</b> Every element takes between one and six parameters,
 * and exposing all of them would need a GUI; exposing none would make the palette a set of nine
 * fixed shapes you cannot fit to a hillside. The single exposed parameter is in each case the one
 * that changes the piece's <em>footprint</em> — radius for anything that turns, length for anything
 * that runs straight, rise for a slope — because footprint is what a builder is fighting with when
 * a piece does not fit. The rest are good defaults from the element's own documentation.</p>
 *
 * <p>Pure Java, no Minecraft types, same as the package it draws from — so the whole palette can be
 * generated and geometrically checked on a bare JVM.</p>
 */
public final class PiecePalette {

    /**
     * Speed the banked pieces are balanced for, in blocks per second.
     *
     * <p>About 54 km/h — a mid-sized steel coaster's cruising speed through its turns. Bank angle
     * is {@code atan(v^2/rg)}, so this choice only sets how banked a given radius comes out; a
     * builder who wants a different balance changes the radius, which is the knob they have.</p>
     */
    public static final double DESIGN_SPEED_BLOCKS_PER_SECOND = 15.0D;

    /** Bank ceiling for the turning pieces. Beyond this a curve reads as a wall rather than a turn. */
    public static final double MAX_BANK_DEGREES = 60.0D;

    /** Height a helix loses per full revolution, in blocks. Negative: helices descend by default,
     *  because that is what a helix is for — bleeding height off after a drop without eating the
     *  footprint a long straight would. */
    public static final double HELIX_DESCENT_PER_TURN = -8.0D;

    /** Arc of a single curve piece, in degrees. A quarter turn, so four of them make a square
     *  circuit and two make a U — the shapes a builder chains without thinking about it. */
    public static final double CURVE_ARC_DEGREES = 90.0D;

    /** Arc swept over an airtime hill's crest, in degrees. */
    public static final double HILL_CREST_ARC_DEGREES = 60.0D;

    /** Creates the configured element for a parameter value. */
    public interface Factory {
        TrackElement create(double parameter);
    }

    /** One entry on the menu: a piece, its adjustable parameter, and that parameter's bounds. */
    public static final class Entry {

        private final String parameterLabel;
        private final double minimum;
        private final double maximum;
        private final double step;
        private final double defaultValue;
        private final Factory factory;

        Entry(String parameterLabel, double minimum, double maximum, double step,
              double defaultValue, Factory factory) {
            this.parameterLabel = parameterLabel;
            this.minimum = minimum;
            this.maximum = maximum;
            this.step = step;
            this.defaultValue = defaultValue;
            this.factory = factory;
        }

        /** What the adjustable parameter is called, for the HUD — e.g. {@code "Radius"}. */
        public String parameterLabel() {
            return parameterLabel;
        }

        public double minimum() {
            return minimum;
        }

        public double maximum() {
            return maximum;
        }

        /** How much one scroll notch moves the parameter. */
        public double step() {
            return step;
        }

        public double defaultValue() {
            return defaultValue;
        }

        public double clamp(double parameter) {
            return Math.max(minimum, Math.min(maximum, parameter));
        }

        /** The configured generator. The parameter is clamped, so no caller can build an element
         *  whose constructor would reject it. */
        public TrackElement element(double parameter) {
            return factory.create(clamp(parameter));
        }

        /** Name shown to the builder, taken from the element itself rather than duplicated here. */
        public String displayName(double parameter) {
            return element(parameter).displayName();
        }

        /**
         * The parameter as a builder should read it.
         *
         * <p>Signed only where the sign carries meaning: a rise of {@code -6} is a drop and has to
         * say so, whereas {@code Radius: +12} reads as though a radius could be negative.</p>
         */
        public String describeParameter(double parameter) {
            String format = minimum < 0.0D ? "%+.0f" : "%.0f";
            return parameterLabel + ": " + String.format(Locale.ROOT, format, clamp(parameter));
        }
    }

    private static final List<Entry> ENTRIES;

    static {
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry("Length", 4.0D, 64.0D, 2.0D, 12.0D,
            parameter -> new Straight(parameter)));
        // One signed entry rather than separate up and down pieces: Slope already takes a signed
        // height change, and a builder scrolling a rise through zero into a drop is a smoother
        // interaction than hunting for the other piece. The length is derived so the grade stays
        // near 30 degrees, which also guarantees |rise| < length — the one thing Slope rejects.
        entries.add(new Entry("Rise", -24.0D, 24.0D, 1.0D, 6.0D,
            parameter -> new Slope(slopeLength(parameter), parameter)));
        entries.add(new Entry("Radius", 4.0D, 48.0D, 2.0D, 12.0D,
            parameter -> new Curve(parameter, CURVE_ARC_DEGREES, TurnDirection.LEFT,
                DESIGN_SPEED_BLOCKS_PER_SECOND, MAX_BANK_DEGREES)));
        entries.add(new Entry("Radius", 4.0D, 48.0D, 2.0D, 12.0D,
            parameter -> new Curve(parameter, CURVE_ARC_DEGREES, TurnDirection.RIGHT,
                DESIGN_SPEED_BLOCKS_PER_SECOND, MAX_BANK_DEGREES)));
        entries.add(new Entry("Radius", 4.0D, 32.0D, 2.0D, 10.0D,
            parameter -> new Helix(parameter, 360.0D, HELIX_DESCENT_PER_TURN, TurnDirection.LEFT,
                DESIGN_SPEED_BLOCKS_PER_SECOND, MAX_BANK_DEGREES)));
        entries.add(new Entry("Radius", 4.0D, 32.0D, 2.0D, 10.0D,
            parameter -> new Helix(parameter, 360.0D, HELIX_DESCENT_PER_TURN, TurnDirection.RIGHT,
                DESIGN_SPEED_BLOCKS_PER_SECOND, MAX_BANK_DEGREES)));
        entries.add(new Entry("Top radius", 3.0D, 20.0D, 1.0D, 6.0D,
            parameter -> new VerticalLoop(parameter)));
        entries.add(new Entry("Length", 8.0D, 48.0D, 2.0D, 18.0D,
            parameter -> new Corkscrew(parameter, parameter / 3.0D, RollDirection.POSITIVE)));
        entries.add(new Entry("Radius", 6.0D, 48.0D, 2.0D, 16.0D,
            parameter -> new AirtimeHill(parameter, HILL_CREST_ARC_DEGREES)));
        ENTRIES = Collections.unmodifiableList(entries);
    }

    private PiecePalette() {
        throw new AssertionError("No instances.");
    }

    /**
     * Path length for a slope of the given rise.
     *
     * <p>Twice the rise is a 30-degree grade, which is about as steep as a chain lift runs and well
     * inside the 85-degree ceiling {@code Slope} enforces. The floor keeps a near-level slope from
     * degenerating into a piece too short to have a shape.</p>
     */
    static double slopeLength(double rise) {
        return Math.max(8.0D, Math.abs(rise) * 2.0D);
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static int size() {
        return ENTRIES.size();
    }

    public static Entry get(int index) {
        return ENTRIES.get(wrap(index));
    }

    /** Brings any index — including a negative one, or one from a stale packet — into range. */
    public static int wrap(int index) {
        int size = ENTRIES.size();
        int wrapped = index % size;
        return wrapped < 0 ? wrapped + size : wrapped;
    }
}
