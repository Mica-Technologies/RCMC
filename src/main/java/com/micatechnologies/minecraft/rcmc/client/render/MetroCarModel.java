package com.micatechnologies.minecraft.rcmc.client.render;

import net.minecraft.client.renderer.BufferBuilder;

/**
 * Geometry for a metro car, emitted in the same local frame as {@link CarModel} (+X right,
 * +Y up, +Z travel, origin on the track centreline) and under the same conventions: flat-shaded
 * placeholder boxes, winding unmaintained because the renderer draws with culling off.
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
    private static final float SKIRT_HALF_WIDTH = 1.62F;
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
    private static final float DOOR_WIDTH = 1.8F;
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
        float bodyLength = bogieSpacing / TRUCK_CENTRE_RATIO;
        float half = bodyLength * 0.5F;
        // Unlit, the interior is not black — it is dim. Everything inside is drawn through this
        // factor so switching the lights off dims the saloon rather than only dulling the fittings.
        float shade = lightsOn ? 1.0F : 0.55F;

        // Underframe skirt between the trucks, and the floor slab the whole interior stands on.
        box(buffer, -SKIRT_HALF_WIDTH, SKIRT_BOTTOM, -half + 0.3F,
            SKIRT_HALF_WIDTH, FLOOR_TOP - FLOOR_SLAB, half - 0.3F, UNDERFRAME_COLOR);
        box(buffer, -BODY_HALF_WIDTH, FLOOR_TOP - FLOOR_SLAB, -half,
            BODY_HALF_WIDTH, FLOOR_TOP, half, UNDERFRAME_COLOR);

        emitSide(buffer, half, BODY_HALF_WIDTH - WALL_THICKNESS, BODY_HALF_WIDTH,
            bodyColour, trimColour, doorFraction, shade);
        emitSide(buffer, half, -BODY_HALF_WIDTH, -BODY_HALF_WIDTH + WALL_THICKNESS,
            bodyColour, trimColour, doorFraction, shade);

        // Ends differ now: an end facing out of the consist is a cab with a windshield, an end
        // facing the next car carries a gangway door. The body itself stays symmetric — which end
        // is which is decided by the car's place in the train, not by the model having a nose.
        emitEnd(buffer, half - WALL_THICKNESS, half, bodyColour, trimColour, outerFront, shade);
        emitEnd(buffer, -half, -half + WALL_THICKNESS, bodyColour, trimColour, outerRear, shade);

        box(buffer, -BODY_HALF_WIDTH, ROOF_BASE, -half,
            BODY_HALF_WIDTH, ROOF_TOP, half, ROOF_COLOR);

        emitFloor(buffer, half, shade);
        emitBenches(buffer, half, seatColour, shade);
        emitFittings(buffer, half, shade);
        emitCeiling(buffer, half, lightsOn);

        float truckAt = bogieSpacing * 0.5F;
        emitTruck(buffer, truckAt);
        emitTruck(buffer, -truckAt);

        if (drawPantograph) {
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
        boolean doorsOpen = doorFraction > 0.5F;
        float[] doorCentres = doorCentres(half);
        float doorHalf = DOOR_WIDTH * 0.5F;

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
        }

        // Door leaves: a pair per bay, meeting at the centre when shut and slid back into their
        // pockets when open. Open leaves are drawn proud of the wall, as real external-pocket
        // sliding doors sit, so "open" reads at a glance from outside the train.
        float sign = xOuter > 0.0F ? 1.0F : -1.0F;
        float doorOuter = Math.abs(xOuter) + DOOR_PROUD;
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

        // Yellow threshold strip on the floor at each doorway.
        for (float centre : doorCentres) {
            box(buffer, sign * (Math.abs(xInner) - 0.45F), FLOOR_TOP,
                centre - doorHalf, sign * Math.abs(xInner), FLOOR_TOP + 0.03F,
                centre + doorHalf, shaded(GRAB_YELLOW, shade));
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
    private static void emitFittings(BufferBuilder buffer, float half, float shade) {
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
            box(buffer, x - POLE_HALF * 0.8F, GRAB_RAIL_U - POLE_HALF * 0.8F, -half + RAIL_END_GAP,
                x + POLE_HALF * 0.8F, GRAB_RAIL_U + POLE_HALF * 0.8F, half - RAIL_END_GAP, yellow);

            // Hanging strap handles, skipping the doorways so none dangles in a doorway.
            for (float z = -half + RAIL_END_GAP + 0.3F; z < half - RAIL_END_GAP; z += STRAP_SPACING) {
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
    private static void emitCeiling(BufferBuilder buffer, float half, boolean lightsOn) {
        float inner = BODY_HALF_WIDTH - WALL_THICKNESS;
        // Centre vent panel, slightly proud of the ceiling.
        box(buffer, -0.80F, ROOF_BASE - 0.07F, -half + 0.35F,
            0.80F, ROOF_BASE, half - 0.35F, SHELL_COLOR);

        float[] lamp = lightsOn ? LIGHT_ON_COLOR : LIGHT_OFF_COLOR;
        for (int side = -1; side <= 1; side += 2) {
            float x = side * (inner - 0.62F);
            box(buffer, x - 0.26F, ROOF_BASE - 0.10F, -half + 0.6F,
                x + 0.26F, ROOF_BASE - 0.02F, half - 0.6F, lamp);
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
    static void emitGlazing(BufferBuilder buffer, float bogieSpacing, float doorFraction,
                            boolean outerFront, boolean outerRear) {
        float bodyLength = bogieSpacing / TRUCK_CENTRE_RATIO;
        float half = bodyLength * 0.5F;
        float[] doorCentres = doorCentres(half);
        float doorHalf = DOOR_WIDTH * 0.5F;

        for (int side = -1; side <= 1; side += 2) {
            float outer = side * BODY_HALF_WIDTH;
            float inner = side * (BODY_HALF_WIDTH - GLASS_THICKNESS);
            float from = -half;
            for (float centre : doorCentres) {
                if (centre - doorHalf > from) {
                    pane(buffer, inner, outer, from, centre - doorHalf);
                }
                from = centre + doorHalf;
            }
            if (half > from) {
                pane(buffer, inner, outer, from, half);
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

    /** Door bay centres along the car — the quarter points, where real metro doors cluster. */
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
    private static void emitBenches(BufferBuilder buffer, float half, float[] seatColour,
                                    float shade) {
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

    private static void emitTruck(BufferBuilder buffer, float atZ) {
        box(buffer, TRUCK_INNER, TRUCK_BOTTOM, atZ - TRUCK_HALF_LENGTH,
            TRUCK_OUTER, TRUCK_TOP, atZ + TRUCK_HALF_LENGTH, TRUCK_COLOR);
        box(buffer, -TRUCK_OUTER, TRUCK_BOTTOM, atZ - TRUCK_HALF_LENGTH,
            -TRUCK_INNER, TRUCK_TOP, atZ + TRUCK_HALF_LENGTH, TRUCK_COLOR);
        // Truck bolster spanning under the car between the side frames.
        box(buffer, -TRUCK_INNER, RAIL_TOP + 0.20F, atZ - 0.25F,
            TRUCK_INNER, RAIL_TOP + 0.45F, atZ + 0.25F, TRUCK_COLOR);
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
        quad(buffer, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, 0.82F, alpha);
        quad(buffer, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2, r, g, b, 0.86F, alpha);
        quad(buffer, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, r, g, b, 0.72F, alpha);
        quad(buffer, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, 0.72F, alpha);
        quad(buffer, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, 1.0F, alpha);
        quad(buffer, x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2, r, g, b, 0.55F, alpha);
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
        buffer.pos(x1, y1, z1).color(sr, sg, sb, alpha).endVertex();
        buffer.pos(x2, y2, z2).color(sr, sg, sb, alpha).endVertex();
        buffer.pos(x3, y3, z3).color(sr, sg, sb, alpha).endVertex();
        buffer.pos(x4, y4, z4).color(sr, sg, sb, alpha).endVertex();
    }
}
