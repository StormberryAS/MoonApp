package no.stormberry.moonapp.widget

import no.stormberry.moonapp.lunar.LunarDayKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Two decisions of Marcos's of 2026-10-30, pinned in the resources and in WidgetText:
 * - the 2x1's three rows shrink together: they are the lines of ONE autosized text view, so they
 *   always share one text size;
 * - a widget that has not been drawn yet says "Open MoonApp once to start the widget." (a widget
 *   placed before the app was ever opened stays on its first layout on Android 12 to 14).
 */
class WidgetLayoutTest {

    private val oslo = ZoneId.of("Europe/Oslo")

    private fun state(kind: LunarDayKind, rise: Instant?, transit: Instant?, set: Instant?) = WidgetState(
        placeLabel = "Oslo", zone = oslo, zoneDiffers = false, today = LocalDate.parse("2026-10-30"),
        fraction = 0.78, phase = 0.36, percent = 78, phaseName = "Waxing Gibbous", litOnRight = true,
        dayKind = kind, rise = rise, transit = transit, set = set, phases = emptyList(),
    )

    private fun res(rel: String): File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .flatMap { sequenceOf(File(it, "src/main/res/$rel"), File(it, "app/src/main/res/$rel"), File(it, "android/app/src/main/res/$rel")) }
            .firstOrNull { it.exists() } ?: error("res/$rel not found")

    private fun xml(rel: String): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
            .newDocumentBuilder().parse(res(rel)).documentElement

    private fun Element.all(tag: String? = null): List<Element> {
        val nodes = getElementsByTagName(tag ?: "*")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.byId(id: String): Element? = all().firstOrNull { it.getAttribute("android:id") == "@+id/$id" }

    private fun style(name: String): Map<String, String> {
        val s = xml("values/widget_styles.xml").all("style").single { it.getAttribute("name") == name }
        return s.all("item").associate { it.getAttribute("name") to it.textContent.trim() }
    }

    @Test fun `the 2x1 lines, top to bottom`() {
        val normal = state(
            LunarDayKind.NORMAL,
            Instant.parse("2026-10-30T16:56:00Z"), Instant.parse("2026-10-31T02:35:00Z"), Instant.parse("2026-10-31T13:23:00Z"),
        )
        assertEquals("Rise 17:56\nTransit 03:35\nSet 14:23", WidgetText.mediumText(normal))
        val noRise = normal.copy(rise = null)
        assertEquals("Rise \u2013\nTransit 03:35\nSet 14:23", WidgetText.mediumText(noRise))
        val up = state(LunarDayKind.ALWAYS_UP, null, Instant.parse("2026-10-31T02:14:00Z"), null)
        assertEquals("Always Up\nTransit 03:14", WidgetText.mediumText(up))
        val down = state(LunarDayKind.ALWAYS_DOWN, null, null, null)
        assertEquals("Always Down", WidgetText.mediumText(down))
        assertEquals(
            listOf(WidgetText.Tint.RISE, WidgetText.Tint.TRANSIT, WidgetText.Tint.SET),
            WidgetText.timeLines(normal).map { it.tint },
        )
    }

    /** One text view holds every row of the 2x1, and it autosizes: so all rows share one size. */
    @Test fun `the 2x1 rows share one text size`() {
        for (layout in listOf("layout/widget_medium.xml", "layout/widget_medium_preview.xml")) {
            val root = xml(layout)
            val texts = root.all("TextView").map { it.getAttribute("android:id") }
            assertEquals(layout, listOf("@+id/percent", "@+id/rows"), texts.filter { it != "@+id/hint" })
            for (gone in listOf("row1", "row2", "row3")) assertEquals(layout, null, root.byId(gone))
            assertEquals(layout, "@style/Widget.MoonApp.MediumRows", root.byId("rows")!!.getAttribute("style"))
        }
        val s = style("Widget.MoonApp.MediumRows")
        assertEquals("uniform", s["android:autoSizeTextType"])
        assertEquals("3", s["android:maxLines"])
        assertEquals("12sp", s["android:autoSizeMaxTextSize"])
        assertEquals("8dp", s["android:autoSizeMinTextSize"])
        assertEquals(
            "Rise 13:53\\nTransit 20:51\\nSet 02:30",
            xml("layout/widget_medium_preview.xml").byId("rows")!!.getAttribute("android:text"),
        )
    }

    /** Each provider's first layout shows the hint and hides its empty content until it is drawn. */
    @Test fun `a widget not yet drawn asks for the app to be opened once`() {
        val hint = xml("values/strings.xml").all("string").single { it.getAttribute("name") == "widget_hint" }.textContent
        assertEquals("Open MoonApp once to start the widget.", hint)
        assertFalse(hint.contains('\u2014'))
        assertFalse(hint.contains("--"))
        for (info in listOf("xml/widget_small_info.xml", "xml/widget_medium_info.xml", "xml/widget_large_info.xml")) {
            val initial = xml(info).getAttribute("android:initialLayout").removePrefix("@layout/")
            val root = xml("layout/$initial.xml")
            val h = root.byId("hint")
            assertTrue("$initial has no hint", h != null)
            assertEquals(initial, "@string/widget_hint", h!!.getAttribute("android:text"))
            assertEquals(initial, "", h.getAttribute("android:visibility"))
            val content = root.byId("content")
            assertTrue("$initial has no content view", content != null)
            assertEquals(initial, "invisible", content!!.getAttribute("android:visibility"))
        }
        // The previews are never placeholders.
        for (preview in listOf("widget_small_preview", "widget_medium_preview", "widget_large_preview")) {
            assertEquals(preview, null, xml("layout/$preview.xml").byId("hint"))
        }
    }
}
