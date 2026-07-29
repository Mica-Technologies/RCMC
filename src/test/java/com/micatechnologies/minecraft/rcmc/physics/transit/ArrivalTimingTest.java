package com.micatechnologies.minecraft.rcmc.physics.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How long a train has left before it berths — what a platform announcement is really asking.
 *
 * <p>Reported from a live session: the "now approaching" call did not fire until the train had
 * <em>arrived</em>. The trigger was a fixed 30-block distance, which sounds reasonable and is not:
 * a train on its stopping curve is already crawling at 30 blocks out, so a call that started then
 * was still being spoken as the doors opened.</p>
 */
class ArrivalTimingTest {

    /** The metro preset's service braking rate. */
    private static final double BRAKE = 1.2D;

    @Test
    @DisplayName("a braking train's remaining time is far longer than distance over speed suggests")
    void brakingTakesTwiceAsLong() {
        // On the stopping curve the train covers the remaining distance at a mean of half its
        // current speed, so it takes twice as long as a naive d/v — which is exactly the error that
        // made a distance-based trigger fire too late.
        double distance = 30.0D;
        double speed = Math.sqrt(2.0D * BRAKE * distance); // exactly on the curve

        double seconds = ArrivalEstimator.secondsToArrival(distance, speed, BRAKE);

        assertEquals(2.0D * distance / speed, seconds, 1e-9D);
        assertTrue(seconds > distance / speed, "a decelerating train takes longer, not less");
    }

    @Test
    @DisplayName("the two regimes agree exactly where the train starts braking")
    void regimesMeetAtTheBrakePoint() {
        // Continuity matters: a jump at the boundary would make the estimate flicker across it, and
        // a flickering estimate means an announcement that fires, unfires and fires again.
        double speed = 15.0D;
        double brakePoint = speed * speed / (2.0D * BRAKE);

        double justBraking = ArrivalEstimator.secondsToArrival(brakePoint - 1e-6D, speed, BRAKE);
        double justCruising = ArrivalEstimator.secondsToArrival(brakePoint + 1e-6D, speed, BRAKE);

        assertEquals(justBraking, justCruising, 1e-4D);
    }

    @Test
    @DisplayName("a cruising train counts its cruise and its braking separately")
    void cruisingAddsTheBrakingTime() {
        double speed = 15.0D;
        double distance = 300.0D;

        double seconds = ArrivalEstimator.secondsToArrival(distance, speed, BRAKE);

        assertEquals(distance / speed + speed / (2.0D * BRAKE), seconds, 1e-9D);
    }

    @Test
    @DisplayName("a stopped train has no arrival time, rather than an arrival time of zero")
    void stoppedTrainIsUnknown() {
        // A train dwelling at the previous platform must not trigger this station's call. As it
        // pulls away the estimate falls from infinity, so the announcement fires when it is due.
        assertTrue(Double.isInfinite(ArrivalEstimator.secondsToArrival(200.0D, 0.0D, BRAKE)));
        assertTrue(Double.isInfinite(ArrivalEstimator.secondsToArrival(200.0D, 0.01D, BRAKE)));
    }

    @Test
    @DisplayName("a train already at the stop point has arrived")
    void arrivedIsZero() {
        assertEquals(0.0D, ArrivalEstimator.secondsToArrival(0.0D, 5.0D, BRAKE), 1e-9D);
        assertEquals(0.0D, ArrivalEstimator.secondsToArrival(-3.0D, 5.0D, BRAKE), 1e-9D,
            "overshot counts as arrived, not as negative time");
    }

    @Test
    @DisplayName("a fixed distance buys whatever time the braking rate happens to give")
    void aFixedDistanceIsNotAFixedAmountOfTime() {
        // Why the trigger moved to time. 30 blocks is not an amount of time: it is however long
        // the train takes to cover 30 blocks, which depends entirely on how hard it brakes. On the
        // metro preset it happens to leave about 7 seconds — enough. Firm up the braking and the
        // same 30 blocks leaves under 5, and the call is still being spoken as the doors open.
        //
        // Written after the first version of this test asserted the 30-block trigger was too late
        // on the stock preset and failed, which was the honest answer: it was marginal, not broken.
        double distance = 30.0D;
        double announcementSeconds = 5.5D;

        double onStockBrake = ArrivalEstimator.secondsToArrival(
            distance, Math.sqrt(2.0D * BRAKE * distance), BRAKE);
        double firmBrake = 2.4D;
        double onFirmBrake = ArrivalEstimator.secondsToArrival(
            distance, Math.sqrt(2.0D * firmBrake * distance), firmBrake);

        assertTrue(onStockBrake > announcementSeconds,
            "the stock preset was marginal but adequate: " + String.format("%.1f", onStockBrake) + "s");
        assertTrue(onFirmBrake < announcementSeconds,
            "the same distance on a firmer brake leaves only "
                + String.format("%.1f", onFirmBrake) + "s, so the call cannot finish");
    }

    @Test
    @DisplayName("a longer announcement starts further out, which a distance trigger cannot do")
    void longerCallsNeedMoreRoom() {
        // The other half of it: the trigger has to account for how long the sentence takes to say.
        // "The next inbound Ring train to Parkside is now approaching" is a good deal longer than a
        // short line, and a fixed distance gives both exactly the same run-up.
        double speed = 15.0D;

        double shortCall = firstDistanceMeeting(speed, 3.0D);
        double longCall = firstDistanceMeeting(speed, 7.0D);

        assertTrue(longCall > shortCall,
            "a longer call must start earlier; got " + longCall + " vs " + shortCall);
    }

    /** Where a call of {@code seconds} would fire, walking the train in from far away. */
    private static double firstDistanceMeeting(double speed, double seconds) {
        for (double d = 600.0D; d > 0.0D; d -= 0.5D) {
            if (ArrivalEstimator.secondsToArrival(d, speed, BRAKE) <= seconds) {
                return d;
            }
        }
        return 0.0D;
    }

    @Test
    @DisplayName("a 5.5s call at cruise fires far enough out to finish before the train arrives")
    void timedTriggerFiresEarlyEnough() {
        // What the fix buys: at line speed the same announcement now starts while the train is
        // still well down the tunnel, and lands as it pulls in.
        double speed = 15.0D;
        double announcementSeconds = 5.5D;

        // Walk in from far away and find where the trigger would fire.
        double firedAt = -1.0D;
        for (double d = 400.0D; d > 0.0D; d -= 0.5D) {
            if (ArrivalEstimator.secondsToArrival(d, speed, BRAKE) <= announcementSeconds) {
                firedAt = d;
                break;
            }
        }

        assertTrue(firedAt > 30.0D,
            "expected the call to start well beyond the old 30-block mark, got " + firedAt);
    }
}
