package com.micatechnologies.minecraft.rcmc.client.render;

import com.micatechnologies.minecraft.rcmc.physics.CarSeating;
import net.minecraft.client.renderer.BufferBuilder;

/**
 * Geometry for a metro car, emitted in the same local frame as {@link CarModel} (+X right,
 * +Y up, +Z travel, origin on the track centreline) and under the same conventions: flat colour
 * shaded by which way a face points, winding unmaintained because the renderer draws with culling
 * off. Mostly boxes; the round and sloped parts — wheels, air tanks, the roof — come from
 * {@link CarMesh}.
 *
 * <p>Proportioned from real North American heavy-rail stock at 1 block ≈ 1 metre (see
 * {@code TrainSpec}'s metro presets): the body is <b>3 blocks wide</b> — real bodies run
 * 2.7–3.05 m — with a high floor over the trucks, a continuous window band, door pairs along
 * each side, and longitudinal bench seating. The body is deliberately <b>double-ended and
 * symmetric</b>, like real metro stock: a metro reverses at every terminus, so unlike the
 * coaster model there must be no "nose" for the direction flip to point the wrong way.</p>
 *
 * <p><b>Length convention.</b> The {@code bogieSpacing} passed in is {@code TrainSpec.carLength}
 * — bogie-centre distance, per that class's documented convention. The visible body extends
 * beyond the bogies by the ratio real stock uses (trucks at ~72% of body length), so
 * {@code bodyLength = bogieSpacing / 0.72}. The metro presets size their coupling gaps against
 * this same ratio, leaving {@code GAP_CLEARANCE} of daylight between bodies; keep the two in
 * step or couplers will float or overlap.</p>
 *
 * <p>The window band is left as genuine openings (pillars only, no glass), so riders inside are
 * visible — the placeholder-model equivalent of looking into a lit car.</p>
 */
final class MetroCarModel {

    /** Trucks sit at this fraction of body length; see the class javadoc. */
    private static final float TRUCK_CENTRE_RATIO = 0.72F;

    /** Daylight between adjacent car bodies; must match the metro presets' coupling gaps. */
    private static final float GAP_CLEARANCE = 0.7F;

    private static final float RAIL_TOP = 0.05F;

    // --- Underframe and floor: high-floor stock riding above its trucks. -----------------------
    // Raised a full block and thickened, 2026-07-21: real metro stock carries a deep underframe of
    // traction gear, air tanks and cabling between the trucks, and the earlier slab read as the car
    // resting straight on its wheels. Everything in the saloon is measured from FLOOR_TOP, so
    // raising it here carries the whole interior up with it and leaves the clear height unchanged.
    //
    // A FULL block rather than the half first tried, because block tops are integers: at 1.5 a
    // platform could only ever land half a block off the car floor, and boarding a train should not
    // involve a step. At 2.0 the platform surface and the saloon floor are exactly level.
    private static final float SKIRT_BOTTOM = RAIL_TOP + 0.22F;
    private static final float FLOOR_TOP = 2.00F;

    /** Depth of the floor slab itself, under the saloon and over the underframe. */
    private static final float FLOOR_SLAB = 0.35F;

    // --- Body shell. ---------------------------------------------------------------------------
    // Sized for a Minecraft player rather than to metric scale. A 1.8-tall player standing on a
    // 1.0 floor needs real headroom, and the ceiling destination sign hangs above that again, so
    // the saloon is 3.75 clear — well over the ~2.4 m a real metro gives a 1.8 m passenger. The
    // earlier 3.55-tall, 3.0-wide body was to scale and still read as cramped from the inside,
    // which is the measurement that actually matters here.
    private static final float BODY_HALF_WIDTH = 1.90F;
    private static final float WALL_THICKNESS = 0.10F;
    private static final float WINDOW_SILL = 3.05F;
    private static final float WINDOW_HEAD = 5.15F;
    private static final float ROOF_BASE = 5.75F;
    private static final float ROOF_TOP = 5.95F;

    /** Clear interior height: floor to ceiling. Asserted by {@code MetroCarScaleTest}. */
    static final float INTERIOR_HEIGHT = ROOF_BASE - FLOOR_TOP;

    // --- Pantograph: a raised frame reaching from the roof to just under the contact wire. ------
    private static final float PANTO_BASE_TOP = ROOF_TOP + 0.18F;

    /**
     * Clearance between the contact wire's underside and the pantograph shoe.
     *
     * <p>The wire is drawn with a {@code 0.03} half-height, so the shoe stops just under that and
     * the two read as touching without z-fighting. The shoe height is no longer a constant: it is
     * derived from whatever height the section's own style puts the wire at, so a pantograph
     * stretches to meet a 6-block wire or a 15-block one without anything else changing.</p>
     */
    private static final float PANTO_WIRE_CLEARANCE = 0.07F;

    private static final float PANTO_HALF_SPAN = 0.95F;

    /** Window pillars, roughly one per body panel. */
    private static final float PILLAR_WIDTH = 0.16F;
    private static final float PANEL_PITCH = 2.4F;

    // --- Doors: paired sliding doors, drawn slightly proud of the wall in trim colour. ---------
    // Width comes from CarSeating, which also decides where a rider may walk through an opening.
    // Three things have to agree about where a door is — this model, the walkable bounds, and the
    // test for "has stepped out" — so exactly one of them owns the number.
    private static final float DOOR_WIDTH = (float) (CarSeating.DOOR_HALF_WIDTH * 2.0D);
    private static final float DOOR_PROUD = 0.03F;

    /** Thickness of a door leaf, and the width of the frame around its window. */
    private static final float LEAF_THICKNESS = 0.06F;
    private static final float LEAF_FRAME = 0.16F;

    /** The window set into each door leaf — roughly chest to head height for a standing rider. */
    private static final float DOOR_WINDOW_BOTTOM = FLOOR_TOP + 1.05F;
    private static final float DOOR_WINDOW_TOP = FLOOR_TOP + 2.75F;

    // --- Longitudinal bench seating along each wall. -------------------------------------------
    private static final float SEAT_DEPTH = 0.55F;
    private static final float SEAT_TOP = FLOOR_TOP + 0.45F;
    private static final float SEAT_BACK_TOP = FLOOR_TOP + 1.15F;

    // --- Trucks: longer and heavier than a coaster bogie, straddling the rails outboard. -------
    // Wheels ride the railheads, which the transit style puts at +/-1.40.
    private static final float TRUCK_INNER = 0.90F;
    private static final float TRUCK_OUTER = 1.32F;
    private static final float TRUCK_HALF_LENGTH = 1.10F;
    private static final float TRUCK_TOP = RAIL_TOP + 0.72F;
    private static final float TRUCK_BOTTOM = RAIL_TOP - 0.12F;

    private static final float COUPLER_HALF_WIDTH = 0.10F;
    private static final float COUPLER_BOTTOM = RAIL_TOP + 0.45F;
    private static final float COUPLER_TOP = RAIL_TOP + 0.80F;

    private static final float[] UNDERFRAME_COLOR = {0.20F, 0.20F, 0.22F};
    private static final float[] TRUCK_COLOR = {0.28F, 0.29F, 0.32F};
    private static final float[] ROOF_COLOR = {0.45F, 0.46F, 0.48F};

    // --- Interior fittings, from the MBTA Red Line reference the owner supplied. ---------------
    /** Safety yellow: stanchions, grab rails, door thresholds. The colour that reads "transit". */
    private static final float[] GRAB_YELLOW = {0.95F, 0.76F, 0.13F};

    /** Moulded grey of seat shells, wall panels and the ceiling vent run. */
    private static final float[] SHELL_COLOR = {0.72F, 0.73F, 0.75F};

    private static final float[] FLOOR_COLOR = {0.30F, 0.31F, 0.33F};
    private static final float[] STRAP_COLOR = {0.80F, 0.81F, 0.83F};

    /** Ceiling fluorescents: near-white lit, dead grey unlit. */
    private static final float[] LIGHT_ON_COLOR = {1.00F, 0.98F, 0.90F};
    private static final float[] LIGHT_OFF_COLOR = {0.50F, 0.51F, 0.53F};

    /** Half-width of the cab windshield opening at an outer car end. */
    private static final float END_WINDOW_HALF_WIDTH = 1.15F;

    // --- Gangway door on the ends that face the next car. --------------------------------------
    private static final float END_DOOR_WIDTH = 1.30F;
    private static final float END_DOOR_TOP = FLOOR_TOP + 2.60F;
    private static final float END_DOOR_WINDOW_HALF = 0.42F;
    private static final float END_DOOR_WINDOW_BOTTOM = FLOOR_TOP + 1.30F;
    private static final float END_DOOR_WINDOW_TOP = FLOOR_TOP + 2.20F;

    /** Driving cab depth, shared with {@code CarSeating} so no passenger is seated inside it. */
    private static final float CAB_DEPTH = (float) CarSeating.CAB_DEPTH;
    private static final float PARTITION_HALF_THICKNESS = 0.05F;
    private static final float CAB_DOOR_HALF = 0.42F;
    private static final float CAB_DOOR_TOP = FLOOR_TOP + 2.35F;
    private static final float CAB_DOOR_WINDOW_BOTTOM = FLOOR_TOP + 1.35F;
    private static final float CAB_DOOR_WINDOW_TOP = FLOOR_TOP + 2.10F;

    /** The driver's desk runs across the cab under the windscreen. */
    private static final float DESK_DEPTH = 0.70F;
    private static final float DESK_TOP = FLOOR_TOP + 0.98F;
    private static final float[] DESK_COLOR = {0.19F, 0.20F, 0.22F};
    private static final float[] SCREEN_COLOR = {0.08F, 0.14F, 0.20F};
    private static final float[] DRIVER_SEAT_COLOR = {0.22F, 0.24F, 0.30F};
    private static final float[] CAB_WINDOW_COLOR = {0.22F, 0.27F, 0.31F};

