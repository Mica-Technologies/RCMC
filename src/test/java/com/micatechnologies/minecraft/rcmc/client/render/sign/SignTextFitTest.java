package com.micatechnologies.minecraft.rcmc.client.render.sign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rule a sign has to keep: nothing is ever drawn off the panel.
 *
 * <p>Reported from a live board at the demo's interchange — rows reading
 * {@code "NORTHBOUND/Airfield  3 stops away"} painted straight off both sides of the screen and hung
 * in the tunnel air. The scale was fixed and only the row <em>count</em> was ever bounded, so width
 * was unchecked.</p>
 *
 * <p>A fixed six pixels per character stands in for the font. Real glyphs vary, but every property
 * asserted here is about the fitting, not about the metrics.</p>
 */
class SignTextFitTest {

    private static final ToIntFunction<String> SIX_PX = s -> s.length() * 6;

    /** A four-block panel less its margins, and the height below its top text line. */
    private static final double WIDTH = 3.76D;
    private static final double HEIGHT = 1.89D;

    private static final float MAX_SCALE = 0.026F;
    private static final float MIN_SCALE = 0.013F;

    private static SignTextFit.Layout fit(List<String> rows) {
        return SignTextFit.fit(rows, SIX_PX, WIDTH, HEIGHT, MAX_SCALE, MIN_SCALE);
    }

    /** The interchange board that exposed the bug: two lines, four directions, long destinations. */
    private static List<String> interchangeRows() {
        return Arrays.asList(
            "Exchange",
            "OUTBOUND/Lakeshore  2 stops",
            "      3 stops",
            "INBOUND/Kingsway  --",
            "SOUTHBOUND/Parkway  --",
            "NORTHBOUND/Airfield  3 stops");
    }

    /** Asserts the invariant, on every page — not merely on the one that happens to be showing. */
    private static void assertNeverOverflows(SignTextFit.Layout layout) {
        for (int page = 0; page < layout.pageCount(); page++) {
            List<String> frame = layout.frame(page);
            for (String line : frame) {
                double drawn = SIX_PX.applyAsInt(line) * layout.scale;
                assertTrue(drawn <= WIDTH + 1e-6D,
                    "page " + page + " ran off the panel: \"" + line + "\" is " + drawn
                        + " wide at scale " + layout.scale);
            }
            double stack = frame.size() * SignTextFit.LINE_HEIGHT * layout.scale;
            assertTrue(stack <= HEIGHT + 1e-6D,
                "page " + page + " overran the panel height: " + stack + " > " + HEIGHT);
        }
    }

    @Test
    @DisplayName("a busy interchange board fits inside its panel on every page")
    void interchangeFits() {
        SignTextFit.Layout layout = fit(interchangeRows());
        assertNeverOverflows(layout);
        assertEquals(interchangeRows().size(), layout.frame(0).size(),
            "paging must not cost a row — every service still has its own line");
    }

    @Test
    @DisplayName("a quiet board keeps the full text size and never moves")
    void shortRowsAreNotShrunkOrPaged() {
        List<String> rows = Arrays.asList("Foundry", "INBOUND  --", "OUTBOUND  --");
        SignTextFit.Layout layout = fit(rows);
        assertEquals(MAX_SCALE, layout.scale, 1e-6F,
            "nothing here needs shrinking, so the board must not shrink — the largest scale that "
                + "works is the answer, not merely one that works");
        assertEquals(1, layout.pageCount(),
            "and with nothing too wide it must hold still; a board that animates for no reason "
                + "is worse than one that cannot");
        assertEquals(rows, layout.frame(0));
    }

    /**
     * The behaviour the whole class exists for: a row too wide takes turns in place rather than
     * costing a line or shrinking every other row to fit it.
     */
    @Test
    @DisplayName("a row too wide alternates in place at full size")
    void longRowPagesRatherThanShrinking() {
        List<String> rows = Arrays.asList(
            "Exchange", "NORTHBOUND/Airfield  3 stops", "INBOUND  --");
        SignTextFit.Layout layout = fit(rows);

        assertEquals(MAX_SCALE, layout.scale, 1e-6F,
            "the long row must not drag the text size down with it");
        assertEquals(rows.size(), layout.frame(0).size(), "and must not cost a line either");
        assertTrue(layout.pageCount() > 1, "it has to page");

        List<String> parts = layout.rows().get(1);
        assertEquals("NORTHBOUND/Airfield", parts.get(0), "destination first");
        assertEquals("3 stops", parts.get(1), "then when it gets here");
        assertNeverOverflows(layout);
    }

    @Test
    @DisplayName("rows with fewer pages hold still while a longer one alternates beside them")
    void shortRowsRepeatAcrossPages() {
        List<String> rows = Arrays.asList("Exchange", "NORTHBOUND/Airfield  3 stops");
        SignTextFit.Layout layout = fit(rows);
        assertTrue(layout.pageCount() > 1);
        for (int page = 0; page < layout.pageCount(); page++) {
            assertEquals("Exchange", layout.frame(page).get(0),
                "the station name has one page, so it must show on all of them");
        }
    }

    @Test
    @DisplayName("paging wraps round, so the board cycles rather than running out")
    void pagingIsCyclic() {
        SignTextFit.Layout layout = fit(interchangeRows());
        assertEquals(layout.frame(0), layout.frame(layout.pageCount()),
            "one full turn must return to the first page");
        assertEquals(layout.frame(1), layout.frame(layout.pageCount() + 1));
    }

    @Test
    @DisplayName("text too big for any scale is cut, never drawn off the edge")
    void unbreakableTextIsCut() {
        // One word, no spaces to break at, far wider than the panel at any scale it may use.
        String monster = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        SignTextFit.Layout layout = SignTextFit.fit(Collections.singletonList(monster), SIX_PX,
            1.0D, HEIGHT, MAX_SCALE, MIN_SCALE);
        for (String line : layout.frame(0)) {
            assertTrue(SIX_PX.applyAsInt(line) * layout.scale <= 1.0D + 1e-6D,
                "an unbreakable word must be cut to the panel, not hung off it");
        }
        assertTrue(layout.frame(0).get(0).length() < monster.length(), "it had to lose characters");
    }

    @Test
    @DisplayName("more rows than the panel can hold lose the last ones, not the panel's edge")
    void overflowingRowsAreDropped() {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            rows.add("Row " + i);
        }
        SignTextFit.Layout layout = fit(rows);
        assertNeverOverflows(layout);
        assertTrue(layout.frame(0).size() < rows.size(), "the surplus rows must be dropped");
        assertEquals("Row 0", layout.frame(0).get(0),
            "and dropped from the bottom — the first rows are the ones a rider needs");
    }
}
