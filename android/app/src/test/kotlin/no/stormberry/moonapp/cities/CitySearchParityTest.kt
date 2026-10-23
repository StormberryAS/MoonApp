package no.stormberry.moonapp.cities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The phone and the website must return the same cities for the same query.
 *
 * `City.kt` states the reason plainly: a person who finds a place on moon.stormberry.as and
 * then cannot find it on their phone reports that as a bug, and rightly so. The two search
 * implementations are independent (JavaScript in `app.js`, Kotlin here) over one shared
 * catalogue, so nothing but a test keeps them honest.
 *
 * The expectations below were captured from the live web picker running the real
 * `cities.js`, in headless Chromium, on 2026-09-04. Accent folding is the part most likely
 * to drift: a Norwegian user types "Askoy" without the slashed o and must still find Askøy.
 *
 * The catalogue is read from data/cities.tsv rather than from Android assets, which is what
 * keeps this a plain JUnit test with no device and no Robolectric.
 *
 * Since 1.0.4 the full ordered list of every query in citysearch-golden.csv is held to the
 * website's, which tools/gen-search-parity.ts writes from the compiled page.
 */
class CitySearchParityTest {

    private fun table(): CityTable {
        // app/src/test/kotlin/... -> repo root is five levels up from the module dir
        val tsv = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "data/cities.tsv") }
            .firstOrNull { it.isFile }
            ?: error("data/cities.tsv not found; the copyCityData task keeps it in sync")
        return tsv.reader().use { CityTable.parse(it) }
    }

    @Test
    fun `catalogue parses to the full published size`() {
        assertEquals("row count drifted from the shared catalogue", 25007, table().cities.size)
    }

    @Test
    fun `top hit matches the website for each probe query`() {
        val t = table()
        // query to the name the web picker put first
        val expected = mapOf(
            "Bergen" to "Bergen",
            "Askoy" to "Askøy",
            "Kleppesto" to "Kleppestø",
            "Tokyo" to "Tokyo",
        )
        for ((query, want) in expected) {
            val hits = CitySearch.search(t, query, limit = 5)
            assertTrue("no results for '$query'", hits.isNotEmpty())
            assertEquals("top hit for '$query' diverges from the website", want, hits.first().name)
        }
    }

    @Test
    fun `accent folding works in both directions`() {
        val t = table()
        // typed without the diacritic, and typed with it: both must find the same place
        val plain = CitySearch.search(t, "Askoy", limit = 3).firstOrNull()?.name
        val accented = CitySearch.search(t, "Askøy", limit = 3).firstOrNull()?.name
        assertEquals("folding is asymmetric between 'Askoy' and 'Askøy'", plain, accented)
        assertEquals("Askøy", plain)
    }

    @Test
    fun `a one-letter query lists at most eight cities`() {
        // Renamed in 1.0.4: this was called "a short query returns nothing rather than the whole
        // catalogue" and its comment said the picker searches from two characters, but it only
        // ever asserted "at most 8", and the website searches from one character. What it checks
        // is the cap, so that is its name now; the full answer is pinned below.
        assertTrue(CitySearch.search(table(), "B", limit = 8).size <= 8)
    }

    private data class Vector(val query: String, val open: Boolean, val rows: List<String>)

    private fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                out.append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; else -> s[i + 1] })
                i += 2
            } else {
                out.append(c); i++
            }
        }
        return out.toString()
    }

    private fun vectors(): List<Vector> =
        javaClass.classLoader!!.getResourceAsStream("citysearch-golden.csv")!!
            .bufferedReader().useLines { lines ->
                lines.filterNot { it.isEmpty() || it.startsWith("#") }.map { l ->
                    val f = l.split("\t")
                    Vector(unescape(f[0]), f[1] == "1", f.drop(2))
                }.toList()
            }

    /**
     * A query of nothing but combining marks folds to nothing. The website then matches every
     * city and lists the first eight of the catalogue; the app deliberately lists none (see
     * [CitySearch.search]). The one known divergence, asserted as one so a change on either side
     * is noticed.
     */
    private fun foldsToNothing(q: String): Boolean = foldQuery(q.trim()).isEmpty()

    @Test
    fun `every query lists the same cities in the same order as the website`() {
        // citysearch-golden.csv is written from the compiled page by tools/gen-search-parity.ts
        // (1.0.4, PLAN section 4.4): 162 queries, from plain and accented names, exonyms and
        // country-only matches to HTML, no match and a single letter. Until 1.0.4 this class
        // checked the top hit of four queries.
        val t = table()
        val all = vectors()
        assertEquals("queries in citysearch-golden.csv", 162, all.size)
        val failures = mutableListOf<String>()
        var divergences = 0
        for (v in all) {
            val got = CitySearch.search(t, v.query, limit = 8).map { "${it.name}|${it.country}|${it.tz}" }
            if (foldsToNothing(v.query)) {
                divergences++
                if (got.isNotEmpty() || !v.open || v.rows.size != 8) {
                    failures += "${'"'}${v.query}${'"'}: the known divergence changed: app $got, website ${v.rows}"
                }
                continue
            }
            if (got != v.rows) failures += "${'"'}${v.query}${'"'}:\n  app     $got\n  website ${v.rows}"
            if (v.open != v.rows.isNotEmpty()) failures += "${'"'}${v.query}${'"'}: the website's list is open with no rows"
        }
        assertEquals("queries that fold to nothing", 2, divergences)
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
