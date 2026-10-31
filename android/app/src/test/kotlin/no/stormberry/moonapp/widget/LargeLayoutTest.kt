package no.stormberry.moonapp.widget

import no.stormberry.moonapp.widget.LargeLayout.Arrangement.COMPACT
import no.stormberry.moonapp.widget.LargeLayout.Arrangement.TALL
import no.stormberry.moonapp.widget.LargeLayout.Arrangement.WIDE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The large widget's arrangement (1.0.8): tall where it is tall enough, wide where it is low and
 * wide, compact where it is neither, on Android 7 to 11 (from the reported size options) and on
 * 12 and later (the launcher's choice among LargeLayout.SIZES).
 */
class LargeLayoutTest {

    /**
     * Typical options (minWidth, minHeight, maxWidth, maxHeight) in dp; a phone shows
     * minWidth x maxHeight in portrait.
     */
    @Test
    fun `Android 7 to 11 pick from the portrait size`() {
        // 4x2 on a 5-row grid: 320 x 220 portrait, 420 x 110 landscape.
        assertEquals(TALL, LargeLayout.pick(320, 110, 420, 220))
        // 3x2: 240 x 220 portrait.
        assertEquals(TALL, LargeLayout.pick(240, 110, 320, 220))
        // 4x3: tall in both.
        assertEquals(TALL, LargeLayout.pick(320, 180, 420, 330))
        // A low and wide widget, 4x2 on a tablet in landscape grid units: 400 x 120 at most.
        assertEquals(WIDE, LargeLayout.pick(380, 100, 560, 120))
        // A legacy 4x2 that reports only its minimum (250 x 110): too narrow for WIDE.
        assertEquals(COMPACT, LargeLayout.pick(250, 110, 250, 110))
        // A 4x2 on a small phone: 340 x 170 in portrait.
        assertEquals(COMPACT, LargeLayout.pick(340, 120, 440, 170))
        // No size yet: the portrait case.
        assertEquals(TALL, LargeLayout.pick(0, 0, 0, 0))
        // The thresholds themselves.
        assertEquals(TALL, LargeLayout.pick(250, 110, 300, LargeLayout.TALL_MIN_HEIGHT_DP))
        assertEquals(COMPACT, LargeLayout.pick(250, 110, 300, LargeLayout.TALL_MIN_HEIGHT_DP - 1))
        assertEquals(WIDE, LargeLayout.pick(LargeLayout.WIDE_MIN_WIDTH_DP, 110, 500, 170))
        assertEquals(COMPACT, LargeLayout.pick(LargeLayout.WIDE_MIN_WIDTH_DP - 1, 110, 500, 170))
        assertEquals(COMPACT, LargeLayout.pick(LargeLayout.TALL_MIN_WIDTH_DP - 1, 110, 300, 220))
        assertEquals(WIDE, LargeLayout.pick(412, 100, 500, LargeLayout.WIDE_MIN_HEIGHT_DP))
        assertEquals(COMPACT, LargeLayout.pick(412, 100, 500, LargeLayout.WIDE_MIN_HEIGHT_DP - 1))
        // The default 4x2 on a 1080 x 1920, 420 dpi phone: 292 x 184 in portrait.
        assertEquals(COMPACT, LargeLayout.pick(292, 110, 412, 184))
    }

    @Test
    fun `Android 12 and later give every size the arrangement that fits it`() {
        // Portrait 4x2 and 3x2 on common grids.
        for ((w, h) in listOf(
            380f to 220f, 350f to 200f, 400f to 240f, 290f to 220f, 240f to 200f, 292f to 200f,
            400f to 210f, 500f to 400f, 560f to 400f, 380f to 330f,
        )) {
            assertEquals("$w x $h", TALL, LargeLayout.chosenOnApi31(w, h))
        }
        // Landscape 4x2 and 3x2, and a stretched low widget.
        for ((w, h) in listOf(412f to 117f, 600f to 120f, 560f to 110f, 380f to 150f, 370f to 199f, 370f to 110f)) {
            assertEquals("$w x $h", WIDE, LargeLayout.chosenOnApi31(w, h))
        }
        // Too small for TALL and for WIDE: the default 4x2 on a 1080 x 1920 phone (292 x 184), the
        // review's 340 x 170, narrow tall widgets and widgets resized down.
        for ((w, h) in listOf(
            292f to 184f, 340f to 170f, 359f to 179f, 369f to 199f, 300f to 150f, 250f to 110f,
            180f to 110f, 180f to 100f, 203f to 220f, 180f to 180f, 239f to 400f, 400f to 109f,
        )) {
            assertEquals("$w x $h", COMPACT, LargeLayout.chosenOnApi31(w, h))
        }
        // Smaller than every entry: the smallest entry, which is COMPACT.
        assertEquals(COMPACT, LargeLayout.chosenOnApi31(170f, 90f))
    }

    /**
     * A larger font needs a larger TALL or WIDE widget; below that the widget is COMPACT, whose
     * rows shrink to fit. On the AVD at font scale 2.0 a 260 x 213 dp TALL widget lost its moon
     * and two of its times.
     */
    @Test
    fun `a large font moves the thresholds up`() {
        assertEquals(1f, LargeLayout.scaleFor(0.85f), 0f)
        assertEquals(1f, LargeLayout.scaleFor(1f), 0f)
        assertEquals(1.15f, LargeLayout.scaleFor(1.3f), 1e-6f)
        assertEquals(1.5f, LargeLayout.scaleFor(2f), 1e-6f)
        // The AVD's 4x3 on a 360 dp phone: TALL at the default font, COMPACT from 1.3.
        assertEquals(TALL, LargeLayout.chosenOnApi31(260f, 213f, 1f))
        assertEquals(COMPACT, LargeLayout.chosenOnApi31(260f, 213f, 1.3f))
        assertEquals(COMPACT, LargeLayout.chosenOnApi31(260f, 213f, 2f))
        // A common 4x2 (380 x 220): TALL at the default font, COMPACT from 1.3; a 4x3 is TALL
        // again at 2.0.
        assertEquals(TALL, LargeLayout.chosenOnApi31(380f, 220f, 1f))
        assertEquals(COMPACT, LargeLayout.chosenOnApi31(380f, 220f, 1.3f))
        assertEquals(COMPACT, LargeLayout.chosenOnApi31(380f, 220f, 2f))
        assertEquals(TALL, LargeLayout.chosenOnApi31(380f, 330f, 2f))
        // Landscape 4x2: WIDE at the default font, COMPACT when the font makes it too low.
        assertEquals(WIDE, LargeLayout.chosenOnApi31(600f, 120f, 1f))
        assertEquals(COMPACT, LargeLayout.chosenOnApi31(600f, 120f, 2f))
        // Android 7 to 11.
        assertEquals(TALL, LargeLayout.pick(320, 110, 420, 240, 1.3f))
        assertEquals(COMPACT, LargeLayout.pick(320, 110, 420, 220, 1.3f))
        assertEquals(COMPACT, LargeLayout.pick(320, 110, 420, 220, 2f))
        assertEquals(WIDE, LargeLayout.pick(560, 150, 600, 170, 2f))
        // At every font scale, whatever is chosen fits.
        for (f in listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)) {
            val k = LargeLayout.scaleFor(f)
            var w = 180f
            while (w <= 560f) {
                var h = 100f
                while (h <= 400f) {
                    // COMPACT only when no TALL or WIDE entry fits the widget.
                    val ok = when (LargeLayout.chosenOnApi31(w, h, f)) {
                        TALL -> h >= LargeLayout.TALL_MIN_HEIGHT_DP * k && w >= LargeLayout.TALL_MIN_WIDTH_DP * k
                        WIDE -> w >= LargeLayout.WIDE_MIN_WIDTH_DP * k && h >= LargeLayout.WIDE_MIN_HEIGHT_DP * k
                        COMPACT -> LargeLayout.sizes(f).none { (ew, eh, ea) -> ea != COMPACT && ew <= w && eh <= h }
                    }
                    assertTrue("$w x $h at $f", ok)
                    h += 5f
                }
                w += 5f
            }
        }
    }

    /** No size the widget can take falls through to an arrangement that does not fit it. */
    @Test
    fun `every size from the provider's minimum up gets an arrangement that fits`() {
        var w = 180f
        while (w <= 560f) {
            var h = 100f
            while (h <= 400f) {
                val a = LargeLayout.chosenOnApi31(w, h)
                val ok = when (a) {
                    TALL -> h >= LargeLayout.TALL_MIN_HEIGHT_DP && w >= LargeLayout.TALL_MIN_WIDTH_DP
                    WIDE -> w >= LargeLayout.WIDE_MIN_WIDTH_DP && h >= LargeLayout.WIDE_MIN_HEIGHT_DP
                    COMPACT ->
                        !(h >= LargeLayout.TALL_MIN_HEIGHT_DP && w >= LargeLayout.TALL_MIN_WIDTH_DP) &&
                            !(w >= LargeLayout.WIDE_MIN_WIDTH_DP && h >= LargeLayout.WIDE_MIN_HEIGHT_DP)
                }
                assertTrue("$w x $h got $a", ok)
                h += 5f
            }
            w += 5f
        }
    }
}
