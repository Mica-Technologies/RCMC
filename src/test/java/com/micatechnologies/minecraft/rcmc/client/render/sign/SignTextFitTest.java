package com.micatechnologies.minecraft.rcmc.client.render.sign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** The interchange board that exposed the bug: two lines, four directions, long destinations. */
    private static List<String> interchangeRows() {
        return Arrays.asList(
            "Exchange",
            "OUTBOUND/Lakeshore  2 stops away",
            "      3 stops away",
            "INBOUND/Kingsway  --",
            "SOUTHBOUND/Parkway  --",
            "NORTHBOUND/Airfield  3 stops away");
    }

    private static void assertWithin(SignTextFit.Layout layout, double maxWidth, double maxHeight) {
        for (String line : layout.lines) {
            double drawn = SIX_PX.applyAsInt(line) * layout.scale;
            assertTrue(drawn <= maxWidth + 1e-6D,
                "line ran off the panel: \"" + line + "\" is " + drawn + " wide at scale "
                    + layout.scale + ", panel is " + maxWidth);
        }
        double stack = layout.lines.size() * SignTextFit.LINE_HEIGHT * layout.scale;
        assertTrue(stack <= maxHeight + 1e-6D,
            "text stack " + stack + " overran the panel height " + maxHeight);
    }

    @Test
    @DisplayName("a busy interchange board fits inside its panel, both ways")
    void interchangeFits() {
        double width = 3.76D;   // a four-block panel less its margins
        double height = 1.89D;
        SignTextFit.Layout layout = SignTextFit.fit(interchangeRows(), SIX_PX,
            width, height, 0.026F, 0.013F);
        assertWithin(layout, width, height);
        assertTrue(layout.lines.size() >= interchangeRows().size(),
            "wrapping should keep every row, splitting the long ones rather than dropping them");
    }

    @Test
    @DisplayName("a quiet board keeps the full text size")
    void shortRowsAreNotShrunk() {
        List<String> rows = Arrays.asList("Foundry", "INBOUND  --", "OUTBOUND  --");
        SignTextFit.Layout layout = SignTextFit.fit(rows, SIX_PX, 3.76D, 1.89D, 0.026F, 0.013F);
        assertEquals(0.026F, layout.scale, 1e-6F,
            "nothing here needs shrinking, so the board must not shrink — the largest scale that "
                + "works is the answer, not merely one that works");
        assertEquals(rows, layout.lines, "and nothing needs wrapping either");
    }

    /**
     * The ordering that makes the whole thing worth doing. Shrinking to fit the widest row punishes
     * every other row for it; wrapping spends a line and keeps the text readable.
     */
    @Test
    @DisplayName("one long row wraps rather than shrinking the whole board")
    void wrapsBeforeItShrinks() {
        List<String> rows = Arrays.asList(
            "Exchange", "OUTBOUND/Lakeshore  2 stops away", "INBOUND  --");
        SignTextFit.Layout layout = SignTextFit.fit(rows, SIX_PX, 3.76D, 1.89D, 0.026F, 0.013F);
        assertEquals(0.026F, layout.scale, 1e-6F,
            "there is height to spare, so the long row should cost a line and not the text size");
        assertTrue(layout.lines.size() > rows.size(), "the long row must have been split");
        assertTrue(layout.lines.get(2).startsWith(" "),
            "a continuation stays indented, so a wrapped row still reads as one row: "
                + layout.lines);
    }

    @Test
    @DisplayName("text too big for any scale is cut, never drawn off the edge")
    void unbreakableTextIsCut() {
        // One word, no spaces to break at, far wider than the panel at any scale it may use.
        List<String> rows = Collections.singletonList(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        double width = 1.0D;
        SignTextFit.Layout layout = SignTextFit.fit(rows, SIX_PX, width, 1.89D, 0.026F, 0.013F);
        assertWithin(layout, width, 1.89D);
        assertTrue(layout.lines.get(0).length() < rows.get(0).length(),
            "it had to lose characters — there is no honest layout for a word wider than the sign");
    }

    @Test
    @DisplayName("more rows than the panel can hold lose the last ones, not the panel's edge")
    void overflowingRowsAreDropped() {
        List<String> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            rows.add("Row " + i);
        }
        double height = 1.89D;
        SignTextFit.Layout layout = SignTextFit.fit(rows, SIX_PX, 3.76D, height, 0.026F, 0.013F);
        assertWithin(layout, 3.76D, height);
        assertTrue(layout.lines.size() < rows.size(), "the surplus rows must be dropped");
        assertEquals("Row 0", layout.lines.get(0),
            "and dropped from the bottom — the first rows are the ones a rider needs");
    }
}
