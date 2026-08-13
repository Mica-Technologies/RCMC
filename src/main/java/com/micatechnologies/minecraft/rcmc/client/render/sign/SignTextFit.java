package com.micatechnologies.minecraft.rcmc.client.render.sign;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Fits a block of sign text to the panel it has to live on: wraps what is too wide, shrinks what is
 * then too tall, and never lets either run off the edge.
 *
 * <h2>Why a fit pass rather than a fixed scale</h2>
 *
 * <p>A board's content is not known when its size is chosen. One station's row reads
 * {@code "INBOUND  --"}; an interchange's reads {@code "NORTHBOUND/Airfield  3 stops away"}, which is
 * nearly three times as wide, and the same panel has to hold both. A fixed scale can serve one or
 * the other, and picking the larger meant a busy board painted its rows straight off the sides —
 * text hanging in the tunnel air either side of the screen.</p>
 *
 * <h2>Wrap first, shrink second</h2>
 *
 * <p>Deliberately in that order. Shrinking to fit the widest row punishes every other row for it,
 * and a board that goes tiny because one destination has a long name is worse than one that spends
 * a second line on it. So each candidate scale wraps the rows to the width it affords, and the
 * first scale whose wrapped result also fits the height wins. Bigger text is tried first, so the
 * answer is always the largest that works rather than merely one that does.</p>
 *
 * <p>The two budgets interact, which is why this iterates instead of solving directly: shrinking
 * buys width, which removes wraps, which buys back height. A closed form would have to model that
 * feedback; stepping through a couple of dozen candidates measures it.</p>
 *
 * <p>No Minecraft types — width arrives as a {@link ToIntFunction} over strings, which is a font
 * renderer at runtime and a fake in the tests.</p>
 */
public final class SignTextFit {

    /** Vanilla's font line height, in pixels — the step {@code SignPanels.drawLines} advances by. */
    public static final int LINE_HEIGHT = 10;

    /** How many scales to try between the largest and the smallest, inclusive of both ends. */
    private static final int STEPS = 24;

    /** Continuation lines are indented, so a wrapped row reads as one row rather than two. */
    private static final String CONTINUATION_INDENT = "  ";

    /** The chosen scale and the lines to draw at it. */
    public static final class Layout {

        public final float scale;
        public final List<String> lines;

        Layout(float scale, List<String> lines) {
            this.scale = scale;
            this.lines = lines;
        }
    }

    private SignTextFit() {
        throw new AssertionError("No instances.");
    }

    /**
     * The largest scale at which {@code lines}, wrapped to fit, also fits the panel.
     *
     * @param widthOf  measures a string in font pixels
     * @param maxWidth usable panel width, in world units
     * @param maxHeight usable panel height, in world units
     * @param maxScale world units per font pixel to prefer
     * @param minScale the smallest that is still worth reading; at this point anything that still
     *                 does not fit is cut rather than allowed off the edge
     */
    public static Layout fit(List<String> lines, ToIntFunction<String> widthOf,
                             double maxWidth, double maxHeight,
                             float maxScale, float minScale) {
        for (int step = 0; step <= STEPS; step++) {
            float scale = maxScale + (minScale - maxScale) * step / STEPS;
            int widthBudget = (int) Math.floor(maxWidth / scale);
            int lineBudget = (int) Math.floor(maxHeight / (LINE_HEIGHT * scale));
            List<String> wrapped = wrap(lines, widthOf, widthBudget);
            boolean last = step == STEPS;
            // Both budgets, not just the height. Wrapping breaks at spaces, so it cannot always
            // reach the width budget — one long word has nowhere to break — and checking only the
            // line count accepted exactly that case at full size, which is the overflow this class
            // exists to stop.
            if (!last && wrapped.size() <= lineBudget && widest(wrapped, widthOf) <= widthBudget) {
                return new Layout(scale, wrapped);
            }
            if (last) {
                // Out of room to shrink. Drop the rows that will not fit rather than paint them
                // past the bottom edge, and cut anything still too wide — a single unbreakable word
                // longer than the whole panel has no honest layout, and hanging it off the side is
                // the one outcome that reads as a fault rather than as a full board.
                if (wrapped.size() > lineBudget) {
                    wrapped = new ArrayList<>(wrapped.subList(0, Math.max(0, lineBudget)));
                }
                for (int i = 0; i < wrapped.size(); i++) {
                    wrapped.set(i, clamp(wrapped.get(i), widthOf, widthBudget));
                }
                return new Layout(scale, wrapped);
            }
        }
        throw new AssertionError("the final step always returns");
    }

    private static int widest(List<String> lines, ToIntFunction<String> widthOf) {
        int widest = 0;
        for (String line : lines) {
            if (line != null) {
                widest = Math.max(widest, widthOf.applyAsInt(line));
            }
        }
        return widest;
    }

    /** Every line, broken at spaces so that none exceeds {@code budgetPx}. */
    private static List<String> wrap(List<String> lines, ToIntFunction<String> widthOf,
                                     int budgetPx) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            wrapInto(line, widthOf, budgetPx, out);
        }
        return out;
    }

    private static void wrapInto(String line, ToIntFunction<String> widthOf, int budgetPx,
                                 List<String> out) {
        if (line == null || line.isEmpty() || widthOf.applyAsInt(line) <= budgetPx) {
            out.add(line);
            return;
        }
        // The row's own leading spaces are its indent already — a continuation of an indented row
        // stays indented, so the board's two-level structure survives wrapping.
        String lead = leadingSpaces(line);
        String indent = lead + CONTINUATION_INDENT;
        String[] words = line.trim().split("\\s+");
        if (words.length == 0) {
            out.add(line);
            return;
        }
        StringBuilder current = new StringBuilder(lead);
        boolean started = false;
        for (String word : words) {
            String candidate = started ? current + " " + word : current + word;
            if (started && widthOf.applyAsInt(candidate) > budgetPx) {
                out.add(current.toString());
                current = new StringBuilder(indent).append(word);
            }
            else {
                current = new StringBuilder(candidate);
            }
            started = true;
        }
        out.add(current.toString());
    }

    /** Cuts a line down to what fits. Only ever reached at the smallest scale. */
    private static String clamp(String line, ToIntFunction<String> widthOf, int budgetPx) {
        if (line == null || widthOf.applyAsInt(line) <= budgetPx) {
            return line;
        }
        String cut = line;
        while (!cut.isEmpty() && widthOf.applyAsInt(cut) > budgetPx) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut;
    }

    private static String leadingSpaces(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return line.substring(0, i);
    }
}
