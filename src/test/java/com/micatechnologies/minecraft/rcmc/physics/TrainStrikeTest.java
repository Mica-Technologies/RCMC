package com.micatechnologies.minecraft.rcmc.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Standing in front of a train should be dangerous, in proportion to how fast it is going. */
class TrainStrikeTest {

    /** A car pointing north-east, so an axis-aligned test would get it wrong. */
    private static TrackFrame diagonal() {
        return new TrackFrame(new Vec3(0, 64, 0), new Vec3(1, 0, 1).normalize(), Vec3.UP);
    }

    @Test
    @DisplayName("a fast coaster kills, a lift bruises, and a walking-pace train only shoves")
    void damageScalesWithSpeed() {
        double playerHealth = 20.0D;
        assertTrue(TrainStrike.damage(20.0D, 1.0D) >= playerHealth, "a coaster at speed should kill");
        double lift = TrainStrike.damage(5.0D, 1.0D);
        assertTrue(lift > 0.0D && lift < 6.0D, "a lift should hurt, not maim: " + lift);
        assertEquals(0.0D, TrainStrike.damage(2.0D, 1.0D), 1e-9, "a creeping train should not hurt");
        assertEquals(0.0D, TrainStrike.damage(30.0D, 0.0D), 1e-9, "a multiplier of 0 is knockback only");
    }

    @Test
    @DisplayName("someone beside the car's far end is hit, however the car is turned")
    void contactFollowsTheCarsOwnAxes() {
        TrainStrike.Body body = new TrainStrike.Body(10.0D, 1.9D, 0.3D, 5.95D);
        TrackFrame frame = diagonal();
        Vec3 nearFrontEnd = frame.position.add(frame.forward.scale(9.5D)).add(frame.right.scale(1.5D));
        assertNotNull(TrainStrike.contact(frame, body, nearFrontEnd, 0.3D, 1.8D),
            "the end of a long car is as solid as its middle");

        Vec3 clearToTheSide = frame.position.add(frame.right.scale(3.0D));
        assertNull(TrainStrike.contact(frame, body, clearToTheSide, 0.3D, 1.8D));
        Vec3 onTheRoof = frame.position.add(frame.up.scale(6.5D));
        assertNull(TrainStrike.contact(frame, body, onTheRoof, 0.3D, 1.8D), "above the roof is clear");
    }

    @Test
    @DisplayName("someone hit is thrown out to their own side and along with the train")
    void shoveThrowsClearOfTheTrack() {
        TrackFrame frame = diagonal();
        Vec3 leftSide = TrainStrike.shove(frame, 15.0D, -0.4D);
        assertTrue(leftSide.dot(frame.right) < 0.0D, "thrown across the track into the train's path");
        assertTrue(leftSide.dot(frame.forward) > 0.0D, "not thrown with the train");
        assertTrue(leftSide.y > 0.0D);

        Vec3 reversing = TrainStrike.shove(frame, -15.0D, 0.4D);
        assertTrue(reversing.dot(frame.forward) < 0.0D, "a train running backwards throws people backwards");
        assertTrue(reversing.dot(frame.right) > 0.0D);
    }
}