    /**
     * Head and tail lamps, low on each cab front either side of the coupler: a housing that is
     * always there, and a lens lit white at the leading end and red at the trailing one.
     */
    private static final float LAMP_CENTRE_X = 1.35F;
    private static final float LAMP_HALF_WIDTH = 0.22F;
    private static final float LAMP_BOTTOM = FLOOR_TOP + 0.30F;
    private static final float LAMP_TOP = FLOOR_TOP + 0.62F;
    private static final float[] LAMP_HOUSING_COLOR = {0.10F, 0.10F, 0.11F};
    private static final float[] HEADLAMP_COLOR = {1.00F, 0.97F, 0.84F};
    private static final float[] TAIL_LAMP_COLOR = {1.00F, 0.10F, 0.06F};

    /** What a cab end's lamps show. */
    enum Lamp {
        /** Not a cab end, or the lamps are off. */
        OFF,
        /** The leading end: white headlamps. */
        HEAD,
        /** The trailing end: red tail lamps. */
        TAIL
    }

    private static final float POLE_HALF = 0.07F;
    private static final float GRAB_RAIL_U = ROOF_BASE - 0.35F;

    /**
     * Inboard offset of the stanchions and ceiling rails from the wall face.
     *
     * <p>Far enough in to clear the benches, which reach {@code SEAT_DEPTH} from the wall: a pole
     * standing in the middle of a seat cushion is worse than no pole. This puts them at the aisle
     * edge, which is where a standing passenger holds one anyway.</p>
     */
    private static final float FITTING_INSET = 0.85F;

    /** Spacing of the hanging strap handles along each ceiling rail, in blocks. */
    private static final float STRAP_SPACING = 1.5F;

    /**
     * How far short of each car end the ceiling rails stop.
     *
     * <p>{@code MetroInteriorSign} hangs its panels {@code END_INSET} (0.9) in from the ends at
     * ceiling height and spans the full car width, so a rail running the whole length would pass
     * straight through them. Kept clear here rather than there because the sign is the fixed
     * point — it has to be where a rider looks.</p>
     */
    private static final float RAIL_END_GAP = 1.4F;

    // --- Exterior detail: what makes the outside read as rolling stock rather than as a box. ---
    /** Window gaskets, door tracks and the kick strip: the black rubber and shadow lines. */
    private static final float[] GASKET_COLOR = {0.09F, 0.09F, 0.10F};
    private static final float[] EQUIPMENT_COLOR = {0.27F, 0.28F, 0.30F};
    private static final float[] EQUIPMENT_DARK = {0.17F, 0.17F, 0.19F};
    private static final float[] TANK_COLOR = {0.34F, 0.35F, 0.36F};
    private static final float[] WHEEL_COLOR = {0.15F, 0.15F, 0.16F};
    private static final float[] WHEEL_FACE_COLOR = {0.40F, 0.41F, 0.43F};
    private static final float[] ROOF_UNIT_COLOR = {0.36F, 0.37F, 0.39F};
    private static final float[] BELLOWS_COLOR = {0.12F, 0.12F, 0.13F};

    /** How far a painted band or gasket stands off the surface it is on: enough not to z-fight. */
    private static final float PLATE_PROUD = 0.012F;

    /** The livery band along the body under the windows, in the train's trim colour. */
    private static final float STRIPE_BOTTOM = FLOOR_TOP + 0.62F;
    private static final float STRIPE_TOP = FLOOR_TOP + 0.84F;

    /** Wheels: on the railheads, which the transit style puts 1.40 either side of the centreline. */
    private static final float WHEEL_RADIUS = 0.43F;
    private static final float WHEEL_X = 1.40F;
    private static final float WHEEL_HALF_WIDTH = 0.07F;
    private static final float AXLE_OFFSET = 0.72F;

    /** Roof units stand this far above the roof's crown; under the pantograph's base frame. */
    private static final float ROOF_UNIT_TOP = ROOF_TOP + 0.15F;

    /** How far a gangway bellows reaches toward the next car: just under half the gap between bodies. */
    private static final float BELLOWS_DEPTH = GAP_CLEARANCE * 0.5F - 0.02F;

    /** What of a car {@link #emit} and {@link #emitGlazing} draw; see {@link #setParts}. */
    enum Parts {
        /** The whole car. */
        ALL,
        /** Everything but the door leaves and their glass: the part that never moves. */
        BODY,
        /** Only the door leaves and their glass. */
        DOORS
    }

    private static Parts parts = Parts.ALL;

    /**
     * Restricts the next {@link #emit} and {@link #emitGlazing} to the fixed body or to the door
     * leaves, so the renderer can keep the body compiled while the leaves travel. Render thread
     * only; set it back to {@link Parts#ALL} after.
     */
    static void setParts(Parts value) {
        parts = value;
    }

    // --- Glazing: thin, cool-tinted, and mostly transparent so riders stay visible. ------------
    private static final float[] GLASS_COLOR = {0.62F, 0.76F, 0.82F};
    private static final float GLASS_ALPHA = 0.28F;
    private static final float GLASS_THICKNESS = 0.05F;

    private MetroCarModel() {
        throw new AssertionError("No instances.");
    }

    /**
     * Emits one metro car.
     *
     * @param bogieSpacing   {@code TrainSpec.carLength} — bogie-centre distance in blocks; the
     *                       body is derived longer, see the class javadoc
     * @param drawCoupling   whether to draw the rear coupler bar (not the last car)
     * @param drawPantograph whether this car carries a pantograph — alternate cars, like a real
     *                       EMU consist
     * @param wireHeight     height of the section's contact wire above the railheads; the
     *                       pantograph stretches to meet it, so raising a line's catenary raises
     *                       every pantograph under it with no other change
     * @param doorFraction   how far the leaves have slid, 0 shut to 1 open — animated, so the
     *                       doors visibly travel rather than snapping between two states
     * @param lightsOn       whether the saloon lighting is lit; see {@code RenderCoasterCar}
     * @param outerFront     whether the {@code +z} end faces out of the consist (a cab end) rather
     *                       than onto the next car
     * @param outerRear      likewise for the {@code -z} end
     */
    static void emit(BufferBuilder buffer, float bogieSpacing, boolean drawCoupling,
                     boolean drawPantograph, float wireHeight, float doorFraction,
                     boolean lightsOn, boolean outerFront, boolean outerRear,
                     float[] bodyColour, float[] trimColour, float[] seatColour) {
        emit(buffer, bogieSpacing, drawCoupling, drawPantograph, wireHeight, false, doorFraction,
            doorFraction, lightsOn, outerFront, outerRear, bodyColour, trimColour, seatColour);
    }

    /**
     * As above, but with the two sides animated independently.
     *
     * <p>Most stations have a platform on one side only, and doors that open onto the trackbed
     * look wrong and invite a passenger to step out of one. The sides are given in the car's own
     * model axes, which are the TRACK's axes: {@code +x} is {@code frame.right} whichever way the
     * train is running (see {@code RenderCoasterCar}, which loads the frame straight onto the
     * matrix stack, and note that metro stock is double-ended so it is never flipped).</p>
     *
     * @param rightFraction how far the {@code +x} side's leaves have slid, 0 shut to 1 open
     * @param leftFraction  likewise for the {@code -x} side
     */
    static void emit(BufferBuilder buffer, float bogieSpacing, boolean drawCoupling,
                     boolean drawPantograph, float wireHeight, boolean thirdRail, float rightFraction,
                     float leftFraction, boolean lightsOn, boolean outerFront, boolean outerRear,
                     float[] bodyColour, float[] trimColour, float[] seatColour) {
        float bodyLength = bogieSpacing / TRUCK_CENTRE_RATIO;
        float half = bodyLength * 0.5F;
        passHalf = half;
        // Unlit, the interior is not black — it is dim. Everything inside is drawn through this
        // factor so switching the lights off dims the saloon rather than only dulling the fittings.
        float shade = lightsOn ? 1.0F : 0.55F;
        // The saloon stops at a driving cab's partition; seats, fittings and lights stay behind it.
        float saloonFront = outerFront ? half - CAB_DEPTH : half;
        float saloonRear = outerRear ? -half + CAB_DEPTH : -half;
        float truckAt = bogieSpacing * 0.5F;

        if (parts == Parts.DOORS) {
            // The leaves alone: emitSide draws nothing else in this mode.
            emitSide(buffer, half, BODY_HALF_WIDTH - WALL_THICKNESS, BODY_HALF_WIDTH,
                bodyColour, trimColour, rightFraction, shade);
            emitSide(buffer, half, -BODY_HALF_WIDTH, -BODY_HALF_WIDTH + WALL_THICKNESS,
                bodyColour, trimColour, leftFraction, shade);
            return;
        }

        // The underframe's equipment between the trucks, and the floor slab the interior stands on.
        emitUnderframe(buffer, half, truckAt, outerFront, outerRear);
        box(buffer, -BODY_HALF_WIDTH, FLOOR_TOP - FLOOR_SLAB, -half,
            BODY_HALF_WIDTH, FLOOR_TOP, half, UNDERFRAME_COLOR);

        emitSide(buffer, half, BODY_HALF_WIDTH - WALL_THICKNESS, BODY_HALF_WIDTH,
            bodyColour, trimColour, rightFraction, shade);
        emitSide(buffer, half, -BODY_HALF_WIDTH, -BODY_HALF_WIDTH + WALL_THICKNESS,
            bodyColour, trimColour, leftFraction, shade);

        // Ends differ now: an end facing out of the consist is a cab with a windshield, an end
        // facing the next car carries a gangway door. The body itself stays symmetric — which end
        // is which is decided by the car's place in the train, not by the model having a nose.
        emitEnd(buffer, half - WALL_THICKNESS, half, bodyColour, trimColour, outerFront, shade);
        emitEnd(buffer, -half, -half + WALL_THICKNESS, bodyColour, trimColour, outerRear, shade);

        emitRoof(buffer, half, drawPantograph && !thirdRail && wireHeight > 0.0F);
        if (!outerFront) {
            emitBellows(buffer, half, 1);
        }
        if (!outerRear) {
            emitBellows(buffer, half, -1);
        }

        emitFloor(buffer, half, shade);
        emitBenches(buffer, half, saloonRear, saloonFront, seatColour, shade);
        emitFittings(buffer, half, saloonRear, saloonFront, shade);
        emitCeiling(buffer, half, saloonRear, saloonFront, lightsOn);
        if (outerFront) {
            emitCab(buffer, half, 1, trimColour, shade);
        }
        if (outerRear) {
            emitCab(buffer, half, -1, trimColour, shade);
        }

        emitTruck(buffer, truckAt);
        emitTruck(buffer, -truckAt);

        if (thirdRail) {
            // Collecting from beside the track: shoegear on both sides of each truck, because the
            // conductor changes sides at platforms. Nothing on the roof to reach for.
            emitShoes(buffer, truckAt);
            emitShoes(buffer, -truckAt);
        }
        else if (drawPantograph) {
            emitPantograph(buffer, wireHeight);
        }

        if (drawCoupling) {
            box(buffer, -COUPLER_HALF_WIDTH, COUPLER_BOTTOM, -half - GAP_CLEARANCE,
                COUPLER_HALF_WIDTH, COUPLER_TOP, -half, UNDERFRAME_COLOR);
        }
    }

