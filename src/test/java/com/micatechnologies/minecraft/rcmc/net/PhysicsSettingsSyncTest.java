package com.micatechnologies.minecraft.rcmc.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.micatechnologies.minecraft.rcmc.RcmcConfig;
import com.micatechnologies.minecraft.rcmc.client.ClientPhysics;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A client predicts trains with the server's physics constants, not its own.
 *
 * <p>The docs said physics values were synced on join; nothing sent them. A client whose config
 * differed simulated every train differently between the server's corrections.</p>
 */
class PhysicsSettingsSyncTest {

    private static final PhysicsSettings SERVER = new PhysicsSettings(5.5D, 0.02D, 0.004D, 33.0D, 7);

    @AfterEach
    void forget() {
        ClientPhysics.set(null);
    }

    @Test
    @DisplayName("every physics value survives the trip to the client")
    void roundTrip() {
        ByteBuf buf = Unpooled.buffer();
        new PacketPhysicsSettings(SERVER).toBytes(buf);
        PacketPhysicsSettings received = new PacketPhysicsSettings();
        received.fromBytes(buf);
        PhysicsSettings got = received.settings();
        assertEquals(SERVER.gravity, got.gravity);
        assertEquals(SERVER.rollingResistance, got.rollingResistance);
        assertEquals(SERVER.airDrag, got.airDrag);
        assertEquals(SERVER.maxSpeed, got.maxSpeed);
        assertEquals(SERVER.subSteps, got.subSteps);
    }

    @Test
    @DisplayName("once the server's values arrive the client uses them, and its own again after")
    void clientUsesTheServersValues() {
        assertEquals(RcmcConfig.gravity, ClientPhysics.current().gravity, "own config before join");
        ClientPhysics.set(SERVER);
        assertEquals(SERVER.gravity, ClientPhysics.current().gravity);
        assertEquals(SERVER.subSteps, ClientPhysics.current().subSteps);
        ClientPhysics.set(null);
        assertEquals(RcmcConfig.gravity, ClientPhysics.current().gravity, "own config after leaving");
    }
}
