package com.micatechnologies.minecraft.rcmc.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.element.Curve;
import com.micatechnologies.minecraft.rcmc.track.element.Slope;
import com.micatechnologies.minecraft.rcmc.track.element.Straight;
import com.micatechnologies.minecraft.rcmc.track.element.TurnDirection;
import com.micatechnologies.minecraft.rcmc.track.element.VerticalLoop;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests: build a handful of small coasters out of {@code track.element} pieces, rate them
 * with {@link RideRater}, and assert the results relate to each other the way a coaster designer would
 * expect. Deliberately does not pin exact numbers — see {@code RatingWeights}' own javadoc on why: the
 * weights are hand-tuned and will move, but "a tall fast coaster is more exciting than a short slow
 * one" should hold regardless of exactly how much.
 *
 * <p><b>A note on {@code track.element.Curve}'s authored bank sign.</b> Building the "unbanked vs.
 * banked" fixture below and checking it against {@code GForces} (which is independently verified by
 * {@code GForcesTest}) found that {@code Curve}'s own computed bank had the <em>opposite</em> sign
 * from what cancels lateral G. That was a real bug and has since been fixed in {@code
 * track.element}; these fixtures now use a generated curve's bank directly, as they always should
 * have. Kept as a note because it is the sort of finding a rating test is well placed to make: a
 * sign error is invisible to the element's own tests if they assert the sign, and obvious to
 * anything that measures what a rider feels.</p>
 */
class RideRaterTest {

    private static final double GRAVITY = 9.81D;

    private static PhysicsIntegrator integrator() {
        // Matches the constants DemoCoasterRunTest uses elsewhere in the project, so these test
        // coasters behave the way a real in-game one would.
        return new PhysicsIntegrator(GRAVITY, 0.01D, 0.0015D, 80.0D);
    }

    private static RideRater rater() {
        return RideRater.standard(integrator(), GRAVITY);
    }

    private static TrackFrame level(double y) {
        return new TrackFrame(new Vec3(0.0D, y, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), Vec3.UP);
    }

    // -----------------------------------------------------------------------------------------
    // Flat straight track: nothing should stand out.
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("a flat straight piece of track rates near zero on everything")
    void flatStraightRatesNearZero() {
        List<TrackNode> nodes = TestTracks.chain(level(64.0D), 0.0D, new Straight(100.0D));
        TrackNetwork network = TestTracks.singleSectionNetwork(1, nodes);

        RideRating rating = rater().rate(
            network, null, new TrackRef(1, 0.0D), TrainSpec.singleCar(), 6.0D);

        assertTrue(rating.excitement < 2.0D, "expected low excitement, got " + rating);
        assertTrue(rating.intensity < 1.0D, "expected low intensity, got " + rating);
        assertTrue(rating.nausea < 0.5D, "expected near-zero nausea, got " + rating);
        assertEquals(Verdict.LOW, rating.excitementVerdict);
        assertEquals(Verdict.LOW, rating.intensityVerdict);
        assertEquals(Verdict.LOW, rating.nauseaVerdict);
        assertTrue(rating.safety.safe);
    }

    // -----------------------------------------------------------------------------------------
    // Tall & fast vs. short & slow.
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("a tall fast coaster rates more exciting than a short slow one")
    void tallFastBeatsShortSlowOnExcitement() {
        List<TrackNode> tallNodes = TestTracks.chain(level(150.0D), 0.0D,
            new Straight(10.0D), new Slope(120.0D, -100.0D), new Straight(40.0D));
        List<TrackNode> shortNodes = TestTracks.chain(level(20.0D), 0.0D,
            new Straight(10.0D), new Slope(15.0D, -8.0D), new Straight(20.0D));

        // A small push off the flat lead-in: starting exactly at rest on level track is
        // indistinguishable, to Train's own stall detection, from a train genuinely stuck — see
        // Train.Status.VALLEYED. A real dispatch always leaves the station with some speed.
        RideRating tall = rater().rate(TestTracks.singleSectionNetwork(1, tallNodes), null,
            new TrackRef(1, 0.0D), TrainSpec.singleCar(), 2.0D);
        RideRating shortSlow = rater().rate(TestTracks.singleSectionNetwork(2, shortNodes), null,
            new TrackRef(2, 0.0D), TrainSpec.singleCar(), 2.0D);

        assertTrue(tall.excitement > shortSlow.excitement,
            "tall/fast should be more exciting: tall=" + tall + " short=" + shortSlow);
    }

