package com.micatechnologies.minecraft.rcmc.physics;

/**
 * Where each rider sits in a car, and how many fit.
 *
 * <p>Pure Java and free of Minecraft types, like everything else in this package, so the seat
 * arithmetic can be tested on a bare JVM — the entity that consumes it cannot be. That matters
 * more than it looks: a wrong offset here does not throw, it seats a player inside a wall or
 * hovering beside a moving train, and the only way anyone finds out is by riding it.</p>
 *
 * <p>Offsets are in the car's own frame, in blocks: {@code across} along the car's right axis and
 * {@code along} its forward axis. The entity adds them to the body frame, so a seated rider banks
 * and turns with the car for free.</p>
 *
 * <p><b>Metro seating</b> mirrors {@code MetroCarModel}'s two longitudinal benches: riders alternate
 * left and right, filling from the middle of the car outward in pairs. The bench separation is one
 * measurement expressed in two places — see {@link #BENCH_HALF_SEPARATION}.</p>
 */
public final class CarSeating {

    /**
     * Half the distance between the two longitudinal benches, in blocks.
     *
     * <p>From {@code MetroCarModel}: the benches run from the inner wall face
     * ({@code BODY_HALF_WIDTH 1.90 − WALL_THICKNESS 0.10} = 1.80) inward by
     * {@code SEAT_DEPTH 0.55}, so a rider sits on the middle of the cushion at 1.525. Change the
     * model and change this together.</p>
     */
    public static final double BENCH_HALF_SEPARATION = 1.525D;

    /** Seat height above the car's track-frame origin, in blocks. Metro floors are high. */
    public static final double METRO_SEAT_HEIGHT = 2.45D;

    /** Half the car body's width — nothing inside it may sit outside this. */
    public static final double METRO_BODY_HALF_WIDTH = 1.90D;

    /**
     * Overall height of a metro car above the railheads, mirroring {@code MetroCarModel.ROOF_TOP}.
     *
     * <p>Lives here so the things that must clear it — contact wire heights, the tunnel conductor —
     * can be tested against one number instead of each restating it. Three separate copies of
     * "4.95" in test files is what this replaces, and every one of them would have had to be found
     * and edited when the underframe was raised.</p>
     */
    public static final double METRO_ROOF_HEIGHT = 5.95D;

    /**
     * Height of the saloon floor above the car's frame origin, in blocks.
     *
     * <p>A whole number on purpose: block tops are integers, so a floor at any fraction of a block
     * guarantees a step at every platform edge. Mirrors {@code MetroCarModel.FLOOR_TOP}. Lives here
     * because the floor is not only drawn —
     * {@code EntityCoasterCar.getCollisionBoundingBox} needs it to leave an open car something to
     * stand on, and that is common-side code which must not reach into a client renderer.</p>
     */
    public static final double METRO_FLOOR_HEIGHT = 2.00D;

    /** Seat height for a coaster car — riders sit much closer to the rails. */
    public static final double COASTER_SEAT_HEIGHT = 0.31D;

    /** Clear space kept at each end of the saloon, so no seat lands in the cab area. */
    private static final double END_MARGIN = 2.0D;

    /** Trucks sit at this fraction of body length, per {@code MetroCarModel}'s convention. */
    private static final double TRUCK_CENTRE_RATIO = 0.72D;

    private CarSeating() {
        throw new AssertionError("No instances.");
    }

    /**
     * How many riders a single car of this spec holds.
     *
     * <p>A metro car seats its whole bench — {@code seatsPerCar}, which the presets already carry
     * (8/10/12) and which until 2026-07-21 only decided how many cushions were <em>drawn</em>. A
     * ten-seat car that admitted one passenger was why a metro could not really be ridden.</p>
     *
     * <p>Coaster cars stay at one rider deliberately: {@code CarModel}'s seat rows have no
     * corresponding offsets here, so extra riders would stack on the centreline. That is a real
     * gap, but a coaster one, and it should be closed by giving coasters a seat layout rather than
     * by letting them overfill.</p>
     */
    public static int capacity(TrainSpec spec) {
        if (spec == null) {
            return 1;
        }
        return spec.carStyle() == TrainSpec.CarStyle.METRO
            ? Math.max(1, spec.seatsPerCar())
            : 1;
    }

