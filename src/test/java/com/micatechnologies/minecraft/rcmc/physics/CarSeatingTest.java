package com.micatechnologies.minecraft.rcmc.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Seat capacity and layout.
 *
 * <p>These assert the requirement — every rider gets a distinct place, on a bench, inside the car —
 * rather than the arithmetic that currently produces it, so the spacing formula can be retuned
 * without rewriting the tests. A wrong offset here does not throw; it seats a player inside a wall
 * on a moving train, and only riding it would reveal that.</p>
 */
class CarSeatingTest {

    @Test
    @DisplayName("a metro car seats its whole bench, not one rider")
    void metroCarsSeatEveryone() {
        assertEquals(10, CarSeating.capacity(TrainSpec.metroTrain(3)));
        assertEquals(8, CarSeating.capacity(TrainSpec.metroTrainCompact(3)));
        assertEquals(12, CarSeating.capacity(TrainSpec.metroTrainLong(3)));
    }

    @Test
    @DisplayName("a coaster car still seats exactly one, having no seat layout of its own")
    void coasterCarsSeatOne() {
        assertEquals(1, CarSeating.capacity(TrainSpec.singleCar()));
    }

    @Test
    @DisplayName("an unknown spec is treated as a single seat rather than as no seats")
    void nullSpecIsRideable() {
        assertEquals(1, CarSeating.capacity(null),
            "a car whose train state has not arrived must still be boardable, not permanently full");
        assertEquals(0.0D, CarSeating.acrossOffset(null, 0), 1e-9D);
        assertEquals(0.0D, CarSeating.alongOffset(null, 3), 1e-9D);
    }

    @Test
    @DisplayName("riders alternate between the two benches")
    void ridersAlternateSides() {
        TrainSpec spec = TrainSpec.metroTrain(1);

        assertEquals(CarSeating.BENCH_HALF_SEPARATION, CarSeating.acrossOffset(spec, 0), 1e-9D);
        assertEquals(-CarSeating.BENCH_HALF_SEPARATION, CarSeating.acrossOffset(spec, 1), 1e-9D);
        assertEquals(CarSeating.BENCH_HALF_SEPARATION, CarSeating.acrossOffset(spec, 2), 1e-9D);
    }

    @Test
    @DisplayName("no two riders in a car share a seat")
    void everySeatIsDistinct() {
        TrainSpec spec = TrainSpec.metroTrainLong(1);
        int seats = CarSeating.capacity(spec);

        for (int a = 0; a < seats; a++) {
            for (int b = a + 1; b < seats; b++) {
                boolean sameSide =
                    Math.abs(CarSeating.acrossOffset(spec, a) - CarSeating.acrossOffset(spec, b))
                        < 1e-9D;
                boolean samePlaceAlong =
                    Math.abs(CarSeating.alongOffset(spec, a) - CarSeating.alongOffset(spec, b))
                        < 1e-9D;
                assertTrue(!(sameSide && samePlaceAlong),
                    "seats " + a + " and " + b + " occupy the same place");
            }
        }
    }

    @Test
    @DisplayName("every seat sits inside the car body, clear of the ends")
    void seatsStayInsideTheBody() {
        for (TrainSpec spec : new TrainSpec[] {
            TrainSpec.metroTrainCompact(1), TrainSpec.metroTrain(1), TrainSpec.metroTrainLong(1)}) {
            double halfBody = CarSeating.bodyLength(spec) * 0.5D;
            for (int seat = 0; seat < CarSeating.capacity(spec); seat++) {
                double along = CarSeating.alongOffset(spec, seat);
                assertTrue(Math.abs(along) <= halfBody - 0.5D,
                    "seat " + seat + " at " + along + " is outside a body of half-length " + halfBody);
                assertTrue(Math.abs(CarSeating.acrossOffset(spec, seat))
                        < CarSeating.METRO_BODY_HALF_WIDTH,
                    "seat " + seat + " is outside the car body");
            }
        }
    }

    @Test
    @DisplayName("seats are spread along the car rather than piled at its centre")
    void seatsAreSpreadAlongTheCar() {
        TrainSpec spec = TrainSpec.metroTrain(1);

        assertNotEquals(CarSeating.alongOffset(spec, 0), CarSeating.alongOffset(spec, 2), 1e-6D,
            "consecutive bench rows must be at different places along the car");
    }

    @Test
    @DisplayName("metro riders sit a full block higher than coaster riders")
    void seatHeightFollowsTheStyle() {
        assertEquals(CarSeating.METRO_SEAT_HEIGHT,
            CarSeating.seatHeight(TrainSpec.metroTrain(1)), 1e-9D);
        assertEquals(CarSeating.COASTER_SEAT_HEIGHT,
            CarSeating.seatHeight(TrainSpec.singleCar()), 1e-9D);
        assertTrue(CarSeating.METRO_SEAT_HEIGHT > CarSeating.COASTER_SEAT_HEIGHT);
    }

    @Test
    @DisplayName("the walkable aisle stays clear of the benches on both sides")
    void aisleIsClearOfTheBenches() {
        for (TrainSpec spec : new TrainSpec[] {
            TrainSpec.metroTrainCompact(1), TrainSpec.metroTrain(1), TrainSpec.metroTrainLong(1)}) {
            double aisle = CarSeating.walkableHalfWidth(spec);

            assertTrue(aisle > 0.5D, "there must be room to actually walk, got " + aisle);
            // A standing rider's position is written directly each tick, so vanilla collision never
            // runs: these bounds ARE the walls and the seat fronts.
            assertTrue(aisle < CarSeating.BENCH_HALF_SEPARATION,
                "the aisle must stop short of where seated riders are, or they overlap");
            assertTrue(aisle < CarSeating.METRO_BODY_HALF_WIDTH,
                "a walking rider must never reach outside the body");
        }
    }

    @Test
    @DisplayName("the walkable length stays inside the car's end walls")
    void walkableLengthStaysInsideTheBody() {
        for (TrainSpec spec : new TrainSpec[] {
            TrainSpec.metroTrainCompact(1), TrainSpec.metroTrain(1), TrainSpec.metroTrainLong(1)}) {
            double half = CarSeating.walkableHalfLength(spec);

            assertTrue(half > 1.0D, "a car should be worth walking down, got " + half);
            assertTrue(half < CarSeating.bodyLength(spec) * 0.5D,
                "a walking rider must never reach through an end bulkhead");
        }
    }

    @Test
    @DisplayName("coaster cars have no walkable interior at all")
    void coasterHasNoAisle() {
        assertEquals(0.0D, CarSeating.walkableHalfWidth(TrainSpec.singleCar()), 1e-9D);
        assertEquals(0.0D, CarSeating.walkableHalfLength(TrainSpec.singleCar()), 1e-9D);
        assertEquals(0.0D, CarSeating.walkableHalfWidth(null), 1e-9D);
    }

    @Test
    @DisplayName("an out-of-range seat index does not throw or fling a rider outside the car")
    void outOfRangeIndexIsContained() {
        TrainSpec spec = TrainSpec.metroTrain(1);
        double halfBody = CarSeating.bodyLength(spec) * 0.5D;

        // -1 is what indexOf returns for a passenger that is not aboard.
        assertEquals(0.0D, CarSeating.acrossOffset(spec, -1), 1e-9D);
        assertEquals(0.0D, CarSeating.alongOffset(spec, -1), 1e-9D);
        assertTrue(Math.abs(CarSeating.alongOffset(spec, 999)) <= halfBody);
    }
}