    @Test
    @DisplayName("the tall coaster's simulated top speed reflects its bigger drop")
    void tallCoasterReachesHigherTopSpeed() {
        List<TrackNode> tallNodes = TestTracks.chain(level(150.0D), 0.0D,
            new Straight(10.0D), new Slope(120.0D, -100.0D), new Straight(40.0D));
        List<TrackNode> shortNodes = TestTracks.chain(level(20.0D), 0.0D,
            new Straight(10.0D), new Slope(15.0D, -8.0D), new Straight(20.0D));

        RideStatistics tall = rater().simulate(TestTracks.singleSectionNetwork(1, tallNodes), null,
            new TrackRef(1, 0.0D), TrainSpec.singleCar(), 2.0D);
        RideStatistics shortSlow = rater().simulate(TestTracks.singleSectionNetwork(2, shortNodes), null,
            new TrackRef(2, 0.0D), TrainSpec.singleCar(), 2.0D);

        assertTrue(tall.maxSpeedBlocksPerSecond > shortSlow.maxSpeedBlocksPerSecond * 1.5D,
            "expected the 100-block drop to produce a much higher top speed than the 8-block one: "
                + tall.maxSpeedBlocksPerSecond + " vs " + shortSlow.maxSpeedBlocksPerSecond);
        assertTrue(tall.maxDropHeightBlocks > shortSlow.maxDropHeightBlocks);
    }

    // -----------------------------------------------------------------------------------------
    // Unbanked vs. banked turn — the single most important assertion in this file. Same geometry,
    // same entry speed; only the authored bank differs.
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("an unbanked turn rates more nauseating than the same turn properly banked")
    void unbankedTurnIsMoreNauseatingThanBanked() {
        double designSpeed = 20.0D;
        double radius = 40.0D;

        List<TrackNode> rawCurveNodes = TestTracks.chain(level(64.0D), 0.0D,
            new Straight(10.0D),
            new Curve(radius, 150.0D, TurnDirection.RIGHT, designSpeed, 60.0D, GRAVITY),
            new Straight(20.0D));
        List<TrackNode> bankedNodes = rawCurveNodes;
        List<TrackNode> unbankedNodes = TestTracks.zeroBank(rawCurveNodes);

        TrackNetwork bankedNetwork = TestTracks.singleSectionNetwork(1, bankedNodes);
        TrackNetwork unbankedNetwork = TestTracks.singleSectionNetwork(2, unbankedNodes);

        RideStatistics banked = rater().simulate(bankedNetwork, null,
            new TrackRef(1, 0.0D), TrainSpec.singleCar(), designSpeed);
        RideStatistics unbanked = rater().simulate(unbankedNetwork, null,
            new TrackRef(2, 0.0D), TrainSpec.singleCar(), designSpeed);

        // The physical claim the whole nausea model rests on: stripping the bank should not change
        // the geometry at all, only how much of the turn shows up as lateral G instead of vertical.
        // Peak lateral G alone understates the difference — both fixtures still share the same
        // ease-in/ease-out zones at the very start and end of the curve where bank is near zero
        // either way — which is exactly why the nausea model is built on the duration-weighted
        // integral rather than the peak; see RideStatistics#sustainedLateralGSeconds's javadoc.
        assertTrue(unbanked.peakLateralG >= banked.peakLateralG,
            "unbanked turn should feel at least as much sideways load at its worst instant: banked="
                + banked.peakLateralG + "g unbanked=" + unbanked.peakLateralG + "g");
        assertTrue(unbanked.sustainedLateralGSeconds > banked.sustainedLateralGSeconds * 2.5D,
            "unbanked turn should accumulate dramatically more sustained lateral load: banked="
                + banked.sustainedLateralGSeconds + " unbanked=" + unbanked.sustainedLateralGSeconds);

        RideRating bankedRating = RideRating.from(banked);
        RideRating unbankedRating = RideRating.from(unbanked);
        assertTrue(unbankedRating.nausea > bankedRating.nausea,
            "unbanked should rate more nauseating: banked=" + bankedRating + " unbanked=" + unbankedRating);
    }

    // -----------------------------------------------------------------------------------------
    // Violent vs. gentle — intensity.
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("a violent ride rates more intense than a gentle one")
    void violentRideBeatsGentleRideOnIntensity() {
        // A big drop into a tight, nearly-unbanked high-speed turn (maxBankDegrees clamped low
        // forces most of the turn's load to stay lateral rather than being absorbed by bank) plus a
        // vertical loop.
        List<TrackNode> violentNodes = TestTracks.chain(level(150.0D), 0.0D,
            new Straight(10.0D),
            new Slope(120.0D, -100.0D),
            new Straight(10.0D),
            new Curve(15.0D, 150.0D, TurnDirection.RIGHT, 35.0D, 5.0D, GRAVITY),
            new VerticalLoop(12.0D));

        // A small drop into a big, gentle, properly banked turn at low speed. This fixture is only
        // meaningful if the bank genuinely cancels lateral G rather than adding to it, which is
        // exactly what the Curve sign fix restored.
        List<TrackNode> gentleNodes = (TestTracks.chain(level(30.0D), 0.0D,
            new Straight(10.0D),
            new Slope(20.0D, -5.0D),
            new Straight(10.0D),
            new Curve(80.0D, 90.0D, TurnDirection.RIGHT, 8.0D, 60.0D, GRAVITY),
            new Straight(10.0D)));

        // Both start with a flat lead-in, so both need a small push off rest — see the tall/short
        // test above for why a train starting exactly at rest on level track reads as stalled.
        RideRating violent = rater().rate(TestTracks.singleSectionNetwork(1, violentNodes), null,
            new TrackRef(1, 0.0D), TrainSpec.singleCar(), 2.0D);
        RideRating gentle = rater().rate(TestTracks.singleSectionNetwork(2, gentleNodes), null,
            new TrackRef(2, 0.0D), TrainSpec.singleCar(), 2.0D);

        assertTrue(violent.intensity > gentle.intensity,
            "violent ride should be more intense: violent=" + violent + " gentle=" + gentle);
    }