    /** Seat height above the frame origin for this spec's style. */
    public static double seatHeight(TrainSpec spec) {
        return spec != null && spec.carStyle() == TrainSpec.CarStyle.METRO
            ? METRO_SEAT_HEIGHT : COASTER_SEAT_HEIGHT;
    }

    /**
     * Lateral offset of seat {@code index}: alternating benches for a metro, centreline otherwise.
     * Even indices take the right-hand bench, odd the left.
     */
    public static double acrossOffset(TrainSpec spec, int index) {
        if (spec == null || spec.carStyle() != TrainSpec.CarStyle.METRO || index < 0) {
            return 0.0D;
        }
        return (index % 2 == 0 ? 1.0D : -1.0D) * BENCH_HALF_SEPARATION;
    }

    /**
     * Longitudinal offset of seat {@code index} from the car's centre.
     *
     * <p>Seat pairs are spread evenly over the saloon rather than placed against the model's three
     * individual bench runs. Riders in the door bays would look slightly wrong, but chasing exact
     * run boundaries would tie this to client-side geometry that is free to change; even spacing
     * always lands a rider on a cushion and degrades gracefully at any seat count.</p>
     */
    public static double alongOffset(TrainSpec spec, int index) {
        if (spec == null || spec.carStyle() != TrainSpec.CarStyle.METRO || index < 0) {
            return 0.0D;
        }
        int rows = Math.max(1, capacity(spec) / 2);
        if (rows == 1) {
            return 0.0D;
        }
        int row = Math.min(index / 2, rows - 1);
        double usable = Math.max(0.0D, bodyLength(spec) - END_MARGIN);
        return -usable * 0.5D + usable * row / (rows - 1);
    }

    /**
     * Half-width of the aisle a standing rider may walk in, in blocks.
     *
     * <p>The benches reach {@code SEAT_DEPTH} in from each wall, so the clear aisle is bounded by
     * their inner edge; half a player's width is then kept back from that so a rider stands in the
     * aisle rather than clipping into a seat. This is the only thing stopping a standing passenger
     * walking through the side of the train: their position is written directly each tick, so
     * vanilla collision never gets a say and these bounds <em>are</em> the walls.</p>
     */
    public static double walkableHalfWidth(TrainSpec spec) {
        if (spec == null || spec.carStyle() != TrainSpec.CarStyle.METRO) {
            return 0.0D;
        }
        return BENCH_INNER_EDGE - PLAYER_HALF_WIDTH;
    }

    /** Half-length of the walkable saloon, keeping a rider clear of the end bulkheads. */
    public static double walkableHalfLength(TrainSpec spec) {
        if (spec == null || spec.carStyle() != TrainSpec.CarStyle.METRO) {
            return 0.0D;
        }
        return Math.max(0.0D, bodyLength(spec) * 0.5D - END_WALL_CLEARANCE);
    }

    /** Where the benches stop and the aisle begins: {@code 1.80 inner wall − 0.55 seat depth}. */
    private static final double BENCH_INNER_EDGE = 1.25D;

    /** Half a player's collision width. */
    private static final double PLAYER_HALF_WIDTH = 0.31D;

    /** How far a standing rider is kept from each end bulkhead. */
    private static final double END_WALL_CLEARANCE = 0.9D;

    /** Visible body length, derived from bogie spacing per the model's truck-centre ratio. */
    public static double bodyLength(TrainSpec spec) {
        return spec == null ? 0.0D : spec.carLength() / TRUCK_CENTRE_RATIO;
    }
}