    /**
     * One side wall: solid below the sill and above the head, pillars through the window band,
     * and door pairs (in trim) spaced along the car. Between pillars the band is open — that is
     * the "glass".
     */
    private static void emitSide(BufferBuilder buffer, float half, float xInner, float xOuter,
                                 float[] bodyColour, float[] trimColour, float doorFraction,
                                 float shade) {
        float[] doorCentres = doorCentres(half);
        float doorHalf = DOOR_WIDTH * 0.5F;
        if (parts != Parts.DOORS) {
            emitSideWall(buffer, half, xInner, xOuter, bodyColour, trimColour, doorCentres, doorHalf, shade);
        }
        if (parts != Parts.BODY) {
            emitLeaves(buffer, xOuter, trimColour, doorCentres, doorHalf, doorFraction, shade);
        }
    }

    /** The fixed part of a side: panels, pillars, cant rail, thresholds, and the painted detail. */
    private static void emitSideWall(BufferBuilder buffer, float half, float xInner, float xOuter,
                                     float[] bodyColour, float[] trimColour, float[] doorCentres,
                                     float doorHalf, float shade) {
        float sign = xOuter > 0.0F ? 1.0F : -1.0F;
        // Band below the windows, broken by the doorways. This is a real opening, not a panel
        // painted on a solid wall: with the doors drawn open a rider must be able to see — and
        // walk — through it, which is the whole point of a door.
        float from = -half;
        for (float centre : doorCentres) {
            if (centre - doorHalf > from) {
                box(buffer, xInner, FLOOR_TOP, from, xOuter, WINDOW_SILL, centre - doorHalf,
                    bodyColour);
            }
            from = centre + doorHalf;
        }
        if (half > from) {
            box(buffer, xInner, FLOOR_TOP, from, xOuter, WINDOW_SILL, half, bodyColour);
        }

        // The cant rail above the windows runs unbroken — a doorway's header is structure.
        box(buffer, xInner, WINDOW_HEAD, -half, xOuter, ROOF_BASE, half, bodyColour);

        // Window pillars, skipping any that would stand in a doorway.
        for (float at = -half + PANEL_PITCH; at < half - PANEL_PITCH * 0.5F; at += PANEL_PITCH) {
            if (inDoorway(at, doorCentres, doorHalf + PILLAR_WIDTH)) {
                continue;
            }
            box(buffer, xInner, WINDOW_SILL, at - PILLAR_WIDTH * 0.5F,
                xOuter, WINDOW_HEAD, at + PILLAR_WIDTH * 0.5F, bodyColour);
            // Outside, a pillar is black: the window band reads as one dark ribbon down the car,
            // as it does on stock with bonded glazing, instead of a row of holes in a grey wall.
            plateX(buffer, sign, BODY_HALF_WIDTH, WINDOW_SILL, at - PILLAR_WIDTH * 0.5F,
                WINDOW_HEAD, at + PILLAR_WIDTH * 0.5F, GASKET_COLOR);
        }

        // Painted and fitted detail on the outer skin, each run broken at the doorways like the
        // panel it is on: the livery band, the gaskets along the sill and head of the window band,
        // and the kick strip at floor level.
        // The wall is given low x first, so on the left "xOuter" is its inner face. The skin is
        // whichever is further out.
        float skin = Math.max(Math.abs(xInner), Math.abs(xOuter));
        float wallInner = Math.min(Math.abs(xInner), Math.abs(xOuter));
        float runFrom = -half;
        for (int i = 0; i <= doorCentres.length; i++) {
            float runTo = i < doorCentres.length ? doorCentres[i] - doorHalf : half;
            if (runTo > runFrom) {
                plateX(buffer, sign, skin, STRIPE_BOTTOM, runFrom, STRIPE_TOP, runTo, trimColour);
                plateX(buffer, sign, skin, WINDOW_SILL - 0.05F, runFrom, WINDOW_SILL + 0.03F, runTo, GASKET_COLOR);
                plateX(buffer, sign, skin, WINDOW_HEAD - 0.03F, runFrom, WINDOW_HEAD + 0.05F, runTo, GASKET_COLOR);
                plateX(buffer, sign, skin, FLOOR_TOP, runFrom, FLOOR_TOP + 0.10F, runTo, GASKET_COLOR);
            }
            if (i < doorCentres.length) {
                runFrom = doorCentres[i] + doorHalf;
                // The door track cover over each doorway, and the dark reveal either side of it.
                box(buffer, sign * skin, WINDOW_HEAD, doorCentres[i] - doorHalf - 0.12F,
                    sign * (skin + DOOR_PROUD + 0.03F), WINDOW_HEAD + 0.12F, doorCentres[i] + doorHalf + 0.12F,
                    GASKET_COLOR);
            }
        }
        // Rain gutter under the roof edge, the whole length of the car.
        box(buffer, sign * skin, ROOF_BASE - 0.08F, -half, sign * (skin + 0.035F), ROOF_BASE - 0.02F, half,
            ROOF_COLOR);

        // Yellow threshold strip on the floor at each doorway.
        for (float centre : doorCentres) {
            box(buffer, sign * (wallInner - 0.45F), FLOOR_TOP,
                centre - doorHalf, sign * wallInner, FLOOR_TOP + 0.03F,
                centre + doorHalf, shaded(GRAB_YELLOW, shade));
        }
    }

    /**
     * The door leaves of one side: a pair per bay, meeting at the centre when shut and slid back
     * over the wall when open. Open leaves are drawn proud of the wall, as real external sliding
     * doors sit, so "open" reads at a glance from outside the train.
     */
    private static void emitLeaves(BufferBuilder buffer, float xOuter, float[] trimColour,
                                   float[] doorCentres, float doorHalf, float doorFraction, float shade) {
        float sign = xOuter > 0.0F ? 1.0F : -1.0F;
        // Proud of the body skin on both sides. Measured from the wall's second coordinate, the
        // left side's leaves stood a wall's thickness further in than the right's, and than their
        // own glass.
        float doorOuter = BODY_HALF_WIDTH + DOOR_PROUD;
        float leafHalf = doorHalf * 0.5F;
        float xLeafIn = sign * (doorOuter - LEAF_THICKNESS);
        float xLeafOut = sign * doorOuter;
        for (float centre : doorCentres) {
            for (int side = -1; side <= 1; side += 2) {
                float leafCentre = leafCentre(centre, side, doorHalf, leafHalf, doorFraction);
                float zWindowMin = leafCentre - leafHalf + LEAF_FRAME;
                float zWindowMax = leafCentre + leafHalf - LEAF_FRAME;

                // The leaf is drawn as a FRAME around a real opening, not as a slab: its glass sits
                // inside that opening and slides with it. Drawing a solid leaf and laying a pane
                // over it is what produced a visible second layer of glass on the outside of the
                // doors — the pane was in the wall plane while the leaf stood proud of it.
                box(buffer, xLeafIn, FLOOR_TOP, leafCentre - leafHalf,
                    xLeafOut, DOOR_WINDOW_BOTTOM, leafCentre + leafHalf, trimColour);
                box(buffer, xLeafIn, DOOR_WINDOW_TOP, leafCentre - leafHalf,
                    xLeafOut, WINDOW_HEAD, leafCentre + leafHalf, trimColour);
                box(buffer, xLeafIn, DOOR_WINDOW_BOTTOM, leafCentre - leafHalf,
                    xLeafOut, DOOR_WINDOW_TOP, zWindowMin, trimColour);
                box(buffer, xLeafIn, DOOR_WINDOW_BOTTOM, zWindowMax,
                    xLeafOut, DOOR_WINDOW_TOP, leafCentre + leafHalf, trimColour);

                // Yellow leading edge, as every modern metro door has.
                box(buffer, sign * (doorOuter - LEAF_THICKNESS - 0.01F), FLOOR_TOP,
                    leafCentre - side * leafHalf - 0.05F, sign * (doorOuter + 0.01F), WINDOW_HEAD,
                    leafCentre - side * leafHalf + 0.05F, shaded(GRAB_YELLOW, shade));
            }
        }
    }

