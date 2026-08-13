package com.micatechnologies.minecraft.rcmc.client.render.sign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Fits a block of sign text to the panel it has to live on: sizes it to the rows it must show, and
 * <b>pages</b> anything still too wide rather than letting it run off the edge.
 *
 * <h2>Why paging rather than wrapping</h2>
 *
 * <p>A board's content is not known when its size is chosen. One station's row reads
 * {@code "INBOUND  --"}; an interchange's reads {@code "NORTHBOUND/Airfield  2 stops"}, and the same
 * panel has to hold both. Wrapping the long one onto a second line works, but it costs height that
 * a busy board does not have, and it reads as a ragged paragraph rather than as a departure
 * board.</p>
 *
 * <p>So a row too wide to fit is split into <em>pages</em> that take turns in the same place — which
 * is what a real dot-matrix board does, alternating between a destination and its calling time. The
 * row keeps its line and its full text size; only the moment at which you read each part changes.
 * A board with nothing to page is completely still.</p>
 *
 * <h2>What decides the scale</h2>
 *
 * <p>Height alone. The number of rows is fixed by how many services a station has, and none of them
 * can be paged away, so the text is as large as the row count allows. Width no longer feeds back
 * into it — that was true while wide rows became extra lines, and paging is what breaks the
 * coupling. Below a size worth reading, rows are dropped from the bottom instead: fitting more onto
 * a sign nobody can read is not fitting it on.</p>
 *
 * <p>No Minecraft types — width arrives as a {@link ToIntFunction} over strings, which is a font
 * renderer at runtime and a fake in the tests.</p>
 */
public final class SignTextFit {

    /** Vanilla's font line height, in pixels — the step {@code SignPanels.drawLines} advances by. */
    public static final int LINE_HEIGHT = 10;

    /** How many scales to try between the largest and the smallest, inclusive of both ends. */
    private static final int STEPS = 24;

    /** The gap a row's fields are joined by, and the seam pages are broken at first. */
    private static final String FIELD_GAP = "  ";

    /** A fitted board: the size to draw at, and each row's pages. */
    public static final class Layout {

        public final float scale;
        private final List<List<String>> rows;

        Layout(float scale, List<List<String>> rows) {
            this.scale = scale;
            this.rows = rows;
        }

        /** How many pages the board cycles through; 1 when nothing needs paging. */
        public int pageCount() {
            int pages = 1;
            for (List<String> row : rows) {
                pages = Math.max(pages, row.size());
            }
            return pages;
        }

        /**
         * The lines to draw for one page.
         *
         * <p>Every row advances on the same beat, so the board changes as a unit rather than each
         * row flickering to its own clock. A row with fewer pages than the board simply repeats —
         * a short row holds still while a long one alternates beside it, which is what makes the
         * movement read as deliberate.</p>
         */
        public List<String> frame(int page) {
            List<String> out = new ArrayList<>(rows.size());
            for (List<String> row : rows) {
                out.add(row.get(Math.floorMod(page, row.size())));
            }
            return out;
        }

        /** Every page of every row, for tests and for reasoning about what a board can show. */
        public List<List<String>> rows() {
            return Collections.unmodifiableList(rows);
        }
    }

    private SignTextFit() {
        throw new AssertionError("No instances.");
    }

