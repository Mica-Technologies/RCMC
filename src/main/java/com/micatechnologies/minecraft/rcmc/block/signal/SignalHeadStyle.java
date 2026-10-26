package com.micatechnologies.minecraft.rcmc.block.signal;

import net.minecraft.util.IStringSerializable;

/**
 * The look of a signal head, and what it can say. The ordinal is the item damage and is saved in
 * the tile, so new styles go on the end.
 *
 * <p>Most styles are main signals that show an aspect. Four are accessories that say something
 * else: a route indicator (feather or theatre) shows which way a junction is set, a call-on
 * shows "stop and proceed", and a trip cock is the arm beside the rail that trips the brakes of a
 * train passing a red.</p>
 */
public enum SignalHeadStyle implements IStringSerializable {
    /** Modern LED metro head: three lenses stacked red, yellow, green. */
    LED("led", 3, SignalHeadMount.POST),
    /** Searchlight: one lens that changes colour. */
    SEARCHLIGHT("searchlight", 3, SignalHeadMount.POST),
    /** Compact dwarf/ground LED signal: a stop lens over a proceed lens. */
    DWARF("dwarf", 3, SignalHeadMount.GROUND),
    /** Two-aspect LED head, red over green: the CBTC metro's block signal. */
    LED_TWO("led_two", 2, SignalHeadMount.POST),
    /** Three-aspect LED head with a lunar-white lamp below for the subsidiary aspect. */
    LED_LUNAR("led_lunar", 3, SignalHeadMount.POST),
    /** Platform-end starter: red over green with a "departure" indicator that lights to send a train. */
    STARTER("starter", 2, SignalHeadMount.PLATFORM),
    /** Classic three-aspect colour light with long hoods on a round-cornered backplate. */
    COLOUR_LIGHT("colour_light", 3, SignalHeadMount.MAST),
    /** Classic four-aspect colour light: yellow, green, yellow, red, top to bottom. */
    COLOUR_LIGHT_FOUR("colour_light_four", 4, SignalHeadMount.MAST),
    /** Semaphore stop signal, red arm, rising to clear. */
    SEMAPHORE_UPPER_HOME("semaphore_upper_home", 2, SignalHeadMount.MAST),
    /** Semaphore stop signal, red arm, dropping to clear. */
    SEMAPHORE_LOWER_HOME("semaphore_lower_home", 2, SignalHeadMount.MAST),
    /** Semaphore distant, yellow fishtail arm, rising to clear: caution until the stop signal ahead is off. */
    SEMAPHORE_UPPER_DISTANT("semaphore_upper_distant", 2, SignalHeadMount.MAST),
    /** Semaphore distant, yellow fishtail arm, dropping to clear. */
    SEMAPHORE_LOWER_DISTANT("semaphore_lower_distant", 2, SignalHeadMount.MAST),
    /** Position light: a row of amber lamps, horizontal for stop, diagonal for caution, vertical for clear. */
    POSITION_LIGHT("position_light", 3, SignalHeadMount.MAST),
    /** Colour-position light: pairs of lamps, red horizontal, yellow diagonal, green vertical. */
    COLOUR_POSITION_LIGHT("colour_position_light", 3, SignalHeadMount.MAST),
    /** Junction route indicator: a feather of lunar-white lamps lit when its switch is set its way. */
    ROUTE_FEATHER("route_feather", 2, SignalHeadMount.POST),
    /** Theatre route indicator: a box showing a number or letter for the route its switch is set to. */
    THEATRE("theatre", 2, SignalHeadMount.POST),
    /** Call-on: a "stop and proceed" plate with a small lunar-white lamp. */
    CALL_ON("call_on", 2, SignalHeadMount.POST),
    /** Train stop (trip cock): an arm beside the rail, raised while its signal is red. */
    TRIP_COCK("trip_cock", 2, SignalHeadMount.GROUND);

    private static final SignalHeadStyle[] VALUES = values();

    private final String name;
    private final int aspects;
    private final SignalHeadMount defaultMount;

    SignalHeadStyle(String name, int aspects, SignalHeadMount defaultMount) {
        this.name = name;
        this.aspects = aspects;
        this.defaultMount = defaultMount;
    }

    @Override
    public String getName() {
        return name;
    }

    /** How many aspects it can show: 2, 3 or 4. */
    public int aspects() {
        return aspects;
    }

    /** The mount it is placed with when placed on top of a block. */
    public SignalHeadMount defaultMount() {
        return defaultMount;
    }

    /** A route indicator shows which way a junction is set, from a linked switch. */
    public boolean isRouteIndicator() {
        return this == ROUTE_FEATHER || this == THEATRE;
    }

    /** A semaphore: an arm that moves, rather than lamps alone. */
    public boolean isSemaphore() {
        return this == SEMAPHORE_UPPER_HOME || this == SEMAPHORE_LOWER_HOME
            || this == SEMAPHORE_UPPER_DISTANT || this == SEMAPHORE_LOWER_DISTANT;
    }

    /** A distant: it repeats the stop signal ahead, so it is "on" for caution as well as stop. */
    public boolean isDistant() {
        return this == SEMAPHORE_UPPER_DISTANT || this == SEMAPHORE_LOWER_DISTANT;
    }

    /** It has a subsidiary lamp (lunar white, call-on or departure) that can be lit on its own. */
    public boolean hasSubsidiary() {
        return this == LED_LUNAR || this == STARTER || this == CALL_ON;
    }

    public static SignalHeadStyle fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : LED;
    }

    public static SignalHeadStyle byName(String name) {
        for (SignalHeadStyle style : VALUES) {
            if (style.name.equalsIgnoreCase(name)) {
                return style;
            }
        }
        return null;
    }
}
