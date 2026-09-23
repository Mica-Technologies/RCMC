package com.micatechnologies.minecraft.rcmc.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.TrainType;
import com.micatechnologies.minecraft.rcmc.physics.TrainTypes;
import com.micatechnologies.minecraft.rcmc.track.TrackPalette;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A server's own train types: read from JSON, over the built-ins, and refused with a reason. */
class TrainTypeFilesTest {

    @AfterEach
    void restoreBuiltIns() {
        TrainTypes.setCustom(Collections.emptyList());
    }

    @Test
    @DisplayName("the built-in types build exactly the trains the old presets did")
    void builtInsMatchTheOldPresets() {
        assertEquals(TrainSpec.metroTrain(3), TrainTypes.get("metro").spec(3));
        assertEquals(TrainSpec.metroTrainCompact(4), TrainTypes.get("metrocompact").spec(4));
        assertEquals(TrainSpec.metroTrainLong(2), TrainTypes.get("metrolong").spec(2));
        assertEquals(new TrainSpec(5, 3.0D, 0.5D, 4).withCoasterModel(TrainSpec.CoasterModel.WOODEN),
            TrainTypes.get("wooden").spec(5));
    }

    @Test
    @DisplayName("a file defines a new type, and anything it leaves out comes from the built-in")
    void fileDefinesAType() {
        TrainType type = TrainTypeFiles.parse("{\"id\": \"orange_line\", \"name\": \"Orange Line\","
            + " \"body\": \"metro\", \"defaultCars\": 6, \"maxCars\": 8,"
            + " \"colours\": {\"trim\": \"orange\", \"seats\": \"blue\"}}");
        assertEquals("Orange Line", type.name);
        assertEquals(6, type.defaultCars);
        assertEquals(TrainTypes.get("metro").carLength, type.carLength, 1e-9, "carLength defaults to the metro's");
        assertEquals(TrackPalette.Colour.BLUE.ordinal(), type.seatColour);
        TrainSpec spec = type.spec(20);
        assertEquals(8, spec.carCount(), "a train is held to the type's maxCars");
        assertEquals(TrainSpec.CarStyle.METRO, spec.carStyle());
    }

    @Test
    @DisplayName("a file with a built-in's id replaces it, and a new coaster type joins the panel's list")
    void filesOverrideAndAdd() {
        TrainType longWooden = TrainTypeFiles.parse("{\"id\": \"wooden\", \"body\": \"wooden\", \"seatsPerCar\": 6}");
        TrainType family = TrainTypeFiles.parse("{\"id\": \"family\", \"name\": \"Family\", \"body\": \"sit_down\"}");
        TrainTypes.setCustom(java.util.Arrays.asList(longWooden, family));

        assertEquals(6, TrainTypes.get("wooden").seatsPerCar);
        assertTrue(TrainTypes.coasters().contains(family));
        assertEquals("family", TrainTypes.nextCoaster("wooden").id);
        assertNotNull(TrainTypes.get("FAMILY"), "ids are case-insensitive");
    }

    @Test
    @DisplayName("a bad file is refused, and says why")
    void badFilesSayWhy() {
        assertTrue(message("{\"id\": \"Bad Id\", \"body\": \"metro\"}").contains("\"id\""));
        assertTrue(message("{\"id\": \"x\", \"body\": \"monorail\"}").contains("\"body\""));
        assertTrue(message("{\"id\": \"x\", \"body\": \"metro\", \"maxCars\": 40}").contains("maxCars"));
        assertTrue(message("{\"id\": \"x\", \"body\": \"metro\", \"colours\": {\"body\": \"mauve\"}}")
            .contains("mauve"));
        assertTrue(message("not json").contains("JSON"));
    }

    private static String message(String json) {
        return assertThrows(IllegalArgumentException.class, () -> TrainTypeFiles.parse(json)).getMessage();
    }
}
