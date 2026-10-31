package no.stormberry.moonapp.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.test.AndroidTestCase
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import no.stormberry.moonapp.R
import no.stormberry.moonapp.lunar.LunarDayKind
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The home screen widgets drawn on a device (1.0.8, design 9.2; the androidTest source set Marcos
 * accepted on 2026-10-30). Each layout is built by the app's own render functions, applied as the
 * launcher applies it (RemoteViews.apply), laid out at a widget size in dp and drawn to a bitmap.
 *
 * It checks what a unit test cannot: every visible text fits (inside the widget, not cut short
 * with an ellipsis, all its lines inside its view), the 2x1's rows are one view with one line per
 * row (so one text size), the hint of a widget not yet drawn fits even a small 1x1, the hint is
 * gone once a widget is drawn, and the moon bitmap lights the printed share on the right side.
 *
 * The platform's own instrumentation runner and JUnit 3 (`android.test`), so it adds no library.
 * Run: `:app:assembleDebugAndroidTest`, install both APKs, then `adb -s <serial> shell am instrument -w -e class
 * no.stormberry.moonapp.widget.WidgetRenderTest no.stormberry.moonapp.debug.test/android.test.InstrumentationTestRunner` (name the class: unnamed, the old runner scans the whole debug APK at start and can time out).
 * Each bitmap is saved for review under the app's external files, `widget-render/`.
 */
class WidgetRenderTest : AndroidTestCase() {

    private val phone = ZoneId.of("Europe/Oslo")

    private fun oslo() = WidgetModel.build(Instant.parse("2026-11-20T12:00:00Z"), "Oslo", 59.9139, 10.7522, "Europe/Oslo", phone)
    private fun sydney() = WidgetModel.build(Instant.parse("2026-11-20T01:00:00Z"), "Sydney", -33.8688, 151.2093, "Australia/Sydney", phone)
    private fun longyearbyenUp() = WidgetModel.build(Instant.parse("2026-10-30T12:00:00Z"), "Longyearbyen", 78.2232, 15.6267, "Arctic/Longyearbyen", phone)
    private fun longyearbyenDown() = WidgetModel.build(Instant.parse("2026-11-12T12:00:00Z"), "Longyearbyen", 78.2232, 15.6267, "Arctic/Longyearbyen", phone)

