package com.micatechnologies.minecraft.rcmc.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNode;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import com.micatechnologies.minecraft.rcmc.track.math.TrackFrame;
import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrainBodyFrameTest {

    private static TrackNode node(double x, double y, double z) {
        return new TrackNode(new Vec3(x, y, z));
    }

    private static TrackNetwork straightNetwork() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(50, 64, 0), node(100, 64, 0)), false, null));
        return n;
    }

    private static TrackNetwork curvedNetwork() {
        TrackNetwork n = new TrackNetwork();
        n.addSection(new TrackSection(1, Arrays.asList(
            node(0, 64, 0), node(30, 64, 2), node(55, 64, 12),
            node(72, 64, 30), node(80, 64, 55)), false, null));
        return n;
    }

    private static PhysicsIntegrator integrator() {
        return new PhysicsIntegrator(9.81D, 0.0D, 0.0D, 100.0D);
    }

    @Test
    @DisplayName("on straight track the two-bogie body frame matches the single-point frame")
    void straightTrackMatchesPointFrame() {
        TrackNetwork network = straightNetwork();
        Train train = new Train(TrainSpec.metroTrain(1), integrator(), new TrackRef(1, 50.0D), 0.0D);

        TrackFrame point = train.frameOfCar(network, 0);
        TrackFrame body = train.bodyFrameOfCar(network, 0);
        assertEquals(point.position.x, body.position.x, 1e-6D);
        assertEquals(point.position.y, body.position.y, 1e-6D);
        assertEquals(point.position.z, body.position.z, 1e-6D);
        assertEquals(1.0D, Math.abs(body.forward.x), 1e-6D, "forward should lie along the track");
    }

    @Test
    @DisplayName("on a curve the body sits on the bogie chord, pulled toward the inside of the curve")
    void curveBodySitsOnTheChord() {
        TrackNetwork network = curvedNetwork();
        Train train = new Train(TrainSpec.metroTrain(1), integrator(), new TrackRef(1, 45.0D), 0.0D);

        TrackFrame point = train.frameOfCar(network, 0);
        TrackFrame body = train.bodyFrameOfCar(network, 0);
        double offset = body.position.subtract(point.position).length();
        assertTrue(offset > 0.15D,
            "a 14-block rigid body on this curve must sit measurably inside the rail line, offset was " + offset);

        // The chord stays level on flat track, and forward stays unit-length by construction.
        assertEquals(point.position.y, body.position.y, 1e-6D);
        assertEquals(1.0D, body.forward.length(), 1e-9D);
    }

    @Test
    @DisplayName("coaster stock keeps its single-point placement exactly")
    void coasterKeepsPointPlacement() {
        TrackNetwork network = curvedNetwork();
        Train train = new Train(TrainSpec.singleCar(), integrator(), new TrackRef(1, 45.0D), 0.0D);

        TrackFrame point = train.frameOfCar(network, 0);
        TrackFrame body = train.bodyFrameOfCar(network, 0);
        assertEquals(point.position.x, body.position.x, 0.0D);
        assertEquals(point.position.y, body.position.y, 0.0D);
        assertEquals(point.position.z, body.position.z, 0.0D);
    }
}