    /**
     * The largest scale at which {@code rows} fit the panel's height, with each row split into as
     * many pages as its width needs.
     *
     * @param widthOf   measures a string in font pixels
     * @param maxWidth  usable panel width, in world units
     * @param maxHeight usable panel height, in world units
     * @param maxScale  world units per font pixel to prefer
     * @param minScale  the smallest still worth reading; past it, rows are dropped instead
     */
    public static Layout fit(List<String> rows, ToIntFunction<String> widthOf,
                             double maxWidth, double maxHeight,
                             float maxScale, float minScale) {
        for (int step = 0; step <= STEPS; step++) {
            float scale = maxScale + (minScale - maxScale) * step / STEPS;
            int lineBudget = (int) Math.floor(maxHeight / (LINE_HEIGHT * scale));
            boolean last = step == STEPS;
            if (rows.size() > lineBudget && !last) {
                continue;
            }
            // Rows come out in the order a rider needs them — the station, then each direction's
            // soonest train — so anything that will not fit is lost from the bottom.
            List<String> visible = rows.size() <= lineBudget
                ? rows : rows.subList(0, Math.max(0, lineBudget));
            int widthBudget = (int) Math.floor(maxWidth / scale);
            List<List<String>> paged = new ArrayList<>(visible.size());
            for (String row : visible) {
                paged.add(pages(row, widthOf, widthBudget));
            }
            return new Layout(scale, paged);
        }
        throw new AssertionError("the final step always returns");
    }

    /**
     * One row split into pages, none of which exceeds {@code budgetPx}.
     *
     * <p><b>Broken at the seam it was built with, not at just any space.</b> A board row is a
     * destination and an arrival phrase joined by a wide gap — {@code "NORTHBOUND/Airfield  3
     * stops"} — and breaking it anywhere else produces pages like {@code "NORTHBOUND/Airfield 3"}
     * followed by {@code "stops"}, which is worse than the overflow it replaced: it reads as a
     * different, wrong sentence. Splitting on the double space first keeps each page a whole
     * thought, which is exactly what makes the alternation legible.</p>
     *
     * <p>Ordinary spaces are the fallback, for a single field still too wide to stand alone.</p>
     */
    private static List<String> pages(String row, ToIntFunction<String> widthOf, int budgetPx) {
        List<String> out = new ArrayList<>(1);
        if (row == null || row.isEmpty() || widthOf.applyAsInt(row) <= budgetPx) {
            out.add(row == null ? "" : row);
            return out;
        }
        // A continuation row's leading spaces are what tie it to the group above, so every page it
        // turns into keeps them.
        String indent = leadingSpaces(row);
        String[] fields = row.substring(indent.length()).split("\\s{2,}");

        List<String> grouped = new ArrayList<>();
        String current = null;
        for (String field : fields) {
            if (field.isEmpty()) {
                continue;
            }
            String candidate = current == null ? indent + field : current + FIELD_GAP + field;
            if (current != null && widthOf.applyAsInt(candidate) > budgetPx) {
                grouped.add(current);
                current = indent + field;
            }
            else {
                current = candidate;
            }
        }
        if (current != null) {
            grouped.add(current);
        }

        for (String page : grouped) {
            if (widthOf.applyAsInt(page) <= budgetPx) {
                out.add(page);
            }
            else {
                splitOnSpaces(page, indent, widthOf, budgetPx, out);
            }
        }
        if (out.isEmpty()) {
            out.add("");
        }
        return out;
    }

    /** Last resort: break one over-wide field at ordinary spaces, cutting a word that will not fit. */
    private static void splitOnSpaces(String page, String indent, ToIntFunction<String> widthOf,
                                      int budgetPx, List<String> out) {
        String current = null;
        for (String word : page.trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = current == null
                ? indent + clamp(word, widthOf, budgetPx) : current + " " + word;
            if (current != null && widthOf.applyAsInt(candidate) > budgetPx) {
                out.add(current);
                // A word wider than the whole panel has nowhere to break, so it is cut. Hanging it
                // off the side is the one outcome that reads as a fault rather than as a board.
                current = indent + clamp(word, widthOf, budgetPx);
            }
            else {
                current = candidate;
            }
        }
        if (current != null) {
            out.add(current);
        }
    }

    private static String leadingSpaces(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return line.substring(0, i);
    }

    private static String clamp(String word, ToIntFunction<String> widthOf, int budgetPx) {
        String cut = word;
        while (!cut.isEmpty() && widthOf.applyAsInt(cut) > budgetPx) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut;
    }
}