    /** The saloon floor: a dark wearing surface over the structural slab. */
    private static void emitFloor(BufferBuilder buffer, float half, float shade) {
        float inner = BODY_HALF_WIDTH - WALL_THICKNESS;
        box(buffer, -inner, FLOOR_TOP, -half + WALL_THICKNESS,
            inner, FLOOR_TOP + 0.02F, half - WALL_THICKNESS, shaded(FLOOR_COLOR, shade));
    }

    /**
     * Stanchions, ceiling grab rails and hanging straps — everything a standing passenger holds.
     *
     * <p>Straight from the reference interior: floor-to-ceiling poles flanking every doorway, a
     * longitudinal rail down each side just under the ceiling, and strap handles hanging off it at
     * intervals. This is most of what makes a saloon read as a metro rather than as a corridor;
     * an empty box with benches reads as neither.</p>
     */
    private static void emitFittings(BufferBuilder buffer, float half, float saloonRear,
                                     float saloonFront, float shade) {
        float inner = BODY_HALF_WIDTH - WALL_THICKNESS;
        float at = inner - FITTING_INSET;
        float[] yellow = shaded(GRAB_YELLOW, shade);
        float[] strap = shaded(STRAP_COLOR, shade);
        float doorHalf = DOOR_WIDTH * 0.5F;

        for (int side = -1; side <= 1; side += 2) {
            float x = side * at;

            // Vertical stanchions flanking each doorway.
            for (float centre : doorCentres(half)) {
                for (int edge = -1; edge <= 1; edge += 2) {
                    float z = centre + edge * (doorHalf + 0.20F);
                    box(buffer, x - POLE_HALF, FLOOR_TOP, z - POLE_HALF,
                        x + POLE_HALF, ROOF_BASE, z + POLE_HALF, yellow);
                }
            }

            // Longitudinal ceiling grab rail down the length of the saloon, stopping short of the
            // ends so it does not run through the ceiling destination signs hanging there.
            box(buffer, x - POLE_HALF * 0.8F, GRAB_RAIL_U - POLE_HALF * 0.8F, saloonRear + RAIL_END_GAP,
                x + POLE_HALF * 0.8F, GRAB_RAIL_U + POLE_HALF * 0.8F, saloonFront - RAIL_END_GAP, yellow);

            // Hanging strap handles, skipping the doorways so none dangles in a doorway.
            for (float z = saloonRear + RAIL_END_GAP + 0.3F; z < saloonFront - RAIL_END_GAP;
                 z += STRAP_SPACING) {
                if (inDoorway(z, doorCentres(half), doorHalf)) {
                    continue;
                }
                box(buffer, x - 0.03F, GRAB_RAIL_U - 0.42F, z - 0.02F,
                    x + 0.03F, GRAB_RAIL_U, z + 0.02F, strap);
                box(buffer, x - 0.11F, GRAB_RAIL_U - 0.56F, z - 0.03F,
                    x + 0.11F, GRAB_RAIL_U - 0.40F, z + 0.03F, strap);
            }
        }
    }

    /**
     * The ceiling: a vent run down the centre, flanked by the two continuous light strips.
     *
     * <p>The strips are drawn bright when lit and dead grey when not, which is the entire visible
     * effect of the saloon lighting — the car is drawn with GL lighting off, so nothing here
     * responds to world light on its own and the switch has to be explicit.</p>
     */
    private static void emitCeiling(BufferBuilder buffer, float half, float saloonRear,
                                    float saloonFront, boolean lightsOn) {
        float inner = BODY_HALF_WIDTH - WALL_THICKNESS;
        // Centre vent panel, slightly proud of the ceiling.
        box(buffer, -0.80F, ROOF_BASE - 0.07F, -half + 0.35F,
            0.80F, ROOF_BASE, half - 0.35F, SHELL_COLOR);

        float[] lamp = lightsOn ? LIGHT_ON_COLOR : LIGHT_OFF_COLOR;
        for (int side = -1; side <= 1; side += 2) {
            float x = side * (inner - 0.62F);
            box(buffer, x - 0.26F, ROOF_BASE - 0.10F, saloonRear + 0.6F,
                x + 0.26F, ROOF_BASE - 0.02F, saloonFront - 0.6F, lamp);
        }
    }

    /**
     * A driving cab at one outer end: the partition that closes it off from the saloon, with the
     * cab door in it, and the driver's desk and seat behind the windscreen. {@code s} is {@code +1}
     * for the front ({@code +z}) end and {@code -1} for the rear.
     *
     * <p>The driver sits on their left, looking out of the cab, as on the reference stock — which
     * is {@code -x} at the front end and {@code +x} at the rear, since the rear cab looks the other
     * way down the same axes.</p>
     */
    private static void emitCab(BufferBuilder buffer, float half, int s, float[] trimColour,
                                float shade) {
        float inner = BODY_HALF_WIDTH - WALL_THICKNESS;
        float wall = half - WALL_THICKNESS;
        float[] shell = shaded(SHELL_COLOR, shade);

        // Partition, full height, with a cab door in the middle; it faces both the saloon and the cab.
        float p = half - CAB_DEPTH;
        float pa = s * (p - PARTITION_HALF_THICKNESS);
        float pb = s * (p + PARTITION_HALF_THICKNESS);
        box(buffer, -inner, FLOOR_TOP, pa, -CAB_DOOR_HALF, ROOF_BASE, pb, shell);
        box(buffer, CAB_DOOR_HALF, FLOOR_TOP, pa, inner, ROOF_BASE, pb, shell);
        box(buffer, -CAB_DOOR_HALF, CAB_DOOR_TOP, pa, CAB_DOOR_HALF, ROOF_BASE, pb, shell);
        float da = s * (p - PARTITION_HALF_THICKNESS * 0.6F);
        float db = s * (p + PARTITION_HALF_THICKNESS * 0.6F);
        box(buffer, -CAB_DOOR_HALF, FLOOR_TOP, da, CAB_DOOR_HALF, CAB_DOOR_TOP, db, shaded(DESK_COLOR, 1.6F * shade));
        float fa = s * (p - PARTITION_HALF_THICKNESS - 0.02F);
        float fb = s * (p + PARTITION_HALF_THICKNESS + 0.02F);
        box(buffer, -CAB_DOOR_HALF - 0.06F, FLOOR_TOP, fa, -CAB_DOOR_HALF, CAB_DOOR_TOP, fb, trimColour);
        box(buffer, CAB_DOOR_HALF, FLOOR_TOP, fa, CAB_DOOR_HALF + 0.06F, CAB_DOOR_TOP, fb, trimColour);
        box(buffer, -CAB_DOOR_HALF - 0.06F, CAB_DOOR_TOP, fa, CAB_DOOR_HALF + 0.06F, CAB_DOOR_TOP + 0.06F, fb,
            trimColour);
        box(buffer, -CAB_DOOR_HALF + 0.08F, CAB_DOOR_WINDOW_BOTTOM, fa, CAB_DOOR_HALF - 0.08F,
            CAB_DOOR_WINDOW_TOP, fb, CAB_WINDOW_COLOR);

        // Desk right across the cab under the windscreen, with an instrument hood along its front.
        float deskBack = s * (wall - DESK_DEPTH);
        box(buffer, -inner, FLOOR_TOP, deskBack, inner, DESK_TOP, s * wall, DESK_COLOR);
        box(buffer, -inner, DESK_TOP, s * (wall - 0.30F), inner, DESK_TOP + 0.14F, s * wall, DESK_COLOR);

        // The driver's side: screens on the hood, a few indicators, and the traction/brake handle.
        float driverX = -s * 0.80F;
        box(buffer, driverX - 0.45F, DESK_TOP + 0.14F, s * (wall - 0.32F),
            driverX + 0.45F, DESK_TOP + 0.46F, s * (wall - 0.24F), SCREEN_COLOR);
        float[][] indicators = {{0.25F, 0.85F, 0.30F}, {0.98F, 0.72F, 0.15F}, {0.90F, 0.18F, 0.15F}};
        for (int i = 0; i < indicators.length; i++) {
            float x = driverX + (i - 1) * 0.22F;
            box(buffer, x - 0.06F, DESK_TOP, s * (wall - 0.52F), x + 0.06F, DESK_TOP + 0.03F,
                s * (wall - 0.40F), indicators[i]);
        }
        float handleX = driverX + s * 0.55F;
        box(buffer, handleX - 0.04F, DESK_TOP, s * (wall - 0.58F), handleX + 0.04F, DESK_TOP + 0.26F,
            s * (wall - 0.50F), UNDERFRAME_COLOR);
        box(buffer, handleX - 0.08F, DESK_TOP + 0.26F, s * (wall - 0.62F), handleX + 0.08F, DESK_TOP + 0.34F,
            s * (wall - 0.46F), UNDERFRAME_COLOR);

        // Driver's seat: pedestal, cushion and back, facing the windscreen.
        float seatZ = wall - DESK_DEPTH - 0.55F;
        box(buffer, driverX - 0.08F, FLOOR_TOP, s * (seatZ - 0.08F), driverX + 0.08F, FLOOR_TOP + 0.50F,
            s * (seatZ + 0.08F), UNDERFRAME_COLOR);
        box(buffer, driverX - 0.30F, FLOOR_TOP + 0.50F, s * (seatZ - 0.28F), driverX + 0.30F,
            FLOOR_TOP + 0.64F, s * (seatZ + 0.28F), DRIVER_SEAT_COLOR);
        box(buffer, driverX - 0.30F, FLOOR_TOP + 0.64F, s * (seatZ - 0.36F), driverX + 0.30F,
            FLOOR_TOP + 1.40F, s * (seatZ - 0.26F), DRIVER_SEAT_COLOR);

        // Lamp housings, outside on the cab front; the lenses are drawn lit, in their own pass.
        float face = s * half;
        float proud = s * (half + 0.04F);
        for (int side = -1; side <= 1; side += 2) {
            float cx = side * LAMP_CENTRE_X;
            box(buffer, cx - LAMP_HALF_WIDTH - 0.04F, LAMP_BOTTOM - 0.04F, face,
                cx + LAMP_HALF_WIDTH + 0.04F, LAMP_TOP + 0.04F, proud, LAMP_HOUSING_COLOR);
        }
    }