    @Test
    @DisplayName("an extreme unbanked high-speed turn is flagged unsafe")
    void extremeTurnTripsSafetyLimits() {
        // v^2/(r*g) at 35 blocks/s and an 8-block radius is roughly 15.6g of required centripetal
        // acceleration; clamping the bank to nearly nothing leaves almost all of that as lateral G,
        // far past SafetyLimits.DEFAULT's 2.5g ceiling.
        List<TrackNode> nodes = TestTracks.chain(level(64.0D), 0.0D,
            new Straight(10.0D),
            new Curve(8.0D, 150.0D, TurnDirection.RIGHT, 35.0D, 1.0D, GRAVITY));

        RideRating rating = rater().rate(TestTracks.singleSectionNetwork(1, nodes), null,
            new TrackRef(1, 0.0D), TrainSpec.singleCar(), 35.0D);

        assertTrue(!rating.safety.safe, "expected the safety verdict to flag this turn: " + rating);
        assertTrue(!rating.safety.violations.isEmpty());
        assertEquals(Verdict.ULTRA_EXTREME, rating.intensityVerdict);
        assertEquals(Verdict.ULTRA_EXTREME, rating.nauseaVerdict);
    }

    // -----------------------------------------------------------------------------------------
    // Determinism.
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("rating the same track twice produces identical results")
    void ratingIsDeterministic() {
        List<TrackNode> nodes = TestTracks.chain(level(100.0D), 0.0D,
            new Straight(10.0D), new Slope(60.0D, -40.0D),
            new Curve(30.0D, 120.0D, TurnDirection.LEFT, 15.0D, 45.0D, GRAVITY), new Straight(20.0D));

        RideRating first = rater().rate(TestTracks.singleSectionNetwork(1, nodes), null,
            new TrackRef(1, 0.0D), TrainSpec.singleCar());
        RideRating second = rater().rate(TestTracks.singleSectionNetwork(2, nodes), null,
            new TrackRef(2, 0.0D), TrainSpec.singleCar());

        assertEquals(first.excitement, second.excitement, 0.0D);
        assertEquals(first.intensity, second.intensity, 0.0D);
        assertEquals(first.nausea, second.nausea, 0.0D);
        assertEquals(first.excitementVerdict, second.excitementVerdict);
        assertEquals(first.intensityVerdict, second.intensityVerdict);
        assertEquals(first.nauseaVerdict, second.nauseaVerdict);
        assertEquals(first.safety.safe, second.safety.safe);
    }

    @Test
    @DisplayName("simulating the same track twice produces identical statistics")
    void simulationIsDeterministic() {
        List<TrackNode> nodes = TestTracks.chain(level(100.0D), 0.0D,
            new Straight(10.0D), new Slope(60.0D, -40.0D), new Straight(20.0D));
        TrackNetwork networkA = TestTracks.singleSectionNetwork(1, nodes);
        TrackNetwork networkB = TestTracks.singleSectionNetwork(1, nodes);

        RideStatistics a = rater().simulate(networkA, null, new TrackRef(1, 0.0D), TrainSpec.singleCar(), 0.0D);
        RideStatistics b = rater().simulate(networkB, null, new TrackRef(1, 0.0D), TrainSpec.singleCar(), 0.0D);

        assertEquals(a.maxSpeedBlocksPerSecond, b.maxSpeedBlocksPerSecond, 0.0D);
        assertEquals(a.maxDropHeightBlocks, b.maxDropHeightBlocks, 0.0D);
        assertEquals(a.peakLateralG, b.peakLateralG, 0.0D);
        assertEquals(a.peakPositiveVerticalG, b.peakPositiveVerticalG, 0.0D);
        assertEquals(a.peakNegativeVerticalG, b.peakNegativeVerticalG, 0.0D);
        assertEquals(a.directionChangeCount, b.directionChangeCount);
        assertEquals(a.inversionCount, b.inversionCount);
        assertEquals(a.ticksSimulated, b.ticksSimulated);
    }
}
