package com.micatechnologies.minecraft.rcmc.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micatechnologies.minecraft.rcmc.physics.TrainType;
import com.micatechnologies.minecraft.rcmc.physics.TrainTypes;
import com.micatechnologies.minecraft.rcmc.physics.ride.RideController;
import com.micatechnologies.minecraft.rcmc.track.TrackPalette;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Reads the server's own train types from {@code config/rcmc/trains/*.json}.
 *
 * <p>Each file is one type. A file with a built-in's id replaces it; any other id adds a type. A file
 * that cannot be read, or asks for something no train can be, is skipped and reported, so one bad
 * file never takes the others — or the server — down with it. The first time the folder is made,
 * the built-in types are written into {@code examples/} beside a README, to copy from; that folder
 * is not read.</p>
 */
public final class TrainTypeFiles {

    /** What a load found: how many types came in, and what was wrong with the rest. */
    public static final class Result {
        public final int loaded;
        public final List<String> problems;

        Result(int loaded, List<String> problems) {
            this.loaded = loaded;
            this.problems = problems;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static File folder;

    private TrainTypeFiles() {
    }

    /** Remembers {@code configDir}'s {@code rcmc/trains} and loads it. Called once, at startup. */
    public static Result init(File configDir) {
        folder = new File(new File(configDir, "rcmc"), "trains");
        if (!folder.exists() && folder.mkdirs()) {
            writeExamples(folder);
        }
        return reload();
    }

    /** Reads the folder again, replacing the types it loaded before. */
    public static Result reload() {
        List<TrainType> types = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        File[] files = folder == null ? null : folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files != null) {
            Arrays.sort(files);
            for (File file : files) {
                try {
                    types.add(parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)));
                }
                catch (IOException | RuntimeException e) {
                    problems.add(file.getName() + ": " + e.getMessage());
                }
            }
        }
        TrainTypes.setCustom(types);
        return new Result(types.size(), problems);
    }

    /** One type from its JSON; throws {@link IllegalArgumentException} saying what is wrong. */
    public static TrainType parse(String json) {
        JsonElement root;
        try {
            root = new JsonParser().parse(json);
        }
        catch (RuntimeException e) {
            throw new IllegalArgumentException("not valid JSON (" + e.getMessage() + ")");
        }
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("expected a JSON object");
        }
        JsonObject o = root.getAsJsonObject();
        String id = string(o, "id", null);
        if (id == null || !id.matches("[a-z0-9_]{1,32}")) {
            throw new IllegalArgumentException("\"id\" must be 1-32 lower-case letters, digits or _");
        }
        TrainType.Body body = TrainType.Body.byWord(string(o, "body", ""));
        if (body == null) {
            throw new IllegalArgumentException("\"body\" must be sit_down, shoulder, wooden or metro");
        }
        TrainType base = TrainTypes.get(body == TrainType.Body.METRO ? "metro" : "coaster");
        String name = string(o, "name", id);
        double carLength = number(o, "carLength", base.carLength, 1.0D, 40.0D);
        double gap = number(o, "couplingGap", base.couplingGap, 0.0D, 20.0D);
        int seats = (int) number(o, "seatsPerCar", base.seatsPerCar, 1.0D, 40.0D);
        int maxCars = (int) number(o, "maxCars", base.maxCars, 1.0D, RideController.MAX_CARS);
        int defaultCars = (int) number(o, "defaultCars", Math.min(base.defaultCars, maxCars), 1.0D, maxCars);
        JsonObject colours = o.has("colours") && o.get("colours").isJsonObject()
            ? o.getAsJsonObject("colours") : new JsonObject();
        return new TrainType(id, name, body, carLength, gap, seats, defaultCars, maxCars,
            colour(colours, "body", base.bodyColour), colour(colours, "trim", base.trimColour),
            colour(colours, "seats", base.seatColour), true);
    }

    private static String string(JsonObject o, String key, String fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
    }

    private static double number(JsonObject o, String key, double fallback, double min, double max) {
        if (!o.has(key)) {
            return fallback;
        }
        double v;
        try {
            v = o.get(key).getAsDouble();
        }
        catch (RuntimeException e) {
            throw new IllegalArgumentException("\"" + key + "\" must be a number");
        }
        if (v < min || v > max || Double.isNaN(v)) {
            throw new IllegalArgumentException("\"" + key + "\" must be between " + trim(min) + " and " + trim(max));
        }
        return v;
    }

    private static int colour(JsonObject colours, String key, int fallback) {
        if (!colours.has(key)) {
            return fallback;
        }
        String name = colours.get(key).getAsString();
        for (TrackPalette.Colour colour : TrackPalette.Colour.values()) {
            if (colour.name().equalsIgnoreCase(name)) {
                return colour.ordinal();
            }
        }
        StringBuilder known = new StringBuilder();
        for (TrackPalette.Colour colour : TrackPalette.Colour.values()) {
            known.append(known.length() == 0 ? "" : ", ").append(colour.name().toLowerCase(Locale.ROOT));
        }
        throw new IllegalArgumentException("colour \"" + name + "\" for " + key + " is not one of: " + known);
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** The built-in types as JSON, to copy from; and a README saying what the fields mean. */
    private static void writeExamples(File folder) {
        File examples = new File(folder, "examples");
        if (!examples.mkdirs() && !examples.isDirectory()) {
            return;
        }
        try {
            for (TrainType type : TrainTypes.all()) {
                JsonObject o = new JsonObject();
                o.addProperty("id", type.id);
                o.addProperty("name", type.name);
                o.addProperty("body", type.body.word);
                o.addProperty("carLength", type.carLength);
                o.addProperty("couplingGap", type.couplingGap);
                o.addProperty("seatsPerCar", type.seatsPerCar);
                o.addProperty("defaultCars", type.defaultCars);
                o.addProperty("maxCars", type.maxCars);
                JsonObject colours = new JsonObject();
                colours.addProperty("body", TrackPalette.Colour.values()[type.bodyColour].name().toLowerCase(Locale.ROOT));
                colours.addProperty("trim", TrackPalette.Colour.values()[type.trimColour].name().toLowerCase(Locale.ROOT));
                colours.addProperty("seats", TrackPalette.Colour.values()[type.seatColour].name().toLowerCase(Locale.ROOT));
                o.add("colours", colours);
                Files.write(new File(examples, type.id + ".json").toPath(),
                    GSON.toJson(o).getBytes(StandardCharsets.UTF_8));
            }
            Files.write(new File(folder, "README.txt").toPath(), README.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException ignored) {
            // Examples are a convenience; a server that cannot write them still loads its types.
        }
    }

    private static final String README = String.join("\n",
        "RCMC train types",
        "",
        "Each .json file in this folder is one train type. Copy one from examples/ to start;",
        "examples/ itself is not read. Load changes with /rcmc trains reload, or restart.",
        "",
        "  id           lower-case letters, digits and _. Use a built-in's id to replace it.",
        "  name         shown on the operator panel and in /rcmc trains.",
        "  body         sit_down, shoulder, wooden (coasters) or metro.",
        "  carLength    blocks between a car's bogie centres (1-40).",
        "  couplingGap  blocks between cars (0-20).",
        "  seatsPerCar  riders per car (1-40). Coaster cars seat two abreast.",
        "  defaultCars  cars in a new train.",
        "  maxCars      most cars a train of this type can have (1-12).",
        "  colours      body, trim and seats: steel, graphite, white, red, orange, yellow,",
        "               green, teal, blue, purple, pink or brown.",
        "",
        "Anything left out takes the built-in coaster's (or metro's) value.",
        "");
}