    /**
     * The lit lamp lenses on a car's cab ends. Drawn by the caller in a pass of its own, at full
     * brightness: a lamp is a light source, and must not be darkened by the night it is lighting.
     */
    static void emitLamps(BufferBuilder buffer, float bogieSpacing, Lamp front, Lamp rear) {
        float half = bogieSpacing / TRUCK_CENTRE_RATIO * 0.5F;
        emitLampPair(buffer, half, 1, front);
        emitLampPair(buffer, half, -1, rear);
    }

    private static void emitLampPair(BufferBuilder buffer, float half, int s, Lamp lamp) {
        if (lamp == Lamp.OFF) {
            return;
        }
        float[] colour = lamp == Lamp.HEAD ? HEADLAMP_COLOR : TAIL_LAMP_COLOR;
        float a = s * (half + 0.04F);
        float b = s * (half + 0.07F);
        for (int side = -1; side <= 1; side += 2) {
            float cx = side * LAMP_CENTRE_X;
            box(buffer, cx - LAMP_HALF_WIDTH, LAMP_BOTTOM, a, cx + LAMP_HALF_WIDTH, LAMP_TOP, b, colour);
        }
    }

    /** A colour dimmed by the saloon's lighting state. */
    private static float[] shaded(float[] colour, float shade) {
        return new float[] {colour[0] * shade, colour[1] * shade, colour[2] * shade};
    }

    /**
     * The window glass, emitted as a separate translucent pass.
     *
     * <p>The window band was left as genuine openings — pillars only, no glass — so riders inside
     * would be visible. That reads as a car with no windows at all rather than as a car with clean
     * ones. Thin panes at low alpha keep the interior visible while giving the openings a surface
     * to catch light, which is what actually makes them read as windows.</p>
     *
     * <p>Drawn by the caller <em>after</em> the opaque body with blending on: translucent geometry
     * has to come last or it blends against whatever happened to be drawn before it.</p>
     */
    static void emitGlazing(BufferBuilder buffer, float bogieSpacing, float rightFraction,
                            float leftFraction, boolean outerFront, boolean outerRear) {
        float bodyLength = bogieSpacing / TRUCK_CENTRE_RATIO;
        float half = bodyLength * 0.5F;
        float[] doorCentres = doorCentres(half);
        float doorHalf = DOOR_WIDTH * 0.5F;

        for (int side = -1; side <= 1; side += 2) {
            // Each side's door glass moves with that side's leaves: at a one-sided platform the far
            // side stays shut, windows and all.
            float doorFraction = side > 0 ? rightFraction : leftFraction;
            float outer = side * BODY_HALF_WIDTH;
            float inner = side * (BODY_HALF_WIDTH - GLASS_THICKNESS);
            float from = -half;
            if (parts != Parts.DOORS) {
                for (float centre : doorCentres) {
                    if (centre - doorHalf > from) {
                        pane(buffer, inner, outer, from, centre - doorHalf);
                    }
                    from = centre + doorHalf;
                }
                if (half > from) {
                    pane(buffer, inner, outer, from, half);
                }
            }
            if (parts == Parts.BODY) {
                continue;
            }
            // Glass in the DOORS, travelling with the leaves, rather than a sheet across the bay.
            // The old bay-wide pane sat in the wall plane while the leaves stood proud of it, so a
            // shut door wore a second layer of glass on its outside; and because it spanned the
            // whole opening it was never a window in a door at all.
            float leafHalf = doorHalf * 0.5F;
            float glassOuter = side * (BODY_HALF_WIDTH + DOOR_PROUD - 0.008F);
            float glassInner = side * (BODY_HALF_WIDTH + DOOR_PROUD - LEAF_THICKNESS + 0.008F);
            for (float centre : doorCentres) {
                for (int leaf = -1; leaf <= 1; leaf += 2) {
                    float leafCentre = leafCentre(centre, leaf, doorHalf, leafHalf, doorFraction);
                    box(buffer, Math.min(glassInner, glassOuter), DOOR_WINDOW_BOTTOM + 0.02F,
                        leafCentre - leafHalf + LEAF_FRAME + 0.02F,
                        Math.max(glassInner, glassOuter), DOOR_WINDOW_TOP - 0.02F,
                        leafCentre + leafHalf - LEAF_FRAME - 0.02F, GLASS_COLOR, GLASS_ALPHA);
                }
            }
        }

        // Cab windscreens, on whichever ends face out of the consist.
        if (parts == Parts.DOORS) {
            return;
        }
        if (outerFront) {
            endPane(buffer, half - WALL_THICKNESS, half);
        }
        if (outerRear) {
            endPane(buffer, -half, -half + WALL_THICKNESS);
        }
    }

    /** Glass across a cab end's windscreen opening. */
    private static void endPane(BufferBuilder buffer, float zNear, float zFar) {
        box(buffer, -END_WINDOW_HALF_WIDTH, WINDOW_SILL + 0.04F, Math.min(zNear, zFar),
            END_WINDOW_HALF_WIDTH, WINDOW_HEAD - 0.16F, Math.max(zNear, zFar),
            GLASS_COLOR, GLASS_ALPHA);
    }

    private static void pane(BufferBuilder buffer, float xInner, float xOuter,
                             float zFrom, float zTo) {
        if (zTo - zFrom < 0.05F) {
            return;
        }
        float x1 = Math.min(xInner, xOuter);
        float x2 = Math.max(xInner, xOuter);
        box(buffer, x1, WINDOW_SILL + 0.04F, zFrom + 0.02F,
            x2, WINDOW_HEAD - 0.04F, zTo - 0.02F, GLASS_COLOR, GLASS_ALPHA);
    }

    /**
     * Where a door leaf's centre sits for a given open fraction.
     *
     * <p>Shared by the body pass and the glazing pass deliberately: the leaf and the glass in it
     * have to be in exactly the same place, and two copies of this arithmetic would eventually
     * disagree by a hair and show it as a shimmering edge.</p>
     */
    private static float leafCentre(float bayCentre, int side, float doorHalf, float leafHalf,
                                    float fraction) {
        float shut = bayCentre + side * leafHalf;
        float open = bayCentre + side * (doorHalf + leafHalf);
        return shut + (open - shut) * fraction;
    }

    /**
     * Door bay centres along the car — the quarter points, where real metro doors cluster.
     *
     * <p>Mirrors {@link CarSeating#doorCentreOffsets}, which is the same quarter-point rule
     * expressed against body length rather than half-length. Kept as its own method because the
     * model works in floats and half-lengths throughout; the assertion that the two agree lives in
     * {@code CarSeatingTest}.</p>
     */
    private static float[] doorCentres(float half) {
        return new float[] {-half * 0.5F, half * 0.5F};
    }

