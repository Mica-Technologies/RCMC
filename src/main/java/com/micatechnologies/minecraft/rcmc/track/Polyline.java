package com.micatechnologies.minecraft.rcmc.track;

import com.micatechnologies.minecraft.rcmc.track.math.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Track nodes as text: the format {@code /rcmc track lay} reads and {@code /rcmc track nodes}
 * writes, so a generator can produce an alignment from its own geometry and diff what was built
 * against what it asked for.
 *
 * <p>One node is {@code x,y,z}, optionally {@code x,y,z,bank} with the bank in degrees, optionally
 * followed by {@code @type} ({@code @station}, {@code @lift}, {@code @brake}…). A type names the
 * track arriving at that node, and carries on to the nodes after it until the next {@code @type}
 * — exactly as the track tool's selected type does — so {@code @plain} ends a run. Nodes are
 * separated by {@code ;} or by line breaks, so a whole
 * alignment fits one command argument or one file with a node per line. Whitespace around
 * separators is ignored, and in a file a {@code #} starts a comment.</p>
 *
 * <p>Coordinates are taken exactly. The track tool snaps a click to the block centre and the block
 * top; a polyline does not, because the curves it exists for — loops, reverse curves, turnouts —
 * need nodes off the grid, and rounding them puts a zig-zag into the spline you feel at speed.</p>
 *
 * <p>Pure: no Minecraft types, so the grammar is unit-tested.</p>
 */
public final class Polyline {

    /** One parsed node. {@code type} is lower-case, or {@code null} when none was given. */
    public static final class Point {
        public final Vec3 position;
        public final double bankDegrees;
        public final String type;

        public Point(Vec3 position, double bankDegrees, String type) {
            this.position = position;
            this.bankDegrees = bankDegrees;
            this.type = type;
        }
    }

    private Polyline() {
        throw new AssertionError("No instances.");
    }

    /**
     * Parses {@code text}.
     *
     * @throws IllegalArgumentException naming the offending node, for anything malformed —
     *     including a non-finite number, which would otherwise reach the spline as NaN
     */
    public static List<Point> parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("No nodes given");
        }
        List<Point> points = new ArrayList<>();
        int number = 0;
        for (String line : text.split("\\r?\\n")) {
            int comment = line.indexOf('#');
            String content = comment >= 0 ? line.substring(0, comment) : line;
            for (String token : content.split(";")) {
                String trimmed = token.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                number++;
                points.add(parsePoint(trimmed, number));
            }
        }
        if (points.isEmpty()) {
            throw new IllegalArgumentException("No nodes given");
        }
        return Collections.unmodifiableList(points);
    }

    private static Point parsePoint(String token, int number) {
        String type = null;
        String coordinates = token;
        int at = token.indexOf('@');
        if (at >= 0) {
            type = token.substring(at + 1).trim().toLowerCase(Locale.ROOT);
            coordinates = token.substring(0, at).trim();
            if (type.isEmpty()) {
                throw new IllegalArgumentException("Node " + number + " (" + token + "): empty type after '@'");
            }
        }
        String[] parts = coordinates.split("\\s*,\\s*");
        if (parts.length != 3 && parts.length != 4) {
            throw new IllegalArgumentException("Node " + number + " (" + token
                + "): expected x,y,z or x,y,z,bank");
        }
        double[] values = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                values[i] = Double.parseDouble(parts[i].trim());
            }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("Node " + number + " (" + token + "): '" + parts[i]
                    + "' is not a number");
            }
            if (Double.isNaN(values[i]) || Double.isInfinite(values[i])) {
                throw new IllegalArgumentException("Node " + number + " (" + token + "): '" + parts[i]
                    + "' is not a finite number");
            }
        }
        double bank = parts.length == 4 ? values[3] : 0.0D;
        if (bank < -180.0D || bank > 180.0D) {
            throw new IllegalArgumentException("Node " + number + " (" + token
                + "): bank must be between -180 and 180 degrees");
        }
        return new Point(new Vec3(values[0], values[1], values[2]), bank, type);
    }

    /**
     * {@code section}'s nodes in the format {@link #parse} reads, one per line, preceded by a
     * comment naming the section. Bank is written only where it is non-zero, and coordinates in
     * full precision, so laying the output reproduces the section exactly.
     */
    public static String write(TrackSection section) {
        StringBuilder out = new StringBuilder();
        out.append("# section ").append(section.id()).append(section.isClosed() ? " circuit" : " open")
            .append(", style ").append(TrackStyleIds.label(section.styleId()))
            .append(", ").append(section.nodes().size()).append(" nodes, ")
            .append(String.format(Locale.ROOT, "%.1f", section.totalLength())).append(" blocks\n");
        for (TrackNode node : section.nodes()) {
            out.append(format(node)).append('\n');
        }
        return out.toString();
    }

    /** One node as {@code x,y,z} or {@code x,y,z,bank}. */
    public static String format(TrackNode node) {
        Vec3 p = node.position();
        String text = number(p.x) + "," + number(p.y) + "," + number(p.z);
        double bank = node.bankDegrees();
        return Math.abs(bank) < 1.0e-9D ? text : text + "," + number(bank);
    }

    /** Shortest exact text for {@code value}: {@code 55.5}, not {@code 55.500000}; {@code 47}, not {@code 47.0}. */
    private static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0e15D) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