    private fun states() = listOf("oslo" to oslo(), "sydney" to sydney(), "lyr-up" to longyearbyenUp(), "lyr-down" to longyearbyenDown())

    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: Result<T>? = null
        val done = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            result = runCatching(block)
            done.countDown()
        }
        assertTrue("main thread timed out", done.await(30, TimeUnit.SECONDS))
        return result!!.getOrThrow()
    }

    private fun px(dp: Float): Int = (dp * context.resources.displayMetrics.density).roundToInt()

    /** Applies [views] as a launcher would, at [w] x [h] dp, and draws it; returns the root and the bitmap. */
    private fun render(views: RemoteViews, w: Float, h: Float, name: String): Pair<View, Bitmap> = onMain {
        val host = FrameLayout(context)
        val root = views.apply(context, host)
        val wp = px(w)
        val hp = px(h)
        root.measure(View.MeasureSpec.makeMeasureSpec(wp, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hp, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, wp, hp)
        val bmp = Bitmap.createBitmap(wp, hp, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(0x60, 0x70, 0x80))
        root.draw(Canvas(bmp))
        save(bmp, name)
        root to bmp
    }

    private fun save(bmp: Bitmap, name: String) {
        val dir = File(context.getExternalFilesDir(null), "widget-render").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shown(v: View, root: View): Boolean {
        var p: View? = v
        while (p != null) {
            if (p.visibility != View.VISIBLE) return false
            if (p === root) return true
            p = p.parent as? View
        }
        return true
    }

    private fun texts(v: View): List<TextView> = when (v) {
        is TextView -> listOf(v)
        is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
        else -> emptyList()
    }

    private fun find(root: View, id: Int): View = root.findViewById(id) ?: throw AssertionError("no view ${context.resources.getResourceEntryName(id)}")

    /** Every visible, non-empty text is inside the widget, not ellipsized and not cut off. */
    private fun assertTextsFit(root: View, name: String, ellipsisAllowed: Set<Int> = emptySet()) {
        val problems = textProblems(root, name, ellipsisAllowed)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** What [assertTextsFit] would complain about, every problem at once. */
    private fun textProblems(root: View, name: String, ellipsisAllowed: Set<Int> = emptySet()): List<String> {
        val problems = mutableListOf<String>()
        fun check(ok: Boolean, msg: () -> String) { if (!ok) problems += msg() }
        for (tv in texts(root).filter { shown(it, root) && it.text.isNotEmpty() }) {
            val label = "$name/${runCatching { context.resources.getResourceEntryName(tv.id) }.getOrDefault("?")} \"${tv.text}\""
            var x = 0
            var y = 0
            var p: View? = tv
            while (p != null && p !== root) {
                x += p.left
                y += p.top
                p = p.parent as? View
            }
            check(x >= 0 && y >= 0 && x + tv.width <= root.width + 1 && y + tv.height <= root.height + 1) {
                "$label is outside the widget: x=$x y=$y ${tv.width}x${tv.height} in ${root.width}x${root.height}"
            }
            val layout = tv.layout
            if (layout == null) {
                problems += "$label has no layout"
                continue
            }
            if (tv.id !in ellipsisAllowed) {
                val cut = (0 until layout.lineCount).sumOf { layout.getEllipsisCount(it) }
                check(cut == 0) { "$label is cut short by $cut characters" }
            }
            val room = tv.height - tv.compoundPaddingTop - tv.compoundPaddingBottom
            check(layout.height <= room + 1) { "$label: ${layout.height} px of text in $room px" }
            check(tv.height > 0) { "$label has no height" }
        }
        return problems
    }

    // ------------------------------------------------------------------ the moon

    private fun moon(s: WidgetState, dp: Int) = MoonDiscBitmap.draw(dp, context.resources.displayMetrics.density, s.fraction, s.litOnRight)

    /**
     * Inside the disc, rim excluded: the share of pixels drawn lit, the share [MoonDisc.isLit] says
     * are lit (the same pixels, so the rim and the edge cancel out), and the share of the drawn lit
     * pixels that are on the right half.
     */
    private fun litShares(bmp: Bitmap, s: WidgetState): Triple<Double, Double, Double> {
        val density = context.resources.displayMetrics.density
        val c = bmp.width / 2.0
        val r = c - 2 * density
        val inner = r - 1.5 * density
        var disc = 0
        var lit = 0
        var expected = 0
        var litRight = 0
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            val dx = x + 0.5 - c
            val dy = y + 0.5 - c
            if (hypot(dx, dy) > inner) continue
            disc++
            if (MoonDisc.isLit(s.fraction, if (s.litOnRight) dx else -dx, dy, r)) expected++
            if (Color.red(bmp.getPixel(x, y)) > 76) {
                lit++
                if (dx > 0) litRight++
            }
        }
        return Triple(lit.toDouble() / disc, expected.toDouble() / disc, if (lit == 0) 0.5 else litRight.toDouble() / lit)
    }

    fun testTheMoonLightsThePrintedShareOnTheRightSide() {
        for ((name, s) in states()) {
            val bmp = moon(s, 72)
            save(bmp, "moon-$name")
            val (share, expected, right) = litShares(bmp, s)
            assertEquals("$name lit share", expected, share, 0.02)
            // The drawn share is the printed percentage, but for the thin outer ring left out here.
            assertEquals("$name lit share against the percentage", s.fraction, share, 0.05)
            if (s.fraction in 0.05..0.95) {
                assertEquals("$name lit side", s.litOnRight, right > 0.5)
            }
        }
        // Waxing in Oslo is lit on the right; the same moon from Sydney on the left.
        assertTrue(oslo().litOnRight)
        assertFalse(sydney().litOnRight)
    }

    fun testThePolarDaysAreWhatTheTestExpects() {
        assertEquals(LunarDayKind.ALWAYS_UP, longyearbyenUp().dayKind)
        assertEquals(LunarDayKind.ALWAYS_DOWN, longyearbyenDown().dayKind)
        assertEquals(LunarDayKind.NORMAL, oslo().dayKind)
    }

    // ------------------------------------------------------------------ the three sizes

    fun testSmallFits() {
        for ((name, s) in states()) for ((w, h) in listOf(57f to 51f, 110f to 110f)) {
            val (root, _) = render(MoonWidgets.renderSmall(context, s, moon(s, 56)), w, h, "small-$name-${w.toInt()}x${h.toInt()}")
            assertTextsFit(root, "small $name ${w}x$h")
            assertEquals(View.GONE, find(root, R.id.hint).visibility)
            assertEquals(View.VISIBLE, find(root, R.id.content).visibility)
        }
    }

    /** The 2x1's rows are the lines of one view: one line per row, so they share one text size. */
    fun testMediumRowsShareOneViewAndFit() {
        for ((name, s) in states()) for ((w, h) in listOf(117f to 51f, 130f to 51f, 260f to 90f)) {
            val (root, _) = render(MoonWidgets.renderMedium(context, s, moon(s, 40)), w, h, "medium-$name-${w.toInt()}x${h.toInt()}")
            assertTextsFit(root, "medium $name ${w}x$h")
            val rows = find(root, R.id.rows) as TextView
            val lines = WidgetText.timeLines(s)
            assertEquals("medium $name ${w}x$h text", WidgetText.mediumText(s), rows.text.toString())
            assertEquals("medium $name ${w}x$h: a row wrapped", lines.size, rows.layout.lineCount)
            assertTrue("medium $name ${w}x$h: rows below 8 dp", rows.textSize >= px(8f) - 1)
            assertEquals(View.GONE, find(root, R.id.hint).visibility)
        }
    }

    /**
     * Each arrangement at the smallest size LargeLayout gives it (every entry of LargeLayout.SIZES,
     * so TALL at 240 x 200 and WIDE at 370 x 110), the largest COMPACT sizes just under them, and
     * what the launcher picks for common widget sizes: the default 4x2 on a 1080 x 1920, 420 dpi
     * phone (292 x 184, COMPACT), the same in landscape (412 x 117, WIDE) and the review's
     * 340 x 170. With the zone note (Sydney) and both polar sentences (Longyearbyen).
     */
    fun testLargeFitsEveryArrangementAtItsSizes() {
        val sizes = LargeLayout.SIZES + listOf(
            Triple(LargeLayout.TALL_MIN_WIDTH_DP - 1f, 400f, LargeLayout.Arrangement.COMPACT),
            Triple(LargeLayout.WIDE_MIN_WIDTH_DP - 1f, LargeLayout.TALL_MIN_HEIGHT_DP - 1f, LargeLayout.Arrangement.COMPACT),
            Triple(180f, 260f, LargeLayout.Arrangement.COMPACT),
        ) + listOf(292f to 184f, 412f to 117f, 340f to 170f, 260f to 213f, 380f to 220f).map { (w, h) ->
            Triple(w, h, LargeLayout.chosenOnApi31(w, h))
        }
        assertEquals(LargeLayout.Arrangement.COMPACT, LargeLayout.chosenOnApi31(292f, 184f))
        val problems = mutableListOf<String>()
        for ((name, s) in states()) for ((w, h, a) in sizes) {
            val tag = "large-$name-${a.name.lowercase()}-${w.toInt()}x${h.toInt()}"
            val (root, _) = render(MoonWidgets.renderLarge(context, s, moon(s, 72), a), w, h, tag)
            // The place name may end in an ellipsis; nothing else may.
            problems += textProblems(root, tag, ellipsisAllowed = setOf(R.id.place))
            if (a == LargeLayout.Arrangement.TALL) assertEquals(View.GONE, find(root, R.id.hint).visibility)
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    // ------------------------------------------------------------------ the hint

    /** A widget not yet drawn: its first layout shows the hint, whole, and nothing else. */
    fun testTheHintFitsBeforeTheFirstRender() {
        val firsts = listOf(
            Triple(R.layout.widget_small, 57f, 51f),
            Triple(R.layout.widget_medium, 117f, 51f),
            Triple(R.layout.widget_large_tall, 180f, 100f),
            Triple(R.layout.widget_large_tall, 260f, 137f),
        )
        for ((layout, w, h) in firsts) {
            val tag = "hint-${context.resources.getResourceEntryName(layout)}-${w.toInt()}x${h.toInt()}"
            val (root, _) = render(RemoteViews(context.packageName, layout), w, h, tag)
            val hint = find(root, R.id.hint) as TextView
            assertEquals(tag, View.VISIBLE, hint.visibility)
            assertEquals(tag, "Open MoonApp once to start the widget.", hint.text.toString())
            assertEquals(tag, View.INVISIBLE, find(root, R.id.content).visibility)
            assertTextsFit(root, tag)
        }
    }
}