    private static boolean inDoorway(float at, float[] doorCentres, float reach) {
        for (float centre : doorCentres) {
            if (Math.abs(at - centre) < reach) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a point in car-local space falls inside a doorway opening — what decides if a player
     * walking at the train is stepping through a door or into its side.
     *
     * @param alongZ  position along the car from its centre, in blocks
     * @param bodyLength the car's visible body length
     */
    static boolean isInDoorway(float alongZ, float bodyLength) {
        return inDoorway(alongZ, doorCentres(bodyLength * 0.5F), DOOR_WIDTH * 0.5F);
    }

    /**
     * A car end.
     *
     * <p>An <b>outer</b> end — one facing out of the consist — is a cab: solid below, a glazed
     * windshield band, solid above. An <b>inner</b> end, facing the next car, carries a gangway
     * door instead, with its own small window. The door is scenery for now: it does not open and
     * you cannot walk between cars through it, which is exactly what a real end door looks like
     * from inside anyway when it is shut.</p>
     *
     * <p>The <em>body</em> stays symmetric either way — see the class javadoc on why a metro model
     * must have no nose. Which end is which is decided by the car's index in the consist, so a
     * train that reverses does not suddenly grow a cab in the middle of itself.</p>
     */
    private static void emitEnd(BufferBuilder buffer, float zNear, float zFar,
                                float[] bodyColour, float[] trimColour, boolean outer,
                                float shade) {
        box(buffer, -BODY_HALF_WIDTH, FLOOR_TOP, zNear,
            BODY_HALF_WIDTH, WINDOW_SILL, zFar, bodyColour);
        box(buffer, -BODY_HALF_WIDTH, WINDOW_HEAD, zNear,
            BODY_HALF_WIDTH, ROOF_BASE, zFar, bodyColour);

        if (outer) {
            // Cab end: a windshield frame; the glass itself comes in the translucent pass.
            box(buffer, -BODY_HALF_WIDTH, WINDOW_SILL, zNear,
                -END_WINDOW_HALF_WIDTH, WINDOW_HEAD, zFar, trimColour);
            box(buffer, END_WINDOW_HALF_WIDTH, WINDOW_SILL, zNear,
                BODY_HALF_WIDTH, WINDOW_HEAD, zFar, trimColour);
            box(buffer, -BODY_HALF_WIDTH, WINDOW_HEAD - 0.12F, zNear,
                BODY_HALF_WIDTH, WINDOW_HEAD, zFar, trimColour);
            // The outside of a cab front: a centre mullion in the windscreen, the livery band
            // carried round from the sides, a black surround to the glass, and the ribbed
            // anti-climber at floor level that every metro car wears on its nose.
            float s = Math.abs(zFar) > Math.abs(zNear) ? Math.signum(zFar) : Math.signum(zNear);
            float face = s * Math.max(Math.abs(zNear), Math.abs(zFar));
            box(buffer, -0.045F, WINDOW_SILL, zNear, 0.045F, WINDOW_HEAD, zFar, GASKET_COLOR);
            plateZ(buffer, s, face, -BODY_HALF_WIDTH, STRIPE_BOTTOM, BODY_HALF_WIDTH, STRIPE_TOP, trimColour);
            plateZ(buffer, s, face, -END_WINDOW_HALF_WIDTH - 0.06F, WINDOW_SILL - 0.05F,
                END_WINDOW_HALF_WIDTH + 0.06F, WINDOW_SILL + 0.04F, GASKET_COLOR);
            plateZ(buffer, s, face, -END_WINDOW_HALF_WIDTH - 0.06F, WINDOW_SILL,
                -END_WINDOW_HALF_WIDTH, WINDOW_HEAD - 0.12F, GASKET_COLOR);
            plateZ(buffer, s, face, END_WINDOW_HALF_WIDTH, WINDOW_SILL,
                END_WINDOW_HALF_WIDTH + 0.06F, WINDOW_HEAD - 0.12F, GASKET_COLOR);
            for (int rib = 0; rib < 3; rib++) {
                float y = FLOOR_TOP - 0.32F + rib * 0.11F;
                box(buffer, -1.25F, y, Math.min(face, face + s * 0.09F), 1.25F, y + 0.06F,
                    Math.max(face, face + s * 0.09F), UNDERFRAME_COLOR);
            }
            return;
        }

        // Inner end: full-height bulkhead with a gangway door in the middle of it.
        box(buffer, -BODY_HALF_WIDTH, WINDOW_SILL, zNear,
            BODY_HALF_WIDTH, WINDOW_HEAD, zFar, bodyColour);
        float doorHalf = END_DOOR_WIDTH * 0.5F;
        float face = zFar > zNear ? zNear - 0.04F : zNear + 0.04F;
        float z1 = Math.min(face, zNear);
        float z2 = Math.max(face, zNear);
        box(buffer, -doorHalf, FLOOR_TOP, z1, doorHalf, END_DOOR_TOP, z2,
            shaded(SHELL_COLOR, shade));
        // Door frame and a small window in it, both in trim so the door reads as a door.
        box(buffer, -doorHalf - 0.06F, FLOOR_TOP, z1, -doorHalf, END_DOOR_TOP, z2, trimColour);
        box(buffer, doorHalf, FLOOR_TOP, z1, doorHalf + 0.06F, END_DOOR_TOP, z2, trimColour);
        box(buffer, -doorHalf, END_DOOR_TOP - 0.06F, z1, doorHalf, END_DOOR_TOP, z2, trimColour);
        box(buffer, -END_DOOR_WINDOW_HALF, END_DOOR_WINDOW_BOTTOM, z1 - 0.01F,
            END_DOOR_WINDOW_HALF, END_DOOR_WINDOW_TOP, z2 + 0.01F, trimColour);
    }

    /** Longitudinal benches down both walls, clear of the door bays. */
    private static void emitBenches(BufferBuilder buffer, float half, float saloonRear,
                                    float saloonFront, float[] seatColour, float shade) {
        float[] shell = shaded(SHELL_COLOR, shade);
        float inner = BODY_HALF_WIDTH - WALL_THICKNESS;
        // Three bench runs per side: between the ends and the door bays.
        float doorHalf = DOOR_WIDTH * 0.5F + 0.15F;
        float[][] runs = {
            {-half + 0.4F, -half * 0.5F - doorHalf},
            {-half * 0.5F + doorHalf, half * 0.5F - doorHalf},
            {half * 0.5F + doorHalf, half - 0.4F},
        };
        for (float[] run : runs) {
            // A run reaching into a cab stops at its partition.
            run[0] = Math.max(run[0], saloonRear + 0.3F);
            run[1] = Math.min(run[1], saloonFront - 0.3F);
            if (run[1] - run[0] < 0.5F) {
                continue;
            }
            // Moulded grey shell under a coloured cushion, as the reference car has: the cushion
            // is the only part that takes the train's seat colour.
            float cushion = SEAT_TOP - 0.14F;
            box(buffer, inner - SEAT_DEPTH, FLOOR_TOP, run[0], inner, cushion, run[1], shell);
            box(buffer, -inner, FLOOR_TOP, run[0], -inner + SEAT_DEPTH, cushion, run[1], shell);
            box(buffer, inner - SEAT_DEPTH, cushion, run[0], inner, SEAT_TOP, run[1], seatColour);
            box(buffer, -inner, cushion, run[0], -inner + SEAT_DEPTH, SEAT_TOP, run[1], seatColour);
            // Low seat backs against the walls.
            box(buffer, inner - 0.08F, SEAT_TOP, run[0], inner, SEAT_BACK_TOP, run[1], shell);
            box(buffer, -inner, SEAT_TOP, run[0], -inner + 0.08F, SEAT_BACK_TOP, run[1], shell);
        }
    }

    /**
     * A raised pantograph, simplified to placeholder boxes: base frame on the roof, two slanted
     * arms approximated as stacked struts, and a contact shoe bar riding just under the wire.
     */
    private static void emitPantograph(BufferBuilder buffer, float wireHeight) {
        // Nothing to reach for: an unelectrified section carries no wire, so the pantograph stays
        // down rather than groping at empty sky.
        if (wireHeight <= 0.0F) {
            return;
        }
        float shoeU = Math.max(PANTO_BASE_TOP + 0.3F, wireHeight - PANTO_WIRE_CLEARANCE);
        // Base frame across the roof centre.
        box(buffer, -0.7F, ROOF_TOP, -0.55F, 0.7F, PANTO_BASE_TOP, 0.55F, TRUCK_COLOR);
        // Lower and upper arm struts, narrowing with height. They span whatever gap the wire
        // leaves, so the same frame serves a 6-block wire and a 15-block one.
        float armMidU = (PANTO_BASE_TOP + shoeU) * 0.5F;
        box(buffer, -0.09F, PANTO_BASE_TOP, -0.30F, 0.09F, armMidU, -0.14F, TRUCK_COLOR);
        box(buffer, -0.09F, PANTO_BASE_TOP, 0.14F, 0.09F, armMidU, 0.30F, TRUCK_COLOR);
        box(buffer, -0.07F, armMidU, -0.10F, 0.07F, shoeU - 0.06F, 0.10F, TRUCK_COLOR);
        // The shoe: a wide bar across the direction of travel, kissing the wire height.
        box(buffer, -PANTO_HALF_SPAN, shoeU - 0.06F, -0.10F,
            PANTO_HALF_SPAN, shoeU, 0.10F, UNDERFRAME_COLOR);
    }

    /**
     * One truck: two wheelsets on the railheads, side frames over the axle boxes, and the bolster
     * that carries the car across them. The wheels are what was missing — a car resting on two
     * dark slabs reads as a model on a plinth, and round wheels on the rail read as a train.
     */
    private static void emitTruck(BufferBuilder buffer, float atZ) {
        // Side frames, above the axles and inboard of the wheels.
        box(buffer, TRUCK_INNER, RAIL_TOP + 0.34F, atZ - TRUCK_HALF_LENGTH,
            TRUCK_OUTER, TRUCK_TOP, atZ + TRUCK_HALF_LENGTH, TRUCK_COLOR);
        box(buffer, -TRUCK_OUTER, RAIL_TOP + 0.34F, atZ - TRUCK_HALF_LENGTH,
            -TRUCK_INNER, TRUCK_TOP, atZ + TRUCK_HALF_LENGTH, TRUCK_COLOR);
        // Truck bolster spanning under the car between the side frames.
        box(buffer, -TRUCK_INNER, RAIL_TOP + 0.30F, atZ - 0.28F,
            TRUCK_INNER, RAIL_TOP + 0.62F, atZ + 0.28F, TRUCK_COLOR);

        CarMesh round = new CarMesh();
        float axleY = RAIL_TOP + WHEEL_RADIUS;
        for (int end = -1; end <= 1; end += 2) {
            float z = atZ + end * AXLE_OFFSET;
            // The axle, and a motor case hung on it toward the truck's centre.
            box(buffer, -WHEEL_X, axleY - 0.07F, z - 0.07F, WHEEL_X, axleY + 0.07F, z + 0.07F, EQUIPMENT_DARK);
            box(buffer, -0.62F, RAIL_TOP + 0.16F, Math.min(z, z - end * 0.50F),
                0.62F, RAIL_TOP + 0.66F, Math.max(z, z - end * 0.50F), EQUIPMENT_DARK);
            for (int side = -1; side <= 1; side += 2) {
                round.cylinderX(side * WHEEL_X, axleY, z, WHEEL_RADIUS, WHEEL_HALF_WIDTH, 14, WHEEL_COLOR);
                // The wheel's outer face, a paler disc standing just off it, and the hub.
                round.cylinderX(side * (WHEEL_X + WHEEL_HALF_WIDTH + 0.004F), axleY, z, WHEEL_RADIUS - 0.07F,
                    0.004F, 14, WHEEL_FACE_COLOR);
                // Axle box outboard of the wheel, with the spring seat that carries the frame on it.
                float inner = side * (WHEEL_X + WHEEL_HALF_WIDTH + 0.01F);
                float outer = side * (WHEEL_X + WHEEL_HALF_WIDTH + 0.13F);
                box(buffer, Math.min(inner, outer), axleY - 0.14F, z - 0.15F,
                    Math.max(inner, outer), axleY + 0.14F, z + 0.15F, TRUCK_COLOR);
                box(buffer, Math.min(inner, outer), axleY + 0.14F, z - 0.10F,
                    Math.max(inner, outer), TRUCK_TOP, z + 0.10F, EQUIPMENT_DARK);
            }
        }
        shapes(buffer, round);
    }

    /**
     * Everything hung under the floor: the centre sill, the solebar along each side, and between
     * the trucks the equipment cases and air reservoirs a real car carries there.
     *
     * <p>This was one slab the full width and nearly the full length of the car, down almost to the
     * rail, with the trucks buried inside it. Broken up, there is daylight under the car and round
     * the trucks, which is most of why stock looks like it is riding on its wheels.</p>
     */
    private static void emitUnderframe(BufferBuilder buffer, float half, float truckAt,
                                       boolean outerFront, boolean outerRear) {
        float under = FLOOR_TOP - FLOOR_SLAB;
        // Centre sill, end to end, and a shallow solebar under each body side.
        box(buffer, -0.50F, RAIL_TOP + 0.90F, -half + 0.3F, 0.50F, under, half - 0.3F, UNDERFRAME_COLOR);
        for (int side = -1; side <= 1; side += 2) {
            box(buffer, Math.min(side * 1.66F, side * 1.82F), under - 0.22F, -half + 0.15F,
                Math.max(side * 1.66F, side * 1.82F), under, half - 0.15F, UNDERFRAME_COLOR);
        }

        // Between the trucks. The two sides carry the same kit in the opposite order, as real
        // cars do: one side's inverter case faces the other side's reservoirs.
        float from = -truckAt + TRUCK_HALF_LENGTH + 0.55F;
        float to = truckAt - TRUCK_HALF_LENGTH - 0.55F;
        float span = to - from;
        if (span > 3.0F) {
            CarMesh round = new CarMesh();
            for (int side = -1; side <= 1; side += 2) {
                float xIn = side * 0.62F;
                float xOut = side * 1.60F;
                float x0 = Math.min(xIn, xOut);
                float x1 = Math.max(xIn, xOut);
                // Positions along the span, mirrored on the second side.
                float a0 = at(from, span, side, 0.00F);
                float a1 = at(from, span, side, 0.30F);
                float b0 = at(from, span, side, 0.34F);
                float b1 = at(from, span, side, 0.50F);
                float c0 = at(from, span, side, 0.55F);
                float c1 = at(from, span, side, 0.78F);
                float d0 = at(from, span, side, 0.83F);
                float d1 = at(from, span, side, 1.00F);
                // The big traction case, with a row of louvres on its outer face.
                box(buffer, x0, SKIRT_BOTTOM + 0.12F, Math.min(a0, a1), x1, under, Math.max(a0, a1), EQUIPMENT_COLOR);
                for (int slat = 0; slat < 4; slat++) {
                    float y = SKIRT_BOTTOM + 0.34F + slat * 0.20F;
                    plateX(buffer, side, 1.60F, y, Math.min(a0, a1) + 0.25F, y + 0.09F, Math.max(a0, a1) - 0.25F,
                        EQUIPMENT_DARK);
                }
                // A shallower case beside it.
                box(buffer, x0, SKIRT_BOTTOM + 0.50F, Math.min(b0, b1), x1, under, Math.max(b0, b1), EQUIPMENT_DARK);
                // Air reservoirs: two tanks side by side, lying along the car.
                for (int tank = 0; tank < 2; tank++) {
                    float cx = side * (0.92F + tank * 0.50F);
                    round.prismZ(ring(cx, under - 0.34F, 0.21F, 10), Math.min(c0, c1), Math.max(c0, c1),
                        TANK_COLOR, true, true);
                }
                // And a battery box at the far end.
                box(buffer, x0, SKIRT_BOTTOM + 0.30F, Math.min(d0, d1), x1, under, Math.max(d0, d1), EQUIPMENT_COLOR);
                plateX(buffer, side, 1.60F, SKIRT_BOTTOM + 0.42F, Math.min(d0, d1) + 0.12F, under - 0.14F,
                    Math.max(d0, d1) - 0.12F, EQUIPMENT_DARK);
            }
            shapes(buffer, round);
        }

        // Under a cab: the obstacle deflector across the front, and the coupler head.
        for (int s = -1; s <= 1; s += 2) {
            if (s > 0 ? !outerFront : !outerRear) {
                continue;
            }
            float z0 = s * (half - 0.75F);
            float z1 = s * (half - 0.10F);
            box(buffer, -1.45F, SKIRT_BOTTOM + 0.05F, Math.min(z0, z1), 1.45F, under, Math.max(z0, z1), UNDERFRAME_COLOR);
            float c0 = s * half;
            float c1 = s * (half + 0.42F);
            box(buffer, -0.20F, COUPLER_BOTTOM - 0.04F, Math.min(c0, c1), 0.20F, COUPLER_TOP + 0.04F, Math.max(c0, c1),
                TRUCK_COLOR);
        }
    }

    /** A point {@code fraction} of the way along a span, measured from the other end on the left side. */
    private static float at(float from, float span, int side, float fraction) {
        return from + span * (side > 0 ? fraction : 1.0F - fraction);
    }

    /** A regular polygon in X-Y, for sweeping into a tank along the car. */
    private static float[][] ring(float cx, float cy, float radius, int sides) {
        float[][] ring = new float[sides][];
        for (int i = 0; i < sides; i++) {
            double a = 2.0D * Math.PI * (i + 0.5D) / sides;
            ring[i] = new float[] {cx + radius * (float) Math.cos(a), cy + radius * (float) Math.sin(a)};
        }
        return ring;
    }

    /**
     * The roof: a ceiling slab the saloon sees from below, a shallow arc over it from one cant rail
     * to the other, and the ventilation units along its crown.
     *
     * <p>The arc rises only as far as the flat roof it replaces stood, so the car's envelope — what
     * clearance checks and tunnels are sized against — is the same. Lit by which way each strip of
     * it faces, that is enough for the roof to read as curved.</p>
     */
    private static void emitRoof(BufferBuilder buffer, float half, boolean pantographAtCentre) {
        box(buffer, -BODY_HALF_WIDTH, ROOF_BASE, -half, BODY_HALF_WIDTH, ROOF_BASE + 0.03F, half, ROOF_COLOR);
        float w = BODY_HALF_WIDTH;
        float rise = ROOF_TOP - ROOF_BASE;
        float[][] arc = {
            {-w, ROOF_BASE + 0.03F}, {-w + 0.10F, ROOF_BASE + rise * 0.48F}, {-w + 0.50F, ROOF_BASE + rise * 0.82F},
            {-w + 1.15F, ROOF_TOP}, {w - 1.15F, ROOF_TOP}, {w - 0.50F, ROOF_BASE + rise * 0.82F},
            {w - 0.10F, ROOF_BASE + rise * 0.48F}, {w, ROOF_BASE + 0.03F},
        };
        CarMesh shell = new CarMesh();
        shell.prismZ(arc, -half, half, ROOF_COLOR, true, true);

        // Ventilation units over each door bay, low and chamfered, with a dark intake on top. A
        // car with a pantograph at its centre has them there as well, either side of the frame.
        float[][] unit = {
            {-0.78F, ROOF_TOP - 0.01F}, {0.78F, ROOF_TOP - 0.01F},
            {0.64F, ROOF_UNIT_TOP}, {-0.64F, ROOF_UNIT_TOP},
        };
        float unitHalf = Math.min(1.5F, half * 0.16F);
        for (int end = -1; end <= 1; end += 2) {
            float centre = end * half * 0.5F;
            shell.prismZ(unit, centre - unitHalf, centre + unitHalf, ROOF_UNIT_COLOR, true, true);
        }
        shapes(buffer, shell);
        for (int end = -1; end <= 1; end += 2) {
            float centre = end * half * 0.5F;
            box(buffer, -0.42F, ROOF_UNIT_TOP, centre - unitHalf * 0.6F,
                0.42F, ROOF_UNIT_TOP + 0.012F, centre + unitHalf * 0.6F, GASKET_COLOR);
        }
        if (!pantographAtCentre) {
            // No pantograph: a long low duct down the middle of the roof instead of bare panel.
            box(buffer, -0.30F, ROOF_TOP - 0.01F, -half * 0.5F + unitHalf + 0.4F,
                0.30F, ROOF_TOP + 0.06F, half * 0.5F - unitHalf - 0.4F, ROOF_UNIT_COLOR);
        }
    }

    /**
     * The gangway bellows on an end that faces the next car: a black frame round the end door,
     * reaching just under half way across the gap, so two coupled cars' bellows meet and the train
     * reads as one connected thing rather than as boxes on a string.
     */
    private static void emitBellows(BufferBuilder buffer, float half, int s) {
        float z0 = s * half;
        float z1 = s * (half + BELLOWS_DEPTH);
        float zMin = Math.min(z0, z1);
        float zMax = Math.max(z0, z1);
        float outer = END_DOOR_WIDTH * 0.5F + 0.26F;
        float inner = END_DOOR_WIDTH * 0.5F + 0.08F;
        float top = END_DOOR_TOP + 0.24F;
        float bottom = FLOOR_TOP - 0.16F;
        box(buffer, -outer, bottom, zMin, -inner, top, zMax, BELLOWS_COLOR);
        box(buffer, inner, bottom, zMin, outer, top, zMax, BELLOWS_COLOR);
        box(buffer, -inner, top - 0.18F, zMin, inner, top, zMax, BELLOWS_COLOR);
        box(buffer, -inner, bottom, zMin, inner, FLOOR_TOP, zMax, BELLOWS_COLOR);
        // The folds: paler ribs round the frame, which is what makes a bellows read as one.
        for (int fold = 1; fold <= 2; fold++) {
            float z = s * (half + BELLOWS_DEPTH * fold / 3.0F);
            box(buffer, -outer - 0.02F, bottom, z - 0.015F, -outer, top + 0.02F, z + 0.015F, EQUIPMENT_COLOR);
            box(buffer, outer, bottom, z - 0.015F, outer + 0.02F, top + 0.02F, z + 0.015F, EQUIPMENT_COLOR);
            box(buffer, -outer, top, z - 0.015F, outer, top + 0.02F, z + 0.015F, EQUIPMENT_COLOR);
        }
    }

    /** Emits shaped geometry built with {@link CarMesh}. It is all outside the car. */
    private static void shapes(BufferBuilder buffer, CarMesh mesh) {
        if (pass == Pass.INTERIOR) {
            return;
        }
        for (CarMesh.Quad q : mesh.quads()) {
            for (int c = 0; c < 4; c++) {
                float lift = heightShade(q.y(c));
                buffer.pos(q.x(c), q.y(c), q.z(c)).color(q.r * lift, q.g * lift, q.b * lift, 1.0F).endVertex();
            }
        }
    }

    /**
     * A flat panel on the outside of a side wall, facing out: paint, a gasket, a louvre. One quad
     * rather than a box, because a box's back face would lie in the wall's own skin and fight it.
     *
     * @param side  {@code +1} for the {@code +x} wall, {@code -1} for the other
     * @param skin  how far out the surface it lies on is, as a positive distance
     */
    private static void plateX(BufferBuilder buffer, float side, float skin, float y1, float z1,
                               float y2, float z2, float[] colour) {
        if (pass == Pass.INTERIOR) {
            return;
        }
        float x = side * (skin + PLATE_PROUD);
        quad(buffer, x, y1, z1, x, y2, z1, x, y2, z2, x, y1, z2, colour[0], colour[1], colour[2], 0.72F, 1.0F);
    }

    /** As {@link #plateX}, on a car end at {@code z = face}, facing {@code s} along the car. */
    private static void plateZ(BufferBuilder buffer, float s, float face, float x1, float y1,
                               float x2, float y2, float[] colour) {
        if (pass == Pass.INTERIOR) {
            return;
        }
        float z = face + s * PLATE_PROUD;
        quad(buffer, x1, y1, z, x1, y2, z, x2, y2, z, x2, y1, z, colour[0], colour[1], colour[2], 0.84F, 1.0F);
    }

    // Collector shoes, sized against TrackMeshBuilder's third rail: its centre is 2.10 out with a
    // top at u 0.16, under a cover board whose underside is at 0.275. The shoe paddle rides on the
    // rail's top; the beam carrying it out from the truck passes under the board.
    private static final float SHOE_BEAM_INNER = TRUCK_OUTER;
    private static final float SHOE_PADDLE_INNER = 1.95F;
    private static final float SHOE_OUTER = 2.25F;
    private static final float SHOE_BOTTOM = 0.16F;
    private static final float SHOE_TOP = 0.21F;
    private static final float SHOE_BEAM_TOP = 0.26F;
    private static final float SHOE_HALF_LENGTH = 0.30F;
    private static final float[] SHOE_COLOR = { 0.22F, 0.20F, 0.18F };

    /** Shoegear both sides of the truck at {@code atZ}: a beam out from the side frame, a paddle on it. */
    private static void emitShoes(BufferBuilder buffer, float atZ) {
        for (int side = -1; side <= 1; side += 2) {
            float beamFrom = side * SHOE_BEAM_INNER;
            float paddleFrom = side * SHOE_PADDLE_INNER;
            float outer = side * SHOE_OUTER;
            box(buffer, Math.min(beamFrom, paddleFrom), SHOE_TOP, atZ - 0.08F,
                Math.max(beamFrom, paddleFrom), SHOE_BEAM_TOP, atZ + 0.08F, SHOE_COLOR);
            box(buffer, Math.min(paddleFrom, outer), SHOE_BOTTOM, atZ - SHOE_HALF_LENGTH,
                Math.max(paddleFrom, outer), SHOE_TOP, atZ + SHOE_HALF_LENGTH, SHOE_COLOR);
        }
    }

    /** Same box emitter as {@link CarModel}; see there for the winding/culling note. */
    private static void box(BufferBuilder buffer, float x1, float y1, float z1,
                            float x2, float y2, float z2, float[] color) {
        box(buffer, x1, y1, z1, x2, y2, z2, color, 1.0F);
    }

    private static void box(BufferBuilder buffer, float x1, float y1, float z1,
                            float x2, float y2, float z2, float[] color, float alpha) {
        float r = color[0];
        float g = color[1];
        float b = color[2];
        float cx = (x1 + x2) * 0.5F;
        float cy = (y1 + y2) * 0.5F;
        float cz = (z1 + z2) * 0.5F;
        // Each face with the way it faces out of the box: the low face of an axis faces down it.
        float sx = x1 <= x2 ? 1.0F : -1.0F;
        float sy = y1 <= y2 ? 1.0F : -1.0F;
        float sz = z1 <= z2 ? 1.0F : -1.0F;
        if (wanted(cx, cy, z1, 2, -sz)) {
            quad(buffer, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, 0.82F, alpha);
        }
        if (wanted(cx, cy, z2, 2, sz)) {
            quad(buffer, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2, r, g, b, 0.86F, alpha);
        }
        if (wanted(x1, cy, cz, 0, -sx)) {
            quad(buffer, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, r, g, b, 0.72F, alpha);
        }
        if (wanted(x2, cy, cz, 0, sx)) {
            quad(buffer, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, 0.72F, alpha);
        }
        if (wanted(cx, y2, cz, 1, sy)) {
            quad(buffer, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, 1.0F, alpha);
        }
        if (wanted(cx, y1, cz, 1, -sy)) {
            quad(buffer, x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2, r, g, b, 0.55F, alpha);
        }
    }

    /** Which faces {@link #emit} draws: all of them, or only the outside or inside of the car. */
    enum Pass {
        ALL, EXTERIOR, INTERIOR
    }

    private static Pass pass = Pass.ALL;
    /** Half the body length of the car being emitted, for telling inside from out. */
    private static float passHalf = Float.MAX_VALUE;

    /**
     * Restricts what the next {@link #emit} draws, so the renderer can light the saloon apart
     * from the outside of the car. Render thread only; set it back to {@link Pass#ALL} after.
     */
    static void setPass(Pass value) {
        pass = value;
    }

    /**
     * Whether a face at {@code (x, y, z)}, facing along {@code axis} (0 x, 1 y, 2 z) in direction
     * {@code sign}, belongs to the current pass.
     *
     * <p>A face is the inside of the car when it is within the body shell, between floor and
     * ceiling, and either lies inside the walls or faces in toward the middle: the inner skin of a
     * wall faces in, its outer skin out. Everything else — the outer skin, the roof, the
     * underframe and trucks — is the outside. A door leaf's inner face, standing proud of the wall,
     * still faces in, so a lit saloon's doors are lit from inside too.</p>
     */
    private static boolean wanted(float x, float y, float z, int axis, float sign) {
        if (pass == Pass.ALL) {
            return true;
        }
        return interior(x, y, z, axis, sign, passHalf) == (pass == Pass.INTERIOR);
    }

    static boolean interior(float x, float y, float z, int axis, float sign, float half) {
        float eps = 0.01F;
        float inner = BODY_HALF_WIDTH - WALL_THICKNESS;
        if (y < FLOOR_TOP - eps || y > ROOF_BASE + eps || Math.abs(z) > half + eps
            || Math.abs(x) > BODY_HALF_WIDTH + DOOR_PROUD + eps) {
            return false;
        }
        switch (axis) {
            case 0:
                return Math.abs(x) < inner - 0.001F || sign * x < 0.0F;
            case 1:
                return Math.abs(x) <= BODY_HALF_WIDTH;
            default:
                if (Math.abs(x) > inner + 0.001F) {
                    return false;
                }
                return Math.abs(z) < half - WALL_THICKNESS - 0.001F || sign * z < 0.0F;
        }
    }

    private static void quad(BufferBuilder buffer,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float r, float g, float b, float shade) {
        quad(buffer, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, r, g, b, shade, 1.0F);
    }

    private static void quad(BufferBuilder buffer,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float r, float g, float b, float shade, float alpha) {
        float sr = r * shade;
        float sg = g * shade;
        float sb = b * shade;
        float l1 = heightShade(y1);
        float l2 = heightShade(y2);
        float l3 = heightShade(y3);
        float l4 = heightShade(y4);
        buffer.pos(x1, y1, z1).color(sr * l1, sg * l1, sb * l1, alpha).endVertex();
        buffer.pos(x2, y2, z2).color(sr * l2, sg * l2, sb * l2, alpha).endVertex();
        buffer.pos(x3, y3, z3).color(sr * l3, sg * l3, sb * l3, alpha).endVertex();
        buffer.pos(x4, y4, z4).color(sr * l4, sg * l4, sb * l4, alpha).endVertex();
    }

    /** Height at and below which a vertex is at its darkest, and at and above which it is not darkened. */
    private static final float SHADE_LOW = RAIL_TOP;
    private static final float SHADE_HIGH = WINDOW_SILL;

    /** How dark the lowest parts go: the running gear, in the shadow of the car above it. */
    private static final float SHADE_FLOOR = 0.80F;

    /**
     * Darkens a vertex by how low on the car it is: full brightness from the window sill up,
     * falling away smoothly to the rail.
     *
     * <p>Flat colour shaded only by facing gives every side panel one tone from roof to rail, which
     * is what made the car look like a cut-out. The sky lights the top of anything more than its
     * foot, and the underframe sits in the body's own shadow; a gradient on the vertices says so
     * for nothing — no texture, no extra geometry, and it is baked into the compiled lists. It is
     * a function of height alone, so panels stacked on one another shade as one surface.</p>
     */
    static float heightShade(float y) {
        float t = (y - SHADE_LOW) / (SHADE_HIGH - SHADE_LOW);
        if (t >= 1.0F) {
            return 1.0F;
        }
        if (t <= 0.0F) {
            return SHADE_FLOOR;
        }
        // Smoothstep: no visible line where the gradient starts or stops.
        float eased = t * t * (3.0F - 2.0F * t);
        return SHADE_FLOOR + (1.0F - SHADE_FLOOR) * eased;
    }
}
